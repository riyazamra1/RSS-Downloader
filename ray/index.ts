import { RssDownloaderCore } from "../core/core";
import { createDownloaderEventBus } from "../core/contracts/events";
import { MockRayAdapter } from "./adapters/mock";
import { RayWorker } from "./worker";

export function createRssDownloaderRuntime(): RssDownloaderCore {
  const events = createDownloaderEventBus();
  const ray = new RayWorker(events);
  ray.registerAdapter(new MockRayAdapter());
  return new RssDownloaderCore(ray, events);
}
