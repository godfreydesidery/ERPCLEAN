package com.orbix.engine.modules.catalog.repository;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {

    @Query("select i from Item i where i.companyId = :companyId and i.code = :code")
    Optional<Item> findByCompanyAndCode(@Param("companyId") Long companyId,
                                        @Param("code") String code);
}
