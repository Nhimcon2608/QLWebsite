// Task 2's two adapters share the REST contract in docs/TASK_1_PLAN.md.
export const DEMO = document.body.dataset.demo === "true";
const KEY = "qlwebsite.demo.v1";
let redirectingToLogin = false;
const iso = (n) => new Date(n).toISOString();
const pageOf = (items, page = 0, size = 8) => ({
  content: items.slice(page * size, (page + 1) * size),
  totalElements: items.length,
  totalPages: Math.ceil(items.length / size),
  number: page,
});
export class ApiError extends Error {
  constructor(message, status = 400, fieldErrors = {}) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}
function seed() {
  const now = Date.now();
  const definitions = [
    [
      "Cổng thông tin nội bộ",
      "https://portal.company.vn",
      "UP",
      142,
      "Cổng thông tin dành cho nhân viên.",
    ],
    [
      "Hệ thống nhân sự",
      "https://hr.company.vn",
      "UP",
      218,
      "Quản lý nhân sự và chấm công.",
    ],
    [
      "Quản lý công việc",
      "https://projects.company.vn",
      "UP",
      186,
      "Không gian làm việc của các nhóm.",
    ],
    [
      "Dịch vụ API",
      "https://api.company.vn",
      "DOWN",
      null,
      "API dùng chung cho các ứng dụng nội bộ.",
    ],
    [
      "Kho tài liệu",
      "https://docs.company.vn",
      "SLOW",
      2450,
      "Tài liệu và hướng dẫn vận hành.",
    ],
    [
      "Hệ thống kế toán",
      "https://finance.company.vn",
      "UP",
      164,
      "Báo cáo và nghiệp vụ kế toán.",
    ],
    [
      "Môi trường thử nghiệm",
      "https://staging.company.vn",
      "UNKNOWN",
      null,
      "Tạm dừng trong thời gian bảo trì.",
    ],
    [
      "Trung tâm hỗ trợ",
      "https://support.company.vn",
      "UP",
      192,
      "Tiếp nhận yêu cầu hỗ trợ nội bộ.",
    ],
  ];
  const websites = definitions.map(
    ([name, url, currentStatus, responseTimeMs, description], i) => ({
      id: String(i + 1),
      name,
      url,
      description,
      currentStatus,
      responseTimeMs,
      enabled: i !== 6,
      intervalSeconds: 30,
      timeoutMs: 5000,
      slowThresholdMs: 2000,
      failureThreshold: 3,
      expectedCodes: "200-299",
      consecutiveFailures: i === 3 ? 3 : 0,
      lastCheckedAt: i === 6 ? null : iso(now - 12000),
      createdAt: iso(now - 7 * 86400000),
      deletedAt: null,
      version: 0,
    }),
  );
  const history = websites.flatMap((w, index) =>
    w.lastCheckedAt
      ? Array.from({ length: 337 }, (_, j) => {
          const completedAt = now - 12000 - j * 1800000;
          const failure =
            (index === 3 && j < 3) || (j > 0 && (j + index * 11) % 67 === 0);
          const latency = failure
            ? null
            : Math.round(
                (index === 4 ? 2100 : 120 + index * 13) +
                  (Math.sin(j * 0.7 + index) + 1) * 44,
              );
          return {
            id: `${w.id}-${j}`,
            websiteId: w.id,
            completedAt: iso(completedAt),
            success: !failure,
            httpCode: failure ? null : 200,
            responseTimeMs: j === 0 ? w.responseTimeMs : latency,
            elapsedMs: failure ? 5000 : latency,
            errorType: failure ? "TIMEOUT" : null,
            errorMessage: failure
              ? "Không nhận được phản hồi trong 5.000 ms."
              : null,
            triggerType: "SCHEDULED",
          };
        })
      : [],
  );
  const incidents = [
    {
      id: "incident-1",
      websiteId: "4",
      firstFailureAt: iso(now - 3612000),
      detectedAt: iso(now - 12000),
      resolvedAt: null,
      reason: "TIMEOUT",
      status: "OPEN",
    },
  ];
  const alerts = [
    {
      id: "alert-1",
      websiteId: "4",
      websiteName: websites[3].name,
      eventType: "DOWN",
      message: "Không phản hồi sau 3 lần kiểm tra liên tiếp.",
      createdAt: iso(now - 12000),
    },
    {
      id: "alert-2",
      websiteId: "2",
      websiteName: websites[1].name,
      eventType: "RECOVERY",
      message: "Đã kết nối trở lại. Website hoạt động bình thường.",
      createdAt: iso(now - 7200000),
    },
  ];
  return { websites, history, incidents, alerts };
}
function read() {
  try {
    const saved = localStorage.getItem(KEY);
    if (saved) return JSON.parse(saved);
  } catch {
    throw new ApiError(
      "Không thể đọc dữ liệu demo. Kiểm tra quyền lưu trữ của trình duyệt.",
    );
  }
  const data = seed();
  write(data);
  return data;
}
function write(data) {
  try {
    localStorage.setItem(KEY, JSON.stringify(data));
  } catch {
    throw new ApiError(
      "Không thể lưu dữ liệu demo. Bộ nhớ trình duyệt đã đầy hoặc bị chặn.",
    );
  }
}
function decorate(w) {
  return {
    ...w,
    stale: !!(
      w.enabled &&
      w.lastCheckedAt &&
      Date.now() - Date.parse(w.lastCheckedAt) >
        w.intervalSeconds * 2000 + w.timeoutMs
    ),
  };
}
export function validateWebsite(input) {
  const errors = {};
  if (!input.name?.trim() || input.name.trim().length > 100)
    errors.name = "Nhập tên website từ 1 đến 100 ký tự.";
  try {
    const u = new URL(input.url);
    if (
      !["http:", "https:"].includes(u.protocol) ||
      !u.hostname ||
      u.username ||
      u.password
    )
      throw Error();
  } catch {
    errors.url =
      "Nhập URL HTTP/HTTPS hợp lệ, không chứa tài khoản hoặc mật khẩu.";
  }
  for (const [field, min, max, label] of [
    ["intervalSeconds", 10, 86400, "Chu kỳ"],
    ["timeoutMs", 100, 60000, "Timeout"],
    ["slowThresholdMs", 1, 59999, "Ngưỡng chậm"],
    ["failureThreshold", 1, 10, "Ngưỡng lỗi"],
  ]) {
    if (
      !Number.isInteger(input[field]) ||
      input[field] < min ||
      input[field] > max
    )
      errors[field] = `${label} phải là số nguyên từ ${min} đến ${max}.`;
  }
  if (input.slowThresholdMs >= input.timeoutMs)
    errors.slowThresholdMs = "Ngưỡng chậm phải nhỏ hơn timeout.";
  if (!validCodes(input.expectedCodes || ""))
    errors.expectedCodes = "Ví dụ: 200-299, 301. Mã HTTP nằm trong 100–599.";
  if ((input.description || "").length > 500)
    errors.description = "Mô tả tối đa 500 ký tự.";
  return errors;
}
function validCodes(value) {
  return (
    value.trim() &&
    value.split(",").every((part) => {
      const m = part.trim().match(/^(\d{3})(?:-(\d{3}))?$/);
      return (
        m && +m[1] >= 100 && +(m[2] || m[1]) <= 599 && +m[1] <= +(m[2] || m[1])
      );
    })
  );
}
function filteredHistory(data, id, options = {}) {
  return data.history
    .filter(
      (h) =>
        h.websiteId === id &&
        (!options.from || h.completedAt >= options.from) &&
        (!options.to || h.completedAt < options.to) &&
        (!options.result || h.success === (options.result === "SUCCESS")),
    )
    .sort((a, b) => b.completedAt.localeCompare(a.completedAt));
}
function metrics(data, id, options) {
  const samples = filteredHistory(data, id, options);
  const step =
    options.bucket === "5m"
      ? 300000
      : options.bucket === "1h"
        ? 3600000
        : 1800000;
  const from = Date.parse(options.from),
    to = Date.parse(options.to);
  const points = [];
  for (let start = from; start < to; start += step) {
    const bucket = samples.filter(
      (s) =>
        Date.parse(s.completedAt) >= start &&
        Date.parse(s.completedAt) < start + step,
    );
    const latencies = bucket.filter((s) => s.responseTimeMs !== null);
    points.push({
      at: iso(start),
      responseTimeMs: latencies.length
        ? Math.round(
            latencies.reduce((n, s) => n + s.responseTimeMs, 0) /
              latencies.length,
          )
        : null,
      sampleCount: bucket.length,
      failureCount: bucket.filter((s) => !s.success).length,
    });
  }
  const responses = samples.filter((s) => s.responseTimeMs !== null);
  const successCount = samples.filter((s) => s.success).length;
  return {
    from: options.from,
    to: options.to,
    sampleCount: samples.length,
    successCount,
    successRate: samples.length ? (successCount / samples.length) * 100 : null,
    averageResponseTimeMs: responses.length
      ? Math.round(
          responses.reduce((n, s) => n + s.responseTimeMs, 0) /
            responses.length,
        )
      : null,
    points,
  };
}
async function demoRequest(path, method, body, options) {
  const data = read();
  const match = path.match(/^\/websites\/([^/]+)(?:\/(.+))?$/);
  const id = match?.[1],
    action = match?.[2];
  const w = id && data.websites.find((w) => w.id === id && !w.deletedAt);
  if (id && !w)
    throw new ApiError("Website không tồn tại hoặc đã được lưu trữ.", 404);
  if (path === "/dashboard/summary") {
    const all = data.websites.filter((w) => !w.deletedAt).map(decorate),
      active = all.filter((w) => w.enabled);
    const samples = data.history.filter(
      (h) =>
        active.some((w) => w.id === h.websiteId) &&
        h.completedAt >= options.from &&
        h.completedAt < options.to,
    );
    const good = samples.filter((s) => s.success).length;
    return {
      total: all.length,
      active: active.length,
      up: active.filter((w) => w.currentStatus === "UP").length,
      down: active.filter((w) => w.currentStatus === "DOWN").length,
      slow: active.filter((w) => w.currentStatus === "SLOW").length,
      unknown: active.filter((w) => w.currentStatus === "UNKNOWN").length,
      paused: all.length - active.length,
      stale: active.filter((w) => w.stale).length,
      archived: data.websites.filter((w) => w.deletedAt).length,
      sampleCount: samples.length,
      successRate: samples.length ? (good / samples.length) * 100 : null,
    };
  }
  if (path === "/alerts")
    return pageOf(
      data.alerts
        .slice()
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
      options.page,
      options.size,
    );
  if (path === "/websites" && method === "GET") {
    const query = (options.search || "").toLocaleLowerCase("vi");
    const list = data.websites
      .filter(
        (w) =>
          !w.deletedAt &&
          (!query ||
            `${w.name} ${w.url}`.toLocaleLowerCase("vi").includes(query)) &&
          (!options.status ||
            (options.status === "PAUSED"
              ? !w.enabled
              : w.enabled && w.currentStatus === options.status)),
      )
      .map(decorate);
    return pageOf(list, options.page, options.size);
  }
  if (
    (path === "/websites" && method === "POST") ||
    (w && !action && method === "PUT")
  ) {
    const errors = validateWebsite(body);
    if (
      data.websites.some(
        (item) =>
          !item.deletedAt &&
          item.id !== id &&
          item.url.replace(/\/$/, "") === body.url.replace(/\/$/, ""),
      )
    )
      errors.url = "URL này đã có trong danh sách.";
    if (Object.keys(errors).length)
      throw new ApiError("Vui lòng kiểm tra lại thông tin.", 400, errors);
    if (w && body.version !== w.version)
      throw new ApiError(
        "Website đã thay đổi ở cửa sổ khác. Đóng biểu mẫu và tải lại trang.",
        409,
      );
    if (w) Object.assign(w, body, { version: w.version + 1 });
    else
      data.websites.push({
        ...body,
        id: crypto.randomUUID(),
        enabled: true,
        currentStatus: "UNKNOWN",
        responseTimeMs: null,
        lastCheckedAt: null,
        consecutiveFailures: 0,
        version: 0,
        createdAt: iso(Date.now()),
        deletedAt: null,
      });
    write(data);
    return w || data.websites.at(-1);
  }
  if (w && !action && method === "GET")
    return {
      ...decorate(w),
      latestResult: filteredHistory(data, id)[0] || null,
    };
  if (w && (method === "DELETE" || action === "monitoring")) {
    if (method === "DELETE") {
      w.deletedAt = iso(Date.now());
      w.enabled = false;
    } else {
      w.enabled = body.enabled;
      if (body.enabled) {
        w.currentStatus = "UNKNOWN";
        w.consecutiveFailures = 0;
      }
    }
    w.version++;
    if (!w.enabled)
      data.incidents
        .filter((i) => i.websiteId === id && i.status === "OPEN")
        .forEach((i) =>
          Object.assign(i, {
            status: "MONITORING_STOPPED",
            resolvedAt: iso(Date.now()),
          }),
        );
    write(data);
    return decorate(w);
  }
  if (action === "checks" && method === "POST") {
    if (!w.enabled) throw new ApiError("Bật giám sát trước khi kiểm tra.", 409);
    // A demonstration sample only. No network request is made to the configured URL.
    const failed = w.currentStatus === "DOWN",
      responseTimeMs = failed ? null : w.currentStatus === "SLOW" ? 2450 : 168;
    const result = {
      id: crypto.randomUUID(),
      websiteId: id,
      completedAt: iso(Date.now()),
      success: !failed,
      httpCode: failed ? null : 200,
      responseTimeMs,
      elapsedMs: failed ? w.timeoutMs : responseTimeMs,
      errorType: failed ? "TIMEOUT" : null,
      errorMessage: failed ? "Kết quả timeout mô phỏng." : null,
      triggerType: "MANUAL",
    };
    Object.assign(w, {
      lastCheckedAt: result.completedAt,
      responseTimeMs,
      currentStatus: failed
        ? "DOWN"
        : responseTimeMs > w.slowThresholdMs
          ? "SLOW"
          : "UP",
      consecutiveFailures: failed ? w.consecutiveFailures + 1 : 0,
    });
    data.history.unshift(result);
    write(data);
    return result;
  }
  if (action === "history")
    return pageOf(
      filteredHistory(data, id, options),
      options.page,
      options.size,
    );
  if (action === "metrics") return metrics(data, id, options);
  if (action === "incidents")
    return pageOf(
      data.incidents.filter((i) => i.websiteId === id),
      options.page,
      options.size,
    );
  throw new ApiError("Không tìm thấy dữ liệu.", 404);
}
export async function request(
  path,
  { method = "GET", body, params = {} } = {},
) {
  if (DEMO) return demoRequest(path, method, body, params);
  const query = new URLSearchParams(
    Object.entries(params).filter(([, value]) => value !== "" && value != null),
  );
  const headers = { Accept: "application/json" };
  if (body) headers["Content-Type"] = "application/json";
  const csrf = document.querySelector('meta[name="_csrf"]')?.content;
  if (csrf)
    headers[
      document.querySelector('meta[name="_csrf_header"]')?.content ||
        "X-CSRF-TOKEN"
    ] = csrf;
  let response;
  try {
    response = await fetch(`/api${path}${query.size ? `?${query}` : ""}`, {
      method,
      headers,
      credentials: "same-origin",
      body: body ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(20000),
    });
  } catch {
    throw new ApiError(
      "Không thể kết nối máy chủ. Kiểm tra kết nối và thử lại.",
      0,
    );
  }
  if (response.status === 204) return null;
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    if (
      response.status === 401 &&
      !(path === "/session" && method === "POST") &&
      !redirectingToLogin
    ) {
      redirectingToLogin = true;
      location.assign("/login");
    }
    throw new ApiError(
      data?.message || `Yêu cầu chưa thành công (HTTP ${response.status}).`,
      response.status,
      data?.fieldErrors || {},
    );
  }
  if (!data) throw new ApiError("Máy chủ trả về dữ liệu không hợp lệ.", 502);
  return data;
}
