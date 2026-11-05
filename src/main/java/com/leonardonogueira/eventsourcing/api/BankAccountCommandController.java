package com.leonardonogueira.eventsourcing.api;

import com.leonardonogueira.eventsourcing.application.BankAccountCommandService;
import com.leonardonogueira.eventsourcing.domain.model.BankAccount;
import com.leonardonogueira.eventsourcing.dto.AccountDtos.*;
import com.leonardonogueira.eventsourcing.infrastructure.persistence.EventStoreJpaEntity;
import com.leonardonogueira.eventsourcing.infrastructure.persistence.SpringDataEventStoreRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * =============================================================================
 * CONTROLLER REST: BankAccountCommandController (CQRS Write Model)
 * =============================================================================
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class BankAccountCommandController {

    private final BankAccountCommandService commandService;
    private final SpringDataEventStoreRepository eventStoreRepository;

    public BankAccountCommandController(
            BankAccountCommandService commandService,
            SpringDataEventStoreRepository eventStoreRepository
    ) {
        this.commandService = commandService;
        this.eventStoreRepository = eventStoreRepository;
    }

    /**
     * POST /api/v1/accounts: Abre uma nova conta bancária (Gera AccountOpenedEvent).
     */
    @PostMapping
    public ResponseEntity<AccountCreatedResponse> openAccount(@Valid @RequestBody OpenAccountRequest request) {
        BankAccount account = commandService.openAccount(request.holderName(), request.initialDeposit());

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(account.getId())
                .toUri();

        return ResponseEntity.created(location).body(new AccountCreatedResponse(
                account.getId(),
                account.getHolderName(),
                account.getBalance(),
                account.getVersion(),
                "Conta aberta com sucesso! O evento inicial AccountOpenedEvent foi gravado no Event Store."
        ));
    }

    /**
     * POST /api/v1/accounts/{id}/deposits: Deposita dinheiro (Gera MoneyDepositedEvent).
     */
    @PostMapping("/{id}/deposits")
    public ResponseEntity<TransactionResponse> deposit(
            @PathVariable UUID id,
            @Valid @RequestBody DepositRequest request
    ) {
        BankAccount account = commandService.deposit(id, request.amount(), request.description());

        return ResponseEntity.ok(new TransactionResponse(
                account.getId(),
                account.getBalance(),
                account.getVersion(),
                "Depósito confirmado! Novo evento MoneyDepositedEvent apensado ao stream."
        ));
    }

    /**
     * POST /api/v1/accounts/{id}/withdrawals: Saca dinheiro (Gera MoneyWithdrawnEvent).
     */
    @PostMapping("/{id}/withdrawals")
    public ResponseEntity<TransactionResponse> withdraw(
            @PathVariable UUID id,
            @Valid @RequestBody WithdrawRequest request
    ) {
        BankAccount account = commandService.withdraw(id, request.amount(), request.reason());

        return ResponseEntity.ok(new TransactionResponse(
                account.getId(),
                account.getBalance(),
                account.getVersion(),
                "Saque efetuado com sucesso! Novo evento MoneyWithdrawnEvent apensado ao stream."
        ));
    }

    /**
     * GET /api/v1/accounts/{id}: Carrega a conta reidratando os eventos em tempo real.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getAccount(@PathVariable UUID id) {
        BankAccount account = commandService.getAccount(id);

        return ResponseEntity.ok(new TransactionResponse(
                account.getId(),
                account.getBalance(),
                account.getVersion(),
                "Conta reidratada em memória a partir dos eventos históricos do Event Store!"
        ));
    }

    /**
     * GET /api/v1/accounts/{id}/events: O Extrato Imutável de Eventos (Event Stream puro).
     */
    @GetMapping("/{id}/events")
    public ResponseEntity<EventStreamResponse> getEventStream(@PathVariable UUID id) {
        List<EventStoreJpaEntity> entities = eventStoreRepository.findByAggregateIdOrderByVersionAsc(id);

        List<EventItemDto> eventDtos = entities.stream()
                .map(e -> new EventItemDto(
                        e.getVersion(),
                        e.getEventType(),
                        e.getPayload(),
                        e.getCreatedAt()
                ))
                .toList();

        return ResponseEntity.ok(new EventStreamResponse(
                id,
                eventDtos.size(),
                eventDtos
        ));
    }
}
