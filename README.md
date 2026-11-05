# 📜 Laboratório de Event Sourcing, Snapshots & CQRS (Command Query Responsibility Segregation)

Este laboratório demonstra a implementação completa de um sistema bancário utilizando **Event Sourcing** nativo em **Java 21** e **Spring Boot 3.4**, otimizado com **Snapshots de Estado** e **CQRS com Projeções em Tempo Real**, onde o banco de dados principal é um log imutável (*Append-Only*) sem comandos `UPDATE` ou `DELETE`.

---

## 🏛️ 1. Arquitetura do Sistema

```mermaid
flowchart TD
    subgraph WRITE_MODEL ["1. WRITE MODEL (Comandos & Consistência ACID)"]
        CmdCtrl["BankAccountCommandController\n(POST /api/v1/accounts)"]
        CmdService["BankAccountCommandService"]
        Agg["BankAccount Aggregate Root\n(Decide vs Evolve / apply())"]
        EventStoreRepo["AccountEventStoreRepository"]
        EventTable[("Tabela event_store\n(Unique: aggregate_id + version)")]
        SnapshotTable[("Tabela account_snapshots\n(Cache de Fotografias v5, v10...)")]

        CmdCtrl --> CmdService
        CmdService --> EventStoreRepo
        EventStoreRepo -->|1. Busca Snapshot + Delta| SnapshotTable
        EventStoreRepo -->|2. Busca Eventos Delta| EventTable
        EventStoreRepo -->|3. fromSnapshot + apply()| Agg
        CmdService -->|4. Valida Invariantes| Agg
        CmdService -->|5. Salva Novos Eventos| EventStoreRepo
        EventStoreRepo -->|6. INSERT Novos Fatos| EventTable
        EventStoreRepo -->|7. Salva Snapshot se v % 5 == 0| SnapshotTable
    end

    EventStoreRepo -->|📢 Publica Eventos de Domínio| ProjHandler["AccountProjectionHandler\n(@EventListener)"]

    subgraph READ_MODEL ["2. READ MODEL (Consultas O(1) & Relatórios)"]
        ProjHandler -->|Atualiza Visão Desnormalizada| ReadTable[("Tabela account_views\n(Índices: balance, holder_name)")]
        QueryCtrl["BankAccountQueryController\n(GET /queries/accounts)"] -->|SELECT direto O(1)| ReadTable
    end
```

---

## 🚀 2. Como Rodar a Aplicação

A aplicação executa na porta **8087**:

```powershell
cd "event-sourcing-cqrs-lab"
.\mvnw.cmd spring-boot:run
```

Console do Banco H2: `http://localhost:8087/h2-console`
* JDBC URL: `jdbc:h2:mem:eventsourcingdb`
* Usuário: `sa` | Senha: *(vazio)*

---

## 🧪 3. Guia Prático de Testes dos Endpoints

### 1. Abrir uma Nova Conta (`POST /api/v1/accounts`)
```powershell
curl -X POST http://localhost:8087/api/v1/accounts `
  -H "Content-Type: application/json" `
  -d '{
    "holderName": "Leonardo Nogueira",
    "initialDeposit": 1000.00
  }'
```

### 2. Realizar Depósitos e Saques
```powershell
$ACCOUNT_ID="<COLE_O_ID_DA_CONTA_AQUI>"

# Depósito:
curl -X POST "http://localhost:8087/api/v1/accounts/$ACCOUNT_ID/deposits" `
  -H "Content-Type: application/json" `
  -d '{"amount": 500.00, "description": "Salário"}'

# Saque legítimo:
curl -X POST "http://localhost:8087/api/v1/accounts/$ACCOUNT_ID/withdrawals" `
  -H "Content-Type: application/json" `
  -d '{"amount": 300.00, "reason": "Aluguel"}'
```

### 3. Inspecionar o Event Stream Puro (`GET /api/v1/accounts/{id}/events`)
Mostra o log cronológico e imutável de eventos:
```powershell
curl -X GET "http://localhost:8087/api/v1/accounts/$ACCOUNT_ID/events"
```

---

## 📸 4. Otimização de Escala com Snapshots

* **Tabela:** `account_snapshots` (índice composto em `aggregate_id, version DESC`).
* **Estratégia:** A cada **5 versões**, o agregado tira uma fotografia de si mesmo (`balance`, `holderName`, `version`).
* **Reidratação Inteligente:** 
  $$\text{Estado}(V) = \text{Snapshot}_K + \sum_{i=K+1}^{V} \text{Evento}_i$$
  Se uma conta tiver 100.000 eventos, ao invés de ler 100.000 linhas, ela lê **1 snapshot + no máximo 4 eventos delta**.
* **Descartabilidade:** Se a tabela `account_snapshots` for truncada, o sistema se recupera 100% lendo do `event_store`.

---

## 📊 5. CQRS: Endpoints do Lado de Leitura (Query Side)

O controller `BankAccountQueryController` consulta diretamente a tabela desnormalizada `account_views` em $\mathcal{O}(1)$ sem tocar no Event Store:

### Listar todas as contas ordenadas pelo maior saldo:
```powershell
curl -X GET "http://localhost:8087/queries/accounts"
```

### Filtrar contas com saldo mínimo de R$ 1.000,00:
```powershell
curl -X GET "http://localhost:8087/queries/accounts?minBalance=1000"
```

### Buscar contas pelo nome do titular (LIKE case-insensitive):
```powershell
curl -X GET "http://localhost:8087/queries/accounts?name=Leonardo"
```

### Consultar a Visão de Leitura de uma conta com métricas acumuladas:
```powershell
curl -X GET "http://localhost:8087/queries/accounts/$ACCOUNT_ID"
```

*Retorno da Projeção de Leitura:*
```json
{
  "id": "a1b2c3d4-...",
  "holderName": "Leonardo Nogueira",
  "balance": 1200.00,
  "active": true,
  "version": 3,
  "totalDepositsCount": 1,
  "totalWithdrawalsCount": 1,
  "lastTransactionAt": "2026-11-04T15:35:10Z"
}
```
