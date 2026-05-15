package com.orbix.engine.modules.sales.repository;

import com.orbix.engine.modules.sales.domain.entity.CustomerReturnLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerReturnLineRepository extends JpaRepository<CustomerReturnLine, Long> {

    List<CustomerReturnLine> findByCustomerReturnIdOrderByLineNoAsc(Long customerReturnId);

    void deleteByCustomerReturnId(Long customerReturnId);
}
