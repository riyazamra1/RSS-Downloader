# RSS Downloader Architecture

## System boundary

```text
┌───────────────────────────────┐
│        RSS Downloader UI      │
│ approved interface preserved  │
└───────────────┬───────────────┘
                │ API / events
                ▼
┌───────────────────────────────┐
│           RSS Core            │
│ URL analysis                  │
│ provider routing              │
│ job queue/state               │
│ authorization/policy checks   │
│ progress aggregation          │
└───────────────┬───────────────┘
                │ job dispatch
                ▼
┌───────────────────────────────┐
│             RAY               │
│ execution worker              │
│ provider adapters             │
│ media acquisition             │
│ progress + result reporting   │
└───────────────────────────────┘
```

## Responsibilities

### UI

- Preserve the approved interface.
- Detect URLs from the clipboard.
- Submit URLs/search queries to RSS Core.
- Display analysis, media options, queue state and progress.
- Keep tab ordering persistent.
- Never embed provider-specific execution logic.

### RSS Core

- Validate and normalize requests.
- Analyze supported URLs.
- Select the appropriate provider/adapter.
- Create and track download jobs.
- Enforce authorization/policy checks.
- Aggregate worker progress.
- Return stable API/event contracts to clients.

### RAY

- Receive executable jobs from RSS Core.
- Run provider adapters.
- Stream progress.
- Return success/failure metadata.
- Remain replaceable and independently testable.

## UI-to-core contract

The first implementation should expose these conceptual operations:

- `analyzeUrl(url)`
- `search(tab, query)`
- `listMediaOptions(requestId)`
- `createDownload(request)`
- `getDownload(jobId)`
- `cancelDownload(jobId)`
- `reorderTabs(order)`

The exact transport can be selected during implementation, but the UI must depend on the contract rather than the worker implementation.

## Security and policy

Provider adapters must only perform downloads that are permitted by the provider, content owner, applicable law and the user's authorization. Authentication/session material must not be stored in UI state or committed to the repository.
