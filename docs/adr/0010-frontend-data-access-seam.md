# Frontend data-access seam

The UI talks to the API through one facade in the frontend data layer and never
imports the generated SDK. The facade exposes use-case functions returning a
discriminated `ApiResult`; transport errors, HTTP errors, aborts and unexpected
failures become one `ApiError` shape with a typed code. A single `useResource` hook
owns loading, abort and stale-response handling, while mutations keep local
pending/error state. Only the two 409 codes reload availability.

We rejected a query library because it would add a runtime dependency for a small app,
and a global store because the two views do not share live data. The cost is a
hand-rolled hook with no cache or retries; those stay out of scope until a real need
appears. A grep check in `make verify` keeps generated imports out of the UI.
