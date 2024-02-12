package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIDWorker {

    private static final long BEGIN_TIMESTAMP = 1704067200L;
    private static final int COUNT_BITS = 32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix){
        //生成时间戳
        LocalDateTime currentTime = LocalDateTime.now();
        long currentSecond = currentTime.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = currentSecond - BEGIN_TIMESTAMP;

        //生成序列号
        //获取当前日期，精确到天
        String date = currentTime.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //一天一个新的key
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);



        //拼接并返回
        return timeStamp << COUNT_BITS | count;
    }

}
