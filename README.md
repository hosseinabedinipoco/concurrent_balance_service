# concurrent_balance_service

# overwiew
A small banking transaction service built with Spring Boot that supports
three core account operations:

- Credit
- Debit
- Transfer

The main goal of this project is not to implement a simple CRUD-based
banking application, but to correctly handle concurrent financial
operations while maintaining data consistency, atomicity, and idempotency.

The system is designed to safely process concurrent requests targeting
the same or different accounts, prevent duplicate transaction processing,
and avoid deadlocks when multiple accounts are involved in a transfer.

## Requirements

The service must support the following requirements:

### 1. Account Operations

The system must support three financial operations:

- **Credit** — increase an account balance.
- **Debit** — decrease an account balance.
- **Transfer** — transfer money from one account to another.

### 2. Concurrency

The system must safely handle concurrent operations on the same account.

Concurrent requests must not result in:

- Lost updates
- Incorrect balances
- Negative balances
- Inconsistent transaction states

The system should avoid unnecessarily blocking accounts that are not involved in the current operation.

### 3. Idempotency

Each transaction must have a unique client-provided transaction identifier.

If the same transaction is submitted multiple times, it must not be processed more than once.

The idempotency mechanism must also handle concurrent duplicate requests.

### 4. Atomicity

Each financial operation must be atomic.

A balance update and its corresponding transaction status update must either both succeed or both be rolled back.

For example, if a debit fails because of insufficient balance, the account balance must remain unchanged and the transaction must be marked as `FAILED`.

### 5. Consistency

The system must maintain valid financial state at all times.

For example:

- An account balance must not become negative.
- A transfer must update both the source and destination accounts consistently.
- Invalid transactions must not modify account balances.
- Failed operations must not leave partial balance updates.

### 6. Deadlock Prevention

Transfers may involve two accounts simultaneously.

The system must prevent deadlocks when concurrent transfers operate on the same accounts in opposite directions.

Account locks must therefore be acquired in a deterministic order.

### 7. Validation

Invalid requests must be rejected before processing.

Validation includes:

- Transaction identifier must be provided.
- Transaction type must be valid.
- Amount must be greater than zero.
- Credit requires a destination account.
- Debit requires a source account.
- Transfer requires both source and destination accounts.
- A transfer from an account to itself is not allowed.


## Architecture

The application follows a layered architecture built with Spring Boot.

The main components are:

- **Controller Layer** — exposes the transaction API.
- **Transaction Service** — validates incoming requests, creates the transaction record, and delegates the financial operation.
- **Balance Service** — performs the actual balance modification and transaction processing.
- **Repository Layer** — provides access to accounts and transactions.
- **PostgreSQL** — stores account balances and transaction records and provides database-level guarantees such as unique constraints and row-level locking.

### High-Level Flow

A transaction request follows this flow:

```text
Client
   │
   │ POST /api/transaction
   ▼
TransactionController
   │
   ▼
TransactionService
   │
   ├── Validate request
   │
   ├── Create transaction (WAITING)
   │
   └── Process transaction
   │
   ▼
BalanceService
   │
   ├── Credit
   ├── Debit
   └── Transfer
   │
   ▼
PostgreSQL
   │
   ├── Account
   └── Transaction
```

## Domain Model

The system is based on two main domain entities: `Account` and `Transaction`.

### Account

An account represents a customer's bank account and contains the current balance.

```text
Account {
    id
    username
    password
    balance
}
```

### Transaction
A transaction represents a financial operation requested by a client.

```text
Transaction {
    id
    transactionUid
    sourceAccountId
    destinationAccountId
    amount
    type
    status
    createdAt
}
```

## Concurrency Control
Concurrency is handled at the database row level.

The system uses pessimistic row-level locking when modifying an account.
Only the account rows involved in the current operation are locked.

This means the application does not globally lock all accounts while
processing a transaction.

For example, if two requests operate on different accounts:
Request A → Account 1
Request B → Account 2

they can execute concurrently without blocking each other.

For operations targeting the same account, the database row lock
serializes the balance modifications.

This prevents race conditions such as:
Initial balance = 100

Request A reads 100
Request B reads 100

Request A writes 50
Request B writes 20

Final balance = 20  ❌

With row-level locking, one operation must complete before another
operation modifies the same account.


## Deadlock Prevention
Transfers require locking two accounts.

A potential deadlock exists if two concurrent transfers acquire locks
in different orders.

For example:
Transfer A → B

lock(A)
lock(B)

while another request performs:
Transfer B → A

lock(B)
lock(A)

This can result in:
Transaction 1 owns A and waits for B
Transaction 2 owns B and waits for A

To prevent this, accounts are always locked in ascending order of
their IDs, regardless of the transfer direction.

For example, if:
A.id = 10
B.id = 20

both of these transfers acquire locks in the same order:
A → B
lock(10)
lock(20)

B → A
lock(10)
lock(20)

The transfer direction only determines which locked account is the
source and which is the destination. It does not determine the lock
order.

This provides a deterministic lock acquisition order and prevents
circular wait between concurrent transfers.

## Idempotency
The system uses transactionUid as the idempotency key.

Every transaction must have a unique transactionUid, enforced by a
database-level UNIQUE constraint.

A simple application-level check is performed before creating a
transaction, but this check alone is not sufficient.

Therefore, even when duplicate requests arrive concurrently, only one
transaction can be created and processed.

The same logical transaction cannot cause the balance operation to be
executed twice.

## Atomicity and Consistency
Financial operations are executed inside database transactions.

The balance modification and transaction status update belong to the
same transactional operation.

### Successful operation
Transaction = WAITING
       │
       ▼
