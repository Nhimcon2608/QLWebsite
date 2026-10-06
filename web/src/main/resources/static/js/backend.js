// Adapt the backend branch's Website / UptimeCheckLog API without creating samples.
export function createBackendAdapter({ raw, ApiError, pageOf, metrics }) {
  const pending = new Map();
  function get(path) {
    if (!pending.has(path)) {
      const promise = raw(path).finally(() => pending.delete(path));
      pending.set(path, promise);
    }
    return pending.get(path);
  }
  const timestamp = (value) => value ? new Date(value).toISOString() : null;
  function log(item) {
    return {
      id: String(item.id), websiteId: String(item.website.id),
      completedAt: timestamp(item.checkedAt), success: item.isUp,
      httpCode: item.statusCode,
      responseTimeMs: item.statusCode == null ? null : item.responseTimeMs,
      elapsedMs: item.responseTimeMs,
      errorType: item.isUp ? null : item.statusCode == null ? "CONNECTION_ERROR" : "HTTP_ERROR",
      errorMessage: item.errorMessage, triggerType: "MANUAL",
    };
  }
  async function logs(id) {
    return (await get(`/websites/${id}/logs`)).map(log)
      .sort((a, b) => b.completedAt.localeCompare(a.completedAt));
  }
  async function website(item) {
    const history = await logs(item.id), latest = history[0];
    const firstSuccess = history.findIndex((entry) => entry.success);
    return {
      id: String(item.id), name: item.name, url: item.url,
      enabled: item.isActive, intervalSeconds: item.checkIntervalSeconds,
      createdAt: timestamp(item.createdAt), description: "",
      currentStatus: latest ? latest.success ? "UP" : "DOWN" : "UNKNOWN",
      responseTimeMs: latest?.responseTimeMs ?? null,
      lastCheckedAt: latest?.completedAt ?? null,
      latestResult: latest ?? null,
      consecutiveFailures: firstSuccess < 0 ? history.length : firstSuccess,
      // Fixed behavior of WebsiteService; these are not editable settings.
      timeoutMs: 10000, failureThreshold: 1, expectedCodes: "200-399",
      slowThresholdMs: null, stale: false, history,
    };
  }
  async function all() {
    return Promise.all((await get("/websites")).map(website));
  }
  function filterHistory(history, params) {
    return history.filter((h) => (!params.from || h.completedAt >= params.from)
      && (!params.to || h.completedAt < params.to)
      && (!params.result || h.success === (params.result === "SUCCESS")));
  }
  function payload(body, current = {}) {
    return { name: body.name, url: body.url,
      checkIntervalSeconds: body.intervalSeconds,
      isActive: body.enabled ?? current.isActive ?? true };
  }
  return async function request(path, { method = "GET", body, params = {} } = {}) {
    if (path === "/alerts" || /\/incidents$/.test(path))
      return { ...pageOf([], params.page, params.size), supported: false };
    if (path === "/dashboard/summary") {
      const websites = await all(), active = websites.filter((w) => w.enabled);
      const history = filterHistory(active.flatMap((w) => w.history), params);
      return { total: websites.length, active: active.length,
        up: active.filter((w) => w.currentStatus === "UP").length,
        down: active.filter((w) => w.currentStatus === "DOWN").length,
        unknown: active.filter((w) => w.currentStatus === "UNKNOWN").length,
        slow: 0, paused: websites.length - active.length, archived: 0, stale: 0,
        sampleCount: history.length,
        successRate: history.length ? history.filter((h) => h.success).length * 100 / history.length : null };
    }
    if (path === "/websites") {
      if (method === "POST") return website(await raw(path, { method, body: payload(body) }));
      const query = (params.search || "").toLocaleLowerCase("vi");
      const result = (await all()).filter((w) =>
        (!query || `${w.name} ${w.url}`.toLocaleLowerCase("vi").includes(query))
        && (!params.status || (params.status === "PAUSED" ? !w.enabled : w.enabled && w.currentStatus === params.status)));
      return pageOf(result, params.page, params.size);
    }
    const match = path.match(/^\/websites\/(\d+)(?:\/(.+))?$/);
    if (!match) throw new ApiError("Chức năng này chưa được hỗ trợ.", 501);
    const [, id, action] = match, base = `/websites/${id}`;
    if (!action && method === "GET") return website(await get(base));
    if (!action && method === "PUT") {
      const current = await get(base);
      return website(await raw(base, { method, body: payload(body, current) }));
    }
    if (action === "monitoring" && method === "PATCH") {
      const current = await get(base);
      return website(await raw(base, { method: "PUT", body: { ...current, isActive: body.enabled } }));
    }
    if (action === "checks" && method === "POST") {
      if (!(await get(base)).isActive) throw new ApiError("Bật website trước khi kiểm tra.", 409);
      return log(await raw(`${base}/check`, { method: "POST" }));
    }
    if (action === "history") return pageOf(filterHistory(await logs(id), params), params.page, params.size);
    if (action === "metrics") return metrics({ history: await logs(id) }, id, params);
    throw new ApiError("Backend chưa hỗ trợ lưu trữ website và giữ lại lịch sử.", 501);
  };
}
