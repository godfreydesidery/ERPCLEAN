package com.orbix.engine.modules.party.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Sales-agent role attached to a {@link Party} via a shared primary key.
 * May or may not be an employee. DATA-MODEL.md §2.7.
 */
@Entity
@Table(name = "sales_agent")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "partyId")
public class SalesAgent {

    @Id
    @Column(name = "party_id")
    private Long partyId;

    @Column(name = "app_user_id")
    private Long appUserId;

    @Column(name = "agent_code", nullable = false, length = 40)
    private String agentCode;

    @Column(name = "route_code", length = 40)
    private String routeCode;

    @Column(name = "commission_rate", precision = 10, scale = 4)
    private BigDecimal commissionRate;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    public SalesAgent(Long partyId, String agentCode, Long branchId) {
        this.partyId = partyId;
        this.agentCode = agentCode;
        this.branchId = branchId;
    }

    public void update(Long appUserId, String routeCode, BigDecimal commissionRate, Long branchId) {
        this.appUserId = appUserId;
        this.routeCode = routeCode;
        this.commissionRate = commissionRate;
        this.branchId = branchId;
    }
}
