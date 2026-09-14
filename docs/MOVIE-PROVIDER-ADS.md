# Movie provider ad policy

RSS Downloader does not display third-party advertising from the source websites used by the two movie tabs.

## Runtime behavior

The Tamil Movies and Tamil Dubbed Movies provider adapters receive the movie provider policy from RAY. The policy can:

- block known advertising/tracking resources;
- block pop-up windows;
- block unwanted redirects;
- block notification prompts.

Media, metadata, artwork, and other requests required by an authorized provider integration remain available unless independently classified as advertising/tracking resources.

## Provider compliance

This policy is not a mechanism for bypassing provider access controls, paywalls, authentication, DRM, or terms that prohibit page modification. If a provider does not permit embedded ad filtering, RSS Downloader must use an authorized API/feed or another permitted integration instead.
