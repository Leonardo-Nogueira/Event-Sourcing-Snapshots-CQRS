package com.leonardonogueira.eventsourcing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataEventStoreRepository extends JpaRepository<EventStoreJpaEntity, UUID> {

    List<EventStoreJpaEntity> findByAggregateIdOrderByVersionAsc(UUID aggregateId);

    /**
     * Retorna apenas os eventos delta posteriores a uma determinada versão (usado em conjunto com Snapshots).
     */
    List<EventStoreJpaEntity> findByAggregateIdAndVersionGreaterThanOrderByVersionAsc(UUID aggregateId, long version);

    long countByAggregateId(UUID aggregateId);
}

