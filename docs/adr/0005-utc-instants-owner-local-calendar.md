# UTC instants, owner-local calendar logic

Every instant in requests, responses and storage is absolute UTC (ISO 8601 in JSON,
epoch milliseconds in SQLite), while the booking window, working days, working hours
and day grouping are computed in the owner's timezone (`Europe/Moscow`). Availability
therefore mixes owner-local `plainDate` days with UTC slot starts. We rejected storing
wall-clock local times: guests can be in other timezones, and a stored local time
cannot be ordered or checked for overlap without re-applying a zone. Moscow has no DST,
but the code still resolves the zone through `OwnerService.zone()` instead of
hard-coding UTC+3.
