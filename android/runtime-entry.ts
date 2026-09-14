import { createRssDownloaderRuntime } from "../ray/index";
import type { DownloadJob } from "../core/contracts/api";
import type { DownloadStore } from "../core/storage/download-store";

class LocalStorageDownloadStore implements DownloadStore {
  private readonly key = "rss-downloader-jobs";

  async load(): Promise<DownloadJob[]> {
    try {
      const raw = window.localStorage.getItem(this.key);
      const parsed = raw ? JSON.parse(raw) : [];
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  async save(jobs: DownloadJob[]): Promise<void> {
    window.localStorage.setItem(this.key, JSON.stringify(jobs));
  }
}

const runtime = createRssDownloaderRuntime({
  downloadStore: new LocalStorageDownloadStore(),
});

(window as typeof window & { RSSDownloaderAPI?: unknown }).RSSDownloaderAPI = runtime;
