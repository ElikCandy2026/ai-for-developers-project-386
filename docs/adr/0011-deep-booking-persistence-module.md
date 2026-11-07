# Deep booking persistence module

`BookingRepository.tryInsert(Booking)` is the only way to create a booking. The
repository owns the fair lock, the transaction, the interval pre-check and the mapping
of the SQLite trigger failure (`SQLITE_CONSTRAINT_TRIGGER`, code 1811) to a
`SlotTaken` result. `BookingService` validates the slot with the scheduling policy and
maps `SlotTaken` to the public `SLOT_TAKEN` conflict; it no longer catches
`DataAccessException` or knows about SQLite.

This supersedes the placement of the lock and transaction in ADR-0003, not its
three-layer defence: the trigger remains the safety net for a second instance or a
path that bypasses the service. Keeping the pre-check and the trigger mapping in one
module is what makes the persistence seam deep; leaking a driver exception to the
application would make every caller repeat the translation.
