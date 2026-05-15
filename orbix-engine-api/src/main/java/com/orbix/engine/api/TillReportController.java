package com.orbix.engine.api;

import com.orbix.engine.modules.pos.domain.dto.TillReportDto;
import com.orbix.engine.modules.pos.service.TillReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * X / Z reports for till sessions (F5.10). PDF rendering and object-storage
 * upload of the Z-report are deferred to a follow-on slice; this controller
 * returns the JSON shape used by both the Flutter cashier UI and the web
 * manager view.
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class TillReportController {

    private final TillReportService service;

    /** Mid-shift snapshot of an OPEN till session. */
    @GetMapping("/x-report")
    @PreAuthorize("hasAuthority('POS.SALE_POST') or hasAuthority('POS.MANAGE_TILL')")
    public TillReportDto xReport(@RequestParam Long tillSessionId) {
        return service.xReport(tillSessionId);
    }

    /** Post-close snapshot of a CLOSED or RECONCILED till session. */
    @GetMapping("/z-report")
    @PreAuthorize("hasAuthority('POS.MANAGE_TILL') or hasAuthority('POS.SESSION_CLOSE')")
    public TillReportDto zReport(@RequestParam Long tillSessionId) {
        return service.zReport(tillSessionId);
    }
}
