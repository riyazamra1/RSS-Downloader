import type {
  AnalyzeUrlRequest,
  AnalyzeUrlResponse,
  DownloadJob,
  DownloadListResponse,
  DownloadRequest,
  MediaOption,
  RssDownloaderApi,
  SearchRequest,
  SearchResponse,
  DownloaderTab,
} from "../contracts/api";
import { isRssHostConfigured, type RssHostConfig } from "./host-config";

export interface RssHostApiPaths {
  analyze: string;
  search: string;
  mediaOptions: string;
  downloads: string;
  download: string;
  cancel: string;
  reorderTabs: string;
}

export const DEFAULT_HOST_API_PATHS: RssHostApiPaths = {
  analyze: "/api/downloader/analyze",
  search: "/api/downloader/search",
  mediaOptions: "/api/downloader/media-options",
  downloads: "/api/downloader/downloads",
  download: "/api/downloader/download",
  cancel: "/api/downloader/cancel",
  reorderTabs: "/api/downloader/tabs",
};

export class RssHostApi implements RssDownloaderApi {
  constructor(
    private readonly config: RssHostConfig,
    private readonly paths: RssHostApiPaths = DEFAULT_HOST_API_PATHS,
    private readonly fetchImpl: typeof fetch = fetch,
  ) {}

  private async request<T>(path: string, init: RequestInit): Promise<T> {
    if (!isRssHostConfigured(this.config)) {
      throw new Error("RSS host API is not configured.");
    }
    const headers = new Headers(init.headers);
    headers.set("content-type", "application/json");
    if (this.config.accessToken) headers.set("authorization", `Bearer ${this.config.accessToken}`);
    const response = await this.fetchImpl(`${this.config.baseUrl}${path}`, { ...init, headers });
    if (!response.ok) throw new Error(`RSS host API request failed (${response.status}).`);
    return (await response.json()) as T;
  }

  analyzeUrl(request: AnalyzeUrlRequest) { return this.request<AnalyzeUrlResponse>(this.paths.analyze, this.post(request)); }
  search(request: SearchRequest) { return this.request<SearchResponse>(this.paths.search, this.post(request)); }
  listMediaOptions(requestId: string) { return this.request<MediaOption[]>(`${this.paths.mediaOptions}/${encodeURIComponent(requestId)}`, { method: "GET" }); }
  createDownload(request: DownloadRequest) { return this.request<DownloadJob>(this.paths.download, this.post(request)); }
  getDownload(jobId: string) { return this.request<DownloadJob>(`${this.paths.downloads}/${encodeURIComponent(jobId)}`, { method: "GET" }); }
  listDownloads() { return this.request<DownloadListResponse>(this.paths.downloads, { method: "GET" }); }
  cancelDownload(jobId: string) { return this.request<DownloadJob>(`${this.paths.cancel}/${encodeURIComponent(jobId)}`, this.post({})); }
  reorderTabs(order: DownloaderTab[]) { return this.request<DownloaderTab[]>(this.paths.reorderTabs, this.post({ order })); }

  private post(body: unknown): RequestInit { return { method: "POST", body: JSON.stringify(body) }; }
}
