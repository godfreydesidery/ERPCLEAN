package com.orbix.engine.api;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.party.domain.dto.CreatePartyNoteRequestDto;
import com.orbix.engine.modules.party.domain.dto.PartyNoteDto;
import com.orbix.engine.modules.party.service.PartyNoteService;
import com.orbix.engine.modules.sales.domain.dto.AdjustCreditLimitRequestDto;
import com.orbix.engine.modules.sales.domain.dto.CustomerStatementDto;
import com.orbix.engine.modules.sales.domain.dto.DebtAgingDto;
import com.orbix.engine.modules.sales.domain.dto.DunningQueueRowDto;
import com.orbix.engine.modules.sales.domain.enums.AgingBucket;
import com.orbix.engine.modules.sales.service.DebtReadModelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;

/**
 * Slice G — customer-AR debt surface. Aging buckets, dunning queue,
 * customer drill-down, credit-limit adjust, and chase-note CRUD.
 *
 * <p>The class-level {@link PreAuthorize} gates everything with
 * {@code DEBT.READ}; write endpoints add their per-action perm via
 * a second {@link PreAuthorize} on the method.
 */
@RestController
@RequestMapping("/api/v1/debt")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAuthority('DEBT.READ')")
public class DebtController {

    private final DebtReadModelService readModel;
    private final PartyNoteService notes;

    // ---------------------------------------------------------------------
    // Aging + dunning
    // ---------------------------------------------------------------------

    @GetMapping("/aging")
    public DebtAgingDto aging(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return readModel.aging(branchId, asOf);
    }

    @GetMapping("/dunning")
    public PageDto<DunningQueueRowDto> dunning(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) AgingBucket bucket,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Page<DunningQueueRowDto> rows = readModel.dunning(branchId, bucket, PageRequest.of(page, size));
        return PageDto.of(rows, r -> r);
    }

    // ---------------------------------------------------------------------
    // Customer statement / drill-down
    // ---------------------------------------------------------------------

    @GetMapping("/statement")
    public CustomerStatementDto statementByCustomerId(@RequestParam String customerUid) {
        return readModel.customerStatement(customerUid);
    }

    @GetMapping("/statement/uid/{uid}")
    public CustomerStatementDto statementByUid(@PathVariable @ValidUlid String uid) {
        return readModel.customerStatement(uid);
    }

    // ---------------------------------------------------------------------
    // Credit-limit adjust
    // ---------------------------------------------------------------------

    @PostMapping("/customer/uid/{uid}/credit-limit")
    @PreAuthorize("hasAuthority('DEBT.CREDIT_LIMIT.UPDATE')")
    public CustomerStatementDto adjustCreditLimit(
            @PathVariable @ValidUlid String uid,
            @Valid @RequestBody AdjustCreditLimitRequestDto request) {
        return readModel.adjustCreditLimit(uid, request);
    }

    // ---------------------------------------------------------------------
    // Chase notes — create + list + archive + get-by-uid
    // ---------------------------------------------------------------------

    @PostMapping("/notes")
    @PreAuthorize("hasAuthority('DEBT.NOTE.CREATE')")
    public ResponseEntity<PartyNoteDto> createNote(@Valid @RequestBody CreatePartyNoteRequestDto request) {
        PartyNoteDto dto = notes.addNote(request);
        return ResponseEntity.created(URI.create("/api/v1/debt/notes/uid/" + dto.uid())).body(dto);
    }

    @GetMapping("/notes")
    public java.util.List<PartyNoteDto> listNotes(
            @RequestParam String customerUid,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "50") int limit) {
        return notes.listNotesForCustomerUid(customerUid, includeArchived, limit);
    }

    @GetMapping("/notes/uid/{uid}")
    public PartyNoteDto getNote(@PathVariable @ValidUlid String uid) {
        return notes.getNoteByUid(uid);
    }

    @PostMapping("/notes/uid/{uid}/archive")
    @PreAuthorize("hasAuthority('DEBT.NOTE.ARCHIVE')")
    public PartyNoteDto archiveNote(@PathVariable @ValidUlid String uid) {
        return notes.archiveNoteByUid(uid);
    }
}
