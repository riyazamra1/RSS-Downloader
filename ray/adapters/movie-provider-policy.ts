import {
  RSS_MOVIE_AD_BLOCKING_POLICY,
  shouldApplyMovieAdBlocking,
  shouldBlockResource,
  type MovieTab,
} from "../../core/policies/ad-blocking";

export interface MovieProviderRequest {
  tab: MovieTab;
  url: string;
}

export interface MovieProviderPolicyResult {
  allowed: boolean;
  blockPopups: boolean;
  blockRedirects: boolean;
  blockNotificationPrompts: boolean;
  shouldBlockResource: (url: string) => boolean;
}

/** Runtime policy supplied to movie provider adapters. */
export function getMovieProviderPolicy(request: MovieProviderRequest): MovieProviderPolicyResult {
  const enabled = shouldApplyMovieAdBlocking(request.tab);

  return {
    allowed: true,
    blockPopups: enabled && RSS_MOVIE_AD_BLOCKING_POLICY.blockPopups,
    blockRedirects: enabled && RSS_MOVIE_AD_BLOCKING_POLICY.blockRedirects,
    blockNotificationPrompts:
      enabled && RSS_MOVIE_AD_BLOCKING_POLICY.blockNotificationPrompts,
    shouldBlockResource: enabled ? shouldBlockResource : () => false,
  };
}
