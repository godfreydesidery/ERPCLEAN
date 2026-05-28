package com.orbix.engine.modules.party.service;

import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.util.UidGenerator;
import com.orbix.engine.modules.party.domain.dto.CreateEmployeeRequestDto;
import com.orbix.engine.modules.party.domain.dto.EmployeeResponseDto;
import com.orbix.engine.modules.party.domain.dto.PartyDetailsDto;
import com.orbix.engine.modules.party.domain.entity.Employee;
import com.orbix.engine.modules.party.domain.entity.Party;
import com.orbix.engine.modules.party.domain.enums.PartyCategory;
import com.orbix.engine.modules.party.repository.EmployeeRepository;
import com.orbix.engine.modules.party.repository.PartyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long ACTOR_ID = 3L;

    @Mock private EmployeeRepository employees;
    @Mock private PartyRepository parties;
    @Mock private PartyService partyService;
    @Mock private RequestContext context;

    @InjectMocks private EmployeeServiceImpl service;

    @BeforeEach
    void bindContext() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
        lenient().when(partyService.reservePartyCode("EMP")).thenReturn("EMP0001");
    }

    private static Party party(Long id, String code) {
        Party party = new Party(COMPANY_ID, code, "Name " + code, PartyCategory.INDIVIDUAL, ACTOR_ID);
        party.setId(id);
        ReflectionTestUtils.setField(party, "uid", UidGenerator.next());
        return party;
    }

    private static CreateEmployeeRequestDto createRequest(String employeeCode) {
        PartyDetailsDto details = new PartyDetailsDto("Jane Doe", null, PartyCategory.INDIVIDUAL,
            null, null, null, null, null, null, null, null);
        return new CreateEmployeeRequestDto(null, details, employeeCode, null, "Cashier",
            11L, LocalDate.of(2024, 1, 15), null);
    }

    @Test
    void createEmployee_uppercasesEmployeeCode_andStampsAttributes() {
        when(employees.existsByCompanyIdAndEmployeeCode(COMPANY_ID, "E001")).thenReturn(false);
        Party resolved = party(100L, "EMP0001");
        when(partyService.resolveOrCreate(eq("EMP0001"), any(), eq(ACTOR_ID))).thenReturn(resolved);
        when(employees.existsById(100L)).thenReturn(false);
        when(employees.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        EmployeeResponseDto result = service.createEmployee(createRequest(" e001 "));

        ArgumentCaptor<Employee> saved = ArgumentCaptor.forClass(Employee.class);
        verify(employees).save(saved.capture());
        assertThat(saved.getValue().getEmployeeCode()).isEqualTo("E001");
        assertThat(saved.getValue().getBranchId()).isEqualTo(11L);
        assertThat(result.partyId()).isEqualTo(100L);
        assertThat(result.jobTitle()).isEqualTo("Cashier");
    }

    @Test
    void createEmployee_rejectsDuplicateEmployeeCode() {
        when(employees.existsByCompanyIdAndEmployeeCode(COMPANY_ID, "E001")).thenReturn(true);

        assertThatThrownBy(() -> service.createEmployee(createRequest("E001")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
        verify(employees, never()).save(any());
    }

    @Test
    void createEmployee_whenPartyAlreadyHasEmployeeRole_isRejected() {
        when(employees.existsByCompanyIdAndEmployeeCode(COMPANY_ID, "E002")).thenReturn(false);
        Party resolved = party(100L, "EMP0001");
        when(partyService.resolveOrCreate(eq("EMP0001"), any(), eq(ACTOR_ID))).thenReturn(resolved);
        when(employees.existsById(100L)).thenReturn(true);

        assertThatThrownBy(() -> service.createEmployee(createRequest("E002")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already has an employee role");
        verify(employees, never()).save(any());
    }

    @Test
    void archiveEmployee_delegatesToPartyService() {
        Party party = party(100L, "EMP0001");
        when(partyService.requireInCompanyByUid(party.getUid())).thenReturn(party);
        when(employees.findById(100L)).thenReturn(Optional.of(new Employee(100L, "E001", 11L)));

        service.archiveEmployeeByPartyUid(party.getUid());

        verify(partyService).archive(100L);
    }

    @Test
    void archiveEmployee_whenNotAnEmployee_throwsNotFound() {
        Party party = party(100L, "EMP0001");
        when(partyService.requireInCompanyByUid(party.getUid())).thenReturn(party);
        when(employees.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.archiveEmployeeByPartyUid(party.getUid()))
            .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void activateEmployee_delegatesToPartyService() {
        Party party = party(100L, "EMP0001");
        when(partyService.requireInCompanyByUid(party.getUid())).thenReturn(party);
        when(employees.findById(100L)).thenReturn(Optional.of(new Employee(100L, "E001", 11L)));

        service.activateEmployeeByPartyUid(party.getUid());

        verify(partyService).activate(100L);
    }
}
