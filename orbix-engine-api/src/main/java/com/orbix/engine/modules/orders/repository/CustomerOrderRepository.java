package com.orbix.engine.modules.orders.repository;

import com.orbix.engine.modules.orders.domain.entity.CustomerOrder;
import com.orbix.engine.modules.orders.domain.enums.CustomerOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    boolean existsByBranchIdAndNumber(Long branchId, String number);

    List<CustomerOrder> findByCompanyIdOrderByIdDesc(Long companyId);

    List<CustomerOrder> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId);

    List<CustomerOrder> findByCompanyIdAndCustomerIdOrderByIdDesc(Long companyId, Long customerId);

    List<CustomerOrder> findByCompanyIdAndStatusOrderByIdDesc(Long companyId, CustomerOrderStatus status);

    /**
     * Expiry-job driver: orders in a reserved-but-open state past their
     * {@code reserved_until} timestamp. Status filter is supplied by the caller
     * so the same query backs both the daily expiry sweep and the
     * (deferred) "notify-before-expiry" job (US-ORD-008).
     */
    List<CustomerOrder> findByStatusInAndReservedUntilLessThan(
        List<CustomerOrderStatus> statuses, Instant cutoff);
}
