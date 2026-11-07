# Three layers of overlap prevention

Double booking is blocked at three independent levels: a fair `ReentrantLock`
serializes booking creation in the application, the transaction checks
`NEW.start < existing.end AND NEW.end > existing.start` before inserting, and the
`trg_bookings_prevent_overlap` SQLite trigger aborts the insert with `booking_overlap`
if an interval still collides. The lock makes the common case deterministic and returns
a clean `409` instead of a database error, the query states the business rule where it
is readable, and the trigger survives a second instance or a code path that bypasses
the service. Removing the lock because "the trigger exists" would turn every concurrent
collision into a driver-level exception.
