package com.orbix.engine.modules.catalog.repository;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {

    @Query("select i from Item i where i.companyId = :companyId and i.code = :code")
    Optional<Item> findByCompanyAndCode(@Param("companyId") Long companyId,
                                        @Param("code") String code);

    Optional<Item> findByUid(String uid);

    Page<Item> findByCompanyId(Long companyId, Pageable pageable);

    Page<Item> findByCompanyIdAndStatus(Long companyId, ItemStatus status, Pageable pageable);

    /**
     * Full-text search over code and name (case-insensitive substring).
     * {@code q} is a pre-wrapped {@code %term%} string or {@code null} to skip.
     * {@code status} is optional; pass {@code null} for all statuses.
     */
    @Query(value = """
        select i from Item i
        where i.companyId = :companyId
          and (:q is null or lower(i.code) like :q or lower(i.name) like :q)
          and (:status is null or i.status = :status)
        order by i.code
        """,
        countQuery = """
        select count(i) from Item i
        where i.companyId = :companyId
          and (:q is null or lower(i.code) like :q or lower(i.name) like :q)
          and (:status is null or i.status = :status)
        """)
    Page<Item> search(@Param("companyId") Long companyId,
                      @Param("q") String q,
                      @Param("status") ItemStatus status,
                      Pageable pageable);

    /** Snapshot accessor for the F5.4 offline-sync endpoint. */
    List<Item> findByCompanyIdAndStatusOrderByIdAsc(Long companyId, ItemStatus status);

    /**
     * Sync pull delta: items whose change_seq is above the cursor watermark,
     * ordered for stable cursor advancement. Includes archived (= delete signal).
     * change_seq NULL treated as 0 in service layer — these rows are not returned.
     */
    @Query("select i from Item i where i.companyId = :companyId and i.changeSeq > :cursor order by i.changeSeq asc")
    List<Item> findByCompanyIdAndChangeSeqGreaterThan(@Param("companyId") Long companyId,
                                                      @Param("cursor") Long cursor,
                                                      Pageable pageable);

    boolean existsByItemGroupId(Long itemGroupId);

    long countByItemGroupIdAndStatus(Long itemGroupId, ItemStatus status);
}
