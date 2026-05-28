package com.orbix.engine.api;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.iam.domain.enums.Permissions;
import com.orbix.engine.modules.sales.domain.dto.CreateDebtWriteOffRequestDto;
import com.orbix.engine.modules.sales.domain.dto.DebtWriteOffDto;
import com.orbix.engine.modules.sales.domain.dto.RejectDebtWriteOffRequestDto;
import com.orbix.engine.modules.sales.domain.enums.DebtWriteOffStatus;
import com.orbix.engine.modules.sales.domain.enums.DebtWriteOffTargetKind;
import com.orbix.engine.modules.sales.service.DebtWriteOffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Slice G.2 — debt write-off queue (AR + AP). Five endpoints under
 * {@code /api/v1/debt/write-offs}. Class-level {@code DEBT.READ} gates
 * read endpoints; write endpoints add their per-action permission.
 */
@RestController
@RequestMapping("/api/v1/debt/write-offs")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAuthority('" + Permissions.DEBT_READ + "')")
public class DebtWriteOffController {

    private final DebtWriteOffService writeOffService;

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.DEBT_WRITE_OFF_REQUEST + "')")
    public DebtWriteOffDto create(@Valid @RequestBody CreateDebtWriteOffRequestDto request) {
        return writeOffService.create(request);
    }

    @PostMapping("/uid/{uid}/approve")
    @PreAuthorize("hasAuthority('" + Permissions.DEBT_WRITE_OFF_APPROVE + "')")
    public DebtWriteOffDto approve(@PathVariable @ValidUlid String uid) {
        return writeOffService.approve(uid);
    }

    @PostMapping("/uid/{uid}/reject")
    @PreAuthorize("hasAuthority('" + Permissions.DEBT_WRITE_OFF_APPROVE + "')")
    public DebtWriteOffDto reject(@PathVariable @ValidUlid String uid,
                                  @Valid @RequestBody RejectDebtWriteOffRequestDto request) {
        return writeOffService.reject(uid, request);
    }

    @GetMapping
    public PageDto<DebtWriteOffDto> list(
            @RequestParam(required = false) DebtWriteOffStatus status,
            @RequestParam(required = false) DebtWriteOffTargetKind kind,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return writeOffService.list(status, kind, PageRequest.of(page, size));
    }

    @GetMapping("/uid/{uid}")
    public DebtWriteOffDto get(@PathVariable @ValidUlid String uid) {
        return writeOffService.get(uid);
    }
}
