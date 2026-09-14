import { RssDownloaderCore } from "../core/core";
import { MockRayAdapter } from "./adapters/mock";
import { RayWorker } from "./worker";

export function createRssDownloaderRuntime(): RssDownloaderCore {
  const ray = new RayWorker();
  ray.registerAdapter(new MockRayAdapter());
  return new RssDownloaderCore(ray);
}
