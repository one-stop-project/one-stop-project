package com.sparta.one_stop.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class CorsConfigTest {

    @Test
    void validatedOriginsAreTrimmedBeforeTheyAreApplied() {
        Environment environment = mock(Environment.class);
        given(environment.acceptsProfiles(any(Profiles.class))).willReturn(false);
        CorsConfig corsConfig = new CorsConfig(environment);
        ReflectionTestUtils.setField(corsConfig, "allowedOrigins",
            List.of(" http://localhost:8080 ", "https://example.com"));

        corsConfig.validateAllowedOrigins();

        var request = new MockHttpServletRequest("GET", "/api/products");
        var applied = corsConfig.corsConfigurationSource().getCorsConfiguration(request);
        assertThat(applied).isNotNull();
        assertThat(applied.getAllowedOrigins())
            .containsExactly("http://localhost:8080", "https://example.com");
    }

    @Test
    void productionRejectsInsecureOrigin() {
        Environment environment = mock(Environment.class);
        given(environment.acceptsProfiles(any(Profiles.class))).willReturn(true);
        CorsConfig corsConfig = new CorsConfig(environment);
        ReflectionTestUtils.setField(corsConfig, "allowedOrigins",
            List.of("http://example.com"));

        assertThatThrownBy(corsConfig::validateAllowedOrigins)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must use HTTPS");
    }
}
