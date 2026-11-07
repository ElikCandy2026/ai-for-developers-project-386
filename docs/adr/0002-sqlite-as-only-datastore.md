# SQLite as the only datastore

Meeting types and bookings live in a single SQLite file (`./data/call-booking.db` by
default). The project must run as one jar with no external services, so an embedded
database beats Postgres or MySQL on setup cost, while still giving the transactional
interval check a booking needs. WAL and `busy_timeout` are enabled, and writers are
serialized. The trade-off is horizontal scaling: the design assumes a single
application instance, and the SQLite trigger is a safety net rather than a substitute
for a distributed lock.
