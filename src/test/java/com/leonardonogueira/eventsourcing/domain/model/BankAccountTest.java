package com.leonardonogueira.eventsourcing.domain.model;

import com.leonardonogueira.eventsourcing.domain.events.AccountEvents.*;
import com.leonardonogueira.eventsourcing.domain.exception.AccountExceptions.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BankAccountTest {

    @Test
    @DisplayName("Deve abrir conta, calcular saldo inicial e registrar evento não confirmado")
    void shouldOpenAccountWithInitialDeposit() {
        UUID accountId = UUID.randomUUID();
        BankAccount account = BankAccount.open(accountId, "Leonardo Nogueira", new BigDecimal("1000.00"));

        assertEquals(accountId, account.getId());
        assertEquals("Leonardo Nogueira", account.getHolderName());
        assertEquals(new BigDecimal("1000.00"), account.getBalance());
        assertTrue(account.isActive());
        assertEquals(1, account.getVersion());

        // Deve conter exatamente 1 novo evento não persistido
        assertEquals(1, account.getUncommittedEvents().size());
        assertInstanceOf(AccountOpenedEvent.class, account.getUncommittedEvents().getFirst());
    }

    @Test
    @DisplayName("Deve depositar e sacar atualizando o saldo e incrementando a versão do agregado")
    void shouldDepositAndWithdrawCorrectly() {
        UUID accountId = UUID.randomUUID();
        BankAccount account = BankAccount.open(accountId, "Leonardo", new BigDecimal("1000.00")); // v1: 1000

        account.deposit(new BigDecimal("500.00"), "Salário");   // v2: 1500
        account.withdraw(new BigDecimal("300.00"), "Aluguel");  // v3: 1200

        assertEquals(new BigDecimal("1200.00"), account.getBalance());
        assertEquals(3, account.getVersion());
        assertEquals(3, account.getUncommittedEvents().size());
    }

    @Test
    @DisplayName("Deve rejeitar saque quando o saldo for insuficiente (Invariante de Negócio)")
    void shouldRejectWithdrawWhenFundsAreInsufficient() {
        BankAccount account = BankAccount.open(UUID.randomUUID(), "Leonardo", new BigDecimal("100.00"));

        assertThrows(InsufficientFundsException.class, () -> {
            account.withdraw(new BigDecimal("150.00"), "Compra Indevida");
        });

        // Saldo não pode ter sido alterado
        assertEquals(new BigDecimal("100.00"), account.getBalance());
        assertEquals(1, account.getVersion());
    }

    @Test
    @DisplayName("REPLAY HISTÓRICO: Deve reconstruir perfeitamente o estado da conta a partir de eventos antigos")
    void shouldRehydrateAccountFromHistoricalEvents() {
        UUID accountId = UUID.randomUUID();

        // 4 eventos históricos vindos do banco de dados (Event Store)
        List<AccountEvent> history = List.of(
                new AccountOpenedEvent(accountId, "Leonardo Histórico", new BigDecimal("2000.00"), Instant.now()),
                new MoneyDepositedEvent(accountId, new BigDecimal("500.00"), "Depósito Pix", Instant.now()),
                new MoneyWithdrawnEvent(accountId, new BigDecimal("800.00"), "Boleto", Instant.now()),
                new MoneyWithdrawnEvent(accountId, new BigDecimal("200.00"), "Farmácia", Instant.now())
        );

        // 2000 + 500 - 800 - 200 = 1500.00
        BankAccount rehydratedAccount = BankAccount.rehydrate(history);

        assertEquals(accountId, rehydratedAccount.getId());
        assertEquals("Leonardo Histórico", rehydratedAccount.getHolderName());
        assertEquals(new BigDecimal("1500.00"), rehydratedAccount.getBalance());
        assertEquals(4, rehydratedAccount.getVersion());
        assertTrue(rehydratedAccount.isActive());

        // Eventos históricos não são novos, logo uncommittedEvents DEVE ser zero!
        assertTrue(rehydratedAccount.getUncommittedEvents().isEmpty(),
                "A lista de novos eventos deve estar vazia após a reidratação!");
    }

    @Test
    @DisplayName("SNAPSHOT: Deve criar snapshot do estado atual e restaurar a partir dele aplicando apenas eventos delta")
    void shouldCreateSnapshotAndRestoreWithDelta() {
        UUID accountId = UUID.randomUUID();
        BankAccount account = BankAccount.open(accountId, "Leonardo", new BigDecimal("1000.00"));
        account.deposit(new BigDecimal("500.00"), "Depósito 1");
        account.deposit(new BigDecimal("500.00"), "Depósito 2");
        account.withdraw(new BigDecimal("200.00"), "Saque 1"); // v4: saldo 1800.00

        // Tira o snapshot na versão 4
        BankAccount.AccountSnapshot snapshot = account.createSnapshot();
        assertEquals(accountId, snapshot.accountId());
        assertEquals("Leonardo", snapshot.holderName());
        assertEquals(new BigDecimal("1800.00"), snapshot.balance());
        assertEquals(4, snapshot.version());
        assertTrue(snapshot.active());

        // Simula que após o snapshot v4 ocorreram 2 novos eventos delta (v5 e v6)
        List<AccountEvent> deltaEvents = List.of(
                new MoneyDepositedEvent(accountId, new BigDecimal("300.00"), "Depósito 3", Instant.now()),
                new MoneyWithdrawnEvent(accountId, new BigDecimal("100.00"), "Saque 2", Instant.now())
        );

        // Reconstitui a partir do snapshot v4 + 2 delta events (sem precisar reprocessar os 4 primeiros eventos!)
        BankAccount restoredAccount = BankAccount.fromSnapshot(snapshot, deltaEvents);

        assertEquals(accountId, restoredAccount.getId());
        assertEquals(new BigDecimal("2000.00"), restoredAccount.getBalance()); // 1800 + 300 - 100 = 2000
        assertEquals(6, restoredAccount.getVersion()); // v4 + 2 deltas = v6
        assertTrue(restoredAccount.isActive());
        assertTrue(restoredAccount.getUncommittedEvents().isEmpty());
    }
}

