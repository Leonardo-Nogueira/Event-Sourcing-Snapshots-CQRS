package com.leonardonogueira.eventsourcing.infrastructure.persistence.read;

import com.leonardonogueira.eventsourcing.domain.events.AccountEvents.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * =============================================================================
 * CQRS PROJECTION HANDLER: AccountProjectionHandler
 * =============================================================================
 * 
 * Escuta os eventos de domínio publicados pelo Write Model e projeta (desnormaliza)
 * as alterações na tabela de leitura 'account_views'.
 * 
 * Características arquiteturais:
 * 1. Projeção orientada a eventos puros (Event-Driven Projections).
 * 2. Atualizações idempotentes baseadas em chave primária.
 * 3. Estatísticas acumuladas calculadas no momento da escrita (pré-computação).
 */
@Component
public class AccountProjectionHandler {

    private static final Logger log = LoggerFactory.getLogger(AccountProjectionHandler.class);

    private final SpringDataAccountViewRepository viewRepository;

    public AccountProjectionHandler(SpringDataAccountViewRepository viewRepository) {
        this.viewRepository = viewRepository;
    }

    @EventListener
    @Transactional
    public void on(AccountOpenedEvent event) {
        log.info("📊 [PROJEÇÃO] Projetando AccountOpenedEvent para a conta: {}", event.accountId());

        AccountViewJpaEntity view = new AccountViewJpaEntity(
                event.accountId(),
                event.holderName(),
                event.initialDeposit(),
                true,
                1L,
                0,
                0,
                event.occurredOn()
        );

        viewRepository.save(view);
        log.info("✅ [PROJEÇÃO] Visão de leitura criada para a conta: {}", event.accountId());
    }

    @EventListener
    @Transactional
    public void on(MoneyDepositedEvent event) {
        log.info("📊 [PROJEÇÃO] Projetando MoneyDepositedEvent (+R$ {}) para a conta: {}",
                event.amount(), event.accountId());

        viewRepository.findById(event.accountId()).ifPresent(view -> {
            view.setBalance(view.getBalance().add(event.amount()));
            view.setTotalDepositsCount(view.getTotalDepositsCount() + 1);
            view.setVersion(view.getVersion() + 1);
            view.setLastTransactionAt(event.occurredOn());

            viewRepository.save(view);
            log.info("✅ [PROJEÇÃO] Saldo atualizado na visão: R$ {}", view.getBalance());
        });
    }

    @EventListener
    @Transactional
    public void on(MoneyWithdrawnEvent event) {
        log.info("📊 [PROJEÇÃO] Projetando MoneyWithdrawnEvent (-R$ {}) para a conta: {}",
                event.amount(), event.accountId());

        viewRepository.findById(event.accountId()).ifPresent(view -> {
            view.setBalance(view.getBalance().subtract(event.amount()));
            view.setTotalWithdrawalsCount(view.getTotalWithdrawalsCount() + 1);
            view.setVersion(view.getVersion() + 1);
            view.setLastTransactionAt(event.occurredOn());

            viewRepository.save(view);
            log.info("✅ [PROJEÇÃO] Saldo atualizado na visão: R$ {}", view.getBalance());
        });
    }

    @EventListener
    @Transactional
    public void on(AccountBlockedEvent event) {
        log.info("📊 [PROJEÇÃO] Projetando AccountBlockedEvent para a conta: {}", event.accountId());

        viewRepository.findById(event.accountId()).ifPresent(view -> {
            view.setActive(false);
            view.setVersion(view.getVersion() + 1);
            view.setLastTransactionAt(event.occurredOn());

            viewRepository.save(view);
            log.info("✅ [PROJEÇÃO] Conta marcada como bloqueada na visão de leitura.");
        });
    }
}

