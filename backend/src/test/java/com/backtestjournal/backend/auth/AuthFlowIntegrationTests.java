package com.backtestjournal.backend.auth;

import com.backtestjournal.backend.User.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "telegram.bot.token=",
        "telegram.bot.username=test_bot",
        "telegram.bot.polling-enabled=false",
        "auth.code-secret=test-code-secret-that-is-at-least-32-bytes"
})
@AutoConfigureMockMvc
class AuthFlowIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void csrfEndpointProvidesToken() throws Exception {
        mockMvc.perform(get("/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void verifiedSessionCanReadCurrentUserAndLogOut() throws Exception {
        User user = user();
        when(authService.verify("abcdefghijklmnopqrstuvwx", "123456")).thenReturn(user);
        when(authService.findUser(7L)).thenReturn(user);

        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());

        MvcResult login = mockMvc.perform(post("/auth/telegram/verify")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"linkToken":"abcdefghijklmnopqrstuvwx","code":"123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7))
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        mockMvc.perform(get("/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Trader"));

        mockMvc.perform(post("/auth/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent());
        assertTrue(session.isInvalid());

        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    private static User user() {
        User user = new User();
        user.setId(7L);
        user.setTelegramId(42L);
        user.setDisplayName("Trader");
        user.setTelegramUsername("trader");
        return user;
    }
}
