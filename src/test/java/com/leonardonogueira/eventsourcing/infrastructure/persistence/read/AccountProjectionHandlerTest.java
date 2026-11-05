package com.leonardonogueira.eventsourcing.infrastructure.persistence.read;

import com.leonardonogueira.eventsourcing.domain.events.AccountEvents.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class AccountProjectionHandlerTest {

    private InMemoryAccountViewRepository viewRepository;
    private AccountProjectionHandler projectionHandler;

    @BeforeEach
    void setUp() {
        viewRepository = new InMemoryAccountViewRepository();
        projectionHandler = new AccountProjectionHandler(viewRepository);
    }

    @Test
    @DisplayName("Deve projetar AccountOpenedEvent criando uma nova visão de leitura com saldo inicial")
    void shouldProjectAccountOpenedEvent() {
        UUID accountId = UUID.randomUUID();
        AccountOpenedEvent event = new AccountOpenedEvent(
                accountId, "Leonardo Nogueira", new BigDecimal("500.00"), Instant.now()
        );

        projectionHandler.on(event);

        Optional<AccountViewJpaEntity> savedOpt = viewRepository.findById(accountId);
        assertTrue(savedOpt.isPresent());
        AccountViewJpaEntity saved = savedOpt.get();

        assertEquals(accountId, saved.getId());
        assertEquals("Leonardo Nogueira", saved.getHolderName());
        assertEquals(new BigDecimal("500.00"), saved.getBalance());
        assertTrue(saved.isActive());
        assertEquals(1, saved.getVersion());
        assertEquals(0, saved.getTotalDepositsCount());
        assertEquals(0, saved.getTotalWithdrawalsCount());
    }

    @Test
    @DisplayName("Deve projetar MoneyDepositedEvent acumulando saldo e incrementando contador de depósitos")
    void shouldProjectMoneyDepositedEvent() {
        UUID accountId = UUID.randomUUID();
        AccountViewJpaEntity existingView = new AccountViewJpaEntity(
                accountId, "Leonardo", new BigDecimal("500.00"), true, 1L, 0, 0, Instant.now()
        );
        viewRepository.save(existingView);

        MoneyDepositedEvent event = new MoneyDepositedEvent(
                accountId, new BigDecimal("250.00"), "Depósito Pix", Instant.now()
        );

        projectionHandler.on(event);

        AccountViewJpaEntity updated = viewRepository.findById(accountId).orElseThrow();
        assertEquals(new BigDecimal("750.00"), updated.getBalance()); // 500 + 250 = 750
        assertEquals(1, updated.getTotalDepositsCount());
        assertEquals(2, updated.getVersion());
    }

    @Test
    @DisplayName("Deve projetar MoneyWithdrawnEvent deduzindo saldo e incrementando contador de saques")
    void shouldProjectMoneyWithdrawnEvent() {
        UUID accountId = UUID.randomUUID();
        AccountViewJpaEntity existingView = new AccountViewJpaEntity(
                accountId, "Leonardo", new BigDecimal("1000.00"), true, 2L, 1, 0, Instant.now()
        );
        viewRepository.save(existingView);

        MoneyWithdrawnEvent event = new MoneyWithdrawnEvent(
                accountId, new BigDecimal("350.00"), "Boleto", Instant.now()
        );

        projectionHandler.on(event);

        AccountViewJpaEntity updated = viewRepository.findById(accountId).orElseThrow();
        assertEquals(new BigDecimal("650.00"), updated.getBalance()); // 1000 - 350 = 650
        assertEquals(1, updated.getTotalWithdrawalsCount());
        assertEquals(3, updated.getVersion());
    }

    @Test
    @DisplayName("Deve projetar AccountBlockedEvent marcando a visão de leitura como inativa")
    void shouldProjectAccountBlockedEvent() {
        UUID accountId = UUID.randomUUID();
        AccountViewJpaEntity existingView = new AccountViewJpaEntity(
                accountId, "Leonardo", new BigDecimal("100.00"), true, 1L, 0, 0, Instant.now()
        );
        viewRepository.save(existingView);

        AccountBlockedEvent event = new AccountBlockedEvent(
                accountId, "Suspeita de fraude", Instant.now()
        );

        projectionHandler.on(event);

        AccountViewJpaEntity updated = viewRepository.findById(accountId).orElseThrow();
        assertFalse(updated.isActive());
        assertEquals(2, updated.getVersion());
    }

    /**
     * Fake Repository em memória - ultra veloz, determinístico e sem dependência de reflexão de mocks.
     */
    static class InMemoryAccountViewRepository implements SpringDataAccountViewRepository {
        private final Map<UUID, AccountViewJpaEntity> storage = new ConcurrentHashMap<>();

        @Override
        public <S extends AccountViewJpaEntity> S save(S entity) {
            storage.put(entity.getId(), entity);
            return entity;
        }

        @Override
        public Optional<AccountViewJpaEntity> findById(UUID id) {
            return Optional.ofNullable(storage.get(id));
        }

        @Override
        public List<AccountViewJpaEntity> findByBalanceGreaterThanEqualOrderByBalanceDesc(BigDecimal minBalance) {
            return storage.values().stream()
                    .filter(v -> v.getBalance().compareTo(minBalance) >= 0)
                    .sorted((a, b) -> b.getBalance().compareTo(a.getBalance()))
                    .toList();
        }

        @Override
        public List<AccountViewJpaEntity> findByHolderNameContainingIgnoreCaseOrderByHolderNameAsc(String name) {
            return storage.values().stream()
                    .filter(v -> v.getHolderName().toLowerCase().contains(name.toLowerCase()))
                    .sorted(Comparator.comparing(AccountViewJpaEntity::getHolderName))
                    .toList();
        }

        @Override
        public List<AccountViewJpaEntity> findAllByOrderByBalanceDesc() {
            return storage.values().stream()
                    .sorted((a, b) -> b.getBalance().compareTo(a.getBalance()))
                    .toList();
        }

        // Métodos de conveniência do Spring Data (não utilizados nos testes)
        @Override public void flush() {}
        @Override public <S extends AccountViewJpaEntity> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends AccountViewJpaEntity> List<S> saveAllAndFlush(Iterable<S> entities) { return null; }
        @Override public void deleteAllInBatch(Iterable<AccountViewJpaEntity> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<UUID> uuids) {}
        @Override public void deleteAllInBatch() {}
        @Override public AccountViewJpaEntity getOne(UUID uuid) { return storage.get(uuid); }
        @Override public AccountViewJpaEntity getById(UUID uuid) { return storage.get(uuid); }
        @Override public AccountViewJpaEntity getReferenceById(UUID uuid) { return storage.get(uuid); }
        @Override public <S extends AccountViewJpaEntity> Optional<S> findOne(Example<S> example) { return Optional.empty(); }
        @Override public <S extends AccountViewJpaEntity> List<S> findAll(Example<S> example) { return null; }
        @Override public <S extends AccountViewJpaEntity> List<S> findAll(Example<S> example, Sort sort) { return null; }
        @Override public <S extends AccountViewJpaEntity> Page<S> findAll(Example<S> example, Pageable pageable) { return null; }
        @Override public <S extends AccountViewJpaEntity> long count(Example<S> example) { return 0; }
        @Override public <S extends AccountViewJpaEntity> boolean exists(Example<S> example) { return false; }
        @Override public <S extends AccountViewJpaEntity, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public <S extends AccountViewJpaEntity> List<S> saveAll(Iterable<S> entities) { return null; }
        @Override public boolean existsById(UUID uuid) { return storage.containsKey(uuid); }
        @Override public List<AccountViewJpaEntity> findAll() { return new ArrayList<>(storage.values()); }
        @Override public List<AccountViewJpaEntity> findAllById(Iterable<UUID> uuids) { return null; }
        @Override public long count() { return storage.size(); }
        @Override public void deleteById(UUID uuid) { storage.remove(uuid); }
        @Override public void delete(AccountViewJpaEntity entity) { storage.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> uuids) {}
        @Override public void deleteAll(Iterable<? extends AccountViewJpaEntity> entities) {}
        @Override public void deleteAll() { storage.clear(); }
        @Override public List<AccountViewJpaEntity> findAll(Sort sort) { return null; }
        @Override public Page<AccountViewJpaEntity> findAll(Pageable pageable) { return null; }
    }
}
