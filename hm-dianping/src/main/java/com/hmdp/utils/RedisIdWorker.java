package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    /**
     * 开始时间戳
     */
    private static final long BEGIN_TIMESTAMP = 1677950661L;
    /**
     * 序列号的位数
     */
    private static final int COUNT_BITS = 32;

    private StringRedisTemplate stringRedisTemplate;

    //构造器注入
    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }


    /**
     * 用于获取全局唯一id
     * 原理：long型占64bit，从左到右，第一bit表示符号位，下31bit表示时间戳，可以使用69年
     * 最后32bit表示序列号，在统一时间戳下做区分。通过位运算完成拼接
     * @param keyPrefix id前缀
     * @return 全局唯一id
     */
    public long nextId(String keyPrefix){
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        //2.生成序列号
        //2.1获取当前日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //2.2自增长
        //increment默认每次自增1，返回值count表示自增后的值
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + date);

        //3.拼接并返回
        return timestamp << COUNT_BITS | count;

    }
}
