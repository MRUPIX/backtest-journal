package com.backtestjournal.backend.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    SecurityContextRepository securityContextRepository,
                                    Environment environment) throws Exception {
        boolean localProfile = environment.acceptsProfiles(Profiles.of("local"));

        http
            .csrf(csrf -> {
                if (localProfile) csrf.ignoringRequestMatchers("/h2-console/**");
            })
            .securityContext(context -> context
                    .securityContextRepository(securityContextRepository)
                    .requireExplicitSave(true))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/auth/csrf", "/auth/telegram/**", "/error").permitAll();
                if (localProfile) auth.requestMatchers("/h2-console/**").permitAll();
                auth.anyRequest().authenticated();
            })
            .exceptionHandling(errors -> errors
                    .authenticationEntryPoint((request, response, exception) ->
                            response.sendError(HttpStatus.UNAUTHORIZED.value())))
            .requestCache(cache -> cache.disable())
            .logout(logout -> logout
                    .logoutUrl("/auth/logout")
                    .logoutSuccessHandler((request, response, authentication) ->
                            response.setStatus(HttpStatus.NO_CONTENT.value())))
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }
}
