import {
  type AnalyzeUrlRequest,
  type AnalyzeUrlResponse,
  type DownloadJob,
  type DownloadListResponse,
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
  private readonly analyses = new Map<string, AnalyzeUrlResponse>();
  private readonly events: DownloaderEventBus;
  private tabs: DownloaderTab[] = [...DEFAULT_TABS];

  constructor(private readonly ray: RayDispatcher, events = createDownloaderEventBus()) {
    this.events = events;
  }

  async analyzeUrl(request: AnalyzeUrlRequest): Promise<AnalyzeUrlResponse> {
    const url = request.url.trim();
    if (!/^https?:\/\//i.test(url)) throw new Error("A valid HTTP(S) URL is required.");

    const response = (await this.ray.dispatch({
      jobId: crypto.randomUUID(),
      provider: "auto",
      operation: "analyze",
      input: { url },
      authorization: { approved: false, policyVersion: "1" },
      attempt: 1,
    })) as AnalyzeUrlResponse;

    if (!response.requestId) throw new Error("RAY analysis response is missing requestId.");
    this.analyses.set(response.requestId, response);
    return response;
  }

  async search(request: SearchRequest): Promise<SearchResponse> {
    const query = request.query.trim();
    if (!DEFAULT_TABS.includes(request.tab)) throw new Error("Unsupported downloader tab.");
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
    const analysis = this.analyses.get(requestId);
    if (!analysis) throw new Error(`Analysis request not found: ${requestId}`);
    return [...analysis.mediaOptions];
  }

  async createDownload(request: DownloadRequest): Promise<DownloadJob> {
    if (!request.authorizationApproved) {
      throw new Error("Download authorization must be approved before execution.");
    }

    const analysis = this.analyses.get(request.requestId);
    if (!analysis) throw new Error(`Analysis request not found: ${request.requestId}`);

    const selected = request.mediaOptionId
      ? analysis.mediaOptions.find((option) => option.id === request.mediaOptionId)
      : undefined;
    const now = Date.now();
    const jobId = crypto.randomUUID();
    const queued: DownloadJob = {
      jobId,
      status: "queued",
      progressPercent: 0,
      createdAt: now,
      updatedAt: now,
      title: analysis.title,
      thumbnailUrl: analysis.thumbnailUrl,
      mediaKind: selected?.kind,
      quality: request.quality ?? selected?.quality,
      format: selected?.format,
      totalBytes: selected?.sizeBytes,
    };

    this.jobs.set(jobId, queued);
    this.events.publish({ type: "download.created", job: queued });

    try {
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

      const finalJob: DownloadJob = {
        ...queued,
        ...result,
        createdAt: result.createdAt ?? queued.createdAt,
        updatedAt: Date.now(),
        title: result.title ?? queued.title,
        thumbnailUrl: result.thumbnailUrl ?? queued.thumbnailUrl,
        mediaKind: result.mediaKind ?? queued.mediaKind,
        quality: result.quality ?? queued.quality,
        format: result.format ?? queued.format,
        totalBytes: result.totalBytes ?? queued.totalBytes,
      };
      this.jobs.set(jobId, finalJob);
      this.events.publish({
        type: finalJob.status === "completed" ? "download.completed" : "download.failed",
        job: finalJob,
      });
      return finalJob;
    } catch (error) {
      const failed: DownloadJob = {
        ...queued,
        status: "failed",
        updatedAt: Date.now(),
        error: error instanceof Error ? error.message : String(error),
      };
      this.jobs.set(jobId, failed);
      this.events.publish({ type: "download.failed", job: failed });
      return failed;
    }
  }

  async getDownload(jobId: string): Promise<DownloadJob> {
    const job = this.jobs.get(jobId);
    if (!job) throw new Error(`Download job not found: ${jobId}`);
    return { ...job };
  }

  async listDownloads(): Promise<DownloadListResponse> {
    const jobs = [...this.jobs.values()]
      .sort((a, b) => {
        const aActive = a.status === "queued" || a.status === "running" || a.status === "paused";
        const bActive = b.status === "queued" || b.status === "running" || b.status === "paused";
        if (aActive !== bActive) return aActive ? -1 : 1;
        return b.createdAt - a.createdAt;
      })
      .map((job) => ({ ...job }));
    return { jobs };
  }

  async cancelDownload(jobId: string): Promise<DownloadJob> {
    const result = await this.ray.cancel(jobId);
    const current = this.jobs.get(jobId);
    const cancelled: DownloadJob = {
      ...(current ?? result),
      ...result,
      createdAt: result.createdAt ?? current?.createdAt ?? Date.now(),
      updatedAt: Date.now(),
      title: result.title ?? current?.title,
      thumbnailUrl: result.thumbnailUrl ?? current?.thumbnailUrl,
      mediaKind: result.mediaKind ?? current?.mediaKind,
      quality: result.quality ?? current?.quality,
      format: result.format ?? current?.format,
    };
    this.jobs.set(jobId, cancelled);
    this.events.publish({ type: "download.cancelled", job: cancelled });
    return cancelled;
  }

  async reorderTabs(order: DownloaderTab[]): Promise<DownloaderTab[]> {
    if (order.length !== DEFAULT_TABS.length || new Set(order).size !== DEFAULT_TABS.length) {
      throw new Error("Tab order must contain each approved tab exactly once.");
    }
    if (order.some((tab) => !DEFAULT_TABS.includes(tab))) {
      throw new Error("Tab order contains an unsupported tab.");
    }
    this.tabs = [...order];
    return [...this.tabs];
  }

  getEventBus(): DownloaderEventBus {
    return this.events;
  }
}
