import type { DownloadJob, DownloadListResponse, RssDownloaderApi } from "../core/contracts/api";
import type { DownloaderEvent, DownloaderEventListener } from "../core/contracts/events";

export type DownloadsView = "ongoing" | "all";

export interface DownloadsUiState {
  view: DownloadsView;
  jobs: DownloadJob[];
  loading: boolean;
  error: string | null;
}

export interface DownloadsUiController {
  getState(): DownloadsUiState;
  load(): Promise<DownloadsUiState>;
  setView(view: DownloadsView): DownloadsUiState;
  cancel(jobId: string): Promise<DownloadsUiState>;
  subscribe(listener: (state: DownloadsUiState) => void): () => void;
  dispose(): void;
}

const isOngoing = (job: DownloadJob): boolean =>
  job.status === "queued" || job.status === "running" || job.status === "paused";

export function createDownloadsUiController(api: RssDownloaderApi): DownloadsUiController {
  let state: DownloadsUiState = {
    view: "ongoing",
    jobs: [],
    loading: false,
    error: null,
  };
  const listeners = new Set<(next: DownloadsUiState) => void>();

  const emit = () => {
    for (const listener of listeners) listener({ ...state, jobs: [...state.jobs] });
  };

  const replaceJobs = (response: DownloadListResponse) => {
    state = {
      ...state,
      jobs: response.jobs
        .filter((job) => state.view === "all" || isOngoing(job))
        .sort((a, b) => b.updatedAt - a.updatedAt),
      loading: false,
      error: null,
    };
    emit();
  };

  const onEvent: DownloaderEventListener = (event: DownloaderEvent) => {
    const existing = state.jobs.findIndex((job) => job.jobId === event.job.jobId);
    const nextJobs = [...state.jobs];
    const visible = state.view === "all" || isOngoing(event.job);

    if (visible) {
      if (existing >= 0) nextJobs[existing] = event.job;
      else nextJobs.push(event.job);
    } else if (existing >= 0) {
      nextJobs.splice(existing, 1);
    }

    state = {
      ...state,
      jobs: nextJobs.sort((a, b) => b.updatedAt - a.updatedAt),
    };
    emit();
  };

  const unsubscribeEvents = api.getEventBus().subscribe(onEvent);

  return {
    getState() {
      return { ...state, jobs: [...state.jobs] };
    },

    async load() {
      state = { ...state, loading: true, error: null };
      emit();
      try {
        replaceJobs(await api.listDownloads());
      } catch (error) {
        state = {
          ...state,
          loading: false,
          error: error instanceof Error ? error.message : String(error),
        };
        emit();
      }
      return { ...state, jobs: [...state.jobs] };
    },

    setView(view) {
      state = {
        ...state,
        view,
        jobs: view === "all" ? [...state.jobs] : state.jobs.filter(isOngoing),
      };
      emit();
      void api.listDownloads().then(replaceJobs);
      return { ...state, jobs: [...state.jobs] };
    },

    async cancel(jobId) {
      try {
        await api.cancelDownload(jobId);
      } catch (error) {
        state = {
          ...state,
          error: error instanceof Error ? error.message : String(error),
        };
        emit();
      }
      return { ...state, jobs: [...state.jobs] };
    },

    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },

    dispose() {
      unsubscribeEvents();
      listeners.clear();
    },
  };
}
