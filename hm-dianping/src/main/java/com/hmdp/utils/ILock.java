package com.hmdp.utils;

public interface ILock {
    /**
     * 尝试获取锁的简单方法,不可重入、不可重试，锁到期不可自动续约
     * @param timeoutSec 锁持有的超时时间，过期后自动释放
     * @return true代表获取锁成功; false代表获取锁失败
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁方法，为了避免判断锁标识和删除锁之间gc可能导致的停顿，我们使用lua脚本来保证这两个操作的原子性
     */
    void unlock();
}
