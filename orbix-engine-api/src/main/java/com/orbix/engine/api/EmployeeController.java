package com.orbix.engine.api;

import com.orbix.engine.modules.party.domain.dto.CreateEmployeeRequestDto;
import com.orbix.engine.modules.party.domain.dto.EmployeeResponseDto;
import com.orbix.engine.modules.party.domain.dto.UpdateEmployeeRequestDto;
import com.orbix.engine.modules.party.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Employee management (F1.7). Gated by {@code PARTY.MANAGE_EMPLOYEES}. */
@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PARTY.MANAGE_EMPLOYEES')")
public class EmployeeController {

    private final EmployeeService service;

    @GetMapping
    public List<EmployeeResponseDto> listEmployees() {
        return service.listEmployees();
    }

    @GetMapping("/{partyId}")
    public EmployeeResponseDto getEmployee(@PathVariable Long partyId) {
        return service.getEmployee(partyId);
    }

    @PostMapping
    public ResponseEntity<EmployeeResponseDto> createEmployee(
            @Valid @RequestBody CreateEmployeeRequestDto request) {
        EmployeeResponseDto employee = service.createEmployee(request);
        return ResponseEntity.created(URI.create("/api/v1/employees/" + employee.partyId()))
            .body(employee);
    }

    @PatchMapping("/{partyId}")
    public EmployeeResponseDto updateEmployee(@PathVariable Long partyId,
                                              @Valid @RequestBody UpdateEmployeeRequestDto request) {
        return service.updateEmployee(partyId, request);
    }

    @PostMapping("/{partyId}/deactivate")
    public ResponseEntity<Void> deactivateEmployee(@PathVariable Long partyId) {
        service.deactivateEmployee(partyId);
        return ResponseEntity.noContent().build();
    }
}
