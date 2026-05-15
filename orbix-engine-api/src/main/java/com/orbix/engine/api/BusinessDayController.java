package com.orbix.engine.api;

import com.orbix.engine.modules.day.domain.dto.BusinessDayDto;
import com.orbix.engine.modules.day.domain.dto.CloseDayRequestDto;
import com.orbix.engine.modules.day.domain.dto.OpenDayRequestDto;
import com.orbix.engine.modules.day.service.BusinessDayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/** Business-day lifecycle (F2.1). Open gated by {@code DAY.OPEN}, close by {@code DAY.CLOSE}. */
@RestController
@RequestMapping("/api/v1/business-days")
@RequiredArgsConstructor
public class BusinessDayController {

    private final BusinessDayService service;

    @GetMapping
    public List<BusinessDayDto> listDays(@RequestParam Long branchId) {
        return service.listDays(branchId);
    }

    /** The branch's current non-closed day, or null body if none is open. */
    @GetMapping("/current")
    public BusinessDayDto currentDay(@RequestParam Long branchId) {
        return service.getCurrentDay(branchId).orElse(null);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('DAY.OPEN')")
    public ResponseEntity<BusinessDayDto> openDay(@RequestParam Long branchId,
                                                  @Valid @RequestBody OpenDayRequestDto request) {
        BusinessDayDto day = service.openDay(branchId, request.businessDate());
        return ResponseEntity
            .created(URI.create("/api/v1/business-days?branchId=" + branchId))
            .body(day);
    }

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
}
