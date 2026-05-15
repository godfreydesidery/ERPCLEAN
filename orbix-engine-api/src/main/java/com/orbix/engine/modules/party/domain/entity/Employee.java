package com.orbix.engine.modules.party.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Employee role attached to a {@link Party} via a shared primary key.
 * Optionally linked to an {@code app_user} for system access. DATA-MODEL.md §2.6.
 */
@Entity
@Table(name = "employee")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "partyId")
public class Employee {

    @Id
    @Column(name = "party_id")
    private Long partyId;

    @Column(name = "app_user_id")
    private Long appUserId;

    @Column(name = "employee_code", nullable = false, length = 40)
    private String employeeCode;

    @Column(name = "job_title", length = 120)
    private String jobTitle;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Column(name = "termination_date")
    private LocalDate terminationDate;

    public Employee(Long partyId, String employeeCode, Long branchId) {
        this.partyId = partyId;
        this.employeeCode = employeeCode;
        this.branchId = branchId;
    }

    public void update(Long appUserId, String jobTitle, Long branchId,
                       LocalDate hireDate, LocalDate terminationDate) {
        this.appUserId = appUserId;
        this.jobTitle = jobTitle;
        this.branchId = branchId;
        this.hireDate = hireDate;
        this.terminationDate = terminationDate;
    }
}
