package com.orbix.engine.api;

import com.orbix.engine.modules.common.domain.dto.AuditIntegrityResultDto;
import com.orbix.engine.modules.common.domain.dto.AuditPageDto;
import com.orbix.engine.modules.common.service.AuditQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Audit-log viewer (US-IAM-013) and chain-integrity check (US-IAM-014).
 * Every endpoint requires {@code IAM.VIEW_AUDIT}.
 */
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('IAM.VIEW_AUDIT')")
public class AuditController {

    private final AuditQueryService service;

    @GetMapping
    public AuditPageDto list(
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.search(actorId, action, entityType, entityId, branchId, from, to, page, size);
    }

    @GetMapping("/integrity")
    public AuditIntegrityResultDto integrity(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.verify(from, to);
    }
}