Lock account(s)
       │
       ▼
Modify balance
       │
       ▼
Transaction = SUCCESS
       │
       ▼
Commit

### Failed operation
Transaction = WAITING
       │
       ▼
Lock account(s)
       │
       ▼
Operation fails
       │
       ▼
Transaction = FAILED
       │
       ▼
Rollback balance changes

## API

POST /api/transaction
Content-Type: application/json

### credit
{
  "transactionId": "550e8400-e29b-41d4-a716-446655440000",
  "transactionType": "CREDIT",
  "destinationAccountId": 1,
  "amount": 100
}

### debit
{
  "transactionId": "550e8400-e29b-41d4-a716-446655440001",
  "transactionType": "DEBIT",
  "sourceAccountId": 1,
  "amount": 50
}

### transfer
{
  "transactionId": "550e8400-e29b-41d4-a716-446655440002",
  "transactionType": "TRANSFER",
  "sourceAccountId": 1,
  "destinationAccountId": 2,
  "amount": 30
}

## Error Handling
The service uses domain-specific exceptions for business errors.

Examples include:

Invalid transaction request
Duplicate transaction
Account not found
Transaction not found
Invalid transaction state
Insufficient balance

A failed financial operation does not leave a partial balance update.

The transaction record is preserved with a FAILED status so that the
result of the attempted operation can be tracked.

## Database
PostgreSQL is used as the primary database.

The database is responsible not only for persistence but also for
important correctness guarantees.

These include:

Unique constraint on transactionUid
Row-level pessimistic locking for account updates
Transactional commit and rollback
Consistent persistence of account balances and transactions

Database schema changes are managed using Liquibase.

## Liquibase
Liquibase is used to version and manage database schema changes.

The application does not rely on Hibernate automatic schema generation.

Schema changes are explicitly defined as Liquibase migrations, making
the database structure reproducible across environments.

## Testing
The project contains integration tests covering the core financial
and concurrency requirements.

### Validation Tests
Validation tests cover:

Missing transaction ID
Missing transaction type
Missing source account
Missing destination account
Invalid amount
Zero amount
Negative amount
Transfer to the same account
Credit

### Credit Tests
Tests verify that:

A valid credit increases the account balance.
The transaction becomes SUCCESS.

### Debit Tests
Tests verify that:

A valid debit decreases the account balance.
An insufficient balance causes the transaction to fail.
The balance remains unchanged after a failed debit.
The transaction becomes FAILED.

### Transfer Tests
Tests verify that:

The source account is debited.
The destination account is credited.
Both changes happen as one operation.
Insufficient balance causes the transfer to fail.
No partial balance update remains after failure.

### Idempotency Tests
Tests verify that:

The same transaction cannot be processed twice.
Duplicate transaction IDs are rejected.
Concurrent requests with the same transaction ID result in
only one processed transaction.
The database unique constraint protects against race conditions.

### Concurrency Tests
Tests verify that:

Concurrent debits on the same account are serialized correctly.
The balance cannot become negative because of concurrent operations.
Operations on different accounts can execute concurrently.
Concurrent transfers maintain correct balances.

### Deadlock Tests
Tests verify that concurrent transfers in opposite directions do
not cause a deadlock.

For example:
Transfer A → B
Transfer B → A

Both operations acquire account locks using the same deterministic
account ID ordering.

## Running the Project

### Run with Docker Compose
```text
docker compose up -d
```

### Run Tests
```text
mvn test
```


## Design Decisions

### Why Database-Level Locking?
The balance is shared mutable state stored in the database.

Using database row-level locking allows multiple application instances
to safely operate on the same account without relying on an in-memory
lock.

This is important because an in-memory lock would not provide the same
guarantee when the application is horizontally scaled.

### Why Pessimistic Locking?
Financial balance updates require serialized access to the affected
account.

Pessimistic locking provides an explicit database-level guarantee that
two concurrent operations cannot modify the same account balance at
the same time.

### Why Not Lock All Accounts?
Locking all accounts would unnecessarily reduce concurrency.

Only accounts participating in the current operation are locked.

For example:
Transaction A → Account 1
Transaction B → Account 2

These operations do not need to block each other.

### Why Database-Level Idempotency?
An application-level exists check is vulnerable to a race condition.

Two concurrent requests can both observe that a transaction does not
exist.

The database UNIQUE constraint provides the final guarantee that only
one transaction with a given idempotency key can be persisted.

### Why Deterministic Lock Ordering?
Multiple-account operations can create circular waits.

Acquiring locks in a deterministic order removes the possibility of
circular lock acquisition between opposite transfers.

### Design Trade-offs
The current design favors correctness and simplicity for financial
operations.

The main trade-off is that pessimistic locking can reduce concurrency
when many requests target the same account.

However, this is preferable to allowing concurrent balance updates to
produce incorrect financial state.

The locking scope is intentionally limited to the accounts involved in
the current operation so that unrelated accounts can still be processed
concurrently.

## Summary
The project demonstrates a small transactional banking service focused
on correctness under concurrent access.

The main guarantees are:
```text
Concurrency
     ↓
Row-level locking

Idempotency
     ↓
Unique transaction identifier
+
Database UNIQUE constraint

Deadlock Prevention
     ↓
Deterministic account lock ordering

Atomicity
     ↓
Database transactions
+
Rollback

Consistency
     ↓
Validation
+
Balance checks
+
Transactional updates
```

The implementation intentionally focuses on the concurrency and
transactional challenges of a banking system rather than exposing
simple CRUD operations.

THANK‌ YOU‌ FOR‌ YOUR ATTENTION.
HOSSEIN‌ ABEDINI


یب
