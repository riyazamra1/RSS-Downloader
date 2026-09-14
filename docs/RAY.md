# RAY — RSS Downloader Execution Worker

RAY is the execution layer of RSS Downloader. It receives validated executable jobs from RSS Core, runs the selected provider adapter, emits progress, and returns a normalized result.

## Boundary

```text
RSS Downloader UI
        |
        v
    RSS Core
        |
        | dispatch job
        v
      RAY
        |
        +--> Provider Adapter
        |
        +--> Acquisition Process
        |
        +--> Progress Events
        |
        v
    RSS Core
        |
        v
        UI
```

## RAY responsibilities

- Accept only jobs dispatched by RSS Core.
- Validate the job envelope before execution.
- Select an adapter using the provider/adapter identifier supplied by Core.
- Execute acquisition work without exposing provider-specific logic to the UI.
- Emit normalized progress events.
- Support cancellation through a job-scoped control signal.
- Capture output metadata, duration, bytes and final state.
- Return deterministic success/failure information.
- Avoid storing credentials or session material in source control.

## Job lifecycle

```text
QUEUED -> STARTING -> RUNNING -> COMPLETED
                         |
                         +----> CANCELLING -> CANCELLED
                         |
                         +----> FAILED
```

A worker must not silently transition a completed job back to running. Retries, when supported, are new execution attempts associated with the same logical job.

## Normalized progress

Every progress update should contain:

- `jobId`
- `state`
- `percent` from 0 to 100 when known
- `bytesDownloaded` when known
- `totalBytes` when known
- `speedBytesPerSecond` when known
- `message` suitable for the UI
- timestamp

Unknown values must be represented as null rather than fabricated estimates.

## Adapter boundary

Provider adapters are replaceable modules. An adapter should receive a validated execution request and return a normalized acquisition result. It must not know about UI tabs, UI navigation, or Android presentation state.

## Cancellation

Cancellation is cooperative. RSS Core requests cancellation for a job; RAY forwards that request to the active adapter/process and reports `CANCELLING`, followed by either `CANCELLED` or `FAILED` if cancellation cannot be completed cleanly.

## Security

RAY must not bypass provider restrictions, authentication controls, paywalls, DRM, or access controls. Jobs must be authorized by RSS Core before execution. Secrets belong in runtime configuration/secret storage, never in the repository.

## Test targets

The first RAY test layer should cover:

1. valid job accepted;
2. malformed job rejected;
3. unsupported adapter rejected;
4. progress remains within 0–100;
5. cancellation reaches a terminal state;
6. successful result contains output metadata;
7. adapter failure becomes normalized `FAILED` state;
8. worker restart does not fabricate a completed result.
