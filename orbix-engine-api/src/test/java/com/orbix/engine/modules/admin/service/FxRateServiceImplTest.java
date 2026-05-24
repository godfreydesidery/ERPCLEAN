package com.orbix.engine.modules.admin.service;

import com.orbix.engine.modules.admin.domain.dto.FxRateDto;
import com.orbix.engine.modules.admin.domain.dto.QuoteFxRateRequestDto;
import com.orbix.engine.modules.admin.domain.entity.Currency;
import com.orbix.engine.modules.admin.domain.entity.FxRate;
import com.orbix.engine.modules.admin.domain.enums.AdminStatus;
import com.orbix.engine.modules.admin.repository.CurrencyRepository;
import com.orbix.engine.modules.admin.repository.FxRateRepository;
import com.orbix.engine.modules.common.service.RequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FxRateServiceImplTest {

    private static final Long ACTOR_ID = 8L;
    private static final Instant NOW = Instant.parse("2026-05-14T10:00:00Z");

    @Mock private FxRateRepository rates;
    @Mock private CurrencyRepository currencies;
    @Mock private RequestContext context;

    @InjectMocks private FxRateServiceImpl service;

    @BeforeEach
    void bindContext() {
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
    }

    private QuoteFxRateRequestDto quote(String from, String to, String rate) {
        return new QuoteFxRateRequestDto(from, to, new BigDecimal(rate), NOW);
    }

    private static Currency currency(String code, AdminStatus status) {
        Currency c = new Currency(code, code + " name", null, 2);
        c.setStatus(status);
        return c;
    }

    @Test
    void quoteRate_savesNewRateRow() {
        when(currencies.findById("USD")).thenReturn(Optional.of(currency("USD", AdminStatus.ACTIVE)));
        when(currencies.findById("UGX")).thenReturn(Optional.of(currency("UGX", AdminStatus.ACTIVE)));
        when(rates.save(any(FxRate.class))).thenAnswer(inv -> {
            FxRate r = inv.getArgument(0);
            r.setId(5L);
            return r;
        });

        FxRateDto result = service.quoteRate(quote("USD", "UGX", "3800.50"));

        assertThat(result.id()).isEqualTo(5L);
        assertThat(result.fromCurrency()).isEqualTo("USD");
        assertThat(result.toCurrency()).isEqualTo("UGX");
        assertThat(result.rate()).isEqualByComparingTo("3800.50");
    }

    @Test
    void quoteRate_rejectsSameCurrency() {
        assertThatThrownBy(() -> service.quoteRate(quote("USD", "USD", "1")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("two different currencies");
        verify(rates, never()).save(any());
    }

    @Test
    void quoteRate_rejectsZeroRate() {
        assertThatThrownBy(() -> service.quoteRate(quote("USD", "UGX", "0")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("greater than zero");
        verify(rates, never()).save(any());
    }

    @Test
    void quoteRate_rejectsNegativeRate() {
        assertThatThrownBy(() -> service.quoteRate(quote("USD", "UGX", "-3800")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("greater than zero");
    }

    @Test
    void quoteRate_rejectsUnknownCurrency() {
        when(currencies.findById("USD")).thenReturn(Optional.of(currency("USD", AdminStatus.ACTIVE)));
        when(currencies.findById("XXX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.quoteRate(quote("USD", "XXX", "1.5")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown currency: XXX");
        verify(rates, never()).save(any());
    }

    @Test
    void quoteRate_rejectsInactiveCurrency() {
        when(currencies.findById("USD")).thenReturn(Optional.of(currency("USD", AdminStatus.ACTIVE)));
        when(currencies.findById("UGX")).thenReturn(Optional.of(currency("UGX", AdminStatus.INACTIVE)));

        assertThatThrownBy(() -> service.quoteRate(quote("USD", "UGX", "3800")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not active");
        verify(rates, never()).save(any());
    }

    @Test
    void effectiveRate_returnsMostRecentRate() {
        FxRate stored = new FxRate("USD", "UGX", new BigDecimal("3800"), NOW, ACTOR_ID);
        stored.setId(9L);
        when(rates.findMostRecent(eq("USD"), eq("UGX"), any(Instant.class)))
            .thenReturn(Optional.of(stored));

        Optional<FxRateDto> result = service.effectiveRate("usd", "ugx", NOW);

        assertThat(result).isPresent();
        assertThat(result.get().rate()).isEqualByComparingTo("3800");
    }

    @Test
    void effectiveRate_emptyWhenNoRateQuoted() {
        when(rates.findMostRecent(any(), any(), any())).thenReturn(Optional.empty());

        assertThat(service.effectiveRate("USD", "UGX", NOW)).isEmpty();
    }
}
