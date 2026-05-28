package com.orbix.engine.api;

import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.day.domain.dto.BusinessDayDto;
import com.orbix.engine.modules.day.domain.dto.BusinessDayOverrideDto;
import com.orbix.engine.modules.day.domain.dto.CloseDayRequestDto;
import com.orbix.engine.modules.day.domain.dto.OpenDayRequestDto;
import com.orbix.engine.modules.day.domain.dto.PostBusinessDayOverrideRequestDto;
import com.orbix.engine.modules.day.service.BusinessDayService;
import com.orbix.engine.modules.day.service.EodBlockerDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * Business-day lifecycle (F2.1). Open gated by {@code DAY.OPEN}, close by
 * {@code DAY.CLOSE}. Slice D adds uid-keyed endpoints alongside the existing
 * composite-key paths (ADR 0002 — Path A); composite paths stay because POS /
 * EOD orchestration / sales-receipt back-dating all hold {@code (branchId,
 * businessDate)} natively.
 */
@RestController
@RequestMapping("/api/v1/business-days")
@RequiredArgsConstructor
public class BusinessDayController {

    private final BusinessDayService service;

    @GetMapping
    @PreAuthorize("hasAuthority('DAY.READ') or hasAuthority('DAY.OPEN') or hasAuthority('DAY.CLOSE')")
    public List<BusinessDayDto> listDays(@RequestParam Long branchId) {
        return service.listDays(branchId);
    }

    /** The branch's current non-closed day, or null body if none is open. */
    @GetMapping("/current")
    @PreAuthorize("hasAuthority('DAY.READ') or hasAuthority('DAY.OPEN') or hasAuthority('DAY.CLOSE')")
    public BusinessDayDto currentDay(@RequestParam Long branchId) {
        return service.getCurrentDay(branchId).orElse(null);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('DAY.OPEN')")
    public ResponseEntity<BusinessDayDto> openDay(@RequestParam Long branchId,
                                                  @Valid @RequestBody OpenDayRequestDto request) {
        BusinessDayDto day = service.openDay(branchId, request.businessDate());
        return ResponseEntity
            .created(URI.create("/api/v1/business-days/uid/" + day.uid()))
            .body(day);
    }

    // ---- Composite-key endpoints (retained for internal producers) --------

    @PostMapping("/{date}/start-closing")
    @PreAuthorize("hasAuthority('DAY.CLOSE')")
    public BusinessDayDto startClosing(
            @RequestParam Long branchId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.startClosing(branchId, date);
    }

    @PostMapping("/{date}/close")
    @PreAuthorize("hasAuthority('DAY.CLOSE')")
    public BusinessDayDto closeDay(
            @RequestParam Long branchId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody CloseDayRequestDto request) {
        return service.closeDay(branchId, date, request.eodReportObjectKey());
    }

    /**
     * F7.5 — read-only "what's blocking close?" preview. Runs every
     * {@link com.orbix.engine.modules.day.service.EodGuard} and returns the
     * aggregated blocker list without mutating state.
     */
    @GetMapping("/{date}/blockers")
    @PreAuthorize("hasAuthority('DAY.CLOSE') or hasAuthority('DAY.OPEN')")
    public List<EodBlockerDto> blockers(
            @RequestParam Long branchId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.previewBlockers(branchId, date);
    }

    /**
     * F7.5 — EOD orchestration (TC-DAY-006 / TC-DAY-025). Runs startClosing +
     * closeDay + next-day auto-roll in one call. Idempotent on
     * already-CLOSED days.
     */
    @PostMapping("/{date}/end")
    @PreAuthorize("hasAuthority('DAY.CLOSE')")
    public BusinessDayDto endDay(
            @RequestParam Long branchId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody CloseDayRequestDto request) {
        return service.endDay(branchId, date, request.eodReportObjectKey());
    }

    // ---- uid endpoints (Slice D / ADR 0002) -------------------------------

    @GetMapping("/uid/{uid}")
    @PreAuthorize("hasAuthority('DAY.READ') or hasAuthority('DAY.OPEN') or hasAuthority('DAY.CLOSE')")
    public BusinessDayDto getDayByUid(@PathVariable @ValidUlid String uid) {
        return service.getBusinessDayByUid(uid);
    }

    @GetMapping("/uid/{uid}/blockers")
    @PreAuthorize("hasAuthority('DAY.CLOSE') or hasAuthority('DAY.OPEN')")
    public List<EodBlockerDto> blockersByUid(@PathVariable @ValidUlid String uid) {
        return service.previewBlockersByUid(uid);
    }

    @PostMapping("/uid/{uid}/start-closing")
    @PreAuthorize("hasAuthority('DAY.CLOSE')")
    public BusinessDayDto startClosingByUid(@PathVariable @ValidUlid String uid) {
        return service.startClosingByUid(uid);
    }

    @PostMapping("/uid/{uid}/close")
    @PreAuthorize("hasAuthority('DAY.CLOSE')")
    public BusinessDayDto closeDayByUid(@PathVariable @ValidUlid String uid,
                                        @Valid @RequestBody CloseDayRequestDto request) {
        return service.closeDayByUid(uid, request.eodReportObjectKey());
    }

    @PostMapping("/uid/{uid}/end")
    @PreAuthorize("hasAuthority('DAY.CLOSE')")
    public BusinessDayDto endDayByUid(@PathVariable @ValidUlid String uid,
                                      @Valid @RequestBody CloseDayRequestDto request) {
        return service.endDayByUid(uid, request.eodReportObjectKey());
    }

    // ---- business-day overrides -------------------------------------------

    @GetMapping("/overrides")
    @PreAuthorize("hasAuthority('DAY.OVERRIDE_LIST') or hasAuthority('DAY.OVERRIDE')")
    public List<BusinessDayOverrideDto> listOverrides(@RequestParam Long branchId) {
        return service.listOverrides(branchId);
    }

    @PostMapping("/uid/{uid}/overrides")
    @PreAuthorize("hasAuthority('DAY.OVERRIDE')")
    public ResponseEntity<BusinessDayOverrideDto> postOverrideForDay(
            @PathVariable @ValidUlid String uid,
            @Valid @RequestBody PostBusinessDayOverrideRequestDto request) {
        BusinessDayOverrideDto override = service.postOverrideByDayUid(
            uid, request.entityType(), request.entityId(), request.reason());
        return ResponseEntity
            .created(URI.create("/api/v1/business-days/overrides/uid/" + override.uid()))
            .body(override);
    }

    @PostMapping("/overrides/uid/{overrideUid}/archive")
    @PreAuthorize("hasAuthority('DAY.OVERRIDE')")
    public BusinessDayOverrideDto archiveOverride(@PathVariable @ValidUlid String overrideUid) {
        return service.archiveBusinessDayOverrideByUid(overrideUid);
    }
}
