package com.hmdp.exception;
/**
 * 当doubleCheck时，发现缓存已经被重建，用于终止当前线程的执行。
 * 原因如下：当我们检测到不需要执行缓存重建，无论是返回R还是返回null都是不合理的，所以我们需要终止线程的执行
 * 抛出异常，然后交给全局异常处理类，而且不做任何提示，就相当于悄悄终止当前线程的执行
 */
public class AlreadyRebuildCacheException extends RuntimeException{
    public AlreadyRebuildCacheException(String message) {
        super(message);
    }
}
