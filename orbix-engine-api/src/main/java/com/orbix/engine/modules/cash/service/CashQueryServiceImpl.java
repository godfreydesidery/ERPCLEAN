package com.orbix.engine.modules.cash.service;

import com.orbix.engine.modules.cash.domain.dto.CashBookDto;
import com.orbix.engine.modules.cash.domain.dto.CashEntryDto;
import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import com.orbix.engine.modules.cash.repository.CashBookRepository;
import com.orbix.engine.modules.cash.repository.CashEntryRepository;
import com.orbix.engine.modules.common.service.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CashQueryServiceImpl implements CashQueryService {

    private final CashEntryRepository entries;
    private final CashBookRepository books;
    private final RequestContext context;

    @Override
    @Transactional(readOnly = true)
    public List<CashEntryDto> listEntries(Long branchId, CashAccount account, LocalDate businessDate) {
        Long companyId = context.companyId();
        List<com.orbix.engine.modules.cash.domain.entity.CashEntry> rows;
        if (branchId == null && businessDate != null) {
            rows = entries.findByCompanyIdAndBusinessDateOrderByAtAsc(companyId, businessDate);
        } else if (branchId != null && account != null && businessDate != null) {
            rows = entries.findByBranchIdAndAccountAndBusinessDateOrderByAtAsc(branchId, account, businessDate);
        } else if (branchId != null && businessDate != null) {
            rows = entries.findByBranchIdAndBusinessDateOrderByAtAsc(branchId, businessDate);
        } else {
            throw new IllegalArgumentException(
                "Provide at least businessDate, optionally with branchId + account, to query cash entries");
        }
        return rows.stream()
            .filter(e -> Objects.equals(e.getCompanyId(), companyId))
            .map(CashEntryDto::from)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashBookDto> listCashBook(Long branchId, LocalDate businessDate) {
        Long companyId = context.companyId();
        List<com.orbix.engine.modules.cash.domain.entity.CashBook> rows = branchId == null
            ? books.findByCompanyIdAndIdBusinessDate(companyId, businessDate)
            : books.findByIdBranchIdAndIdBusinessDate(branchId, businessDate);
        return rows.stream()
            .filter(b -> Objects.equals(b.getCompanyId(), companyId))
            .sorted(Comparator
                .comparing(com.orbix.engine.modules.cash.domain.entity.CashBook::getBranchId)
                .thenComparing(com.orbix.engine.modules.cash.domain.entity.CashBook::getAccount))
            .map(CashBookDto::from)
            .toList();
    }
}
