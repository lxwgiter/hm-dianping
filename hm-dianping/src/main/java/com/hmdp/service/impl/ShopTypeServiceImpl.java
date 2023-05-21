package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    /**
     * 商铺缓存查询
     * @return
     */
    @Override
    public Result getTypeList() {
        //1.从redis中查询缓存
        List<String> shopTypeList = stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, -1);
        //2.缓存命中，直接返回
        if(shopTypeList.size()!=0){
            List<ShopType> beanShop = new ArrayList<>();
            for(String  jsonStr: shopTypeList){
                ShopType bean = JSONUtil.toBean(jsonStr,ShopType.class);
                beanShop.add(bean);
            }
            return Result.ok(beanShop);
        }
        //3.缓存未命中，去数据库查询
        List<ShopType> typeList = query().orderByAsc("sort").list();
        //4.查询到的情况，将数据缓存到redis中,并返回
        if(typeList.size()!=0){
            List<String> jsonShop = new ArrayList<>();
            for(ShopType str : typeList){
                jsonShop.add(JSONUtil.toJsonStr(str));
            }
            //为什么先把list中的对象都先json化？因为rightPushAll方法的最后一个参数其实是可变形参，作用是将所有字符串存储到redis的list中，
            // 若是直接写java-list的json，则相当于redis-list中只有一个元素
            stringRedisTemplate.opsForList().rightPushAll(CACHE_SHOP_TYPE_KEY,jsonShop);
            stringRedisTemplate.expire(CACHE_SHOP_TYPE_KEY,CACHE_SHOP_TYPE_TTL,TimeUnit.MINUTES);
            return Result.ok(typeList);
        }
        //5.数据库没有查询到，返回错误信息
        return Result.fail("未查询到数据！");

    }
}
