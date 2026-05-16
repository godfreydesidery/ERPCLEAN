package com.orbix.engine.modules.orders.repository;

import com.orbix.engine.modules.orders.domain.entity.CustomerOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerOrderLineRepository extends JpaRepository<CustomerOrderLine, Long> {

    List<CustomerOrderLine> findByCustomerOrderIdOrderByLineNoAsc(Long customerOrderId);

    void deleteByCustomerOrderId(Long customerOrderId);
}
