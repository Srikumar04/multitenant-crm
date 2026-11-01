package com.srikumar.multitenantcrm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.srikumar.multitenantcrm.auth.JwtAuthenticationFilter;
import com.srikumar.multitenantcrm.auth.JwtService;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.User.UserBuilder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * JWT auth for tenant-scoped requests (populates TenantContext for RLS), plus HTTP Basic for a
 * single in-memory PLATFORM_ADMIN account that guards tenant onboarding - the two coexist
 * because they use different Authorization schemes (Bearer vs Basic).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper)
            throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/tenants").hasRole("PLATFORM_ADMIN")
                .anyRequest().authenticated()
            )
            // Writing the problem+json body directly (not response.sendError()) matters under
            // SessionCreationPolicy.STATELESS: sendError() triggers a servlet-container /error
            // forward, which re-enters this same (stateless, re-challenged) filter chain and
            // silently overwrites a 403 with a 401 before the client ever sees it.
            .exceptionHandling(ex -> ex
                .accessDeniedHandler(problemDetailHandler(objectMapper, HttpStatus.FORBIDDEN, "Access is denied"))
                .authenticationEntryPoint(problemDetailEntryPoint(
                        objectMapper, HttpStatus.UNAUTHORIZED, "Full authentication is required to access this resource"))
            )
            .httpBasic(Customizer.withDefaults())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private AccessDeniedHandler problemDetailHandler(ObjectMapper objectMapper, HttpStatus status, String detail) {
        return (request, response, accessDeniedException) -> writeProblemDetail(objectMapper, response, status, detail);
    }

    private AuthenticationEntryPoint problemDetailEntryPoint(ObjectMapper objectMapper, HttpStatus status, String detail) {
        return (request, response, authException) -> writeProblemDetail(objectMapper, response, status, detail);
    }

    private void writeProblemDetail(ObjectMapper objectMapper, jakarta.servlet.http.HttpServletResponse response,
            HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ProblemDetail.forStatusAndDetail(status, detail));
    }

    @Bean
    JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService) {
        return new JwtAuthenticationFilter(jwtService);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(
            PasswordEncoder passwordEncoder,
            @Value("${app.platform-admin.username}") String username,
            @Value("${app.platform-admin.password}") String password) {
        UserBuilder builder = User.withUsername(username);
        return new InMemoryUserDetailsManager(
                builder.password(passwordEncoder.encode(password)).roles("PLATFORM_ADMIN").build());
    }
}
