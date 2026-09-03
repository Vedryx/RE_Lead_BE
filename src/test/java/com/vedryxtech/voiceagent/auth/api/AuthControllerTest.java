package com.vedryxtech.voiceagent.auth.api;

import com.vedryxtech.voiceagent.auth.api.dto.LoginResponse;
import com.vedryxtech.voiceagent.auth.application.AuthService;
import com.vedryxtech.voiceagent.common.error.ApiErrorFactory;
import com.vedryxtech.voiceagent.common.error.GlobalExceptionHandler;
import com.vedryxtech.voiceagent.exception.UnauthorizedException;
import com.vedryxtech.voiceagent.user.api.dto.UserResponse;
import com.vedryxtech.voiceagent.user.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The auth surface's shape: login and refresh answer the new
 * (access, refresh, refreshExpires, user) tuple; the login response no longer carries
 * an organization; logout is idempotent 204; a bad refresh is 401.
 */
@WebMvcTest(AuthController.class)
@Import({ApiErrorFactory.class, GlobalExceptionHandler.class,
        com.vedryxtech.voiceagent.config.WebConfig.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void loginReturnsAccessAndRefreshWithoutOrganization() throws Exception {
        given(authService.login(any())).willReturn(new LoginResponse(
                "eyJhcc.access.token", "Bearer", 900L,
                "vrt_r3fr3sh", 2_592_000L, sampleUser()));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@vedryxtech.com\",\"password\":\"a-real-password!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("eyJhcc.access.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900))
                .andExpect(jsonPath("$.refreshToken").value("vrt_r3fr3sh"))
                .andExpect(jsonPath("$.refreshExpiresInSeconds").value(2_592_000))
                .andExpect(jsonPath("$.user.email").value("admin@vedryxtech.com"))
                // The pre-rework field must be gone; a dashboard still reading it would break loudly.
                .andExpect(jsonPath("$.organization").doesNotExist());
    }

    @Test
    void refreshRoundtripsANewPair() throws Exception {
        given(authService.refresh("vrt_old")).willReturn(new LoginResponse(
                "eyJhcc.new", "Bearer", 900L,
                "vrt_new", 2_592_000L, sampleUser()));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"vrt_old\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("eyJhcc.new"))
                .andExpect(jsonPath("$.refreshToken").value("vrt_new"));
    }

    @Test
    void badRefreshIs401() throws Exception {
        willThrow(new UnauthorizedException("Refresh token is invalid or expired"))
                .given(authService).refresh("vrt_dead");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"vrt_dead\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutIsAlways204() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"vrt_whatever\"}"))
                .andExpect(status().isNoContent());
        verify(authService).logout("vrt_whatever");
    }

    private static UserResponse sampleUser() {
        return new UserResponse(
                "6a99abcdef0123456789abcd",
                "admin@vedryxtech.com",
                "Vedryx Admin",
                null,
                Set.of(UserRole.ADMIN),
                Boolean.TRUE,
                OffsetDateTime.parse("2026-09-03T00:00:00Z"),
                OffsetDateTime.parse("2026-09-01T00:00:00Z"));
    }
}
