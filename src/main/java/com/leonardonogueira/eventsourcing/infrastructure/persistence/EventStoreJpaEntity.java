package com.leonardonogueira.eventsourcing.infrastructure.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * =============================================================================
 * TABELA: event_store (Append-Only Event Store)
 * =============================================================================
 * 
 * Regra Sagrada:
 * A constraint única uk_stream_version (aggregate_id + version) garante
 * que nenhuma transação concorrente consiga gravar na mesma versão,
 * implementando Controle de Concorrência Otimista (OCC) em nível de banco de dados.
 */
@Entity
@Table(name = "event_store", uniqueConstraints = {
        @UniqueConstraint(name = "uk_stream_version", columnNames = {"aggregate_id", "version"})
}, indexes = {
        @Index(name = "idx_aggregate_version", columnList = "aggregate_id, version")
})
public class EventStoreJpaEntity {

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "event_type", nullable = false, length = 150)
    private String eventType;

    @Lob
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public EventStoreJpaEntity() {}

    public EventStoreJpaEntity(UUID id, UUID aggregateId, long version, String eventType, String payload, Instant createdAt) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.version = version;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getAggregateId() { return aggregateId; }
    public long getVersion() { return version; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
}

