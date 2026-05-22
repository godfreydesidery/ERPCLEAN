package com.orbix.engine.modules.catalog.repository;

import com.orbix.engine.modules.catalog.domain.entity.ItemBarcode;
import com.orbix.engine.modules.catalog.domain.enums.BarcodeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemBarcodeRepository extends JpaRepository<ItemBarcode, Long> {

    Optional<ItemBarcode> findByUid(String uid);

    List<ItemBarcode> findByItemId(Long itemId);

    boolean existsByBarcode(String barcode);

    Optional<ItemBarcode> findByBarcode(String barcode);

    Optional<ItemBarcode> findByBarcodeAndBarcodeType(String barcode, BarcodeType barcodeType);
}
