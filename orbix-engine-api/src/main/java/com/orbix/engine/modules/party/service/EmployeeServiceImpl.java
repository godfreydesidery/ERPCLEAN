package com.orbix.engine.modules.party.service;

import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.party.domain.dto.CreateEmployeeRequestDto;
import com.orbix.engine.modules.party.domain.dto.EmployeeResponseDto;
import com.orbix.engine.modules.party.domain.dto.UpdateEmployeeRequestDto;
import com.orbix.engine.modules.party.domain.entity.Employee;
import com.orbix.engine.modules.party.domain.entity.Party;
import com.orbix.engine.modules.party.repository.EmployeeRepository;
import com.orbix.engine.modules.party.repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employees;
    private final PartyRepository parties;
    private final PartyService partyService;
    private final RequestContext context;

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeResponseDto> listEmployees() {
        List<Employee> rows = employees.findByCompanyId(context.companyId());
        Map<Long, Party> partyById = parties.findAllById(
                rows.stream().map(Employee::getPartyId).toList())
            .stream().collect(Collectors.toMap(Party::getId, Function.identity()));
        return rows.stream()
            .map(e -> EmployeeResponseDto.from(e, partyById.get(e.getPartyId())))
            .sorted(Comparator.comparing(EmployeeResponseDto::employeeCode))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeResponseDto getEmployee(Long partyId) {
        Party party = partyService.requireInCompany(partyId);
        Employee employee = employees.findById(partyId)
            .orElseThrow(() -> new NoSuchElementException("Not an employee: " + partyId));
        return EmployeeResponseDto.from(employee, party);
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = "Employee")
    public EmployeeResponseDto createEmployee(CreateEmployeeRequestDto request) {
        Long companyId = context.companyId();
        String employeeCode = request.employeeCode().trim().toUpperCase();
        if (employees.existsByCompanyIdAndEmployeeCode(companyId, employeeCode)) {
            throw new IllegalArgumentException("Employee code already exists: " + employeeCode);
        }
        Party party = partyService.resolveOrCreate(request.code(), request.party(), context.userId());
        if (employees.existsById(party.getId())) {
            throw new IllegalArgumentException(
                "Party " + party.getCode() + " already has an employee role");
        }
        Employee employee = new Employee(party.getId(), employeeCode, request.branchId());
        employee.update(request.appUserId(), request.jobTitle(), request.branchId(),
            request.hireDate(), request.terminationDate());
        return EmployeeResponseDto.from(employees.save(employee), party);
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE", entityType = "Employee")
    public EmployeeResponseDto updateEmployee(Long partyId, UpdateEmployeeRequestDto request) {
        Party party = partyService.requireInCompany(partyId);
        Employee employee = employees.findById(partyId)
            .orElseThrow(() -> new NoSuchElementException("Not an employee: " + partyId));
        partyService.applyDetails(party, request.party(), context.userId());
        employee.update(request.appUserId(), request.jobTitle(), request.branchId(),
            request.hireDate(), request.terminationDate());
        return EmployeeResponseDto.from(employee, party);
    }

    @Override
    @Transactional
    @Auditable(action = "DEACTIVATE", entityType = "Employee")
    public void deactivateEmployee(Long partyId) {
        employees.findById(partyId)
            .orElseThrow(() -> new NoSuchElementException("Not an employee: " + partyId));
        partyService.deactivate(partyId);
    }
}
