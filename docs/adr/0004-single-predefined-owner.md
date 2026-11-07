# One predefined owner, no authentication

The service has exactly one owner (`owner_anna`, Anna Sokolova), configured in
`application.yml`, with no registration, login or sessions; guest and owner call the
same API. Ownership is not a security boundary here — every meeting type belongs to
the same person, so owner id appears in no route or payload. This keeps the project
focused on the booking rules, but it is an explicit scope decision: adding multi-tenancy
later means threading an owner identifier through the contract, storage and every query.
