package com.leonardonogueira.eventsourcing.domain.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class AccountEvents {

    private AccountEvents() {}

    public sealed interface AccountEvent permits
            AccountOpenedEvent, MoneyDepositedEvent, MoneyWithdrawnEvent, AccountBlockedEvent {
        UUID accountId();
        Instant occurredOn();
    }

    public record AccountOpenedEvent(
            UUID accountId,
            String holderName,
            BigDecimal initialDeposit,
            Instant occurredOn
    ) implements AccountEvent {}

    public record MoneyDepositedEvent(
            UUID accountId,
            BigDecimal amount,
            String description,
            Instant occurredOn
    ) implements AccountEvent {}

    public record MoneyWithdrawnEvent(
            UUID accountId,
            BigDecimal amount,
            String reason,
            Instant occurredOn
    ) implements AccountEvent {}

    public record AccountBlockedEvent(
            UUID accountId,
            String reason,
            Instant occurredOn
    ) implements AccountEvent {}
}

