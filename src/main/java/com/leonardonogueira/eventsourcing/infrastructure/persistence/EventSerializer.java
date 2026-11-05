package com.leonardonogueira.eventsourcing.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leonardonogueira.eventsourcing.domain.events.AccountEvents.*;
import org.springframework.stereotype.Component;

/**
 * =============================================================================
 * SERIALIZADOR DE EVENTOS DE DOMÍNIO (JSON <-> AccountEvent)
 * =============================================================================
 */
@Component
public class EventSerializer {

    private final ObjectMapper objectMapper;

    public EventSerializer() {
        this(new ObjectMapper());
    }

    public EventSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public String serialize(AccountEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao serializar evento: " + event.getClass().getSimpleName(), e);
        }
    }

    public AccountEvent deserialize(String payload, String eventType) {
        try {
            return switch (eventType) {
                case "AccountOpenedEvent" -> objectMapper.readValue(payload, AccountOpenedEvent.class);
                case "MoneyDepositedEvent" -> objectMapper.readValue(payload, MoneyDepositedEvent.class);
                case "MoneyWithdrawnEvent" -> objectMapper.readValue(payload, MoneyWithdrawnEvent.class);
                case "AccountBlockedEvent" -> objectMapper.readValue(payload, AccountBlockedEvent.class);
                default -> throw new IllegalArgumentException("Tipo de evento desconhecido: " + eventType);
            };
        } catch (Exception e) {
            throw new RuntimeException("Falha ao desserializar evento do tipo: " + eventType, e);
        }
    }
}

