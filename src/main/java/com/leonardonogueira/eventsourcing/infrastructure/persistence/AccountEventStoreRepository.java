package com.leonardonogueira.eventsourcing.infrastructure.persistence;

import com.leonardonogueira.eventsourcing.domain.events.AccountEvents.AccountEvent;
import com.leonardonogueira.eventsourcing.domain.exception.AccountExceptions.AccountNotFoundException;
import com.leonardonogueira.eventsourcing.domain.exception.ConcurrencyException;
import com.leonardonogueira.eventsourcing.domain.model.BankAccount;
import com.leonardonogueira.eventsourcing.domain.model.BankAccount.AccountSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * =============================================================================
 * REPOSITÓRIO EVENT-SOURCED COM SUPORTE A SNAPSHOTS & CQRS: AccountEventStoreRepository
 * =============================================================================
 * 
 * Responsabilidades:
 * 1. load(accountId):
 *    - Passo 1: Tenta obter o snapshot mais recente da conta (O(1)).
 *    - Passo 2: Se houver snapshot (ex: versão 100), busca APENAS os eventos posteriores (versão > 100).
 *               Reidrata o agregado a partir do snapshot + delta.
 *    - Passo 3: Se NÃO houver snapshot, busca a stream completa desde o evento 1 e reidrata do zero.
 * 
 * 2. save(account):
 *    - Persiste os novos eventos no Append-Only Stream com OCC (Optimistic Concurrency Control).
 *    - Publica os eventos no barramento de eventos (ApplicationEventPublisher) para atualizar
 *      os modelos de leitura das Projeções CQRS.
 *    - Verifica a estratégia de Snapshots: a cada SNAPSHOT_INTERVAL (ex: 5 eventos),
 *      grava uma nova fotografia na tabela account_snapshots.
 */
@Component
public class AccountEventStoreRepository {

    private static final Logger log = LoggerFactory.getLogger(AccountEventStoreRepository.class);

    // No lab usaremos intervalo de 5 eventos para facilitar testes e demonstrações práticas
    public static final int SNAPSHOT_INTERVAL = 5;

    private final SpringDataEventStoreRepository eventStoreRepository;
    private final SpringDataSnapshotRepository snapshotRepository;
    private final EventSerializer serializer;
    private final ApplicationEventPublisher eventPublisher;

