package com.sparta.one_stop.domain.admin.security;

import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/security")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminSecurityController {

    private final AdminSecurityActionService service;

    @PostMapping("/users/{userId}/actions")
    public ResponseEntity<ApiResponse<SecurityActionResponse>> execute(
        @PathVariable Long userId,
        @Valid @RequestBody SecurityActionRequest request,
        @AuthenticationPrincipal AuthUser admin
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            service.execute(admin.userId(), userId, request)));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<SecurityTargetResponse>> getTarget(
        @PathVariable Long userId,
        @AuthenticationPrincipal AuthUser admin
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.getTarget(admin.userId(), userId)));
    }
}
