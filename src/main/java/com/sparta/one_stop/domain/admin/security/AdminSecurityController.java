package com.sparta.one_stop.domain.admin.security;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/security") @RequiredArgsConstructor @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class AdminSecurityController {
 private final AdminSecurityActionService service;
 @PostMapping("/users/{userId}/actions")
 public ResponseEntity<ApiResponse<SecurityActionResponse>> execute(@PathVariable Long userId,@Valid @RequestBody SecurityActionRequest request,@AuthenticationPrincipal AuthUser admin){
  return ResponseEntity.ok(ApiResponse.success(service.execute(admin.userId(),userId,request)));
 }
}
