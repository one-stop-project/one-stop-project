package com.spartafarmer.one_stop.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the default Spring Security auto-configuration behavior that takes effect
 * after SecurityConfig.java was deleted from this PR.
 *
 * Without a custom SecurityConfig, Spring Boot applies its security defaults:
 * - All endpoints require authentication
 * - Unauthenticated requests are redirected to /login (form login)
 * - HTTP Basic is also enabled
 * - CSRF protection is active (unlike the deleted config which disabled it)
 */
@WebMvcTest
@AutoConfigureMockMvc
class DefaultSecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Unauthenticated GET to any path is redirected to /login by default security")
    void unauthenticatedGet_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("Unauthenticated GET to admin path is redirected to /login")
    void unauthenticatedAdminPath_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("Unauthenticated GET to seller path is redirected to /login")
    void unauthenticatedSellerPath_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/api/seller/products"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("Unauthenticated GET to auth path is redirected to /login - no permitAll without SecurityConfig")
    void unauthenticatedAuthPath_redirectsToLogin() throws Exception {
        // Previously /api/auth/** was permitAll() in the deleted SecurityConfig.
        // Now, with default auto-config, /api/auth/** is also protected.
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("Default /login page is accessible without authentication")
    void loginPage_isPubliclyAccessible() throws Exception {
        // Spring Boot auto-configures a default login page at /login
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Authenticated user accessing non-existent path gets 404, not redirect")
    @WithMockUser(username = "user", roles = {"USER"})
    void authenticatedUser_nonExistentPath_returns404() throws Exception {
        // An authenticated user is not redirected; gets 404 because no controller handles the path
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Authenticated ADMIN user accessing admin path gets 404 without custom role restriction")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void authenticatedAdmin_adminPath_returns404WithoutCustomRoleRestriction() throws Exception {
        // Without SecurityConfig, there is no hasRole("ADMIN") restriction on /api/admin/**.
        // Any authenticated user can attempt this path; no controller → 404.
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Authenticated SELLER user accessing seller path gets 404 without custom role restriction")
    @WithMockUser(username = "seller", roles = {"SELLER"})
    void authenticatedSeller_sellerPath_returns404WithoutCustomRoleRestriction() throws Exception {
        // Without SecurityConfig defining hasRole("SELLER") for /api/seller/**,
        // any authenticated user can attempt this path.
        mockMvc.perform(get("/api/seller/products"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Unauthenticated POST is redirected to login - CSRF is active in default config")
    void unauthenticatedPost_redirectsToLogin() throws Exception {
        // Default security config includes CSRF protection (unlike the deleted SecurityConfig
        // which disabled CSRF). Unauthenticated POST gets redirected to login first.
        mockMvc.perform(post("/api/orders"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("Swagger UI path is not publicly accessible without SecurityConfig permitAll")
    void swaggerUiPath_isNotPublicWithoutSecurityConfig() throws Exception {
        // The deleted SecurityConfig had .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
        // Without it, these paths require authentication.
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("Actuator path is not publicly accessible without SecurityConfig permitAll")
    void actuatorPath_isNotPublicWithoutSecurityConfig() throws Exception {
        // The deleted SecurityConfig had .requestMatchers("/actuator/**").permitAll()
        // Without it, actuator endpoints require authentication.
        // Also, actuator dependency was removed in this PR, so 404 after auth is expected.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }
}
