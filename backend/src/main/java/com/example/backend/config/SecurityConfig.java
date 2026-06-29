package com.example.backend.config;

import com.example.backend.filter.JwtAuthenticationFilter;
import com.example.backend.filter.RateLimitFilter;
import com.example.backend.service.RedisService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity  //启用 Web 安全
public class SecurityConfig {

    /**
     * 注册 JWT 认证过滤器 Bean。
     * 备注：不使用 @Component 避免被 Spring Boot 自动注册为 Servlet Filter。
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(RedisService redisService) {
        return new JwtAuthenticationFilter(redisService);
    }

    /**
     * 注册限流过滤器 Bean。
     */
    @Bean
    public RateLimitFilter rateLimitFilter(RedisService redisService) {
        return new RateLimitFilter(redisService);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   RateLimitFilter rateLimitFilter) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests()
                // 放行登录和健康检查接口
                .antMatchers(HttpMethod.GET, "/api/health").permitAll()
                .antMatchers("/api/auth/**").permitAll()
                // 其他接口需要认证
                .anyRequest().authenticated()
                .and()
                // 添加 JWT 过滤器（在用户名密码认证过滤器之前执行）
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // 添加限流过滤器（在 JWT 过滤器之前执行，优先拦截恶意请求）
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }
}

