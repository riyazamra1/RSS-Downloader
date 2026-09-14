import type {
  AnalyzeUrlResponse,
  DownloadJob,
  SearchResponse,
} from "../../core/contracts/api";
import type { RayAdapter } from "../worker";

/** Development adapter only. No provider-specific acquisition is implemented here. */
export class MockRayAdapter implements RayAdapter {
  readonly provider = "mock";

  async analyze(input: Record<string, unknown>): Promise<AnalyzeUrlResponse> {
    const url = String(input.url ?? "").trim();
    return {
      requestId: crypto.randomUUID(),
      normalizedUrl: url,
      mediaOptions: [
        { id: "mock-video", kind: "video", format: "mp4", quality: "best" },
        { id: "mock-audio", kind: "audio", format: "m4a", quality: "best" },
      ],
    };
  }

  async search(input: Record<string, unknown>): Promise<SearchResponse> {
    return {
      tab: input.tab as SearchResponse["tab"],
      query: String(input.query ?? ""),
      results: [],
    };
  }

  async download(_input: Record<string, unknown>, job: DownloadJob): Promise<DownloadJob> {
    return {
      ...job,
      status: "completed",
      progressPercent: 100,
    };
  }
}
