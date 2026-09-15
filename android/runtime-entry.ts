import { createRssDownloaderRuntime } from "../ray/index";
import type { DownloadJob, RssDownloaderApi } from "../core/contracts/api";
import type { DownloadStore } from "../core/storage/download-store";
import { RssHostApi } from "../core/host/host-api";
import { loadRssHostConfig } from "../core/host/host-config";

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

const hostConfig = loadRssHostConfig(window.localStorage);
const embeddedRuntime = createRssDownloaderRuntime({
  downloadStore: new LocalStorageDownloadStore(),
});
const api: RssDownloaderApi = hostConfig.baseUrl
  ? new RssHostApi(hostConfig)
  : embeddedRuntime;

const runtimeWindow = window as typeof window & {
  RSSDownloaderAPI?: RssDownloaderApi;
  RSSDownloaderHost?: {
    configured: boolean;
    baseUrl: string;
    mode: "host" | "embedded";
  };
};

runtimeWindow.RSSDownloaderAPI = api;
runtimeWindow.RSSDownloaderHost = {
  configured: Boolean(hostConfig.baseUrl),
  baseUrl: hostConfig.baseUrl,
  mode: hostConfig.baseUrl ? "host" : "embedded",
};
