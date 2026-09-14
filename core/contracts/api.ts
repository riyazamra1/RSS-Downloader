export type DownloaderTab =
  | "social-downloader"
  | "tamil-movies"
  | "tamil-dubbed-movies";

export type MediaKind = "audio" | "video";

export interface AnalyzeUrlRequest {
  url: string;
}

export interface AnalyzeUrlResponse {
  requestId: string;
  normalizedUrl: string;
  title?: string;
  thumbnailUrl?: string;
  durationSeconds?: number;
  mediaOptions: MediaOption[];
}

export interface MediaOption {
  id: string;
  kind: MediaKind;
  format: string;
  quality?: string;
  sizeBytes?: number;
}

export interface SearchRequest {
  tab: DownloaderTab;
  query: string;
}

export interface SearchResult {
  id: string;
  title: string;
  thumbnailUrl?: string;
  year?: number;
  qualities?: string[];
}

export interface SearchResponse {
  tab: DownloaderTab;
  query: string;
  results: SearchResult[];
}

export interface DownloadRequest {
  requestId: string;
  mediaOptionId?: string;
  quality?: string;
  authorizationApproved: boolean;
}

export type DownloadStatus =
  | "queued"
  | "running"
  | "paused"
  | "completed"
  | "failed"
  | "cancelled";

export interface DownloadJob {
  jobId: string;
  status: DownloadStatus;
  progressPercent: number;
  downloadedBytes?: number;
  totalBytes?: number;
  speedBytesPerSecond?: number;
  etaSeconds?: number;
  error?: string;
  outputName?: string;
}

export interface RssDownloaderApi {
  analyzeUrl(request: AnalyzeUrlRequest): Promise<AnalyzeUrlResponse>;
  search(request: SearchRequest): Promise<SearchResponse>;
  listMediaOptions(requestId: string): Promise<MediaOption[]>;
  createDownload(request: DownloadRequest): Promise<DownloadJob>;
  getDownload(jobId: string): Promise<DownloadJob>;
  cancelDownload(jobId: string): Promise<DownloadJob>;
  reorderTabs(order: DownloaderTab[]): Promise<DownloaderTab[]>;
}
