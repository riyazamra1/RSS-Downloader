export interface RssHostConfig {
  /** Empty means the embedded local Core/RAY runtime remains the active adapter. */
  baseUrl: string;
  /** Optional bearer token supplied by the host application at runtime. */
  accessToken?: string;
}

const DEFAULT_KEY = "rss-downloader-host-config";

export function loadRssHostConfig(storage?: Pick<Storage, "getItem">): RssHostConfig {
  try {
    const raw = storage?.getItem(DEFAULT_KEY);
    if (!raw) return { baseUrl: "" };
    const parsed = JSON.parse(raw) as Partial<RssHostConfig>;
    return {
      baseUrl: typeof parsed.baseUrl === "string" ? parsed.baseUrl.trim().replace(/\/$/, "") : "",
      accessToken: typeof parsed.accessToken === "string" && parsed.accessToken ? parsed.accessToken : undefined,
    };
  } catch {
    return { baseUrl: "" };
  }
}

export function isRssHostConfigured(config: RssHostConfig): boolean {
  return /^https:\/\//i.test(config.baseUrl);
}
