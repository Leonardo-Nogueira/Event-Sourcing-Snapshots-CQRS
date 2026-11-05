package com.leonardonogueira.eventsourcing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AccountDtos {

    private AccountDtos() {}

    public record OpenAccountRequest(
            @NotBlank(message = "O nome do titular é obrigatório")
            String holderName,

            @NotNull(message = "O depósito inicial é obrigatório")
            @DecimalMin(value = "0.00", message = "O depósito inicial não pode ser negativo")
            BigDecimal initialDeposit
    ) {}

    public record DepositRequest(
            @NotNull(message = "O valor do depósito é obrigatório")
            @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
            BigDecimal amount,

            @NotBlank(message = "A descrição do depósito é obrigatória")
            String description
    ) {}

    public record WithdrawRequest(
            @NotNull(message = "O valor do saque é obrigatório")
            @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
            BigDecimal amount,

            @NotBlank(message = "O motivo do saque é obrigatório")
            String reason
    ) {}

    public record AccountCreatedResponse(
            UUID accountId,
            String holderName,
            BigDecimal initialBalance,
            long currentVersion,
            String message
    ) {}

    public record TransactionResponse(
            UUID accountId,
            BigDecimal currentBalance,
            long currentVersion,
            String message
    ) {}

    public record EventStreamResponse(
            UUID accountId,
            long totalEvents,
            List<EventItemDto> events
    ) {}

    public record EventItemDto(
            long version,
            String eventType,
            String payload,
            Instant createdAt
    ) {}
}
