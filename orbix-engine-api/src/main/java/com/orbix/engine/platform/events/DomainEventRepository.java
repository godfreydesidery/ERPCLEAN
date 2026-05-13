package com.orbix.engine.platform.events;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DomainEventRepository extends JpaRepository<DomainEvent, Long> {

    List<DomainEvent> findByStatusOrderByOccurredAtAsc(DomainEvent.Status status, Pageable page);
}
