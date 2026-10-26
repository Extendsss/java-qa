package com.quiz.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.quiz.security.SessionService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {
    private final SessionService session;
    public SaTokenConfigure(SessionService session) { this.session = session; }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
            StpUtil.checkLogin();
            session.current();
            SaRouter.match("/api/admin/**").check(r -> StpUtil.checkRole("ADMIN"));
        })).addPathPatterns("/api/**").excludePathPatterns("/api/auth/login", "/api/ping");
    }
}
