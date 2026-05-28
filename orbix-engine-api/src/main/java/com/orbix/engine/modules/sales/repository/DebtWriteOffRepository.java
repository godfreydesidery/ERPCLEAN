package com.orbix.engine.modules.sales.repository;

import com.orbix.engine.modules.sales.domain.entity.DebtWriteOff;
import com.orbix.engine.modules.sales.domain.enums.DebtWriteOffStatus;
import com.orbix.engine.modules.sales.domain.enums.DebtWriteOffTargetKind;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DebtWriteOffRepository extends JpaRepository<DebtWriteOff, Long> {

    Optional<DebtWriteOff> findByUid(String uid);

    Page<DebtWriteOff> findByCompanyIdOrderByRequestedAtDescIdDesc(Long companyId, Pageable pageable);

    Page<DebtWriteOff> findByCompanyIdAndStatusOrderByRequestedAtDescIdDesc(
        Long companyId, DebtWriteOffStatus status, Pageable pageable);

    Page<DebtWriteOff> findByCompanyIdAndTargetKindOrderByRequestedAtDescIdDesc(
        Long companyId, DebtWriteOffTargetKind targetKind, Pageable pageable);

    Page<DebtWriteOff> findByCompanyIdAndStatusAndTargetKindOrderByRequestedAtDescIdDesc(
        Long companyId, DebtWriteOffStatus status, DebtWriteOffTargetKind targetKind, Pageable pageable);

    /**
     * Flexible list — all four filter combinations in one query.
     * Null params are treated as "all values" via JPQL nullable trick.
     */
    @Query("""
        select w from DebtWriteOff w
         where w.companyId = :companyId
           and (:status   is null or w.status     = :status)
           and (:kind     is null or w.targetKind = :kind)
         order by w.requestedAt desc, w.id desc
        """)
    Page<DebtWriteOff> findFiltered(
        @Param("companyId") Long companyId,
        @Param("status") DebtWriteOffStatus status,
        @Param("kind") DebtWriteOffTargetKind kind,
        Pageable pageable);
}
