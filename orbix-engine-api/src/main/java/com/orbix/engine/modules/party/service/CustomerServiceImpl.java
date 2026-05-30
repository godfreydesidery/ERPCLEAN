package com.orbix.engine.modules.party.service;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.service.SyncChangeSeqService;
import com.orbix.engine.modules.party.domain.dto.CreateCustomerRequestDto;
import com.orbix.engine.modules.party.domain.dto.CustomerResponseDto;
import com.orbix.engine.modules.party.domain.dto.UpdateCustomerRequestDto;
import com.orbix.engine.modules.party.domain.entity.Customer;
import com.orbix.engine.modules.party.domain.entity.Party;
import com.orbix.engine.modules.party.domain.enums.PartyCategory;
import com.orbix.engine.modules.party.domain.enums.PartyStatus;
import com.orbix.engine.modules.party.repository.CustomerRepository;
import com.orbix.engine.modules.party.repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private static final String NOT_A_CUSTOMER = "Not a customer: ";

    private final CustomerRepository customers;
    private final PartyRepository parties;
    private final PartyService partyService;
    private final SyncChangeSeqService syncSeq;
    private final RequestContext context;

    @Override
    @Transactional(readOnly = true)
    public PageDto<CustomerResponseDto> listCustomers(String q, PartyStatus status, Pageable pageable) {
        Page<Customer> page = customers.search(context.companyId(), blankToNull(q), status, pageable);
        Map<Long, Party> partyById = parties.findAllById(
                page.getContent().stream().map(Customer::getPartyId).toList())
            .stream().collect(Collectors.toMap(Party::getId, Function.identity()));
        return PageDto.of(page, c -> CustomerResponseDto.from(c, partyById.get(c.getPartyId())));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponseDto getCustomerByPartyUid(String partyUid) {
        Party party = partyService.requireInCompanyByUid(partyUid);
        Customer customer = customers.findById(party.getId())
            .orElseThrow(() -> new NoSuchElementException(NOT_A_CUSTOMER + partyUid));
        return CustomerResponseDto.from(customer, party);
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = "Customer")
    public CustomerResponseDto createCustomer(CreateCustomerRequestDto request) {
        Party party = resolveParty(request);
        if (customers.existsById(party.getId())) {
            throw new IllegalArgumentException(
                "Party " + party.getCode() + " already has a customer role");
        }
        Customer customer = new Customer(party.getId());
        customer.update(request.creditLimitAmount(), request.creditTermsDays(), request.priceListId(),
            request.defaultSalesAgentId(), request.defaultBranchId(), request.taxExempt());
        stampSync(party);
        return CustomerResponseDto.from(customers.save(customer), party);
    }

    private Party resolveParty(CreateCustomerRequestDto request) {
        if (request.partyId() != null) {
            return partyService.requireInCompany(request.partyId());
        }
        if (request.party() == null) {
            throw new IllegalArgumentException(
                "Supply either partyId, or party details, to create a customer");
        }
        String generatedCode = partyService.reservePartyCode("CUST");
        return partyService.resolveOrCreate(generatedCode, request.party(), context.userId());
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE", entityType = "Customer")
    public CustomerResponseDto updateCustomerByPartyUid(String partyUid, UpdateCustomerRequestDto request) {
        Party party = partyService.requireInCompanyByUid(partyUid);
        Customer customer = customers.findById(party.getId())
            .orElseThrow(() -> new NoSuchElementException(NOT_A_CUSTOMER + partyUid));
        partyService.applyDetails(party, request.party(), context.userId());
        customer.update(request.creditLimitAmount(), request.creditTermsDays(), request.priceListId(),
            request.defaultSalesAgentId(), request.defaultBranchId(), request.taxExempt());
        stampSync(party);
        return CustomerResponseDto.from(customer, party);
    }

    @Override
    @Transactional
    @Auditable(action = "ARCHIVE", entityType = "Customer")
    public void archiveCustomerByPartyUid(String partyUid) {
        Party party = partyService.requireInCompanyByUid(partyUid);
        customers.findById(party.getId())
            .orElseThrow(() -> new NoSuchElementException(NOT_A_CUSTOMER + partyUid));
        partyService.archive(party.getId());
        // Stamp after archive so the deleted tombstone surfaces via change_seq > cursor.
        stampSync(party);
    }

    @Override
    @Transactional
    @Auditable(action = "ACTIVATE", entityType = "Customer")
    public void activateCustomerByPartyUid(String partyUid) {
        Party party = partyService.requireInCompanyByUid(partyUid);
        customers.findById(party.getId())
            .orElseThrow(() -> new NoSuchElementException(NOT_A_CUSTOMER + partyUid));
        partyService.activate(party.getId());
        stampSync(party);
    }

    @Override
    @Transactional
    public void createWalkInCustomer(Long branchId) {
        Long companyId = context.companyId();
        String code = "WALKIN-" + branchId;
        if (parties.existsByCompanyIdAndCode(companyId, code)) {
            return;
        }
        Party party = parties.save(new Party(
            companyId, code, "Walk-in Customer", PartyCategory.INDIVIDUAL, context.userId()));
        stampSync(party);
        customers.save(Customer.walkIn(party.getId(), branchId));
    }

    /** Bumps party.change_seq from the shared sync sequence. Must be called within an active TX. */
    private void stampSync(Party party) {
        party.setChangeSeq(syncSeq.next());
    }
}
