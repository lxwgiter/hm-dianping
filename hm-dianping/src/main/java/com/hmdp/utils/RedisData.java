package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 这个类用于在不改变其他类的结构的情况下，为他们拓展了逻辑过期的属性，方便我们实现逻辑过期功能
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
