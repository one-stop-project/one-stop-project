package com.sparta.one_stop.global.audit;

import com.sparta.one_stop.global.security.AuthUser;
import com.sparta.one_stop.global.util.ClientIpExtractor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j @Service @RequiredArgsConstructor
public class SecurityAuditService {
    private final ClientIpExtractor ipExtractor;
    private final SecurityAuditPublisher publisher;

    public void recordSuccess(SecurityAuditEventType type){record(SecurityAuditEvent.builder().eventType(type).result("SUCCESS").build());}
    public void recordSuccess(SecurityAuditEventType type,String resource,String id){record(SecurityAuditEvent.builder().eventType(type).result("SUCCESS").targetResource(resource).targetId(id).build());}
    public void recordFailure(SecurityAuditEventType type,String code,String message){record(SecurityAuditEvent.builder().eventType(type).result("FAILURE").errorCode(code).errorMessage(message).build());}

    public void record(SecurityAuditEvent event){
        try {
            HttpServletRequest req=currentRequest(); AuthUser actor=currentActor();
            publisher.publish(event.toBuilder()
                .actorUserId(event.actorUserId()!=null?event.actorUserId():actor==null?null:actor.userId())
                .actorRole(event.actorRole()!=null?event.actorRole():actor==null?null:actor.role().name())
                .clientIp(event.clientIp()!=null?event.clientIp():req==null?null:ipExtractor.extract(req))
                .userAgent(event.userAgent()!=null?event.userAgent():req==null?null:req.getHeader("User-Agent"))
                .requestPath(event.requestPath()!=null?event.requestPath():req==null?null:req.getRequestURI())
                .requestId(event.requestId()!=null?event.requestId():req==null?null:req.getHeader("X-Request-Id"))
                .build());
        } catch(Exception e){log.warn("[SECURITY_AUDIT_PUBLISH_FAILED] type={}",event.eventType(),e);}
    }

    private HttpServletRequest currentRequest(){var a=RequestContextHolder.getRequestAttributes();return a instanceof ServletRequestAttributes s?s.getRequest():null;}
    private AuthUser currentActor(){var a=SecurityContextHolder.getContext().getAuthentication();return a!=null&&a.getPrincipal() instanceof AuthUser u?u:null;}
}
