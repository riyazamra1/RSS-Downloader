import {
  type AnalyzeUrlRequest,
  type AnalyzeUrlResponse,
  type DownloadJob,
  type DownloadRequest,
  type DownloaderTab,
  type MediaOption,
  type RssDownloaderApi,
  type SearchRequest,
  type SearchResponse,
} from "./contracts/api";
import { createDownloaderEventBus, type DownloaderEventBus } from "./contracts/events";

export interface RayDispatcher {
  dispatch(job: {
    jobId: string;
    provider: string;
    operation: "analyze" | "search" | "download";
    input: Record<string, unknown>;
    requestedFormat?: string | null;
    requestedQuality?: string | null;
    authorization: { approved: boolean; policyVersion?: string | null };
    attempt?: number;
  }): Promise<DownloadJob | AnalyzeUrlResponse | SearchResponse>;
  cancel(jobId: string): Promise<DownloadJob>;
}

const DEFAULT_TABS: DownloaderTab[] = [
  "social-downloader",
  "tamil-movies",
  "tamil-dubbed-movies",
];

export class RssDownloaderCore implements RssDownloaderApi {
  private readonly jobs = new Map<string, DownloadJob>();
  private readonly events: DownloaderEventBus;
  private tabs: DownloaderTab[] = [...DEFAULT_TABS];

  constructor(private readonly ray: RayDispatcher, events = createDownloaderEventBus()) {
    this.events = events;
  }

  async analyzeUrl(request: AnalyzeUrlRequest): Promise<AnalyzeUrlResponse> {
    const url = request.url.trim();
    if (!/^https?:\/\//i.test(url)) throw new Error("A valid HTTP(S) URL is required.");
    return (await this.ray.dispatch({
      jobId: crypto.randomUUID(),
      provider: "auto",
      operation: "analyze",
      input: { url },
      authorization: { approved: false, policyVersion: "1" },
      attempt: 1,
    })) as AnalyzeUrlResponse;
  }

  async search(request: SearchRequest): Promise<SearchResponse> {
    const query = request.query.trim();
    if (!query) return { tab: request.tab, query: "", results: [] };
    return (await this.ray.dispatch({
      jobId: crypto.randomUUID(),
      provider: "catalog",
      operation: "search",
      input: { tab: request.tab, query },
      authorization: { approved: false, policyVersion: "1" },
      attempt: 1,
    })) as SearchResponse;
  }

  async listMediaOptions(requestId: string): Promise<MediaOption[]> {
    throw new Error(`Media options are returned by analyzeUrl; request ${requestId} is not stored by Core yet.`);
  }

  async createDownload(request: DownloadRequest): Promise<DownloadJob> {
    if (!request.authorizationApproved) {
      throw new Error("Download authorization must be approved before execution.");
    }

    const jobId = crypto.randomUUID();
    const queued: DownloadJob = { jobId, status: "queued", progressPercent: 0 };
    this.jobs.set(jobId, queued);
    this.events.publish({ type: "download.created", job: queued });

    const result = (await this.ray.dispatch({
      jobId,
      provider: "auto",
      operation: "download",
      input: { requestId: request.requestId },
      requestedFormat: request.mediaOptionId ?? null,
      requestedQuality: request.quality ?? null,
      authorization: { approved: true, policyVersion: "1" },
      attempt: 1,
    })) as DownloadJob;

    this.jobs.set(result.jobId, result);
    this.events.publish({
      type: result.status === "completed" ? "download.completed" : "download.failed",
      job: result,
    });
    return result;
  }

  async getDownload(jobId: string): Promise<DownloadJob> {
    const job = this.jobs.get(jobId);
    if (!job) throw new Error(`Download job not found: ${jobId}`);
    return job;
  }

  async cancelDownload(jobId: string): Promise<DownloadJob> {
    const result = await this.ray.cancel(jobId);
    this.jobs.set(jobId, result);
    this.events.publish({ type: "download.cancelled", job: result });
    return result;
  }

  async reorderTabs(order: DownloaderTab[]): Promise<DownloaderTab[]> {
    if (order.length !== DEFAULT_TABS.length || new Set(order).size !== DEFAULT_TABS.length) {
      throw new Error("Tab order must contain each approved tab exactly once.");
    }
    this.tabs = [...order];
    return [...this.tabs];
  }

  getEventBus(): DownloaderEventBus {
    return this.events;
  }
}
