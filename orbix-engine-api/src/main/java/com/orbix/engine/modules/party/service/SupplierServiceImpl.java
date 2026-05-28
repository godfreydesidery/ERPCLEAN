package com.orbix.engine.modules.party.service;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.party.domain.dto.CreateSupplierRequestDto;
import com.orbix.engine.modules.party.domain.dto.SupplierResponseDto;
import com.orbix.engine.modules.party.domain.dto.UpdateSupplierRequestDto;
import com.orbix.engine.modules.party.domain.entity.Party;
import com.orbix.engine.modules.party.domain.entity.Supplier;
import com.orbix.engine.modules.party.domain.enums.PartyStatus;
import com.orbix.engine.modules.party.repository.PartyRepository;
import com.orbix.engine.modules.party.repository.SupplierRepository;
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
public class SupplierServiceImpl implements SupplierService {

    private static final String NOT_A_SUPPLIER = "Not a supplier: ";

    private final SupplierRepository suppliers;
    private final PartyRepository parties;
    private final PartyService partyService;
    private final RequestContext context;

    @Override
    @Transactional(readOnly = true)
    public PageDto<SupplierResponseDto> listSuppliers(String q, PartyStatus status, Pageable pageable) {
        Page<Supplier> page = suppliers.search(context.companyId(), blankToNull(q), status, pageable);
        Map<Long, Party> partyById = parties.findAllById(
                page.getContent().stream().map(Supplier::getPartyId).toList())
            .stream().collect(Collectors.toMap(Party::getId, Function.identity()));
        return PageDto.of(page, s -> SupplierResponseDto.from(s, partyById.get(s.getPartyId())));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierResponseDto getSupplierByPartyUid(String partyUid) {
        Party party = partyService.requireInCompanyByUid(partyUid);
        Supplier supplier = suppliers.findById(party.getId())
            .orElseThrow(() -> new NoSuchElementException(NOT_A_SUPPLIER + partyUid));
        return SupplierResponseDto.from(supplier, party);
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = "Supplier")
    public SupplierResponseDto createSupplier(CreateSupplierRequestDto request) {
        Party party = resolveParty(request);
        if (suppliers.existsById(party.getId())) {
            throw new IllegalArgumentException(
                "Party " + party.getCode() + " already has a supplier role");
        }
        Supplier supplier = new Supplier(party.getId());
        supplier.update(request.paymentTermsDays(), request.creditLimitAmount(),
            request.defaultCurrencyCode(), request.bankName(), request.bankAccountNo(),
            request.leadTimeDays());
        return SupplierResponseDto.from(suppliers.save(supplier), party);
    }

    private Party resolveParty(CreateSupplierRequestDto request) {
        if (request.partyId() != null) {
            return partyService.requireInCompany(request.partyId());
        }
        if (request.party() == null) {
            throw new IllegalArgumentException(
                "Supply either partyId, or party details, to create a supplier");
        }
        String generatedCode = partyService.reservePartyCode("SUP");
        return partyService.resolveOrCreate(generatedCode, request.party(), context.userId());
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE", entityType = "Supplier")
    public SupplierResponseDto updateSupplierByPartyUid(String partyUid, UpdateSupplierRequestDto request) {
        Party party = partyService.requireInCompanyByUid(partyUid);
        Supplier supplier = suppliers.findById(party.getId())
            .orElseThrow(() -> new NoSuchElementException(NOT_A_SUPPLIER + partyUid));
        partyService.applyDetails(party, request.party(), context.userId());
        supplier.update(request.paymentTermsDays(), request.creditLimitAmount(),
            request.defaultCurrencyCode(), request.bankName(), request.bankAccountNo(),
            request.leadTimeDays());
        return SupplierResponseDto.from(supplier, party);
    }

    @Override
    @Transactional
    @Auditable(action = "ARCHIVE", entityType = "Supplier")
    public void archiveSupplierByPartyUid(String partyUid) {
        Party party = partyService.requireInCompanyByUid(partyUid);
        suppliers.findById(party.getId())
            .orElseThrow(() -> new NoSuchElementException(NOT_A_SUPPLIER + partyUid));
        partyService.archive(party.getId());
    }

    @Override
    @Transactional
    @Auditable(action = "ACTIVATE", entityType = "Supplier")
    public void activateSupplierByPartyUid(String partyUid) {
        Party party = partyService.requireInCompanyByUid(partyUid);
        suppliers.findById(party.getId())
            .orElseThrow(() -> new NoSuchElementException(NOT_A_SUPPLIER + partyUid));
        partyService.activate(party.getId());
    }
}
