/**
 * RSS Downloader provider browsing policy.
 *
 * This policy is intended for source pages used by the two movie-search tabs.
 * It prevents third-party advertising/redirect surfaces from being rendered
 * inside an RSS Downloader browsing surface while leaving the requested media
 * and metadata endpoints available.
 *
 * This is a policy contract, not a guarantee that every provider permits page
 * modification. Providers that prohibit this behavior must be integrated via
 * an authorized API/feed instead of an embedded page.
 */

export const MOVIE_TABS = ["tamil-movies", "tamil-dubbed-movies"] as const;
export type MovieTab = (typeof MOVIE_TABS)[number];

export interface AdBlockingPolicy {
  enabled: boolean;
  blockPopups: boolean;
  blockRedirects: boolean;
  blockNotificationPrompts: boolean;
  blockedResourcePatterns: readonly string[];
}

export const RSS_MOVIE_AD_BLOCKING_POLICY: AdBlockingPolicy = {
  enabled: true,
  blockPopups: true,
  blockRedirects: true,
  blockNotificationPrompts: true,
  blockedResourcePatterns: [
    "doubleclick.net",
    "googlesyndication.com",
    "googleadservices.com",
    "adservice.google.com",
    "adsystem.com",
    "adnxs.com",
    "advertising.com",
    "amazon-adsystem.com",
    "outbrain.com",
    "taboola.com",
    "popads.net",
    "popcash.net",
    "propellerads.com",
  ],
};

export function shouldApplyMovieAdBlocking(tab: string): tab is MovieTab {
  return (MOVIE_TABS as readonly string[]).includes(tab);
}

export function shouldBlockResource(url: string): boolean {
  if (!url) return false;
  const normalized = url.toLowerCase();
  return RSS_MOVIE_AD_BLOCKING_POLICY.blockedResourcePatterns.some(
    (pattern) => normalized.includes(pattern),
  );
}
