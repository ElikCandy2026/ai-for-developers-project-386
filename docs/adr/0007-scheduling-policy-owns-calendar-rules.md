# Scheduling policy owns calendar rules; time is injected

Booking window, working days and hours, slot grid, minimum notice, duration alignment
and the overlap predicate live in one `SchedulingPolicy` over an immutable
`OwnerSchedule`. Availability and booking both call it instead of re-deriving the
rules. A `Clock` bean is injected, so a fixed instant makes every boundary test
deterministic and no `Instant.now()` or `LocalDate.now()` remains in scheduling code.

The policy returns values and small result types (`OK`/`NOT_ALIGNED`,
overlapping/not), never HTTP errors; the application maps them to
`ApplicationErrorCode`. The 15–240 minute range stays in the contract's Bean
Validation, while the policy owns only alignment and calendar semantics. This keeps
one source of truth for the rules without making the domain depend on the transport.
