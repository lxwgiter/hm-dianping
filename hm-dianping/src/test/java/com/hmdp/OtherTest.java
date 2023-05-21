package com.hmdp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@SpringBootTest
public class OtherTest {

    @Test
    public void test1(){
        long BEGIN_TIMESTAMP = 1640995200L;
        String s = Long.toBinaryString(BEGIN_TIMESTAMP);
        System.out.println(s);
        String s1 = Long.toBinaryString(BEGIN_TIMESTAMP << 32);
        System.out.println(s1);
    }
    @Test
    public  void test2(){
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        System.out.println(nowSecond);
    }
}
