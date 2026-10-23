// Translate the domain API into the dashboard model. Reports/history stay on the server.
export function createBackendAdapter({ raw, ApiError, pageOf }) {
  const pending = new Map();
  function get(path) {
    if (!pending.has(path))
      pending.set(
        path,
        raw(path).finally(() => pending.delete(path)),
      );
    return pending.get(path);
  }
  function log(item) {
    return {
      id: String(item.id),
      websiteId: String(item.website.id),
      completedAt: item.checkedAt,
      success: item.isUp,
      httpCode: item.statusCode,
      responseTimeMs:
        item.statusCode != null || item.isUp ? item.responseTimeMs : null,
      elapsedMs: item.responseTimeMs,
      errorType:
        item.errorType ||
        (item.isUp
          ? null
          : item.statusCode == null
            ? "CONNECTION_ERROR"
            : "HTTP_ERROR"),
      checkType: item.checkType || "HTTP",
      checkDetails: item.checkDetails,
      errorMessage: item.errorMessage,
      triggerType: item.triggerType || "MANUAL",
    };
  }
  async function website(item) {
    const latest = (await get(`/websites/${item.id}/logs`))[0];
    const stale = !!(
      item.isActive &&
      item.lastCheckedAt &&
      Date.now() - Date.parse(item.lastCheckedAt) >
        item.checkIntervalSeconds * 2000 + item.timeoutMs
    );
    return {
      ...item,
      id: String(item.id),
      enabled: item.isActive,
      intervalSeconds: item.checkIntervalSeconds,
      currentStatus: item.maintenanceActive
        ? "MAINTENANCE"
        : stale
          ? "UNKNOWN"
          : item.monitoringStatus || "UNKNOWN",
      responseTimeMs:
        latest && (latest.statusCode != null || latest.isUp)
          ? latest.responseTimeMs
          : null,
      latestResult: latest ? log(latest) : null,
      stale,
    };
  }
  function payload(body, current = {}) {
    return {
      ...current,
      ...body,
      checkIntervalSeconds:
        body.intervalSeconds ?? current.checkIntervalSeconds,
      isActive: body.enabled ?? current.isActive ?? true,
    };
  }
  return async function request(
    path,
    { method = "GET", body, params = {} } = {},
  ) {
    if (path === "/websites") {
      if (method === "POST")
        return website(await raw(path, { method, body: payload(body) }));
      const query = (params.search || "").toLocaleLowerCase("vi");
      const rows = await Promise.all((await get(path)).map(website));
      const filtered = rows.filter(
        (w) =>
          (!query ||
            `${w.name} ${w.url} ${w.groupName} ${w.environment} ${w.tags}`
              .toLocaleLowerCase("vi")
              .includes(query)) &&
          (!params.groupName || w.groupName === params.groupName) &&
          (!params.environment || w.environment === params.environment) &&
          (!params.status ||
            (params.status === "PAUSED"
              ? !w.enabled
              : w.enabled && w.currentStatus === params.status)),
      );
      return {
        ...pageOf(filtered, params.page, params.size),
        groups: [
          ...new Set(rows.map((w) => w.groupName).filter(Boolean)),
        ].sort(),
        environments: [
          ...new Set(rows.map((w) => w.environment).filter(Boolean)),
        ].sort(),
      };
    }
    const match = path.match(/^\/websites\/(\d+)(?:\/(.+))?$/);
    if (!match) return raw(path, { method, body, params });
    const [, id, action] = match,
      base = `/websites/${id}`;
    if (!action && method === "GET") return website(await get(base));
    if (!action && method === "PUT")
      return website(
        await raw(base, { method, body: payload(body, await get(base)) }),
      );
    if (!action && method === "DELETE") return raw(base, { method });
    if (action === "monitoring" && method === "PATCH")
      return website(
        await raw(base, {
          method: "PUT",
          body: { ...(await get(base)), isActive: body.enabled },
        }),
      );
    if (action === "checks" && method === "POST")
      return log(await raw(`${base}/check`, { method: "POST" }));
    return raw(path, { method, body, params });
  };
}
