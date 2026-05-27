package com.orbix.engine.modules.party.service;

import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.party.domain.dto.PartyDetailsDto;
import com.orbix.engine.modules.party.domain.dto.PartyResponseDto;
import com.orbix.engine.modules.party.domain.entity.Party;
import com.orbix.engine.modules.party.domain.entity.PartyCodeSequence;
import com.orbix.engine.modules.party.domain.enums.PartyStatus;
import com.orbix.engine.modules.party.repository.PartyCodeSequenceRepository;
import com.orbix.engine.modules.party.repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PartyServiceImpl implements PartyService {

    private final PartyRepository parties;
    private final PartyCodeSequenceRepository codeSequences;
    private final EventPublisher events;
    private final RequestContext context;

    @Override
    @Transactional(readOnly = true)
    public List<PartyResponseDto> listParties() {
        return parties.findByCompanyId(context.companyId()).stream()
            .sorted(Comparator.comparing(Party::getCode))
            .map(PartyResponseDto::from)
            .toList();
    }

    @Override
    @Transactional
    public String reservePartyCode(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("Prefix is required");
        }
        String normalized = prefix.trim().toUpperCase();
        Long companyId = context.companyId();
        PartyCodeSequence seq = codeSequences.findByCompanyIdAndPrefix(companyId, normalized)
            .orElseGet(() -> codeSequences.save(new PartyCodeSequence(companyId, normalized)));
        seq.setCurrentValue(seq.getCurrentValue() + 1);
        codeSequences.save(seq);
        return String.format("%s%04d", normalized, seq.getCurrentValue());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PartyResponseDto> findByTin(String tin) {
        if (tin == null || tin.isBlank()) {
            return Optional.empty();
        }
        return parties.findFirstByCompanyIdAndTinAndTinNotNull(context.companyId(), tin.trim())
            .map(PartyResponseDto::from);
    }

    @Override
    @Transactional
    public Party resolveOrCreate(String code, PartyDetailsDto details, Long actorId) {
        Long companyId = context.companyId();
        String tin = details.tin();
        if (tin != null && !tin.isBlank()) {
            Optional<Party> existing =
                parties.findFirstByCompanyIdAndTinAndTinNotNull(companyId, tin.trim());
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        String partyCode = code.trim().toUpperCase();
        if (parties.existsByCompanyIdAndCode(companyId, partyCode)) {
            throw new IllegalArgumentException("Party code already exists: " + partyCode);
        }
        Party party = new Party(companyId, partyCode, details.name(), details.category(), actorId);
        applyDetails(party, details, actorId);
        return parties.save(party);
    }

    @Override
    public void applyDetails(Party party, PartyDetailsDto d, Long actorId) {
        party.updateDetails(d.name(), d.legalName(), d.category(), d.tin(), d.vrn(), d.phone(),
            d.email(), d.physicalAddress(), d.postalAddress(), d.countryCode(), d.notes(), actorId);
    }

    @Override
    @Transactional(readOnly = true)
    public Party requireInCompany(Long partyId) {
        Party party = parties.findById(partyId)
            .orElseThrow(() -> new NoSuchElementException("Party not found: " + partyId));
        if (!Objects.equals(party.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Party not found: " + partyId);
        }
        return party;
    }

    @Override
    @Transactional(readOnly = true)
    public Party requireInCompanyByUid(String partyUid) {
        Party party = parties.findByUid(partyUid)
            .orElseThrow(() -> new NoSuchElementException("Party not found: " + partyUid));
        if (!Objects.equals(party.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Party not found: " + partyUid);
        }
        return party;
    }

    @Override
    @Transactional
    public void archive(Long partyId) {
        Party party = requireInCompany(partyId);
        if (party.getStatus() == PartyStatus.ARCHIVED) {
            throw new IllegalArgumentException("Party is already archived: " + party.getUid());
        }
        party.archive(context.userId());
        events.publish("PartyArchived.v1", "Party", party.getUid(),
            Map.of("partyUid", party.getUid()));
    }

    @Override
    @Transactional
    public void activate(Long partyId) {
        Party party = requireInCompany(partyId);
        if (party.getStatus() == PartyStatus.ACTIVE) {
            throw new IllegalArgumentException("Party is already active: " + party.getUid());
        }
        party.activate(context.userId());
        events.publish("PartyReactivated.v1", "Party", party.getUid(),
            Map.of("partyUid", party.getUid()));
    }
}
