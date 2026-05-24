package com.orbix.engine.modules.pos.service;

import com.orbix.engine.modules.admin.domain.entity.Company;
import com.orbix.engine.modules.admin.domain.entity.Currency;
import com.orbix.engine.modules.admin.domain.enums.AdminStatus;
import com.orbix.engine.modules.admin.repository.CompanyRepository;
import com.orbix.engine.modules.admin.repository.CurrencyRepository;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.pos.domain.dto.TillCurrencyDto;
import com.orbix.engine.modules.pos.domain.entity.Till;
import com.orbix.engine.modules.pos.domain.entity.TillCurrency;
import com.orbix.engine.modules.pos.domain.entity.TillCurrencyId;
import com.orbix.engine.modules.pos.repository.TillCurrencyRepository;
import com.orbix.engine.modules.pos.repository.TillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TillCurrencyServiceImpl implements TillCurrencyService {

    private static final String AGG = "TillCurrency";

    private final TillCurrencyRepository tillCurrencies;
    private final TillRepository tills;
    private final CurrencyRepository currencies;
    private final CompanyRepository companies;
    private final RequestContext context;
    private final BranchScope branchScope;

    @Override
    @Transactional(readOnly = true)
    public List<TillCurrencyDto> list(Long tillId) {
        requireTill(tillId);
        return tillCurrencies.findByIdTillIdOrderByIdCurrencyCodeAsc(tillId).stream()
            .map(TillCurrencyDto::from)
            .toList();
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = AGG)
    public TillCurrencyDto add(Long tillId, String currencyCode) {
        Till till = requireTill(tillId);
        String code = normaliseCode(currencyCode);
        rejectFunctionalCurrency(till, code);
        Currency currency = currencies.findById(code)
            .orElseThrow(() -> new NoSuchElementException("Currency not found: " + code));
        if (currency.getStatus() != AdminStatus.ACTIVE) {
            throw new IllegalArgumentException("Currency is not active: " + code);
        }
        if (tillCurrencies.existsByIdTillIdAndIdCurrencyCode(tillId, currency.getCode())) {
            throw new IllegalArgumentException(
                "Till " + tillId + " already accepts " + currency.getCode());
        }
        TillCurrency saved = tillCurrencies.save(
            new TillCurrency(tillId, currency.getCode(), context.userId()));
        return TillCurrencyDto.from(saved);
    }

    @Override
    @Transactional
    @Auditable(action = "DELETE", entityType = AGG)
    public void remove(Long tillId, String currencyCode) {
        requireTill(tillId);
        String code = normaliseCode(currencyCode);
        if (!tillCurrencies.existsByIdTillIdAndIdCurrencyCode(tillId, code)) {
            throw new NoSuchElementException(
                "Till " + tillId + " does not accept " + code);
        }
        tillCurrencies.deleteByIdTillIdAndIdCurrencyCode(tillId, code);
    }

    private Till requireTill(Long tillId) {
        Till till = tills.findById(tillId)
            .orElseThrow(() -> new NoSuchElementException("Till not found: " + tillId));
        if (!Objects.equals(till.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Till not found: " + tillId);
        }
        branchScope.requireAccess(till.getBranchId());
        return till;
    }

    private void rejectFunctionalCurrency(Till till, String code) {
        Company company = companies.findById(till.getCompanyId())
            .orElseThrow(() -> new NoSuchElementException(
                "Company not found: " + till.getCompanyId()));
        if (Objects.equals(company.getCurrencyCode(), code)) {
            throw new IllegalArgumentException(
                "Functional currency " + code + " is accepted implicitly — do not register it");
        }
    }

    private String normaliseCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("currencyCode is required");
        }
        return code.trim().toUpperCase();
    }
}
