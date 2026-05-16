package com.orbix.engine.modules.orders.repository;

import com.orbix.engine.modules.orders.domain.entity.CustomerOrderPayment;
import com.orbix.engine.modules.orders.domain.enums.OrderPaymentDirection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerOrderPaymentRepository extends JpaRepository<CustomerOrderPayment, Long> {

    List<CustomerOrderPayment> findByCustomerOrderIdOrderByOccurredAtAsc(Long customerOrderId);

    List<CustomerOrderPayment> findByCustomerOrderIdAndDirection(Long customerOrderId,
                                                                  OrderPaymentDirection direction);

    Optional<CustomerOrderPayment> findByCustomerOrderIdAndIdempotencyKey(Long customerOrderId,
                                                                          String idempotencyKey);
}
