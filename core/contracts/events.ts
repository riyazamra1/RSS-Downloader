import type { DownloadJob } from "./api";

export type DownloaderEvent =
  | { type: "download.created"; job: DownloadJob }
  | { type: "download.progress"; job: DownloadJob }
  | { type: "download.completed"; job: DownloadJob }
  | { type: "download.failed"; job: DownloadJob }
  | { type: "download.cancelled"; job: DownloadJob };

export type DownloaderEventListener = (event: DownloaderEvent) => void;

export interface DownloaderEventBus {
  subscribe(listener: DownloaderEventListener): () => void;
  publish(event: DownloaderEvent): void;
}

export function createDownloaderEventBus(): DownloaderEventBus {
  const listeners = new Set<DownloaderEventListener>();

  return {
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    publish(event) {
      for (const listener of listeners) listener(event);
    },
  };
}
