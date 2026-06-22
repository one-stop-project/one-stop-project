package com.sparta.one_stop.global.audit;

import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.security.AuthUser;
import com.sparta.one_stop.global.security.JwtTokenProvider;
import com.sparta.one_stop.domain.auth.service.AuthQueryService;
import com.sparta.one_stop.domain.auth.service.RedisTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SecurityAuditController.class)
@Import(SecurityAuditControllerTest.MethodSecurityConfig.class)
class SecurityAuditControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean SecurityAuditLogRepository repository;
    @MockitoBean SecurityAuditService audit;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean RedisTokenService redisTokenService;
    @MockitoBean AuthQueryService authQueryService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void criticalUsesApiResponseEnvelope() throws Exception {
        given(repository.findBySeverityAndOccurredAtAfterOrderByOccurredAtDesc(
            eq(SecuritySeverity.CRITICAL), any(), any()))
            .willReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/admin/security-audit/critical")
                .with(authentication(superAdmin())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void highRiskUsesApiResponseEnvelope() throws Exception {
        given(repository.findBySeverityInAndOccurredAtAfterOrderByOccurredAtDesc(
            any(), any(), any())).willReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/admin/security-audit/high-risk")
                .with(authentication(superAdmin())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void categoryStatsUsesApiResponseEnvelope() throws Exception {
        given(repository.countGroupedByCategory(any()))
            .willReturn(List.<Object[]>of(new Object[]{"AUTH", 3L}));

        mockMvc.perform(get("/api/admin/security-audit/stats/by-category")
                .with(authentication(superAdmin())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.AUTH").value(3));
    }

    @Test
    void requestParameterRangesAreValidated() throws Exception {
        mockMvc.perform(get("/api/admin/security-audit/critical")
                .param("hours", "0")
                .with(authentication(superAdmin())))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/security-audit/high-risk")
                .param("days", "91")
                .with(authentication(superAdmin())))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/security-audit/critical")
                .param("page", "-1")
                .with(authentication(superAdmin())))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/security-audit/critical")
                .param("size", "101")
                .with(authentication(superAdmin())))
            .andExpect(status().isBadRequest());
    }

    private UsernamePasswordAuthenticationToken superAdmin() {
        AuthUser principal = new AuthUser(1L, UserRole.SUPER_ADMIN);
        return new UsernamePasswordAuthenticationToken(
            principal, null, principal.authorities());
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }
}
