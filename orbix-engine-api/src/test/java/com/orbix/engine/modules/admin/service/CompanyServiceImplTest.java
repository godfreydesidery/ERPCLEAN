package com.orbix.engine.modules.admin.service;

import com.orbix.engine.modules.admin.domain.dto.CompanyDto;
import com.orbix.engine.modules.admin.domain.dto.UpdateCompanyRequestDto;
import com.orbix.engine.modules.admin.domain.entity.Company;
import com.orbix.engine.modules.admin.domain.entity.Currency;
import com.orbix.engine.modules.admin.domain.enums.AdminStatus;
import com.orbix.engine.modules.admin.repository.CompanyRepository;
import com.orbix.engine.modules.admin.repository.CurrencyRepository;
import com.orbix.engine.modules.common.service.RequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyServiceImplTest {

    private static final Long COMPANY_ID = 3L;
    private static final Long ACTOR_ID = 7L;

    @Mock private CompanyRepository companies;
    @Mock private CurrencyRepository currencies;
    @Mock private RequestContext context;

    @InjectMocks private CompanyServiceImpl service;

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
        lenient().when(companies.findById(COMPANY_ID)).thenReturn(Optional.of(company()));
    }

    private static Company company() {
        Company c = new Company(1L, "COM", "Acme", "TZS", "TZ", "Africa/Dar_es_Salaam", ACTOR_ID);
        c.setId(COMPANY_ID);
        return c;
    }

    private static Currency currency(String code, AdminStatus status) {
        Currency c = new Currency(code, code + " name", null, 2);
        c.setStatus(status);
        return c;
    }

    private static UpdateCompanyRequestDto req(String currency, String country, String timeZone) {
        return new UpdateCompanyRequestDto("Acme Ltd", null, null, null, null, null, null,
            null, null, currency, country, timeZone, null, null);
    }

    @Test
    void updateCurrent_validInputs_persists() {
        when(currencies.findById("TZS")).thenReturn(Optional.of(currency("TZS", AdminStatus.ACTIVE)));
        when(companies.save(any(Company.class))).thenAnswer(inv -> inv.getArgument(0));

        CompanyDto dto = service.updateCurrent(req("TZS", "TZ", "Africa/Dar_es_Salaam"));

        assertThat(dto.currencyCode()).isEqualTo("TZS");
        assertThat(dto.countryCode()).isEqualTo("TZ");
        verify(companies).save(any(Company.class));
    }

    @Test
    void updateCurrent_unknownCurrency_rejected() {
        when(currencies.findById("XXX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCurrent(req("XXX", "TZ", "Africa/Dar_es_Salaam")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown currency");
        verify(companies, never()).save(any());
    }

    @Test
    void updateCurrent_inactiveCurrency_rejected() {
        when(currencies.findById("KES")).thenReturn(Optional.of(currency("KES", AdminStatus.INACTIVE)));

        assertThatThrownBy(() -> service.updateCurrent(req("KES", "TZ", "Africa/Dar_es_Salaam")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not active");
        verify(companies, never()).save(any());
    }

    @Test
    void updateCurrent_unknownCountry_rejected() {
        when(currencies.findById("TZS")).thenReturn(Optional.of(currency("TZS", AdminStatus.ACTIVE)));

        assertThatThrownBy(() -> service.updateCurrent(req("TZS", "ZZ", "Africa/Dar_es_Salaam")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown country code");
        verify(companies, never()).save(any());
    }

    @Test
    void updateCurrent_invalidTimeZone_rejected() {
        when(currencies.findById("TZS")).thenReturn(Optional.of(currency("TZS", AdminStatus.ACTIVE)));

        assertThatThrownBy(() -> service.updateCurrent(req("TZS", "TZ", "Not/AZone")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid time zone");
        verify(companies, never()).save(any());
    }
}
