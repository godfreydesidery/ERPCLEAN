package com.orbix.engine.modules.party.repository;

import com.orbix.engine.modules.party.domain.entity.Supplier;
import com.orbix.engine.modules.party.domain.enums.PartyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    /**
     * Paginated supplier search over the underlying party. {@code q} matches
     * code / name / TIN (case-insensitive substring); {@code status} filters by
     * party lifecycle. Both are optional (null = no filter). Ordered by party
     * code — pass an unsorted {@link Pageable} so this {@code order by} stands.
     */
    @Query(value = """
        select s from Supplier s join Party p on p.id = s.partyId
        where p.companyId = :companyId
          and (:q is null
               or lower(p.code) like lower(concat('%', :q, '%'))
               or lower(p.name) like lower(concat('%', :q, '%'))
               or (p.tin is not null and lower(p.tin) like lower(concat('%', :q, '%'))))
          and (:status is null or p.status = :status)
        order by p.code
        """,
        countQuery = """
        select count(s) from Supplier s join Party p on p.id = s.partyId
        where p.companyId = :companyId
          and (:q is null
               or lower(p.code) like lower(concat('%', :q, '%'))
               or lower(p.name) like lower(concat('%', :q, '%'))
               or (p.tin is not null and lower(p.tin) like lower(concat('%', :q, '%'))))
          and (:status is null or p.status = :status)
        """)
    Page<Supplier> search(@Param("companyId") Long companyId,
                          @Param("q") String q,
                          @Param("status") PartyStatus status,
                          Pageable pageable);
}
