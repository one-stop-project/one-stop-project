package com.sparta.one_stop.global.audit;
import com.sparta.one_stop.global.response.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/security/audit-logs") @RequiredArgsConstructor @Validated
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class SecurityAuditController {
 private final SecurityAuditLogRepository repository; private final SecurityAuditService audit;
 @GetMapping
 public ResponseEntity<ApiResponse<Page<SecurityAuditLogResponse>>> search(
  @RequestParam SecurityAuditEventType eventType,@RequestParam(defaultValue="false") boolean suspicious,
  @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){
   Page<SecurityAuditLogResponse> result=repository
    .findByEventTypeAndSuspiciousOrderByOccurredAtDesc(eventType,suspicious,PageRequest.of(page,size))
    .map(SecurityAuditLogResponse::from);
   audit.record(SecurityAuditEvent.builder().eventType(SecurityAuditEventType.SECURITY_AUDIT_LOG_VIEWED).result("SUCCESS").ruleCode("AUDIT_VIEW").build());
   return ResponseEntity.ok(ApiResponse.success(result));
 }
}
