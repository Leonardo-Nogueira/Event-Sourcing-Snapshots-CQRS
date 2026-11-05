package com.leonardonogueira.eventsourcing.domain.model;

import com.leonardonogueira.eventsourcing.domain.events.AccountEvents.*;
import com.leonardonogueira.eventsourcing.domain.exception.AccountExceptions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * =============================================================================
 * AGGREGATE ROOT COM EVENT SOURCING: BankAccount
 * =============================================================================
 * 
 * Regra Arquitetural de Ouro:
 * 1. O estado da conta (balance, active, version) NÃO é gravado em tabelas comuns.
 * 2. Métodos de Comando (deposit, withdraw) validam regras de negócio e criam eventos.
 * 3. Métodos apply() mutam os campos em memória sem nenhuma validação.
 * 4. Ao reiniciar a JVM, o estado é reconstruído chamando rehydrate(history).
 */
public class BankAccount {

    private UUID id;
    private String holderName;
    private BigDecimal balance = BigDecimal.ZERO;
    private boolean active = false;
    private long version = 0;

    // Fila de novos eventos gerados na requisição atual (ainda não salvos no Event Store)
    private final List<AccountEvent> uncommittedEvents = new ArrayList<>();

    // Construtor vazio usado pelo Replay de eventos
    public BankAccount() {}

    // =========================================================================
    // 1. MÉTODOS DE COMANDO (FASE DE DECISÃO E VALIDAÇÃO DE REGRAS)
    // =========================================================================

    public static BankAccount open(UUID id, String holderName, BigDecimal initialDeposit) {
        Objects.requireNonNull(id, "ID da conta não pode ser nulo");
        if (holderName == null || holderName.isBlank()) {
            throw new IllegalArgumentException("Nome do titular não pode ser vazio");
        }
        if (initialDeposit.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Depósito inicial não pode ser negativo");
        }

        BankAccount account = new BankAccount();
        account.applyChange(new AccountOpenedEvent(id, holderName, initialDeposit, Instant.now()), true);
        return account;
    }

    public void deposit(BigDecimal amount, String description) {
        validateAccountIsActive();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valor do depósito deve ser maior que zero: " + amount);
        }

        applyChange(new MoneyDepositedEvent(this.id, amount, description, Instant.now()), true);
    }

    public void withdraw(BigDecimal amount, String reason) {
        validateAccountIsActive();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valor do saque deve ser maior que zero: " + amount);
        }

        if (this.balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(this.balance, amount);
        }

        applyChange(new MoneyWithdrawnEvent(this.id, amount, reason, Instant.now()), true);
    }

    public void block(String reason) {
        validateAccountIsActive();
        applyChange(new AccountBlockedEvent(this.id, reason, Instant.now()), true);
    }

    private void validateAccountIsActive() {
        if (!this.active) {
            throw new AccountBlockedException(this.id, "A conta está inativa ou bloqueada para transações.");
        }
    }

    // =========================================================================
    // 2. MÉTODOS DE EVOLUÇÃO (FASE APPLY: MUTAÇÃO PURA SEM VALIDAÇÃO)
    // =========================================================================

    private void applyChange(AccountEvent event, boolean isNew) {
        // Despacho polimórfico com Pattern Matching do Java 21!
        switch (event) {
            case AccountOpenedEvent e -> apply(e);
            case MoneyDepositedEvent e -> apply(e);
            case MoneyWithdrawnEvent e -> apply(e);
            case AccountBlockedEvent e -> apply(e);
        }

        this.version++;

        if (isNew) {
            this.uncommittedEvents.add(event);
        }
    }

    private void apply(AccountOpenedEvent event) {
        this.id = event.accountId();
        this.holderName = event.holderName();
        this.balance = event.initialDeposit();
        this.active = true;
    }

    private void apply(MoneyDepositedEvent event) {
        this.balance = this.balance.add(event.amount());
    }

    private void apply(MoneyWithdrawnEvent event) {
        this.balance = this.balance.subtract(event.amount());
    }

    private void apply(AccountBlockedEvent event) {
        this.active = false;
    }

    // =========================================================================
    // 3. REPLAY / REIDRATAÇÃO & SNAPSHOTS (RECONSTRUÇÃO HISTÓRICA DO AGREGADO)
    // =========================================================================

    /**
     * Snapshot imutável que captura a fotografia do estado da conta em uma versão específica.
     */
    public record AccountSnapshot(
            UUID accountId,
            String holderName,
            BigDecimal balance,
            boolean active,
            long version,
            Instant createdAt
    ) {}

    /**
     * Cria uma fotografia (snapshot) do estado atual deste agregado.
     */
    public AccountSnapshot createSnapshot() {
        return new AccountSnapshot(
                this.id,
                this.holderName,
                this.balance,
                this.active,
                this.version,
                Instant.now()
        );
    }

    /**
     * Reconstitui o agregado a partir do evento 1 tocando todo o histórico em ordem.
     * Usado quando NÃO há snapshot disponível. Complexidade: O(N).
     */
    public static BankAccount rehydrate(List<AccountEvent> history) {
        if (history == null || history.isEmpty()) {
            throw new IllegalArgumentException("Histórico de eventos vazio: impossível reconstituir o agregado!");
        }

        BankAccount account = new BankAccount();
        for (AccountEvent event : history) {
            account.applyChange(event, false); // false = não adiciona a uncommittedEvents!
        }
        return account;
    }

    /**
     * Reconstitui o agregado a partir de uma "fotografia" (snapshot) e aplica APENAS os
     * eventos delta ocorridos após a versão do snapshot.
     * Complexidade: O(Delta), reduzindo drasticamente o tempo de reconstrução.
     */
    public static BankAccount fromSnapshot(AccountSnapshot snapshot, List<AccountEvent> deltaEvents) {
        Objects.requireNonNull(snapshot, "Snapshot não pode ser nulo");

        BankAccount account = new BankAccount();
        account.id = snapshot.accountId();
        account.holderName = snapshot.holderName();
        account.balance = snapshot.balance();
        account.active = snapshot.active();
        account.version = snapshot.version();

        if (deltaEvents != null && !deltaEvents.isEmpty()) {
            for (AccountEvent event : deltaEvents) {
                account.applyChange(event, false);
            }
        }
        return account;
    }

    // =========================================================================
    // GETTERS & GERENCIAMENTO DE EVENTOS NÃO CONFIRMADOS
    // =========================================================================

    public UUID getId() { return id; }
    public String getHolderName() { return holderName; }
    public BigDecimal getBalance() { return balance; }
    public boolean isActive() { return active; }
    public long getVersion() { return version; }

    public List<AccountEvent> getUncommittedEvents() {
        return Collections.unmodifiableList(uncommittedEvents);
    }

    public void markEventsAsCommitted() {
        this.uncommittedEvents.clear();
    }
}

