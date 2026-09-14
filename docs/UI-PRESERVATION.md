# UI Preservation Contract

This document is a hard product requirement.

## Rule

The currently approved RSS Downloader interface/design is retained. Development must add functionality inside the existing design instead of replacing it with a new generic interface.

## Allowed changes

- Wiring existing controls to real functionality.
- Adding required states to existing components.
- Adding loading/progress/error/success states that visually belong to the existing design.
- Making existing screens responsive without changing their visual identity.
- Adding the requested in-tab search controls while preserving the tab design.
- Persisting rearranged tab order.

## Not allowed without explicit approval

- Replacing the approved layout.
- Switching to an unrelated design system.
- Rebranding or changing RSS visual identity.
- Removing existing tabs or core interactions.
- Introducing a redesign merely because another UI is easier to implement.

## Required tab behavior

### Social Downloader

Clipboard URL detection -> automatic analysis -> preview/options -> authorized download -> progress/result.

### Tamil Movies

Search stays inside the Tamil Movies tab -> results -> movie selection -> quality selection -> authorized download -> progress/result.

### Tamil Dubbed Movies

Search stays inside the Tamil Dubbed Movies tab -> results -> movie selection -> quality selection -> authorized download -> progress/result.

### Rearrangement

Users can rearrange tabs. The resulting order is persisted locally and restored on next launch.
