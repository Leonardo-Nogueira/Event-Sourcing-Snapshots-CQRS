package com.leonardonogueira.eventsourcing.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entidade JPA para armazenamento das "Fotografias" de estado (Snapshots).
 * Tabela indexada em (aggregate_id, version DESC) para permitir busca instantânea O(1) do último snapshot.
 */
@Entity
@Table(
        name = "account_snapshots",
        indexes = {
                @Index(name = "idx_snapshot_aggregate_version", columnList = "aggregate_id, version DESC")
        }
)
public class AccountSnapshotJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "version", nullable = false, updatable = false)
    private long version;

    @Column(name = "holder_name", nullable = false)
    private String holderName;

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AccountSnapshotJpaEntity() {}

    public AccountSnapshotJpaEntity(
            UUID id,
            UUID aggregateId,
            long version,
            String holderName,
            BigDecimal balance,
            boolean active,
            Instant createdAt
    ) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.version = version;
        this.holderName = holderName;
        this.balance = balance;
        this.active = active;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getAggregateId() { return aggregateId; }
    public long getVersion() { return version; }
    public String getHolderName() { return holderName; }
    public BigDecimal getBalance() { return balance; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
}
