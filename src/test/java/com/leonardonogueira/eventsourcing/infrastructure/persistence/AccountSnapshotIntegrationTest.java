package com.leonardonogueira.eventsourcing.infrastructure.persistence;

import com.leonardonogueira.eventsourcing.domain.model.BankAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class AccountSnapshotTest {

    private InMemorySnapshotRepository snapshotRepository;
    private InMemoryEventStoreRepository rawEventStoreRepository;
    private AccountEventStoreRepository eventStoreRepository;

    @BeforeEach
    void setUp() {
        snapshotRepository = new InMemorySnapshotRepository();
        rawEventStoreRepository = new InMemoryEventStoreRepository();
        EventSerializer serializer = new EventSerializer();
        ApplicationEventPublisher dummyPublisher = event -> {}; // no-op publisher para o teste de snapshot

        eventStoreRepository = new AccountEventStoreRepository(
                rawEventStoreRepository,
                snapshotRepository,
                serializer,
                dummyPublisher
        );
    }

    @Test
    @DisplayName("Deve gerar snapshots a cada 5 versões e reidratar a partir do snapshot + delta")
    void shouldGenerateSnapshotsAndRehydrateFromSnapshotPlusDelta() {
        UUID accountId = UUID.randomUUID();

        // 1. Abre a conta (Versão 1)
        BankAccount account = BankAccount.open(accountId, "Leonardo Nogueira", new BigDecimal("100.00"));
        eventStoreRepository.save(account);

        // Versão 1: nenhum snapshot ainda (intervalo é 5)
        assertEquals(0, snapshotRepository.countByAggregateId(accountId));
        assertEquals(1, rawEventStoreRepository.countByAggregateId(accountId));

        // 2. Faz 4 depósitos para atingir a Versão 5 (1 + 4 = 5)
        for (int i = 1; i <= 4; i++) {
            BankAccount loaded = eventStoreRepository.load(accountId);
            loaded.deposit(new BigDecimal("10.00"), "Depósito " + i);
            eventStoreRepository.save(loaded);
        }

        // Versão 5 atingida: DEVE ter gerado 1 snapshot!
        assertEquals(1, snapshotRepository.countByAggregateId(accountId));
        var snapshotV5 = snapshotRepository.findTopByAggregateIdOrderByVersionDesc(accountId).orElseThrow();
        assertEquals(5, snapshotV5.getVersion());
        assertEquals(new BigDecimal("140.00"), snapshotV5.getBalance()); // 100 + 4*10 = 140

        // 3. Faz mais 2 depósitos (atingindo Versão 7)
        for (int i = 5; i <= 6; i++) {
            BankAccount loaded = eventStoreRepository.load(accountId);
            loaded.deposit(new BigDecimal("20.00"), "Depósito " + i);
            eventStoreRepository.save(loaded);
        }

        // Continua com 1 snapshot (v5), e stream tem 7 eventos no total
        assertEquals(1, snapshotRepository.countByAggregateId(accountId));
        assertEquals(7, rawEventStoreRepository.countByAggregateId(accountId));

        // 4. Carrega a conta: deve carregar Snapshot v5 + 2 eventos delta (v6 e v7)
        BankAccount rehydratedV7 = eventStoreRepository.load(accountId);
        assertEquals(7, rehydratedV7.getVersion());
        assertEquals(new BigDecimal("180.00"), rehydratedV7.getBalance()); // 140 + 2*20 = 180
        assertTrue(rehydratedV7.isActive());

        // 5. Faz mais 3 depósitos para atingir Versão 10 (múltiplo de 5)
        for (int i = 7; i <= 9; i++) {
            BankAccount loaded = eventStoreRepository.load(accountId);
            loaded.deposit(new BigDecimal("10.00"), "Depósito " + i);
            eventStoreRepository.save(loaded);
        }

        // Agora deve haver 2 snapshots no histórico, com o mais recente sendo Versão 10
        assertEquals(2, snapshotRepository.countByAggregateId(accountId));
        var snapshotV10 = snapshotRepository.findTopByAggregateIdOrderByVersionDesc(accountId).orElseThrow();
        assertEquals(10, snapshotV10.getVersion());
        assertEquals(new BigDecimal("210.00"), snapshotV10.getBalance()); // 180 + 3*10 = 210

        // 6. Recarrega a conta na Versão 10: snapshot direto com 0 eventos delta!
        BankAccount rehydratedV10 = eventStoreRepository.load(accountId);
        assertEquals(10, rehydratedV10.getVersion());
        assertEquals(new BigDecimal("210.00"), rehydratedV10.getBalance());
    }

    // =========================================================================
    // InMemory Repositories para Testes Determinísticos Ultrarrápidos
    // =========================================================================

    static class InMemorySnapshotRepository implements SpringDataSnapshotRepository {
        private final Map<UUID, AccountSnapshotJpaEntity> storage = new ConcurrentHashMap<>();

        @Override
        public <S extends AccountSnapshotJpaEntity> S save(S entity) {
            storage.put(entity.getId(), entity);
            return entity;
        }

        @Override
        public Optional<AccountSnapshotJpaEntity> findTopByAggregateIdOrderByVersionDesc(UUID aggregateId) {
            return storage.values().stream()
                    .filter(s -> s.getAggregateId().equals(aggregateId))
                    .max(Comparator.comparingLong(AccountSnapshotJpaEntity::getVersion));
        }

        @Override
        public long countByAggregateId(UUID aggregateId) {
            return storage.values().stream()
                    .filter(s -> s.getAggregateId().equals(aggregateId))
                    .count();
        }

        @Override public void flush() {}
        @Override public <S extends AccountSnapshotJpaEntity> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends AccountSnapshotJpaEntity> List<S> saveAllAndFlush(Iterable<S> entities) { return null; }
        @Override public void deleteAllInBatch(Iterable<AccountSnapshotJpaEntity> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<UUID> uuids) {}
        @Override public void deleteAllInBatch() {}
        @Override public AccountSnapshotJpaEntity getOne(UUID uuid) { return storage.get(uuid); }
        @Override public AccountSnapshotJpaEntity getById(UUID uuid) { return storage.get(uuid); }
        @Override public AccountSnapshotJpaEntity getReferenceById(UUID uuid) { return storage.get(uuid); }
        @Override public <S extends AccountSnapshotJpaEntity> Optional<S> findOne(Example<S> example) { return Optional.empty(); }
        @Override public <S extends AccountSnapshotJpaEntity> List<S> findAll(Example<S> example) { return null; }
        @Override public <S extends AccountSnapshotJpaEntity> List<S> findAll(Example<S> example, Sort sort) { return null; }
        @Override public <S extends AccountSnapshotJpaEntity> Page<S> findAll(Example<S> example, Pageable pageable) { return null; }
        @Override public <S extends AccountSnapshotJpaEntity> long count(Example<S> example) { return 0; }
        @Override public <S extends AccountSnapshotJpaEntity> boolean exists(Example<S> example) { return false; }
        @Override public <S extends AccountSnapshotJpaEntity, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public <S extends AccountSnapshotJpaEntity> List<S> saveAll(Iterable<S> entities) { return null; }
        @Override public Optional<AccountSnapshotJpaEntity> findById(UUID uuid) { return Optional.ofNullable(storage.get(uuid)); }
        @Override public boolean existsById(UUID uuid) { return storage.containsKey(uuid); }
        @Override public List<AccountSnapshotJpaEntity> findAll() { return new ArrayList<>(storage.values()); }
        @Override public List<AccountSnapshotJpaEntity> findAllById(Iterable<UUID> uuids) { return null; }
        @Override public long count() { return storage.size(); }
        @Override public void deleteById(UUID uuid) { storage.remove(uuid); }
        @Override public void delete(AccountSnapshotJpaEntity entity) { storage.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> uuids) {}
        @Override public void deleteAll(Iterable<? extends AccountSnapshotJpaEntity> entities) {}
        @Override public void deleteAll() { storage.clear(); }
        @Override public List<AccountSnapshotJpaEntity> findAll(Sort sort) { return null; }
        @Override public Page<AccountSnapshotJpaEntity> findAll(Pageable pageable) { return null; }
    }

    static class InMemoryEventStoreRepository implements SpringDataEventStoreRepository {
        private final Map<UUID, EventStoreJpaEntity> storage = new ConcurrentHashMap<>();

        @Override
        public <S extends EventStoreJpaEntity> S save(S entity) {
            storage.put(entity.getId(), entity);
            return entity;
        }

        @Override
        public <S extends EventStoreJpaEntity> S saveAndFlush(S entity) {
            return save(entity);
        }

        @Override
        public List<EventStoreJpaEntity> findByAggregateIdOrderByVersionAsc(UUID aggregateId) {
            return storage.values().stream()
                    .filter(e -> e.getAggregateId().equals(aggregateId))
                    .sorted(Comparator.comparingLong(EventStoreJpaEntity::getVersion))
                    .toList();
        }

        @Override
        public List<EventStoreJpaEntity> findByAggregateIdAndVersionGreaterThanOrderByVersionAsc(UUID aggregateId, long version) {
            return storage.values().stream()
                    .filter(e -> e.getAggregateId().equals(aggregateId) && e.getVersion() > version)
                    .sorted(Comparator.comparingLong(EventStoreJpaEntity::getVersion))
                    .toList();
        }

        @Override
        public long countByAggregateId(UUID aggregateId) {
            return storage.values().stream()
                    .filter(e -> e.getAggregateId().equals(aggregateId))
                    .count();
        }

        @Override public void flush() {}
        @Override public <S extends EventStoreJpaEntity> List<S> saveAllAndFlush(Iterable<S> entities) { return null; }
        @Override public void deleteAllInBatch(Iterable<EventStoreJpaEntity> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<UUID> uuids) {}
        @Override public void deleteAllInBatch() {}
        @Override public EventStoreJpaEntity getOne(UUID uuid) { return storage.get(uuid); }
        @Override public EventStoreJpaEntity getById(UUID uuid) { return storage.get(uuid); }
        @Override public EventStoreJpaEntity getReferenceById(UUID uuid) { return storage.get(uuid); }
        @Override public <S extends EventStoreJpaEntity> Optional<S> findOne(Example<S> example) { return Optional.empty(); }
        @Override public <S extends EventStoreJpaEntity> List<S> findAll(Example<S> example) { return null; }
        @Override public <S extends EventStoreJpaEntity> List<S> findAll(Example<S> example, Sort sort) { return null; }
        @Override public <S extends EventStoreJpaEntity> Page<S> findAll(Example<S> example, Pageable pageable) { return null; }
        @Override public <S extends EventStoreJpaEntity> long count(Example<S> example) { return 0; }
        @Override public <S extends EventStoreJpaEntity> boolean exists(Example<S> example) { return false; }
        @Override public <S extends EventStoreJpaEntity, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public <S extends EventStoreJpaEntity> List<S> saveAll(Iterable<S> entities) { return null; }
        @Override public Optional<EventStoreJpaEntity> findById(UUID uuid) { return Optional.ofNullable(storage.get(uuid)); }
        @Override public boolean existsById(UUID uuid) { return storage.containsKey(uuid); }
        @Override public List<EventStoreJpaEntity> findAll() { return new ArrayList<>(storage.values()); }
        @Override public List<EventStoreJpaEntity> findAllById(Iterable<UUID> uuids) { return null; }
        @Override public long count() { return storage.size(); }
        @Override public void deleteById(UUID uuid) { storage.remove(uuid); }
        @Override public void delete(EventStoreJpaEntity entity) { storage.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> uuids) {}
        @Override public void deleteAll(Iterable<? extends EventStoreJpaEntity> entities) {}
        @Override public void deleteAll() { storage.clear(); }
        @Override public List<EventStoreJpaEntity> findAll(Sort sort) { return null; }
        @Override public Page<EventStoreJpaEntity> findAll(Pageable pageable) { return null; }
    }
}
