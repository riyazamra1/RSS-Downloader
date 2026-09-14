# RSS Downloader — Downloads Page

The Downloads page is a first-class destination in the approved RSS Downloader interface. It does not replace or redesign the existing downloader tabs.

## Layout

### Ongoing Downloads

Show active jobs first, with the same visual language as the approved interface:

- Thumbnail when available
- File/movie title
- Audio/video type and selected quality
- Progress percentage
- Progress bar
- Downloaded bytes / total bytes when known
- Current speed
- ETA when known
- Cancel action
- Pause/resume only when the execution adapter explicitly supports it

Active states: `queued`, `running`, `paused`.

### All Downloads

Below ongoing jobs, show the complete local download history:

- Completed
- Failed
- Cancelled
- Current/ongoing jobs

Newest jobs appear first. Active jobs remain visually prominent regardless of sort order.

Completed jobs show the output name and final progress. Failed jobs show a concise error and a retry action when retry is supported. Cancelled jobs remain in history.

## Live updates

The page subscribes to RSS Core download events and updates the matching job without requiring a manual refresh:

- `download.created`
- `download.progress`
- `download.completed`
- `download.failed`
- `download.cancelled`

The UI must never invent progress, speed, ETA, or size values. Unknown values remain unavailable until RAY/Core provides them.

## Persistence

The Downloads UI is backed by a storage abstraction so the Android/web client can persist download history locally. The Core contract must remain platform-neutral; the platform adapter owns the actual persistent storage implementation.

## Navigation

Approved flow:

`Home → Downloads → Download Details`

Opening a job shows its current/final state, media type, quality, size information, output name, error (if any), and available actions.

## Design preservation

Do not replace the approved RSS Downloader interface. This page adds download management to the existing product and follows its existing typography, spacing, surfaces, controls, light/dark appearance, and responsive behavior.

The official `rss-downloader-logo.png` remains unchanged wherever the existing product uses the RSS Downloader logo or splash asset.
