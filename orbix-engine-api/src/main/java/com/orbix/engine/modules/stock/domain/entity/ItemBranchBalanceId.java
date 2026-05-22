package com.orbix.engine.modules.stock.domain.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Composite primary key for {@link ItemBranchBalance}: an item in a branch. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ItemBranchBalanceId implements Serializable {

    private Long itemId;
    private Long branchId;
}
