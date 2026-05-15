package com.orbix.engine.modules.catalog.repository;

import com.orbix.engine.modules.catalog.domain.entity.ItemBarcode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemBarcodeRepository extends JpaRepository<ItemBarcode, Long> {

    List<ItemBarcode> findByItemId(Long itemId);

    boolean existsByBarcode(String barcode);
}
