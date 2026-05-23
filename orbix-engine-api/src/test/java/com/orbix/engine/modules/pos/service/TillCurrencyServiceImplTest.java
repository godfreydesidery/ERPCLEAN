package com.orbix.engine.modules.pos.service;

import com.orbix.engine.modules.admin.domain.entity.Company;
import com.orbix.engine.modules.admin.domain.entity.Currency;
import com.orbix.engine.modules.admin.repository.CompanyRepository;
import com.orbix.engine.modules.admin.repository.CurrencyRepository;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.pos.domain.entity.Till;
import com.orbix.engine.modules.pos.domain.entity.TillCurrency;
import com.orbix.engine.modules.pos.repository.TillCurrencyRepository;
import com.orbix.engine.modules.pos.repository.TillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TillCurrencyServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long TILL_ID = 100L;
    private static final Long ACTOR_ID = 4L;

    @Mock private TillCurrencyRepository tillCurrencies;
    @Mock private TillRepository tills;
    @Mock private CurrencyRepository currencies;
    @Mock private CompanyRepository companies;
    @Mock private RequestContext context;
    @Mock private BranchScope branchScope;

    @InjectMocks private TillCurrencyServiceImpl service;

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);

        Till till = new Till(COMPANY_ID, 12L, "T1", "Front", 1L, ACTOR_ID);
        till.setId(TILL_ID);
        lenient().when(tills.findById(TILL_ID)).thenReturn(Optional.of(till));

        Company company = new Company(1L, "ACME", "Acme",
            "TZS", "TZ", "Africa/Dar_es_Salaam", ACTOR_ID);
        company.setId(COMPANY_ID);
        lenient().when(companies.findById(COMPANY_ID)).thenReturn(Optional.of(company));

        Currency usd = new Currency("USD", "US Dollar", "$", 2);
        lenient().when(currencies.findById("USD")).thenReturn(Optional.of(usd));

        lenient().when(tillCurrencies.save(any(TillCurrency.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void add_newForeignCurrency_persists() {
        when(tillCurrencies.existsByIdTillIdAndIdCurrencyCode(TILL_ID, "USD")).thenReturn(false);

        var dto = service.add(TILL_ID, "usd");

        assertThat(dto.tillId()).isEqualTo(TILL_ID);
        assertThat(dto.currencyCode()).isEqualTo("USD");
        verify(tillCurrencies).save(any(TillCurrency.class));
    }

    @Test
    void add_functionalCurrency_isRejected() {
        assertThatThrownBy(() -> service.add(TILL_ID, "TZS"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Functional currency");
        verify(tillCurrencies, never()).save(any());
    }

    @Test
    void add_duplicate_isRejected() {
        when(tillCurrencies.existsByIdTillIdAndIdCurrencyCode(TILL_ID, "USD")).thenReturn(true);
        assertThatThrownBy(() -> service.add(TILL_ID, "USD"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already accepts");
        verify(tillCurrencies, never()).save(any());
    }

    @Test
    void add_unknownCurrency_404() {
        when(currencies.findById("XYZ")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.add(TILL_ID, "XYZ"))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("Currency not found: XYZ");
    }

    @Test
    void remove_unknown_404() {
        when(tillCurrencies.existsByIdTillIdAndIdCurrencyCode(TILL_ID, "USD")).thenReturn(false);
        assertThatThrownBy(() -> service.remove(TILL_ID, "USD"))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("does not accept USD");
    }

    @Test
    void remove_existing_deletes() {
        when(tillCurrencies.existsByIdTillIdAndIdCurrencyCode(TILL_ID, "USD")).thenReturn(true);
        service.remove(TILL_ID, "USD");
        verify(tillCurrencies).deleteByIdTillIdAndIdCurrencyCode(TILL_ID, "USD");
    }

    @Test
    void crossCompanyTill_404() {
        Till foreign = new Till(999L, 12L, "T1", "Front", 1L, ACTOR_ID);
        foreign.setId(TILL_ID);
        when(tills.findById(TILL_ID)).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.add(TILL_ID, "USD"))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("Till not found");
    }
}
