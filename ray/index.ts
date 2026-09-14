import { RssDownloaderCore } from "../core/core";
import { createDownloaderEventBus } from "../core/contracts/events";
import type { DownloadStore } from "../core/storage/download-store";
import { MockRayAdapter } from "./adapters/mock";
import { RayWorker } from "./worker";

export interface RssDownloaderRuntimeOptions {
  downloadStore?: DownloadStore;
}

export function createRssDownloaderRuntime(
  options: RssDownloaderRuntimeOptions = {},
): RssDownloaderCore {
  const events = createDownloaderEventBus();
  const ray = new RayWorker(events);
  ray.registerAdapter(new MockRayAdapter());
  return new RssDownloaderCore(ray, events, options.downloadStore);
}
