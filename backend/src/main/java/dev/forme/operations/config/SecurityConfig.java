package dev.forme.operations.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.io.IOException;

import tools.jackson.databind.ObjectMapper;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        return http
                .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/api/v1/system/info", "/api/v1/auth/csrf", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/inventory/**").hasAnyRole("OPERATOR", "ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginProcessingUrl("/api/v1/auth/login")
                        .successHandler((request, response, authentication) -> {
                            response.setContentType("application/json;charset=UTF-8");
                            objectMapper.writeValue(response.getWriter(), new LoginSuccessResponse(authentication.getName()));
                        })
                        .failureHandler((request, response, exception) -> writeError(response, objectMapper, 401, "아이디 또는 비밀번호를 확인해 주세요.")))
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> writeError(response, objectMapper, 401, "로그인이 필요합니다."))
                        .accessDeniedHandler((request, response, exception) -> writeError(response, objectMapper, 403, "접근 권한이 없습니다.")))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void writeError(jakarta.servlet.http.HttpServletResponse response, ObjectMapper objectMapper,
                                   int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), new ErrorResponse(status, message));
    }

    private record LoginSuccessResponse(String username) { }
    private record ErrorResponse(int status, String message) { }
}
