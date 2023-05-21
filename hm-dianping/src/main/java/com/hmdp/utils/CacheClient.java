package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jodd.util.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

@Component
public class CacheClient {

    /**
     *  这里可以不用@Resource,采用构造器注入，组件依赖会从IOC中找，然后自动注入，需要提供构造函数。
     */
    private final StringRedisTemplate stringRedisTemplate;
    /**
     * 线程池
     */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 实现任意Java对象向redis中的存储
     * @param key 键
     * @param value 值
     * @param time 时间
     * @param unit 时间单位
     */
    public void set(String key,Object value ,Long time ,TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(value),time,unit);
    }

    /**
     * 实现任意Java对象向redis中的存储，并且加上逻辑过期时间
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void setWithLogicalExpire(String key,Object value ,Long time ,TimeUnit unit){
        //设置逻辑过期时间
        RedisData redisData=new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    /**
     * 具有解决解决缓存穿透功能的查询,但是仍然存在问题：
     * 通过jmeter测试，确实可以解决缓存穿透，但是高并发的情况下，在空缓存还没有建立的时候，仍有大量并发的线程取查询数据库
     * @param keyPrefix 键的前缀
     * @param id 根据id查询
     * @param type 返回值类型
     * @param dbFallback 查询数据库时的逻辑
     * @param time 时间
     * @param unit 时间单位
     * @return
     * @param <R> 指定返回值类型
     * @param <ID> 指定id类型
     */
    public <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit){

        String key=keyPrefix+id;
        //1.从redis中查询商铺缓存
        String json=stringRedisTemplate.opsForValue().get(key);
        //2.判断是否为null或空字符串
        if(StringUtil.isNotBlank(json)){
            //3.存在商铺缓存，直接返回
            return JSONUtil.toBean(json,type);
        }
        //判断是否为 ""(空值)
        if("".equals(json)){
            //为空值,返回错误信息
            return null;
        }
        //4.json==null的情况，即缓存中不存在商铺缓存的情况，到数据库中查询
        R r = dbFallback.apply(id);
        //5.判断数据库中是否存在数据
        if(r == null){
            //5.1不存在该数据，缓存空值,并返回错误信息
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //5.2存在该数据，写入redis中
        this.set(key,r,time,unit);
        return r;
    }

    /**
     *使用互斥锁方案，解决缓存击穿问题，顺带着解决了缓存穿透问题的查询方法
     * 真正安全的方法，完美解决高并发情境下的缓存击穿和缓存穿透，但是由于是悲观锁，所以效率较低
     * @param keyPrefix 键的前缀
     * @param id 根据id查询
     * @param type 返回值类型
     * @param dbFallback 查询数据库时的逻辑
     * @param time 时间
     * @param unit 时间单位
     * @return
     * @param <R> 指定返回值类型
     * @param <ID> 指定id类型
     */
    public <R,ID> R queryWithMutex(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit){
        String key=keyPrefix+id;
        //1.从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StringUtil.isNotBlank(shopJson)){
            //3.存在，直接返回
            return JSONUtil.toBean(shopJson,type);
        }
        //判断是否为空值（""）
        if("".equals(shopJson)){
            //是空值，返回错误
            return null;
        }
        //4.shopJson == null 的情况，实现缓存重建
        //4.1获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isGetLock = tryGetLock(lockKey);
        R r= null;
        try {
            if(!isGetLock){
                //4.2没有成功获取锁，那么该线程就无法获取锁了，线程休眠50毫秒后递归，继续尝试去redis中读取缓存，直到读到新缓存
                Thread.sleep(50);
                return queryWithMutex(keyPrefix,id,type,dbFallback,time,unit);
            }
            //5.成功获取锁，获取锁的线程执行缓存重建，但
            // 是有一种极端情况，就是另一个线程已经完成缓存重建，刚释放锁的情况，所以进行doubleCheck
            R alreadyRebuild = doubleCheckForMutex(key, type);
            if(alreadyRebuild != null){
                //System.out.println("缓存已经重建！");
                return alreadyRebuild;
            }
            //5.1缓存还没有重建,查询数据库
            r = dbFallback.apply(id);
            //5.2数据库中不存在，返回错误
            if(r == null){
                //将空值写入redis
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                //返回错误信息
                return null;
            }
            //5.3数据库中存在，写入redis
            this.set(key,r,time,unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //6.释放锁
            unlock(lockKey);
        }
        return  r;
    }

    /**
     * 获取互斥锁的方法，实现方式是通过redis的setnx命令：在指定的 key 不存在时，
     * 为 key 设置指定的值，这种情况下等同 [SET] 命令。当 `key`存在时，什么也不做。
     * @param key 锁名称
     * @return true:获取成功、false:获取失败
     */
    private boolean tryGetLock(String key){
        //为锁设置TTL，防止锁没有被正常释放
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        //解决包装类拆箱可能会出现的空指针问题
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放互斥锁的方法
     * @param key 锁名称
     */
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    /**
     *使用逻辑过期方案，解决缓存击穿问题
     * 使用逻辑过期方案的前提是，缓存必须先预热，不管是否逻辑过期，缓存中需要有数据
     * @param keyPrefix 键的前缀
     * @param id 根据id查询
     * @param type 返回值类型
     * @param dbFallback 查询数据库时的逻辑
     * @param time 时间
     * @param unit 时间单位
     * @return
     * @param <R> 指定返回值类型
     * @param <ID> 指定id类型
     */
    public <R,ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit){
        String key=keyPrefix+id;
        //1.向redis中查询商铺缓存
        String json=stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在，这里是肯定存在的，因为我们先进行了缓存的预热
        if(StringUtil.isBlank(json)){
            //3.不存在，返回错误
            return null;
        }
        //4.命中，需要先把json序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        //5.判断是否逻辑过期
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())){
            //5.1 未过期，直接返回商铺信息
            return r;
        }
        //5.2已过期，需要缓存重建
        //6.缓存重建
        //6.1获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isGetLock = tryGetLock(lockKey);
        //6.2判断获取锁是否成功
        if(isGetLock){
           //6.3获取成功，开启独立线程，实现缓存重建,但是有一种极端情况，就是另一个线程已经完成缓存重建，刚释放锁的情况，所以进行doubleCheck
            //判断是否还需要进行缓存重建
            R check = doubleCheckForLogicalExpire(key, type);
            //不需要进行缓存重建
            if(check != null){
                System.out.println("缓存已经重建！");
                return check;
            }
            //仍需要进行缓存重建
           CACHE_REBUILD_EXECUTOR.submit(()->{
               try {
                   //查询数据库
                   R newR = dbFallback.apply(id);
                   //重建缓存
                   this.setWithLogicalExpire(key,newR,time,unit);
               } catch (Exception e) {
                   throw new RuntimeException(e);
               } finally {
                   unlock(lockKey);
               }

           });
        }
        //6.4返回过期的店铺信息
        return r;
    }

    /**
     * 为互斥锁方案解决缓存击穿的方法提供的方法，用于检查缓存中的数据是否已经被更新
     * @param key redis键
     * @return null：再次检验后发现仍需更新，r：再次检查后发现无需再更新缓存
     */
    private <R> R doubleCheckForMutex(String key,Class<R> type){
        //向redis中查询商铺缓存
        String value = stringRedisTemplate.opsForValue().get(key);
        //当value == null 时说明，缓存中仍然没有数据
        if (value == null){
            return null;
        }
        //当缓存中是""或者有值时，说明缓存已经进行了重建。
        if ("".equals(value)) {
            //这里return null并不完美，我们的本意是，当有空字符串时，意味着缓存已经重建，且为空对象，是不想让缓存再次重建的
            return null;
        }
        return JSONUtil.toBean(value,type);
    }

    /**
     * 为逻辑过期方案解决缓存击穿的方法提供的方法，用于检查缓存中的数据是否已经被更新
     * @param key redis键
     * @return null：再次检验后发现仍需更新，r：再次检查后发现无需再更新缓存
     */
    private <R> R doubleCheckForLogicalExpire(String key, Class<R> type){
        //向redis中查询商铺缓存
        String json=stringRedisTemplate.opsForValue().get(key);
        //判断是否存在，这里是肯定存在的，因为我们先进行了缓存的预热
        if(StringUtil.isBlank(json)){
            //不存在，返回错误
            throw new RuntimeException("商品信息不存在，请确认是否预热过商铺信息！");
        }
        //命中，需要先把json序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);

        //判断是否逻辑过期
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())){
            //5.1 未过期，直接返回商铺信息
            R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
            return r;
        }
        //已过期，需要缓存重建
        return null;
    }


}
