package com.orbix.engine.modules.party.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Customer role attached to a {@link Party} via a shared primary key.
 * DATA-MODEL.md §2.4.
 */
@Entity
@Table(name = "customer")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "partyId")
public class Customer {

    @Id
    @Column(name = "party_id")
    private Long partyId;

    @Column(name = "credit_limit_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal creditLimitAmount = BigDecimal.ZERO;

    @Column(name = "credit_terms_days", nullable = false)
    private int creditTermsDays = 0;

    @Column(name = "price_list_id")
    private Long priceListId;

    @Column(name = "default_sales_agent_id")
    private Long defaultSalesAgentId;

    @Column(name = "default_branch_id")
    private Long defaultBranchId;

    @Column(name = "is_walk_in", nullable = false)
    private boolean walkIn = false;

    @Column(name = "tax_exempt", nullable = false)
    private boolean taxExempt = false;

    public Customer(Long partyId) {
        this.partyId = partyId;
    }

    public static Customer walkIn(Long partyId, Long branchId) {
        Customer customer = new Customer(partyId);
        customer.walkIn = true;
        customer.defaultBranchId = branchId;
        return customer;
    }

    public void update(BigDecimal creditLimitAmount, int creditTermsDays, Long priceListId,
                       Long defaultSalesAgentId, Long defaultBranchId, boolean taxExempt) {
        this.creditLimitAmount = creditLimitAmount != null ? creditLimitAmount : BigDecimal.ZERO;
        this.creditTermsDays = creditTermsDays;
        this.priceListId = priceListId;
        this.defaultSalesAgentId = defaultSalesAgentId;
        this.defaultBranchId = defaultBranchId;
        this.taxExempt = taxExempt;
    }
}
