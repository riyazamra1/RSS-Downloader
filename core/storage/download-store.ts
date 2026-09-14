import type { DownloadJob } from "../contracts/api";

export interface DownloadStore {
  load(): Promise<DownloadJob[]>;
  save(jobs: DownloadJob[]): Promise<void>;
}

/** In-memory fallback used by the current runtime. Hosts can replace this with persistent storage. */
export class MemoryDownloadStore implements DownloadStore {
  private jobs: DownloadJob[] = [];

  async load(): Promise<DownloadJob[]> {
    return this.jobs.map((job) => ({ ...job }));
  }

  async save(jobs: DownloadJob[]): Promise<void> {
    this.jobs = jobs.map((job) => ({ ...job }));
  }
}
