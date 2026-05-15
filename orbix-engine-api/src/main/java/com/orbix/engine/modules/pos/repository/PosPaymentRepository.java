package com.orbix.engine.modules.pos.repository;

import com.orbix.engine.modules.pos.domain.entity.PosPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PosPaymentRepository extends JpaRepository<PosPayment, Long> {

    List<PosPayment> findByPosSaleIdOrderByIdAsc(Long posSaleId);
}
