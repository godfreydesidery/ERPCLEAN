package com.orbix.engine.modules.party.repository;

import com.orbix.engine.modules.party.domain.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    @Query("select e from Employee e join Party p on p.id = e.partyId where p.companyId = :companyId")
    List<Employee> findByCompanyId(@Param("companyId") Long companyId);

    @Query("""
        select count(e) > 0 from Employee e join Party p on p.id = e.partyId
        where p.companyId = :companyId and e.employeeCode = :code
        """)
    boolean existsByCompanyIdAndEmployeeCode(@Param("companyId") Long companyId,
                                             @Param("code") String code);
}
