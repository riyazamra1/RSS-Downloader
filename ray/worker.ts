import type { DownloadJob } from "../core/contracts/api";
import type { DownloaderEventBus } from "../core/contracts/events";
import type { RayDispatcher } from "../core/core";
import { getMovieProviderPolicy } from "./adapters/movie-provider-policy";

export interface RayAdapter {
  readonly provider: string;
  analyze(input: Record<string, unknown>): Promise<unknown>;
  search(input: Record<string, unknown>): Promise<unknown>;
  download(
    input: Record<string, unknown>,
    job: DownloadJob,
    onProgress?: (job: DownloadJob) => void,
  ): Promise<DownloadJob>;
}

export class RayWorker implements RayDispatcher {
  private readonly adapters = new Map<string, RayAdapter>();
  private readonly jobs = new Map<string, DownloadJob>();

  constructor(private readonly events?: DownloaderEventBus) {}

  registerAdapter(adapter: RayAdapter): void {
    this.adapters.set(adapter.provider, adapter);
  }

  async dispatch(job: {
    jobId: string;
    provider: string;
    operation: "analyze" | "search" | "download";
    input: Record<string, unknown>;
    requestedFormat?: string | null;
    requestedQuality?: string | null;
    authorization: { approved: boolean; policyVersion?: string | null };
    attempt?: number;
  }): Promise<any> {
    if (job.operation === "download" && !job.authorization.approved) {
      throw new Error("RAY rejected an unauthorized download job.");
    }

    const adapter = this.resolveAdapter(job.provider);
    const runtimeInput = this.withProviderPolicy(job.input);

    if (job.operation === "analyze") return adapter.analyze(runtimeInput);
    if (job.operation === "search") return adapter.search(runtimeInput);

    const now = Date.now();
    const queued: DownloadJob = {
      jobId: job.jobId,
      status: "running",
      progressPercent: 0,
      createdAt: now,
      updatedAt: now,
    };
    this.jobs.set(job.jobId, queued);

    const onProgress = (next: DownloadJob) => {
      const progress: DownloadJob = {
        ...queued,
        ...next,
        createdAt: next.createdAt ?? queued.createdAt,
        updatedAt: Date.now(),
      };
      this.jobs.set(job.jobId, progress);
      this.events?.publish({ type: "download.progress", job: progress });
    };

    try {
      const completed = await adapter.download(runtimeInput, queued, onProgress);
      const result = { ...queued, ...completed, updatedAt: Date.now() };
      this.jobs.set(job.jobId, result);
      return result;
    } catch (error) {
      const failed: DownloadJob = {
        ...queued,
        status: "failed",
        updatedAt: Date.now(),
        error: error instanceof Error ? error.message : "RAY adapter failed",
      };
      this.jobs.set(job.jobId, failed);
      return failed;
    }
  }

  async cancel(jobId: string): Promise<DownloadJob> {
    const job = this.jobs.get(jobId);
    if (!job) throw new Error(`RAY job not found: ${jobId}`);
    const cancelled = { ...job, status: "cancelled" as const, updatedAt: Date.now() };
    this.jobs.set(jobId, cancelled);
    return cancelled;
  }

  private withProviderPolicy(input: Record<string, unknown>): Record<string, unknown> {
    const tab = typeof input.tab === "string" ? input.tab : undefined;
    if (tab !== "tamil-movies" && tab !== "tamil-dubbed-movies") return input;

    return {
      ...input,
      movieProviderPolicy: getMovieProviderPolicy({
        tab,
        url: typeof input.url === "string" ? input.url : "",
      }),
    };
  }

  private resolveAdapter(provider: string): RayAdapter {
    if (provider !== "auto" && provider !== "catalog") {
      const adapter = this.adapters.get(provider);
      if (adapter) return adapter;
    }

    const first = this.adapters.values().next().value as RayAdapter | undefined;
    if (!first) throw new Error("No RAY provider adapter is registered.");
    return first;
  }
}
