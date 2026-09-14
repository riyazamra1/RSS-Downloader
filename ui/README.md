# RSS Downloader UI

This directory is the first application-facing UI layer for RSS Downloader.

## Host integration

The host application injects a platform adapter as `window.RSSDownloaderAPI` implementing the platform-neutral Core contract:

- `analyzeUrl(request)`
- `search(request)`
- `listMediaOptions(requestId)`
- `createDownload(request)`
- `getDownload(jobId)`
- `listDownloads()`
- `cancelDownload(jobId)`
- `reorderTabs(order)`
- `getEventBus().subscribe(listener)`

The UI does not contain provider-specific download logic.

### Persistent downloads

RSS Core now accepts a `DownloadStore`. Hosts that have persistent key/value storage should provide `LocalStorageDownloadStore` with their storage implementation when creating the runtime. The Core remains platform-neutral and hydrates saved jobs before serving API requests.

The browser adapter can use the platform's `localStorage`-compatible interface; other hosts can provide their own persistent implementation without changing the UI or Core contracts.

## Implemented behavior

- Preserves the three approved downloader tabs.
- Persists tab order locally and sends reordered tabs to Core when available.
- Social Downloader URL input with automatic clipboard URL detection and URL analysis.
- Tamil Movies and Tamil Dubbed Movies have search controls inside their own tabs.
- Dedicated Downloads destination with Ongoing and All Downloads views.
- Live download event rendering.
- Progress, speed, ETA, bytes, status, cancellation and error states.
- Persistent download-job storage through the Core `DownloadStore` abstraction.
- Responsive mobile/desktop layout and dark RSS visual language.
- Uses the repository's exact `rss-downloader-logo.png`; the asset is not modified.

The UI follows the preservation contract: it adds functionality without replacing the approved product flow.
