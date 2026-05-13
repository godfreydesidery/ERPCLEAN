package com.orbix.engine.modules.admin.domain.entity;

import com.orbix.engine.modules.admin.domain.enums.AdminStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Physical site within a company. DATA-MODEL.md §1.3. */
@Entity
@Table(
    name = "branch",
    uniqueConstraints = @UniqueConstraint(name = "uk_branch_company_code", columnNames = {"company_id", "code"})
)
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "branch_seq")
    @SequenceGenerator(name = "branch_seq", sequenceName = "branch_seq", allocationSize = 50)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(name = "physical_address", columnDefinition = "TEXT")
    private String physicalAddress;

    @Column(length = 40)
    private String phone;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AdminStatus status = AdminStatus.ACTIVE;

    @Version
    private Integer version;

    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "updated_by", nullable = false) private Long updatedBy;

    public Branch(Long companyId, String code, String name, String type,
                  String timeZone, boolean isDefault, Long actorId) {
        this.companyId = companyId;
        this.code = code;
        this.name = name;
        this.type = type;
        this.timeZone = timeZone;
        this.isDefault = isDefault;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }
}
