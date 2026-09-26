package com.smarthire.service;

import com.smarthire.domain.AuditLog;
import com.smarthire.repository.AuditLogRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.smarthire.security.JwtService;

@Service
public class AuditService {

    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    public void record(String eventType, String entityType, Long entityId, String details) {
        AuditLog log = new AuditLog();
        JwtService.Principal p = currentUser();
        if (p != null) {
            log.setActorId(p.userId());
            log.setActorRole(p.role());
        } else {
            log.setActorId(null);
            log.setActorRole("SYSTEM");
        }
        log.setEventType(eventType);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetails(details);
        repo.save(log);
    }

    public static JwtService.Principal currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtService.Principal p) {
            return p;
        }
        return null;
    }
}
