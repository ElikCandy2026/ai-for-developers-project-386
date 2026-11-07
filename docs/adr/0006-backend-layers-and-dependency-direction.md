# Backend layers and dependency direction

The backend is split into four layers with a one-way dependency graph:
`api → application → {domain, persistence → domain}`. `api` owns HTTP, the generated
interfaces/DTOs and mapping to domain; `application` owns use cases, transactions and
`ApplicationErrorCode`; `domain` owns model types and scheduling rules and depends on
the JDK only; `persistence` owns SQL, rows and trigger handling and depends on
`domain`. `application` depends on the concrete repositories rather than ports: the
service has one datastore (ADR-0002), and a port would add an interface with a single
implementation and no test seam the integration tests do not already provide. ArchUnit
tests in test scope enforce the arrows, including "no Spring in domain" and "generated
DTOs only in api".

The cost is that replacing the datastore means editing application wiring, not just an
adapter. That is acceptable while SQLite is fixed, and the deep booking repository
keeps the SQLite specifics in one place.
