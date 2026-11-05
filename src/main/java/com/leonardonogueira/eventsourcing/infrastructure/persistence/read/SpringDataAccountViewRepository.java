package com.leonardonogueira.eventsourcing.infrastructure.persistence.read;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Repositório exclusivo para consultas rápidas do Lado de Leitura (CQRS Query Side).
 */
@Repository
public interface SpringDataAccountViewRepository extends JpaRepository<AccountViewJpaEntity, UUID> {

    List<AccountViewJpaEntity> findByBalanceGreaterThanEqualOrderByBalanceDesc(BigDecimal minBalance);

    List<AccountViewJpaEntity> findByHolderNameContainingIgnoreCaseOrderByHolderNameAsc(String name);

    List<AccountViewJpaEntity> findAllByOrderByBalanceDesc();
}

