package com.leonardonogueira.eventsourcing.api;

import com.leonardonogueira.eventsourcing.infrastructure.persistence.read.AccountViewJpaEntity;
import com.leonardonogueira.eventsourcing.infrastructure.persistence.read.SpringDataAccountViewRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * =============================================================================
 * CQRS QUERY CONTROLLER: BankAccountQueryController
 * =============================================================================
 * 
 * Este controller representa o LADO DE CONSULTAS (Query Side).
 * 
 * Regra Arquitetural de Ouro:
 * 1. ZERO replay de eventos.
 * 2. ZERO instanciação de agregado BankAccount.
 * 3. Acesso direto ao Read Model desnormalizado (AccountViewJpaEntity) via índices SQL.
 * 4. Latência ultra-baixa O(1).
 */
@RestController
@RequestMapping("/queries/accounts")
public class BankAccountQueryController {

    private final SpringDataAccountViewRepository viewRepository;

    public BankAccountQueryController(SpringDataAccountViewRepository viewRepository) {
        this.viewRepository = viewRepository;
    }

    /**
     * Consulta parametrizada com filtros que seriam impossíveis de rodar no Event Store puro.
     */
    @GetMapping
    public ResponseEntity<List<AccountViewJpaEntity>> listAccounts(
            @RequestParam(required = false) BigDecimal minBalance,
            @RequestParam(required = false) String name
    ) {
        if (minBalance != null) {
            return ResponseEntity.ok(viewRepository.findByBalanceGreaterThanEqualOrderByBalanceDesc(minBalance));
        }

        if (name != null && !name.isBlank()) {
            return ResponseEntity.ok(viewRepository.findByHolderNameContainingIgnoreCaseOrderByHolderNameAsc(name));
        }

        return ResponseEntity.ok(viewRepository.findAllByOrderByBalanceDesc());
    }

    /**
     * Retorna a visão desnormalizada e estatísticas acumuladas de uma conta específica.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AccountViewJpaEntity> getAccountView(@PathVariable UUID id) {
        return viewRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

