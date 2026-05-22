package com.orbix.engine.modules.catalog.repository;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {

    @Query("select i from Item i where i.companyId = :companyId and i.code = :code")
    Optional<Item> findByCompanyAndCode(@Param("companyId") Long companyId,
                                        @Param("code") String code);

    Optional<Item> findByUid(String uid);

    Page<Item> findByCompanyId(Long companyId, Pageable pageable);

    Page<Item> findByCompanyIdAndStatus(Long companyId, ItemStatus status, Pageable pageable);

    /** Snapshot accessor for the F5.4 offline-sync endpoint. */
    List<Item> findByCompanyIdAndStatusOrderByIdAsc(Long companyId, ItemStatus status);

    boolean existsByItemGroupId(Long itemGroupId);
}
