package com.sparta.one_stop.domain.admin.security;

import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.enums.user.UserStatus;
import com.sparta.one_stop.global.security.AuthUser;
import com.sparta.one_stop.global.security.JwtTokenProvider;
import com.sparta.one_stop.domain.auth.service.AuthQueryService;
import com.sparta.one_stop.domain.auth.service.RedisTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminSecurityController.class)
@Import(AdminSecurityControllerTest.MethodSecurityConfig.class)
class AdminSecurityControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AdminSecurityActionService service;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean RedisTokenService redisTokenService;
    @MockitoBean AuthQueryService authQueryService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void superAdminCanExecuteSecurityAction() throws Exception {
        given(service.execute(eq(1L), eq(2L), any(SecurityActionRequest.class)))
            .willReturn(new SecurityActionResponse(
                2L, SecurityActionType.SUSPEND, "POLICY",
                LocalDateTime.of(2026, 6, 22, 0, 0),
                LocalDateTime.of(2026, 6, 23, 0, 0)));

        mockMvc.perform(post("/api/admin/security/users/2/actions")
                .with(authentication(authToken(1L, UserRole.SUPER_ADMIN)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"actionType":"SUSPEND","reasonCode":"POLICY","durationMinutes":1440}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.targetUserId").value(2))
            .andExpect(jsonPath("$.data.actionType").value("SUSPEND"));
    }

    @Test
    void regularAdminCannotExecuteSecurityAction() throws Exception {
        mockMvc.perform(post("/api/admin/security/users/2/actions")
                .with(authentication(authToken(1L, UserRole.ADMIN)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"actionType":"SUSPEND","reasonCode":"POLICY","durationMinutes":1440}
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void targetLookupUsesApiResponseEnvelope() throws Exception {
        given(service.getTarget(1L, 2L)).willReturn(new SecurityTargetResponse(
            2L, "buyer@test.com", "buyer", UserRole.BUYER, UserStatus.ACTIVE, true));

        mockMvc.perform(get("/api/admin/security/users/2")
                .with(authentication(authToken(1L, UserRole.SUPER_ADMIN))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(2))
            .andExpect(jsonPath("$.data.role").value("BUYER"))
            .andExpect(jsonPath("$.data.sanctionable").value(true));
    }

    private UsernamePasswordAuthenticationToken authToken(Long userId, UserRole role) {
        AuthUser principal = new AuthUser(userId, role);
        return new UsernamePasswordAuthenticationToken(
            principal, null, principal.authorities());
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }
}
