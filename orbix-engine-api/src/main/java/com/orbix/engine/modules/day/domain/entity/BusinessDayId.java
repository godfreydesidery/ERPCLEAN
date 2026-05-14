package com.orbix.engine.modules.day.domain.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/** Composite primary key for {@link BusinessDay}: a branch and its logical date. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BusinessDayId implements Serializable {

    private Long branchId;
    private LocalDate businessDate;
}
