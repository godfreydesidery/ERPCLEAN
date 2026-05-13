package com.orbix.engine.modules.iam.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Atomic permission unit. Code looks like {@code ITEM.CREATE}. DATA-MODEL.md §1.6. */
@Entity
@Table(name = "permission")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "permission_seq")
    @SequenceGenerator(name = "permission_seq", sequenceName = "permission_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 80)
    private String code;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 40)
    private String module;

    public Permission(String code, String description, String module) {
        this.code = code;
        this.description = description;
        this.module = module;
    }
}
