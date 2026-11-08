package com.backtestjournal.backend.auth;

import com.backtestjournal.backend.User.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    record VerifyRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{24}") String linkToken,
            @NotBlank @Pattern(regexp = "\\d{6}") String code) { }

    record UserResponse(Long userId, String displayName, String telegramUsername) { }

    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;
    private final LoginRateLimiter loginRateLimiter;

    public AuthController(AuthService authService, SecurityContextRepository securityContextRepository,
                          LoginRateLimiter loginRateLimiter) {
        this.authService = authService;
        this.securityContextRepository = securityContextRepository;
        this.loginRateLimiter = loginRateLimiter;
    }

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }

    @PostMapping("/telegram/start")
    public Map<String, String> start(HttpServletRequest request) {
        loginRateLimiter.check(request.getRemoteAddr());
        return authService.start();
    }

    @PostMapping("/telegram/verify")
    public UserResponse verify(@Valid @RequestBody VerifyRequest req,
                               HttpServletRequest request, HttpServletResponse response) {
        User user = authService.verify(req.linkToken(), req.code());

        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                user.getId().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);

        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return response(user);
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        User user = authService.findUser(Long.parseLong(authentication.getName()));
        return response(user);
    }

    private UserResponse response(User user) {
        return new UserResponse(user.getId(),
                user.getDisplayName() == null ? "" : user.getDisplayName(),
                user.getTelegramUsername() == null ? "" : user.getTelegramUsername());
    }
}
