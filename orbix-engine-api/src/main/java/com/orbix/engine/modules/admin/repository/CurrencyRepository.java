package com.orbix.engine.modules.admin.repository;

import com.orbix.engine.modules.admin.domain.entity.Currency;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurrencyRepository extends JpaRepository<Currency, String> {
}
