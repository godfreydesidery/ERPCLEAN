package com.orbix.engine.api;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.party.domain.dto.CreateEmployeeRequestDto;
import com.orbix.engine.modules.party.domain.dto.EmployeeResponseDto;
import com.orbix.engine.modules.party.domain.dto.UpdateEmployeeRequestDto;
import com.orbix.engine.modules.party.domain.enums.PartyStatus;
import com.orbix.engine.modules.party.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/** Employee management (F1.7). Gated by {@code PARTY.MANAGE_EMPLOYEES}. */
@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PARTY.MANAGE_EMPLOYEES')")
@Validated
public class EmployeeController {

    private final EmployeeService service;

    @GetMapping
    public PageDto<EmployeeResponseDto> listEmployees(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) PartyStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listEmployees(q, status, PageRequest.of(page, size));
    }

    @GetMapping("/uid/{partyUid}")
    public EmployeeResponseDto getEmployee(@PathVariable @ValidUlid String partyUid) {
        return service.getEmployeeByPartyUid(partyUid);
    }

    @PostMapping
    public ResponseEntity<EmployeeResponseDto> createEmployee(
            @Valid @RequestBody CreateEmployeeRequestDto request) {
        EmployeeResponseDto employee = service.createEmployee(request);
        return ResponseEntity.created(URI.create("/api/v1/employees/uid/" + employee.party().uid()))
            .body(employee);
    }

    @PatchMapping("/uid/{partyUid}")
    public EmployeeResponseDto updateEmployee(@PathVariable @ValidUlid String partyUid,
                                              @Valid @RequestBody UpdateEmployeeRequestDto request) {
        return service.updateEmployeeByPartyUid(partyUid, request);
    }

    @PostMapping("/uid/{partyUid}/deactivate")
    public ResponseEntity<Void> deactivateEmployee(@PathVariable @ValidUlid String partyUid) {
        service.deactivateEmployeeByPartyUid(partyUid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/uid/{partyUid}/activate")
    public ResponseEntity<Void> activateEmployee(@PathVariable @ValidUlid String partyUid) {
        service.activateEmployeeByPartyUid(partyUid);
        return ResponseEntity.noContent().build();
    }
}
