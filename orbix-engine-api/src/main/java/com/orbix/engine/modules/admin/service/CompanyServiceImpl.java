package com.orbix.engine.modules.admin.service;

import com.orbix.engine.modules.admin.domain.dto.CompanyDto;
import com.orbix.engine.modules.admin.domain.dto.UpdateCompanyRequestDto;
import com.orbix.engine.modules.admin.domain.entity.Company;
import com.orbix.engine.modules.admin.repository.CompanyRepository;
import com.orbix.engine.modules.admin.repository.CurrencyRepository;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {

    private final CompanyRepository companies;
    private final CurrencyRepository currencies;
    private final RequestContext context;

    @Override
    @Transactional(readOnly = true)
    public CompanyDto getCurrent() {
        return CompanyDto.from(requireCurrent());
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE", entityType = "Company")
    public CompanyDto updateCurrent(UpdateCompanyRequestDto r) {
        Company company = requireCurrent();
        String currency = r.currencyCode().trim().toUpperCase();
        String timeZone = r.timeZone().trim();
        // Selectable on the UI, but validate here too in case the API is called directly.
        if (!currencies.existsById(currency)) {
            throw new IllegalArgumentException("Unknown currency: " + currency);
        }
        validateZone(timeZone);
        company.updateProfile(
            r.name().trim(), trimToNull(r.legalName()), trimToNull(r.tin()), trimToNull(r.vrn()),
            trimToNull(r.physicalAddress()), trimToNull(r.postalAddress()), trimToNull(r.phone()),
            trimToNull(r.email()), trimToNull(r.website()),
            currency, r.countryCode().trim().toUpperCase(), timeZone,
            trimToNull(r.defaultInvoiceNote()), trimToNull(r.defaultQuotationNote()),
            context.userId());
        return CompanyDto.from(companies.save(company));
    }

    private static void validateZone(String timeZone) {
        try {
            ZoneId.of(timeZone);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Invalid time zone: " + timeZone);
        }
    }

    private Company requireCurrent() {
        Long companyId = context.companyId();
        return companies.findById(companyId)
            .orElseThrow(() -> new NoSuchElementException("Company not found: " + companyId));
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
