package com.hmdp.exception;

import com.hmdp.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AlreadyRebuildCacheException.class)
    public void exceptionHandler(AlreadyRebuildCacheException ex){
        //捕获异常，结束当前线程。
        log.debug("捕获到AlreadyRebuildCacheException异常！{}",ex.getMessage());
        return ;
    }

    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        log.error(e.toString(), e);
        return Result.fail("服务器异常");
    }
}
