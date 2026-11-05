package com.leonardonogueira.eventsourcing.infrastructure.persistence.read;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * =============================================================================
 * CQRS READ MODEL: AccountViewJpaEntity
 * =============================================================================
 * 
 * Esta tabela NÃO é usada para validar comandos nem aplicar regras de negócio.
 * Ela é uma VISÃO DE LEITURA DESNORMALIZADA, otimizada com índices para:
 * 1. Consultas rápidas por saldo (ex: saldo >= 10.000).
 * 2. Buscas parciais por nome do titular.
 * 3. Estatísticas pré-computadas (total de depósitos e saques).
 */
@Entity
@Table(
        name = "account_views",
        indexes = {
                @Index(name = "idx_view_balance", columnList = "balance"),
                @Index(name = "idx_view_holder_name", columnList = "holder_name")
        }
)
public class AccountViewJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "holder_name", nullable = false)
    private String holderName;

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "total_deposits_count", nullable = false)
    private int totalDepositsCount;

    @Column(name = "total_withdrawals_count", nullable = false)
    private int totalWithdrawalsCount;

    @Column(name = "last_transaction_at", nullable = false)
    private Instant lastTransactionAt;

    public AccountViewJpaEntity() {}

    public AccountViewJpaEntity(
            UUID id,
            String holderName,
            BigDecimal balance,
            boolean active,
            long version,
            int totalDepositsCount,
            int totalWithdrawalsCount,
            Instant lastTransactionAt
    ) {
        this.id = id;
        this.holderName = holderName;
        this.balance = balance;
        this.active = active;
        this.version = version;
        this.totalDepositsCount = totalDepositsCount;
        this.totalWithdrawalsCount = totalWithdrawalsCount;
        this.lastTransactionAt = lastTransactionAt;
    }

    // Getters e Setters para atualização direta pelas Projeções
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public int getTotalDepositsCount() { return totalDepositsCount; }
    public void setTotalDepositsCount(int totalDepositsCount) { this.totalDepositsCount = totalDepositsCount; }

    public int getTotalWithdrawalsCount() { return totalWithdrawalsCount; }
    public void setTotalWithdrawalsCount(int totalWithdrawalsCount) { this.totalWithdrawalsCount = totalWithdrawalsCount; }

    public Instant getLastTransactionAt() { return lastTransactionAt; }
    public void setLastTransactionAt(Instant lastTransactionAt) { this.lastTransactionAt = lastTransactionAt; }
}

