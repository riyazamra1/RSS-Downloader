import type { DownloaderTab, SearchResponse } from "../../core/contracts/api";
import type { RayAdapter } from "../worker";

/**
 * Host/provider bridge for authorized movie catalogs.
 *
 * The adapter deliberately does not embed a third-party site, scraper,
 * authentication material, DRM bypass, or access-control bypass. A host may
 * register an authorized catalog implementation through the callbacks.
 */
export interface CatalogProviderCallbacks {
  search(tab: DownloaderTab, query: string): Promise<SearchResponse["results"]>;
}

export class CatalogRayAdapter implements RayAdapter {
  readonly provider = "catalog";

  constructor(private readonly callbacks: CatalogProviderCallbacks) {}

  async analyze(): Promise<never> {
    throw new Error("Catalog adapter does not analyze arbitrary URLs.");
  }

  async search(input: Record<string, unknown>): Promise<SearchResponse> {
    const tab = input.tab as DownloaderTab;
    const query = String(input.query ?? "").trim();
    if (!query) return { tab, query: "", results: [] };
    return { tab, query, results: await this.callbacks.search(tab, query) };
  }

  async download(): Promise<never> {
    throw new Error("Catalog search results must provide an authorized download adapter.");
  }
}
