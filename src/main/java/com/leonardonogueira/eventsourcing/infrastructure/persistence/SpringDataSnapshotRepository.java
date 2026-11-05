package com.leonardonogueira.eventsourcing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataSnapshotRepository extends JpaRepository<AccountSnapshotJpaEntity, UUID> {

    /**
     * Retorna a fotografia mais recente tirada para o agregado informado.
     */
    Optional<AccountSnapshotJpaEntity> findTopByAggregateIdOrderByVersionDesc(UUID aggregateId);

    long countByAggregateId(UUID aggregateId);
}