    public AccountEventStoreRepository(
            SpringDataEventStoreRepository eventStoreRepository,
            SpringDataSnapshotRepository snapshotRepository,
            EventSerializer serializer,
            ApplicationEventPublisher eventPublisher
    ) {
        this.eventStoreRepository = eventStoreRepository;
        this.snapshotRepository = snapshotRepository;
        this.serializer = serializer;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Carrega a conta utilizando otimização por Snapshots quando disponível.
     */
    @Transactional(readOnly = true)
    public BankAccount load(UUID accountId) {
        log.info("🔍 [EVENT STORE] Buscando agregado da conta: {}", accountId);

        // 1. Tenta carregar o snapshot mais recente
        Optional<AccountSnapshotJpaEntity> latestSnapshot = snapshotRepository.findTopByAggregateIdOrderByVersionDesc(accountId);

        if (latestSnapshot.isPresent()) {
            AccountSnapshotJpaEntity snapshotEntity = latestSnapshot.get();
            log.info("📸 [SNAPSHOT ENCONTRADO] Conta '{}' possui snapshot na versão {} (Saldo: R$ {})",
                    accountId, snapshotEntity.getVersion(), snapshotEntity.getBalance());

            // 2. Busca apenas os eventos que aconteceram APÓS o snapshot
            List<EventStoreJpaEntity> deltaEntities = eventStoreRepository
                    .findByAggregateIdAndVersionGreaterThanOrderByVersionAsc(accountId, snapshotEntity.getVersion());

            List<AccountEvent> deltaEvents = deltaEntities.stream()
                    .map(e -> serializer.deserialize(e.getPayload(), e.getEventType()))
                    .toList();

            log.info("⚡ [SNAPSHOT REHYDRATION] Re-hidratando conta a partir do Snapshot v{} + {} evento(s) delta...",
                    snapshotEntity.getVersion(), deltaEvents.size());

            AccountSnapshot snapshotDomain = new AccountSnapshot(
                    snapshotEntity.getAggregateId(),
                    snapshotEntity.getHolderName(),
                    snapshotEntity.getBalance(),
                    snapshotEntity.isActive(),
                    snapshotEntity.getVersion(),
                    snapshotEntity.getCreatedAt()
            );

            return BankAccount.fromSnapshot(snapshotDomain, deltaEvents);
        }

        // 3. Caso não haja snapshot, busca todos os eventos desde o início (Reidratação pura)
        List<EventStoreJpaEntity> allEntities = eventStoreRepository.findByAggregateIdOrderByVersionAsc(accountId);

        if (allEntities.isEmpty()) {
            throw new AccountNotFoundException(accountId);
        }

        List<AccountEvent> history = allEntities.stream()
                .map(e -> serializer.deserialize(e.getPayload(), e.getEventType()))
                .toList();

        log.info("🔄 [FULL REHYDRATION] Re-hidratando conta '{}' desde o início com {} eventos históricos...",
                accountId, history.size());
        return BankAccount.rehydrate(history);
    }

    /**
     * Persiste os novos eventos no final do Append-Only Stream com validação de versão e salva snapshots periódicos.
     */
    @Transactional
    public void save(BankAccount account) {
        List<AccountEvent> newEvents = account.getUncommittedEvents();
        if (newEvents.isEmpty()) {
            return;
        }

        log.info("💾 [EVENT STORE] Salvando {} novos eventos para a conta: {}", newEvents.size(), account.getId());

        long currentVersion = account.getVersion();
        long startingVersion = currentVersion - newEvents.size() + 1;

        for (int i = 0; i < newEvents.size(); i++) {
            AccountEvent event = newEvents.get(i);
            long eventVersion = startingVersion + i;

            String payloadJson = serializer.serialize(event);

            EventStoreJpaEntity entity = new EventStoreJpaEntity(
                    UUID.randomUUID(),
                    account.getId(),
                    eventVersion,
                    event.getClass().getSimpleName(),
                    payloadJson,
                    event.occurredOn()
            );

            try {
                eventStoreRepository.saveAndFlush(entity);
                log.info("✅ [EVENT STORE] Evento '{}' gravado! (Stream: {}, Versão: {})",
                        event.getClass().getSimpleName(), account.getId(), eventVersion);
            } catch (DataIntegrityViolationException e) {
                log.error("💥 [CONCURRENCY CONFLICT] Colisão de versão detectada para a conta '{}' na versão '{}'",
                        account.getId(), eventVersion);
                throw new ConcurrencyException(account.getId(), eventVersion);
            }
        }

        // Limpa a fila de eventos pendentes na memória do agregado
        account.markEventsAsCommitted();

        // Publica os eventos gravados para os projetores CQRS atualizarem suas visões de leitura
        for (AccountEvent event : newEvents) {
            log.info("📢 [CQRS EVENT BUS] Publicando evento '{}' para projeções de leitura...",
                    event.getClass().getSimpleName());
            eventPublisher.publishEvent(event);
        }

        // Estratégia de Snapshot: a cada SNAPSHOT_INTERVAL versões (ou se for exatamente o múltiplo)
        if (account.getVersion() >= SNAPSHOT_INTERVAL && (account.getVersion() % SNAPSHOT_INTERVAL == 0)) {
            takeSnapshot(account);
        }
    }

    private void takeSnapshot(BankAccount account) {
        log.info("📸 [CRIANDO SNAPSHOT] Tirando fotografia da conta '{}' na versão {} (Saldo: R$ {})...",
                account.getId(), account.getVersion(), account.getBalance());

        AccountSnapshot snapshot = account.createSnapshot();
        AccountSnapshotJpaEntity entity = new AccountSnapshotJpaEntity(
                UUID.randomUUID(),
                snapshot.accountId(),
                snapshot.version(),
                snapshot.holderName(),
                snapshot.balance(),
                snapshot.active(),
                snapshot.createdAt()
        );

        snapshotRepository.save(entity);
        log.info("✅ [SNAPSHOT SALVO] Fotografia salva com sucesso para a versão {}!", snapshot.version());
    }
}

