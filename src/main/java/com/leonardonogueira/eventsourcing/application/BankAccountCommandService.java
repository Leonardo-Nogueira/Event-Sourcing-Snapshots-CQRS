package com.leonardonogueira.eventsourcing.application;

import com.leonardonogueira.eventsourcing.domain.model.BankAccount;
import com.leonardonogueira.eventsourcing.infrastructure.persistence.AccountEventStoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * =============================================================================
 * APPLICATION SERVICE: BankAccountCommandService (CQRS Write Model)
 * =============================================================================
 * 
 * Orquestra as operações de comando:
 * 1. Carrega o agregado (Replay histórico via Event Store).
 * 2. Executa a intenção de negócio no Agregado.
 * 3. Salva os novos eventos no final do stream.
 */
@Service
public class BankAccountCommandService {

    private static final Logger log = LoggerFactory.getLogger(BankAccountCommandService.class);

    private final AccountEventStoreRepository eventStoreRepository;

    public BankAccountCommandService(AccountEventStoreRepository eventStoreRepository) {
        this.eventStoreRepository = eventStoreRepository;
    }

    public BankAccount openAccount(String holderName, BigDecimal initialDeposit) {
        UUID accountId = UUID.randomUUID();
        log.info("Iniciando abertura de conta para titular '{}' com depósito inicial de R$ {}", holderName, initialDeposit);

        BankAccount account = BankAccount.open(accountId, holderName, initialDeposit);
        eventStoreRepository.save(account);

        return account;
    }

    public BankAccount deposit(UUID accountId, BigDecimal amount, String description) {
        log.info("Processando depósito de R$ {} na conta '{}' ({})", amount, accountId, description);

        BankAccount account = eventStoreRepository.load(accountId);
        account.deposit(amount, description);
        eventStoreRepository.save(account);

        return account;
    }

    public BankAccount withdraw(UUID accountId, BigDecimal amount, String reason) {
        log.info("Processando saque de R$ {} na conta '{}' ({})", amount, accountId, reason);

        BankAccount account = eventStoreRepository.load(accountId);
        account.withdraw(amount, reason);
        eventStoreRepository.save(account);

        return account;
    }

    public BankAccount getAccount(UUID accountId) {
        return eventStoreRepository.load(accountId);
    }
}
