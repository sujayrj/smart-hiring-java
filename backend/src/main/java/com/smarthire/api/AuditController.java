package com.smarthire.api;

import com.smarthire.domain.AuditLog;
import com.smarthire.repository.AuditLogRepository;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AuditController {

    private final AuditLogRepository repo;

    public AuditController(AuditLogRepository repo) {
        this.repo = repo;
    }

    /** §9: GET /audit — Admin only. */
    @GetMapping("/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuditLog> audit() {
        return repo.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
