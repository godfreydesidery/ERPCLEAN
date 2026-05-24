package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.sales.domain.dto.CreatePackingListRequestDto;
import com.orbix.engine.modules.sales.domain.dto.PackingListDto;

import java.util.List;

/**
 * Packing lists (F4.5). DRAFT → DISPATCHED → DELIVERED → terminal; DRAFT →
 * CANCELLED. Stock is decremented by the parent {@code sales_invoice} on
 * post (F4.2); the packing list is a delivery-tracking document on top of that.
 */
public interface PackingListService {

    PackingListDto createDraft(CreatePackingListRequestDto request);

    PackingListDto dispatch(String uid);

    PackingListDto markDelivered(String uid);

    PackingListDto cancel(String uid);

    List<PackingListDto> list(Long branchId);

    PackingListDto get(String uid);
}
