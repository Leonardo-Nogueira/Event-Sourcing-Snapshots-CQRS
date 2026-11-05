package com.leonardonogueira.eventsourcing.domain.exception;

import java.math.BigDecimal;
import java.util.UUID;

public final class AccountExceptions {

    private AccountExceptions() {}

    public static class InsufficientFundsException extends RuntimeException {
        public InsufficientFundsException(BigDecimal currentBalance, BigDecimal requestedAmount) {
            super(String.format("Saldo insuficiente. Saldo atual: R$ %s, valor solicitado para saque: R$ %s",
                    currentBalance, requestedAmount));
        }
    }

    public static class AccountBlockedException extends RuntimeException {
        public AccountBlockedException(UUID accountId, String reason) {
            super(String.format("A conta '%s' está bloqueada. Motivo: %s", accountId, reason));
        }
    }

    public static class AccountNotFoundException extends RuntimeException {
        public AccountNotFoundException(UUID accountId) {
            super(String.format("Conta bancária '%s' não foi encontrada no histórico de eventos.", accountId));
        }
    }
}

