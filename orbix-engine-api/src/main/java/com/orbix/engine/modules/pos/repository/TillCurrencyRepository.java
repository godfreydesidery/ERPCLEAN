package com.orbix.engine.modules.pos.repository;

import com.orbix.engine.modules.pos.domain.entity.TillCurrency;
import com.orbix.engine.modules.pos.domain.entity.TillCurrencyId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TillCurrencyRepository extends JpaRepository<TillCurrency, TillCurrencyId> {

    List<TillCurrency> findByIdTillIdOrderByIdCurrencyCodeAsc(Long tillId);

    boolean existsByIdTillIdAndIdCurrencyCode(Long tillId, String currencyCode);

    void deleteByIdTillIdAndIdCurrencyCode(Long tillId, String currencyCode);
}
