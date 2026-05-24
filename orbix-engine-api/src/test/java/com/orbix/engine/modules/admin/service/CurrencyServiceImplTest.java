package com.orbix.engine.modules.admin.service;

import com.orbix.engine.modules.admin.domain.dto.CreateCurrencyRequestDto;
import com.orbix.engine.modules.admin.domain.dto.CurrencyDto;
import com.orbix.engine.modules.admin.domain.dto.UpdateCurrencyRequestDto;
import com.orbix.engine.modules.admin.domain.entity.Currency;
import com.orbix.engine.modules.admin.domain.enums.AdminStatus;
import com.orbix.engine.modules.admin.repository.CompanyRepository;
import com.orbix.engine.modules.admin.repository.CurrencyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyServiceImplTest {

    @Mock private CurrencyRepository currencies;
    @Mock private CompanyRepository companies;

    @InjectMocks private CurrencyServiceImpl service;

    private static Currency currency(String code, AdminStatus status) {
        Currency c = new Currency(code, code + " name", "$", 2);
        c.setStatus(status);
        return c;
    }

    @Test
    void listCurrencies_sortedByCode() {
        when(currencies.findAll()).thenReturn(List.of(
            currency("USD", AdminStatus.ACTIVE), currency("EUR", AdminStatus.ACTIVE)));

        List<CurrencyDto> result = service.listCurrencies();

        assertThat(result).extracting(CurrencyDto::code).containsExactly("EUR", "USD");
    }

    @Test
    void createCurrency_persistsAndNormalisesCode() {
        when(currencies.existsById("USD")).thenReturn(false);
        when(currencies.save(any(Currency.class))).thenAnswer(inv -> inv.getArgument(0));

        CurrencyDto dto = service.createCurrency(
            new CreateCurrencyRequestDto("usd", "US Dollar", "$", 2));

        assertThat(dto.code()).isEqualTo("USD");
        assertThat(dto.status()).isEqualTo(AdminStatus.ACTIVE);
    }

    @Test
    void createCurrency_rejectsDuplicate() {
        when(currencies.existsById("USD")).thenReturn(true);

        assertThatThrownBy(() -> service.createCurrency(
                new CreateCurrencyRequestDto("USD", "US Dollar", "$", 2)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
        verify(currencies, never()).save(any());
    }

    @Test
    void updateCurrency_changesDetails() {
        when(currencies.findById("USD")).thenReturn(Optional.of(currency("USD", AdminStatus.ACTIVE)));
        when(currencies.save(any(Currency.class))).thenAnswer(inv -> inv.getArgument(0));

        CurrencyDto dto = service.updateCurrency("usd",
            new UpdateCurrencyRequestDto("United States Dollar", "US$", 3));

        assertThat(dto.name()).isEqualTo("United States Dollar");
        assertThat(dto.symbol()).isEqualTo("US$");
        assertThat(dto.minorUnitDigits()).isEqualTo(3);
    }

    @Test
    void updateCurrency_unknownCode_throws() {
        when(currencies.findById("XXX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCurrency("XXX",
                new UpdateCurrencyRequestDto("Nope", null, 2)))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("not found");
        verify(currencies, never()).save(any());
    }

    @Test
    void enableCurrency_setsActive() {
        when(currencies.findById("USD")).thenReturn(Optional.of(currency("USD", AdminStatus.INACTIVE)));
        when(currencies.save(any(Currency.class))).thenAnswer(inv -> inv.getArgument(0));

        CurrencyDto dto = service.enableCurrency("USD");

        assertThat(dto.status()).isEqualTo(AdminStatus.ACTIVE);
    }

    @Test
    void disableCurrency_rejectsFunctionalCurrency() {
        when(currencies.findById("TZS")).thenReturn(Optional.of(currency("TZS", AdminStatus.ACTIVE)));
        when(companies.existsByCurrencyCode("TZS")).thenReturn(true);

        assertThatThrownBy(() -> service.disableCurrency("TZS"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("functional currency");
        verify(currencies, never()).save(any());
    }

    @Test
    void disableCurrency_succeedsWhenNotFunctional() {
        when(currencies.findById("USD")).thenReturn(Optional.of(currency("USD", AdminStatus.ACTIVE)));
        when(companies.existsByCurrencyCode("USD")).thenReturn(false);
        when(currencies.save(any(Currency.class))).thenAnswer(inv -> inv.getArgument(0));

        CurrencyDto dto = service.disableCurrency("USD");

        assertThat(dto.status()).isEqualTo(AdminStatus.INACTIVE);
    }
}
