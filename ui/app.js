/* RSS Downloader UI. The host application injects window.RSSDownloaderAPI with the platform adapter. */
(() => {
  const TABS = [
    ["social-downloader", "Social Downloader"],
    ["tamil-movies", "Tamil Movies"],
    ["tamil-dubbed-movies", "Tamil Dubbed Movies"],
  ];
  const api = window.RSSDownloaderAPI;
  const tabsEl = document.querySelector("#tabs");
  const home = document.querySelector("#homeView");
  const downloads = document.querySelector("#downloadsView");
  const urlInput = document.querySelector("#urlInput");
  const analyzeButton = document.querySelector("#analyzeButton");
  const analysisState = document.querySelector("#analysisState");
  const mediaOptions = document.querySelector("#mediaOptions");
  const movieSearchArea = document.querySelector("#movieSearchArea");
  const ongoingList = document.querySelector("#ongoingList");
  const historyList = document.querySelector("#historyList");
  const badge = document.querySelector("#downloadBadge");
  const segmented = document.querySelector(".segmented");

  let currentTab = localStorage.getItem("rss-downloader-active-tab") || "social-downloader";
  let tabOrder = loadTabOrder();
  let jobs = [];
  let downloadView = "ongoing";
  let unsubscribe = null;

  function loadTabOrder() {
    try {
      const saved = JSON.parse(localStorage.getItem("rss-downloader-tab-order") || "null");
      if (Array.isArray(saved) && saved.length === TABS.length && saved.every(x => TABS.some(t => t[0] === x))) return saved;
    } catch (_) {}
    return TABS.map(t => t[0]);
  }

  function tabLabel(id) { return TABS.find(t => t[0] === id)?.[1] || id; }
  function ongoing(job) { return ["queued", "running", "paused"].includes(job.status); }
  function esc(value) { return String(value ?? "").replace(/[&<>\"]/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}[c])); }
  function formatBytes(value) {
    if (value == null) return "—";
    const units = ["B", "KB", "MB", "GB", "TB"];
    let n = Number(value), i = 0;
    while (n >= 1024 && i < units.length - 1) { n /= 1024; i++; }
    return `${n < 10 && i ? n.toFixed(1) : Math.round(n)} ${units[i]}`;
  }
  function formatSpeed(value) { return value == null ? "—" : `${formatBytes(value)}/s`; }
  function formatEta(value) { if (value == null) return "—"; const s = Math.max(0, Math.round(value)); return s < 60 ? `${s}s` : `${Math.floor(s/60)}m ${s%60}s`; }

  function renderTabs() {
    tabsEl.innerHTML = tabOrder.map(id => `<button class="tab ${id === currentTab ? "active" : ""}" draggable="true" data-tab="${id}">${esc(tabLabel(id))}</button>`).join("");
    tabsEl.querySelectorAll(".tab").forEach(button => {
      button.addEventListener("click", () => selectTab(button.dataset.tab));
      button.addEventListener("dragstart", e => e.dataTransfer.setData("text/plain", button.dataset.tab));
      button.addEventListener("dragover", e => e.preventDefault());
      button.addEventListener("drop", e => {
        e.preventDefault();
        const from = e.dataTransfer.getData("text/plain");
        const to = button.dataset.tab;
        if (from === to) return;
        tabOrder = tabOrder.filter(x => x !== from);
        tabOrder.splice(tabOrder.indexOf(to), 0, from);
        localStorage.setItem("rss-downloader-tab-order", JSON.stringify(tabOrder));
        if (api?.reorderTabs) api.reorderTabs(tabOrder).catch(() => {});
        renderTabs();
      });
    });
  }

  function selectTab(tab) {
    currentTab = tab;
    localStorage.setItem("rss-downloader-active-tab", tab);
    renderTabs();
    downloads.hidden = true;
    home.hidden = false;
    renderTabContent();
  }

  function renderTabContent() {
    const social = currentTab === "social-downloader";
    urlInput.closest(".hero-card").style.display = social ? "block" : "none";
    if (!social) renderMovieSearch();
    else movieSearchArea.innerHTML = "";
  }

  function renderMovieSearch() {
    movieSearchArea.innerHTML = `<section class="panel"><p class="eyebrow">${esc(tabLabel(currentTab)).toUpperCase()}</p><h2>Search inside this tab</h2><div class="url-row"><input id="movieQuery" placeholder="Search movies…" autocomplete="off"><button id="movieSearchButton" class="primary">Search</button></div><div id="movieResults" class="download-list"></div></section>`;
    const input = document.querySelector("#movieQuery");
    const button = document.querySelector("#movieSearchButton");
    button.addEventListener("click", () => runMovieSearch(input.value));
    input.addEventListener("keydown", e => { if (e.key === "Enter") runMovieSearch(input.value); });
  }

  async function runMovieSearch(query) {
    const resultsEl = document.querySelector("#movieResults");
    query = query.trim();
    if (!query) return;
    resultsEl.innerHTML = `<div class="state">Searching…</div>`;
    try {
      if (!api) throw new Error("RSS Downloader host API is not connected.");
      const result = await api.search({ tab: currentTab, query });
      resultsEl.innerHTML = result.results.length ? result.results.map(item => `<article class="download-card"><img class="thumb" src="${esc(item.thumbnailUrl || "../rss-downloader-logo.png")}" alt=""><div><div class="download-title">${esc(item.title)}</div><div class="meta">${item.year ? esc(item.year) : ""} ${item.qualities?.length ? "• " + esc(item.qualities.join(" / ")) : ""}</div></div><button class="secondary movie-select" data-id="${esc(item.id)}">Select</button></article>`).join("") : `<div class="empty">No results found.</div>`;
    } catch (error) { resultsEl.innerHTML = `<div class="state">${esc(error.message || error)}</div>`; }
  }

  async function analyze() {
    const url = urlInput.value.trim();
    if (!url) return;
    analysisState.hidden = false;
    analysisState.textContent = "Analyzing URL…";
    mediaOptions.hidden = true;
    try {
      if (!api) throw new Error("RSS Downloader host API is not connected.");
      const result = await api.analyzeUrl({ url });
      analysisState.textContent = result.title || result.normalizedUrl;
      mediaOptions.hidden = false;
      mediaOptions.innerHTML = `<div class="meta">Choose an authorized format</div>` + result.mediaOptions.map(option => `<div class="option"><div><strong>${esc(option.kind.toUpperCase())}</strong><div class="meta">${esc(option.format)}${option.quality ? " • " + esc(option.quality) : ""}${option.sizeBytes ? " • " + formatBytes(option.sizeBytes) : ""}</div></div><button class="primary download-option" data-request="${esc(result.requestId)}" data-option="${esc(option.id)}">Download</button></div>`).join("");
    } catch (error) { analysisState.textContent = error.message || String(error); }
  }

  async function refreshDownloads() {
    if (!api) { renderJobs(); return; }
    try { jobs = (await api.listDownloads()).jobs; renderJobs(); } catch (error) { document.querySelector("#downloadsState").hidden = false; document.querySelector("#downloadsState").textContent = error.message || String(error); }
  }

  function renderJobs() {
    const active = jobs.filter(ongoing).sort((a,b) => b.updatedAt - a.updatedAt);
    const all = [...jobs].sort((a,b) => b.updatedAt - a.updatedAt);
    badge.textContent = active.length;
    ongoingList.innerHTML = active.length ? active.map(jobCard).join("") : `<div class="empty">No ongoing downloads.</div>`;
    historyList.innerHTML = all.length ? all.map(jobCard).join("") : `<div class="empty">No downloads yet.</div>`;
    document.querySelector("#historySection").style.display = downloadView === "all" ? "block" : "none";
    document.querySelectorAll(".cancel-job").forEach(b => b.addEventListener("click", () => cancelJob(b.dataset.id)));
  }

  function jobCard(job) {
    const percent = Number.isFinite(job.progressPercent) ? Math.max(0, Math.min(100, job.progressPercent)) : null;
    const meta = [job.mediaKind, job.quality, job.format].filter(Boolean).join(" • ");
    const bytes = job.totalBytes != null ? `${formatBytes(job.downloadedBytes || 0)} / ${formatBytes(job.totalBytes)}` : formatBytes(job.downloadedBytes);
    const stateMeta = `${esc(job.status)} • ${esc(meta || "media")} • ${esc(bytes)}`;
    return `<article class="download-card"><img class="thumb" src="${esc(job.thumbnailUrl || "../rss-downloader-logo.png")}" alt=""><div><div class="download-title">${esc(job.title || job.outputName || "RSS Download")}</div><div class="meta">${stateMeta} • ${esc(formatSpeed(job.speedBytesPerSecond))} • ETA ${esc(formatEta(job.etaSeconds))}</div><div class="progress"><i style="width:${percent == null ? 0 : percent}%"></i></div>${job.error ? `<div class="meta">${esc(job.error)}</div>` : ""}${ongoing(job) ? `<button class="cancel cancel-job" data-id="${esc(job.jobId)}">Cancel</button>` : ""}</div><div class="percent">${percent == null ? "—" : percent + "%"}</div></article>`;
  }

  async function cancelJob(jobId) { if (!api) return; await api.cancelDownload(jobId).catch(() => {}); await refreshDownloads(); }

  document.querySelector("#downloadsButton").addEventListener("click", async () => {
    home.hidden = true; downloads.hidden = false; await refreshDownloads();
  });
  document.querySelector("#refreshDownloads").addEventListener("click", refreshDownloads);
  document.querySelectorAll(".segmented button").forEach(button => button.addEventListener("click", () => {
    downloadView = button.dataset.view;
    document.querySelectorAll(".segmented button").forEach(b => b.classList.toggle("active", b === button));
    renderJobs();
  }));
  analyzeButton.addEventListener("click", analyze);
  urlInput.addEventListener("keydown", e => { if (e.key === "Enter") analyze(); });

  async function readClipboardUrl() {
    try {
      const text = await navigator.clipboard.readText();
      if (/^https?:\/\//i.test(text.trim()) && text.trim() !== urlInput.value.trim()) { urlInput.value = text.trim(); analyze(); }
    } catch (_) {}
  }
  document.addEventListener("visibilitychange", () => { if (!document.hidden) readClipboardUrl(); });
  setTimeout(readClipboardUrl, 500);

  if (api?.getEventBus) {
    unsubscribe = api.getEventBus().subscribe(event => {
      const index = jobs.findIndex(j => j.jobId === event.job.jobId);
      if (index >= 0) jobs[index] = event.job; else jobs.push(event.job);
      renderJobs();
    });
  }

  renderTabs();
  renderTabContent();
  renderJobs();
  window.addEventListener("beforeunload", () => unsubscribe?.());
})();
