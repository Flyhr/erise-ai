package com.erise.ai.backend.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// 配置全局跨域资源共享（CORS）规则，解决前端与后端之间的跨域访问限制问题，允许前端应用从不同的域名/端口访问后端API
//生产环境需要更换跨域规则，限制特定来源和方法，增强安全性
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {

        registry.addMapping("/**")// 对项目中所有请求路径（如 /api/user、/admin/info）生效跨域规则
                .allowedOriginPatterns("*")//允许所有来源（不同域名 / 端口的前端页面）发起跨域请求（新版本替代allowedOrigins）
                .allowedMethods("*")//允许所有 HTTP 请求方法（GET、POST、PUT、DELETE、OPTIONS 等）
                .allowedHeaders("*")//允许请求中携带所有请求头（如 Token、Content-Type 等）
                .exposedHeaders("X-Trace-Id");//主动暴露自定义响应头X-Trace-Id，让前端可以获取到该响应头的值
    }
}
