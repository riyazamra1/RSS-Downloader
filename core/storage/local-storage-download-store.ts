import type { DownloadJob } from "../contracts/api";
import type { DownloadStore } from "./download-store";

/** Minimal host storage contract so RSS Core stays independent of browser globals. */
export interface KeyValueStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

/** Persistent DownloadStore backed by a host-provided key/value store. */
export class LocalStorageDownloadStore implements DownloadStore {
  constructor(
    private readonly storage: KeyValueStorage,
    private readonly key = "rss-downloader.downloads.v1",
  ) {}

  async load(): Promise<DownloadJob[]> {
    const raw = this.storage.getItem(this.key);
    if (!raw) return [];

    try {
      const parsed: unknown = JSON.parse(raw);
      if (!Array.isArray(parsed)) return [];
      return parsed.filter(isDownloadJob).map((job) => ({ ...job }));
    } catch {
      return [];
    }
  }

  async save(jobs: DownloadJob[]): Promise<void> {
    this.storage.setItem(this.key, JSON.stringify(jobs));
  }

  async clear(): Promise<void> {
    this.storage.removeItem(this.key);
  }
}

function isDownloadJob(value: unknown): value is DownloadJob {
  if (!value || typeof value !== "object") return false;
  const job = value as Record<string, unknown>;
  return (
    typeof job.jobId === "string" &&
    typeof job.status === "string" &&
    typeof job.progressPercent === "number" &&
    typeof job.createdAt === "number" &&
    typeof job.updatedAt === "number"
  );
}
