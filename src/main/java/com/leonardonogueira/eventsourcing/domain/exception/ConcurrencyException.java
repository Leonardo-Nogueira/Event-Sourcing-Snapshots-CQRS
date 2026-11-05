package com.leonardonogueira.eventsourcing.domain.exception;

import java.util.UUID;

/**
 * =============================================================================
 * EXCEÇÃO DE CONCORRÊNCIA OTIMISTA (OCC)
 * =============================================================================
 * Lançada quando outra transação alterou o mesmo agregado concorrentemente.
 */
public class ConcurrencyException extends RuntimeException {

    public ConcurrencyException(UUID aggregateId, long attemptedVersion) {
        super(String.format("Conflito de concorrência detectado para o agregado '%s'. A versão '%d' já foi gravada por outra transação.",
                aggregateId, attemptedVersion));
    }
}

