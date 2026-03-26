package com.erise.ai.cloud.common;

import com.erise.ai.cloud.config.EriseCloudProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalKeyFilter extends OncePerRequestFilter {

    private final EriseCloudProperties properties;

    public InternalKeyFilter(EriseCloudProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/internal/")) {
            String key = request.getHeader("X-Internal-Key");
            if (!properties.getInternal().getApiKey().equals(key)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\":\"Invalid internal key\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
