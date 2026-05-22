package com.orbix.engine.modules.party.repository;

import com.orbix.engine.modules.party.domain.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @Query("select c from Customer c join Party p on p.id = c.partyId where p.companyId = :companyId")
    List<Customer> findByCompanyId(@Param("companyId") Long companyId);
}
