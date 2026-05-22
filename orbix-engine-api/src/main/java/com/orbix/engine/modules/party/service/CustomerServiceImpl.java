package com.orbix.engine.modules.party.service;

import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.party.domain.dto.CreateCustomerRequestDto;
import com.orbix.engine.modules.party.domain.dto.CustomerResponseDto;
import com.orbix.engine.modules.party.domain.dto.UpdateCustomerRequestDto;
import com.orbix.engine.modules.party.domain.entity.Customer;
import com.orbix.engine.modules.party.domain.entity.Party;
import com.orbix.engine.modules.party.domain.enums.PartyCategory;
import com.orbix.engine.modules.party.repository.CustomerRepository;
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
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customers;
    private final PartyRepository parties;
    private final PartyService partyService;
    private final RequestContext context;

    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponseDto> listCustomers() {
        List<Customer> rows = customers.findByCompanyId(context.companyId());
        Map<Long, Party> partyById = parties.findAllById(
                rows.stream().map(Customer::getPartyId).toList())
            .stream().collect(Collectors.toMap(Party::getId, Function.identity()));
        return rows.stream()
            .map(c -> CustomerResponseDto.from(c, partyById.get(c.getPartyId())))
            .sorted(Comparator.comparing(dto -> dto.party().code()))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponseDto getCustomer(Long partyId) {
        Party party = partyService.requireInCompany(partyId);
        Customer customer = customers.findById(partyId)
            .orElseThrow(() -> new NoSuchElementException("Not a customer: " + partyId));
        return CustomerResponseDto.from(customer, party);
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = "Customer")
    public CustomerResponseDto createCustomer(CreateCustomerRequestDto request) {
        Party party = partyService.resolveOrCreate(request.code(), request.party(), context.userId());
        if (customers.existsById(party.getId())) {
            throw new IllegalArgumentException(
                "Party " + party.getCode() + " already has a customer role");
        }
        Customer customer = new Customer(party.getId());
        customer.update(request.creditLimitAmount(), request.creditTermsDays(), request.priceListId(),
            request.defaultSalesAgentId(), request.defaultBranchId(), request.taxExempt());
        return CustomerResponseDto.from(customers.save(customer), party);
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE", entityType = "Customer")
    public CustomerResponseDto updateCustomer(Long partyId, UpdateCustomerRequestDto request) {
        Party party = partyService.requireInCompany(partyId);
        Customer customer = customers.findById(partyId)
            .orElseThrow(() -> new NoSuchElementException("Not a customer: " + partyId));
        partyService.applyDetails(party, request.party(), context.userId());
        customer.update(request.creditLimitAmount(), request.creditTermsDays(), request.priceListId(),
            request.defaultSalesAgentId(), request.defaultBranchId(), request.taxExempt());
        return CustomerResponseDto.from(customer, party);
    }

    @Override
    @Transactional
    @Auditable(action = "DEACTIVATE", entityType = "Customer")
    public void deactivateCustomer(Long partyId) {
        customers.findById(partyId)
            .orElseThrow(() -> new NoSuchElementException("Not a customer: " + partyId));
        partyService.deactivate(partyId);
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
        customers.save(Customer.walkIn(party.getId(), branchId));
    }
}
