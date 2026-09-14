# RSS Downloader

RSS Downloader is a responsive downloader application built around the RSS Core + RAY architecture.

## Approved UI direction

The approved RSS Downloader interface is preserved as the source design. The implementation must not replace or substantially redesign it. New functionality is integrated into the existing visual language and interaction model.

### Core interface requirements

- 3 rearrangeable tabs:
  1. Social Downloader
  2. Tamil Movies
  3. Tamil Dubbed Movies
- Social Downloader:
  - Clipboard URL auto-detection/paste
  - Automatic URL analysis
  - Media preview
  - Audio/video format selection
  - Animated download state
- Tamil Movies:
  - Search button and search flow inside the tab
  - Movie -> quality -> authorized download flow
- Tamil Dubbed Movies:
  - Search button and search flow inside the tab
  - Movie -> quality -> authorized download flow
- Tab order is persisted after rearranging.
- Responsive mobile/tablet/desktop layout.
- Light/dark appearance support.
- Existing approved visual design remains unchanged.

## Architecture

`RSS Downloader UI -> RSS Core -> RAY -> Downloader/Provider adapters`

RSS Core owns orchestration, validation, queue/state, provider selection and policy. RAY executes jobs and reports progress/results. The UI communicates with RSS Core through the project API boundary rather than directly controlling workers.

## Project status

Repository connected: `riyazamra1/RSS-Downloader`.

Initial implementation is now being established in this repository. UI preservation is a hard requirement.
