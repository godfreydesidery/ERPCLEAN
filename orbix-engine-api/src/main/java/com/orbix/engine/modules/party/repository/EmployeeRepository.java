package com.orbix.engine.modules.party.repository;

import com.orbix.engine.modules.party.domain.entity.Employee;
import com.orbix.engine.modules.party.domain.enums.PartyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    @Query("""
        select count(e) > 0 from Employee e join Party p on p.id = e.partyId
        where p.companyId = :companyId and e.employeeCode = :code
        """)
    boolean existsByCompanyIdAndEmployeeCode(@Param("companyId") Long companyId,
                                             @Param("code") String code);

    /**
     * Paginated employee search over the underlying party. {@code q} matches
     * employee code / party code / name (case-insensitive substring);
     * {@code status} filters by party lifecycle. Both are optional (null = no
     * filter). Ordered by employee code — pass an unsorted {@link Pageable}.
     */
    @Query(value = """
        select e from Employee e join Party p on p.id = e.partyId
        where p.companyId = :companyId
          and (:q is null
               or lower(e.employeeCode) like lower(concat('%', :q, '%'))
               or lower(p.code) like lower(concat('%', :q, '%'))
               or lower(p.name) like lower(concat('%', :q, '%')))
          and (:status is null or p.status = :status)
        order by e.employeeCode
        """,
        countQuery = """
        select count(e) from Employee e join Party p on p.id = e.partyId
        where p.companyId = :companyId
          and (:q is null
               or lower(e.employeeCode) like lower(concat('%', :q, '%'))
               or lower(p.code) like lower(concat('%', :q, '%'))
               or lower(p.name) like lower(concat('%', :q, '%')))
          and (:status is null or p.status = :status)
        """)
    Page<Employee> search(@Param("companyId") Long companyId,
                          @Param("q") String q,
                          @Param("status") PartyStatus status,
                          Pageable pageable);
}
