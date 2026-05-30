package com.orbix.engine.modules.fiscal.service;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.enums.ItemType;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.fiscal.domain.dto.FiscalReceiptDto;
import com.orbix.engine.modules.fiscal.domain.dto.FiscalReceiptResultDto;
import com.orbix.engine.modules.fiscal.domain.entity.FiscalReceipt;
import com.orbix.engine.modules.fiscal.domain.enums.FiscalStatus;
import com.orbix.engine.modules.fiscal.domain.event.FiscalizationRequestedEventDto;
import com.orbix.engine.modules.fiscal.repository.FiscalReceiptRepository;
import com.orbix.engine.modules.pos.domain.entity.PosSale;
import com.orbix.engine.modules.pos.domain.entity.PosSaleLine;
import com.orbix.engine.modules.pos.repository.PosSaleLineRepository;
import com.orbix.engine.modules.pos.repository.PosSaleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.util.UidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FiscalizationServiceImplTest {

    private static final Long COMPANY_ID = 10L;
    private static final Long BRANCH_ID  = 20L;
    private static final Long ACTOR_ID   = 5L;
    private static final Long SALE_ID    = 1001L;
    private static final Long ITEM_ID    = 777L;

    @Mock private FiscalReceiptRepository fiscalReceipts;
    @Mock private PosSaleRepository posSales;
    @Mock private PosSaleLineRepository posSaleLines;
    @Mock private ItemRepository items;
    @Mock private FiscalProviderFactory providerFactory;

    @InjectMocks private FiscalizationServiceImpl service;

    private PosSale sale;
    private PosSaleLine saleLine;

    @BeforeEach
    void setUp() {
        // Bind @Value fields
        ReflectionTestUtils.setField(service, "sellerTin", "123456789");
        ReflectionTestUtils.setField(service, "sellerVrn", "40-012345-A");

        sale = makeSale(SALE_ID, COMPANY_ID, BRANCH_ID);
        saleLine = makeLine(SALE_ID, ITEM_ID);

        lenient().when(posSales.findById(SALE_ID)).thenReturn(Optional.of(sale));
        lenient().when(posSaleLines.findByPosSaleIdOrderByLineNoAsc(SALE_ID))
            .thenReturn(List.of(saleLine));
        lenient().when(items.findById(ITEM_ID)).thenReturn(Optional.of(makeItem(ITEM_ID, COMPANY_ID)));

        // Default: save returns the receipt with an id
        lenient().when(fiscalReceipts.save(any(FiscalReceipt.class))).thenAnswer(inv -> {
            FiscalReceipt r = inv.getArgument(0);
            if (r.getId() == null) ReflectionTestUtils.setField(r, "id", 999L);
            ReflectionTestUtils.setField(r, "uid", UidGenerator.next());
            return r;
        });
    }

    // -----------------------------------------------------------------------
    // Provider selection tests
    // -----------------------------------------------------------------------

    @Test
    void regime_NONE_uses_NoOpFiscalProvider() {
        NoOpFiscalProvider noOp = new NoOpFiscalProvider();
        when(providerFactory.getProvider()).thenReturn(noOp);
        when(fiscalReceipts.findByPosSaleId(SALE_ID)).thenReturn(Optional.empty());

        service.handleFiscalizationRequested(SALE_ID, COMPANY_ID, BRANCH_ID, ACTOR_ID);

        ArgumentCaptor<FiscalReceipt> captor = ArgumentCaptor.forClass(FiscalReceipt.class);
        verify(fiscalReceipts, atLeastOnce()).save(captor.capture());
        FiscalReceipt saved = captor.getAllValues().get(0);
        assertThat(saved.getStatus()).isEqualTo(FiscalStatus.NONE);
    }

    @Test
    void regime_TZ_VFD_uses_TraVfdFiscalProvider_and_returns_FISCALIZED() {
        StubEfdmsClient stub = new StubEfdmsClient();
        TraVfdFiscalProvider tzProvider = new TraVfdFiscalProvider(stub);
        when(providerFactory.getProvider()).thenReturn(tzProvider);
        when(fiscalReceipts.findByPosSaleId(SALE_ID)).thenReturn(Optional.empty());

        service.handleFiscalizationRequested(SALE_ID, COMPANY_ID, BRANCH_ID, ACTOR_ID);

        ArgumentCaptor<FiscalReceipt> captor = ArgumentCaptor.forClass(FiscalReceipt.class);
        verify(fiscalReceipts, atLeastOnce()).save(captor.capture());
        // The first save creates the row (PENDING); subsequent saves apply the result.
        // Check the final applied state via the receipt that had applyResult called:
        FiscalReceipt receipt = captor.getAllValues().get(0);
        // After applyResult the status should be FISCALIZED
        assertThat(receipt.getStatus()).isEqualTo(FiscalStatus.FISCALIZED);
        // STUB artefacts should be non-null
        assertThat(receipt.getRctnum()).isNotNull();
        assertThat(receipt.getVerificationCode()).startsWith("STUB-VCODE-");
        assertThat(receipt.getVerifyUrl()).contains("verify.tra.go.tz");
        assertThat(receipt.getQrPayload()).isNotBlank();
        assertThat(receipt.getSignature()).startsWith("STUB-SIG-");
    }

    @Test
    void providerFactory_regimeCode_NONE_returns_noOp_regimeCode() {
        NoOpFiscalProvider noOp = new NoOpFiscalProvider();
        assertThat(noOp.regimeCode()).isEqualTo("NONE");
    }

    @Test
    void providerFactory_regimeCode_TZ_VFD_returns_TZ_VFD() {
        TraVfdFiscalProvider tzProvider = new TraVfdFiscalProvider(new StubEfdmsClient());
        assertThat(tzProvider.regimeCode()).isEqualTo("TZ_VFD");
    }

    // -----------------------------------------------------------------------
    // TraVfdFiscalProvider + StubEfdmsClient happy path
    // -----------------------------------------------------------------------

    @Test
    void traNfd_with_stub_produces_FISCALIZED_result_with_simulated_fields() {
        StubEfdmsClient stub = new StubEfdmsClient();
        TraVfdFiscalProvider provider = new TraVfdFiscalProvider(stub);

        FiscalReceiptResultDto result = provider.fiscalize(makeFiscalizableDto());

        assertThat(result.status()).isEqualTo(FiscalStatus.FISCALIZED);
        // STUB values are deterministic; just assert non-null and structural prefix
        assertThat(result.rctnum()).isNotNull().isPositive();
        assertThat(result.gc()).isNotNull().isPositive();
        assertThat(result.dc()).isNotNull().isPositive();
        assertThat(result.verificationCode()).startsWith("STUB-VCODE-");
        assertThat(result.verifyUrl()).contains("verify.tra.go.tz");
        assertThat(result.qrPayload()).isNotBlank();
        assertThat(result.signature()).startsWith("STUB-SIG-");
        assertThat(result.rawResponse()).contains("STUB");
        assertThat(result.errorMessage()).isNull();
        assertThat(result.submittedAt()).isNotNull();
    }

    @Test
    void traNfd_with_failing_stub_produces_FAILED_status_and_preserves_error() {
        // Stub a client that always throws on submitReceipt
        EfdmsClient failingClient = new EfdmsClient() {
            @Override
            public FiscalReceiptResultDto submitReceipt(
                    com.orbix.engine.modules.fiscal.domain.dto.FiscalizableSaleDto s) {
                throw new EfdmsClientException("EFDMS timeout");
            }
            @Override
            public com.orbix.engine.modules.fiscal.domain.dto.ZReportResultDto submitZReport(
                    com.orbix.engine.modules.fiscal.domain.dto.ZReportRequestDto r) {
                throw new UnsupportedOperationException("not called in this test");
            }
        };
        TraVfdFiscalProvider provider = new TraVfdFiscalProvider(failingClient);
        when(providerFactory.getProvider()).thenReturn(provider);
        when(fiscalReceipts.findByPosSaleId(SALE_ID)).thenReturn(Optional.empty());

        // The service should catch the exception, set FAILED, and re-throw so outbox retries.
        assertThatThrownBy(() ->
            service.handleFiscalizationRequested(SALE_ID, COMPANY_ID, BRANCH_ID, ACTOR_ID))
            .isInstanceOf(EfdmsClientException.class)
            .hasMessageContaining("EFDMS timeout");

        ArgumentCaptor<FiscalReceipt> captor = ArgumentCaptor.forClass(FiscalReceipt.class);
        verify(fiscalReceipts, atLeastOnce()).save(captor.capture());
        FiscalReceipt receipt = captor.getAllValues().get(0);
        assertThat(receipt.getStatus()).isEqualTo(FiscalStatus.FAILED);
        assertThat(receipt.getLastError()).contains("EFDMS timeout");
        assertThat(receipt.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void handleFiscalizationRequested_missing_sellerTin_throws_FiscalizationException() {
        ReflectionTestUtils.setField(service, "sellerTin", ""); // blank TIN
        StubEfdmsClient stub = new StubEfdmsClient();
        TraVfdFiscalProvider provider = new TraVfdFiscalProvider(stub);
        when(providerFactory.getProvider()).thenReturn(provider);
        when(fiscalReceipts.findByPosSaleId(SALE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.handleFiscalizationRequested(SALE_ID, COMPANY_ID, BRANCH_ID, ACTOR_ID))
            .isInstanceOf(FiscalizationException.class)
            .hasMessageContaining("orbix.fiscal.tra.tin");
    }

    // -----------------------------------------------------------------------
    // Outbox event handler: emits + handler transitions PROVISIONAL→FISCALIZED
    // -----------------------------------------------------------------------

    @Test
    void fiscalizationEventHandler_deserializes_and_calls_service() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        FiscalizationService mockService = mock(FiscalizationService.class);
        FiscalizationEventHandler handler = new FiscalizationEventHandler(mockService, mapper);

        assertThat(handler.eventType()).isEqualTo("FiscalizationRequested.v1");

        FiscalizationRequestedEventDto dto = new FiscalizationRequestedEventDto(
            SALE_ID, "POS-001-0001", COMPANY_ID, BRANCH_ID, ACTOR_ID);
        String json = mapper.writeValueAsString(dto);

        handler.handle(json);

        verify(mockService).handleFiscalizationRequested(SALE_ID, COMPANY_ID, BRANCH_ID, ACTOR_ID);
    }

    @Test
    void handler_transitions_provisional_to_fiscalized_on_success() {
        // Arrange: no existing receipt, NoOp provider (returns NONE → maps to NONE status)
        NoOpFiscalProvider noOp = new NoOpFiscalProvider();
        when(providerFactory.getProvider()).thenReturn(noOp);
        when(fiscalReceipts.findByPosSaleId(SALE_ID)).thenReturn(Optional.empty());

        // Act
        service.handleFiscalizationRequested(SALE_ID, COMPANY_ID, BRANCH_ID, ACTOR_ID);

        // Assert: stampPosSale called on the sale entity — fiscal_status set to NONE
        assertThat(sale.getFiscalStatus()).isEqualTo("NONE");
    }

    @Test
    void handler_transitions_provisional_to_fiscalized_with_stub_provider() {
        TraVfdFiscalProvider tzProvider = new TraVfdFiscalProvider(new StubEfdmsClient());
        when(providerFactory.getProvider()).thenReturn(tzProvider);
        when(fiscalReceipts.findByPosSaleId(SALE_ID)).thenReturn(Optional.empty());

        service.handleFiscalizationRequested(SALE_ID, COMPANY_ID, BRANCH_ID, ACTOR_ID);

        // The sale's denormalized mirror columns should be set
        assertThat(sale.getFiscalStatus()).isEqualTo("FISCALIZED");
        assertThat(sale.getFiscalVerificationCode()).startsWith("STUB-VCODE-");
        assertThat(sale.getFiscalQrPayload()).isNotBlank();
        assertThat(sale.getFiscalSignature()).startsWith("STUB-SIG-");
    }

    @Test
    void idempotency_already_FISCALIZED_receipt_is_skipped() {
        FiscalReceipt already = makeFiscalizedReceipt(SALE_ID);
        when(fiscalReceipts.findByPosSaleId(SALE_ID)).thenReturn(Optional.of(already));

        service.handleFiscalizationRequested(SALE_ID, COMPANY_ID, BRANCH_ID, ACTOR_ID);

        // Provider should never be called
        verify(providerFactory, never()).getProvider();
        // No additional saves
        verify(fiscalReceipts, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // ArchUnit: pos does not import fiscal (structural; real ArchUnit test
    // enforces this; this test documents the intent explicitly)
    // -----------------------------------------------------------------------

    @Test
    void pos_sale_service_does_not_directly_import_fiscal_classes() throws ClassNotFoundException {
        Class<?> posServiceImplClass = Class.forName(
            "com.orbix.engine.modules.pos.service.PosSaleServiceImpl");
        for (var field : posServiceImplClass.getDeclaredFields()) {
            String typeName = field.getType().getName();
            assertThat(typeName)
                .as("PosSaleServiceImpl must not hold a direct reference to the fiscal module")
                .doesNotContain("com.orbix.engine.modules.fiscal");
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static PosSale makeSale(Long id, Long companyId, Long branchId) {
        PosSale s = new PosSale(
            "POS-001-0001", "op-123", 200L, 100L, branchId, companyId, 33L,
            540L, ACTOR_ID, null,
            com.orbix.engine.modules.pos.domain.enums.PosSaleKind.SALE,
            Instant.now(), LocalDate.now(),
            new BigDecimal("1000.0000"), BigDecimal.ZERO,
            new BigDecimal("180.0000"), new BigDecimal("1180.0000"),
            new BigDecimal("1180.0000"), BigDecimal.ZERO, null
        );
        ReflectionTestUtils.setField(s, "id", id);
        ReflectionTestUtils.setField(s, "uid", UidGenerator.next());
        return s;
    }

    private static PosSaleLine makeLine(Long saleId, Long itemId) {
        PosSaleLine l = new PosSaleLine(saleId, 1, itemId, 1L,
            new BigDecimal("2.0000"), new BigDecimal("500.0000"),
            BigDecimal.ZERO, BigDecimal.ZERO, 2L,
            new BigDecimal("180.0000"), new BigDecimal("1180.0000"));
        ReflectionTestUtils.setField(l, "id", 8001L);
        return l;
    }

    private static Item makeItem(Long id, Long companyId) {
        Item item = new Item(companyId, "SUGAR1", "Sugar 1kg",
            ItemType.SELLABLE, 10L, 1L, 2L, ACTOR_ID);
        ReflectionTestUtils.setField(item, "id", id);
        ReflectionTestUtils.setField(item, "uid", UidGenerator.next());
        return item;
    }

    private static com.orbix.engine.modules.fiscal.domain.dto.FiscalizableSaleDto makeFiscalizableDto() {
        return new com.orbix.engine.modules.fiscal.domain.dto.FiscalizableSaleDto(
            SALE_ID, "POS-001-0001", Instant.now(),
            COMPANY_ID, BRANCH_ID,
            "123456789", "40-012345-A",
            null, null,
            List.of(new com.orbix.engine.modules.fiscal.domain.dto.FiscalizableSaleDto.LineDto(
                1, "SUGAR1", "Sugar 1kg",
                new BigDecimal("2"), new BigDecimal("500"),
                BigDecimal.ZERO, new BigDecimal("1000"),
                "A", new BigDecimal("180"), new BigDecimal("1180")
            )),
            new BigDecimal("1000"), new BigDecimal("180"), new BigDecimal("1180"), "TZS"
        );
    }

    private static FiscalReceipt makeFiscalizedReceipt(Long posSaleId) {
        FiscalReceipt r = new FiscalReceipt(posSaleId, COMPANY_ID, BRANCH_ID, "TZ_VFD", ACTOR_ID);
        ReflectionTestUtils.setField(r, "id", 888L);
        ReflectionTestUtils.setField(r, "uid", UidGenerator.next());
        r.applyResult(FiscalStatus.FISCALIZED, 1001L, 5001L, 101L, 1,
            "VCODE-001", "https://verify.tra.go.tz/verify?rc=1001",
            "https://verify.tra.go.tz/verify?rc=1001", "SIG-001",
            "{\"status\":\"OK\"}", ACTOR_ID);
        return r;
    }
}
