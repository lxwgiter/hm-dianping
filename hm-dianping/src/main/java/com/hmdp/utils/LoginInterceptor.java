package com.hmdp.utils;

import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.判断是否需要拦截,即ThreadLocal中是否保存了用户
        if(UserHolder.getUser()==null){
            //没有，需要拦截，设置状态码401
            response.setStatus(401);
            return false;
        }
        //存在用户，放行
        return true;
    }

}
