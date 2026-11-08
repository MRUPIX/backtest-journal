package com.backtestjournal.backend.auth;

import com.backtestjournal.backend.User.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTests {

    @Mock
    private AuthService authService;

    @Mock
    private LoginRateLimiter loginRateLimiter;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService, new HttpSessionSecurityContextRepository(), loginRateLimiter);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void verifyCreatesAuthenticatedSession() {
        User user = user(7L, "Trader", "trader");
        when(authService.verify("abcdefghijklmnopqrstuvwx", "123456")).thenReturn(user);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession();

        AuthController.UserResponse result = controller.verify(
                new AuthController.VerifyRequest("abcdefghijklmnopqrstuvwx", "123456"),
                request, new MockHttpServletResponse());

        SecurityContext stored = (SecurityContext) request.getSession().getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertNotNull(stored);
        assertEquals("7", stored.getAuthentication().getName());
        assertEquals("ROLE_USER", stored.getAuthentication().getAuthorities().iterator().next().getAuthority());
        assertEquals(7L, result.userId());
        assertEquals("Trader", result.displayName());
    }

    @Test
    void meResolvesUserFromAuthenticatedPrincipal() {
        User user = user(7L, "Trader", "trader");
        when(authService.findUser(7L)).thenReturn(user);
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated("7", null, List.of());

        AuthController.UserResponse result = controller.me(authentication);

        verify(authService).findUser(7L);
        assertEquals("trader", result.telegramUsername());
    }

    private static User user(long id, String displayName, String telegramUsername) {
        User user = new User();
        user.setId(id);
        user.setTelegramId(42L);
        user.setDisplayName(displayName);
        user.setTelegramUsername(telegramUsername);
        return user;
    }
}
