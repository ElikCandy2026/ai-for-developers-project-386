# Closed error contract with typed codes

The API error set is closed in TypeSpec: `BadRequestCode`, `NotFoundCode`,
`ConflictCode` and `InternalErrorCode` enumerate the seven public codes, and every
operation includes `InternalError` (500). `ApplicationErrorCode` mirrors the contract
in the application layer, and a drift test compares the two sets. `ApiErrorHandler`
extends `ResponseEntityExceptionHandler`, maps our exceptions to the generated models
and lets framework 4xx errors (405, 415, 406) keep their status instead of turning
them into 500.

Bean Validation is authoritative for syntax — presence, lengths, format, ranges and
patterns — while the scheduling policy owns semantics. Clients get a closed union they
can switch exhaustively, while the JSON `code` stays a string. Adding a code is a
contract change and intentionally breaks the frontend code map at compile time.
