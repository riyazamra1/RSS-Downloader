import type { DownloadJob } from "../core/contracts/api";
import type { RayDispatcher } from "../core/core";

export interface RayAdapter {
  readonly provider: string;
  analyze(input: Record<string, unknown>): Promise<unknown>;
  search(input: Record<string, unknown>): Promise<unknown>;
  download(input: Record<string, unknown>, job: DownloadJob): Promise<DownloadJob>;
}

export class RayWorker implements RayDispatcher {
  private readonly adapters = new Map<string, RayAdapter>();
  private readonly jobs = new Map<string, DownloadJob>();

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
    if (job.operation === "analyze") return adapter.analyze(job.input);
    if (job.operation === "search") return adapter.search(job.input);

    const queued: DownloadJob = {
      jobId: job.jobId,
      status: "running",
      progressPercent: 0,
    };
    this.jobs.set(job.jobId, queued);

    try {
      const completed = await adapter.download(job.input, queued);
      this.jobs.set(job.jobId, completed);
      return completed;
    } catch (error) {
      const failed: DownloadJob = {
        ...queued,
        status: "failed",
        error: error instanceof Error ? error.message : "RAY adapter failed",
      };
      this.jobs.set(job.jobId, failed);
      return failed;
    }
  }

  async cancel(jobId: string): Promise<DownloadJob> {
    const job = this.jobs.get(jobId);
    if (!job) throw new Error(`RAY job not found: ${jobId}`);
    const cancelled = { ...job, status: "cancelled" as const };
    this.jobs.set(jobId, cancelled);
    return cancelled;
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
