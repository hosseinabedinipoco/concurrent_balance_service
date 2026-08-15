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

