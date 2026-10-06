import { DEMO, BACKEND, request, validateWebsite } from "./data.js";
import { responseChart, bindChart } from "./chart.js";

const paths = {
  pulse: '<path d="M2 12h5l3-8 4 16 3-8h5"/>',
  grid: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
  globe:
    '<circle cx="12" cy="12" r="9"/><ellipse cx="12" cy="12" rx="4" ry="9"/><path d="M3 12h18"/>',
  bell: '<path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  refresh:
    '<path d="M20 10a8 8 0 0 0-14-4L3 9m0-6v6h6M4 14a8 8 0 0 0 14 4l3-3m0 6v-6h-6"/>',
  check: '<path d="m5 12 4 4L19 6"/>',
  down: '<path d="M6 6l12 12M18 6 6 18"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  arrow: '<path d="M5 12h14m-5-5 5 5-5 5"/>',
  left: '<path d="m14 6-6 6 6 6"/>',
  right: '<path d="m10 6 6 6-6 6"/>',
  pause: '<path d="M8 5v14M16 5v14"/>',
  play: '<path d="m8 4 12 8-12 8z"/>',
  search: '<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/>',
  edit: '<path d="m15 4 5 5M4 20l5-1L21 7a2 2 0 0 0-4-4L5 15z"/>',
  archive:
    '<rect x="3" y="3" width="18" height="4" rx="1"/><path d="M5 7v14h14V7M10 11h4"/>',
  external: '<path d="M14 3h7v7m0-7L10 14M10 3H3v18h18v-7"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7h.01"/>',
  menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
  logout: '<path d="M9 3H3v18h6M8 12h13m-5-5 5 5-5 5"/>',
  server:
    '<rect x="3" y="3" width="18" height="7" rx="2"/><rect x="3" y="14" width="18" height="7" rx="2"/><path d="M7 6.5h.01M7 17.5h.01"/>',
  shield:
    '<path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6z"/><path d="m8 12 3 3 5-6"/>',
};
const icon = (name) =>
  `<svg class="icon" viewBox="0 0 24 24" aria-hidden="true">${paths[name] || paths.info}</svg>`;
const esc = (value) =>
  String(value ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
const fmt = (n) =>
  n == null
    ? "—"
    : new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 1 }).format(n);
const date = (value) =>
  value
    ? new Date(value).toLocaleString("vi-VN", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
      })
    : "Chưa kiểm tra";
const time = (value) =>
  value
    ? new Date(value).toLocaleTimeString("vi-VN", {
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
      })
    : "—";
const relative = (value) => {
  if (!value) return "Chưa kiểm tra";
  const seconds = Math.max(
    0,
    Math.floor((Date.now() - Date.parse(value)) / 1000),
  );
  return seconds < 60
    ? `${seconds} giây trước`
    : seconds < 3600
      ? `${Math.floor(seconds / 60)} phút trước`
      : seconds < 86400
        ? `${Math.floor(seconds / 3600)} giờ trước`
        : `${Math.floor(seconds / 86400)} ngày trước`;
};
const websiteLink = (id) => `/websites/${encodeURIComponent(id)}`;
const safeUrl = (value) => {
  try {
    const url = new URL(value);
    return ["http:", "https:"].includes(url.protocol) ? esc(url.href) : "#";
  } catch {
    return "#";
  }
};
const host = (value) => {
  try {
    return new URL(value).host;
  } catch {
    return value;
  }
};
const statusNames = {
  UP: "Hoạt động",
  DOWN: "Ngừng hoạt động",
  SLOW: "Phản hồi chậm",
  UNKNOWN: "Chưa xác định",
  PAUSED: "Tạm dừng",
};
function badge(w) {
  const status = w.enabled === false ? "PAUSED" : w.currentStatus;
  return `<span class="status ${status?.toLowerCase()}">${icon({ UP: "check", DOWN: "down", SLOW: "clock", UNKNOWN: "info", PAUSED: "pause" }[status])}${statusNames[status] || "Chưa xác định"}</span>`;
}
const app = document.querySelector("#app"),
  modal = document.querySelector("#modal");
const pathname = location.pathname.replace(/\/$/, "") || "/";
const detailId = pathname.match(/^\/websites\/([^/]+)$/)?.[1];
const view = detailId
  ? "detail"
  : pathname === "/websites"
    ? "websites"
    : pathname === "/alerts"
      ? "alerts"
      : pathname === "/login"
        ? "login"
        : "dashboard";
const state = {
  search: "",
  status: "",
  page: 0,
  range: "24h",
  historyPage: 0,
  historyResult: "",
  incidentPage: 0,
  chartId: "",
  busy: false,
  refreshing: false,
  reloadQueued: false,
  data: null,
  timer: null,
  updatedAt: null,
};
function rangeParams() {
  const to = new Date();
  return {
    from: new Date(
      to.getTime() -
        { "1h": 3600000, "24h": 86400000, "7d": 604800000 }[state.range],
    ).toISOString(),
    to: to.toISOString(),
    bucket: state.range === "1h" ? "5m" : state.range === "7d" ? "1h" : "30m",
  };
}
function toast(message) {
  const element = document.createElement("div");
  element.className = "toast";
  element.innerHTML = `${icon("info")}<span>${esc(message)}</span><button aria-label="Đóng thông báo">×</button>`;
  document.querySelector("#toasts").append(element);
  element.querySelector("button").onclick = () => element.remove();
  setTimeout(() => element.remove(), 6000);
}
function shell() {
  const active = view === "detail" ? "websites" : view;
  const title = {
    dashboard: "Tổng quan",
    websites: "Quản lý website",
    detail: "Chi tiết website",
    alerts: "Cảnh báo",
  }[view];
  document.title = `${title} · QLWebsite`;
  app.innerHTML = `<button class="mobile-shade" aria-label="Đóng điều hướng"></button><aside class="sidebar" id="navigation"><a class="brand" href="/"> <span class="brand-mark">${icon("pulse")}</span>QLWebsite</a><div class="workspace"><span class="workspace-icon">${icon("server")}</span><div><strong>Không gian nội bộ</strong><span class="muted">Website monitoring</span></div></div><div class="nav-label">KHÔNG GIAN LÀM VIỆC</div><nav class="nav" aria-label="Điều hướng chính">${[
    ["dashboard", "/", "grid", "Tổng quan"],
    ["websites", "/websites", "globe", "Quản lý website"],
    ["alerts", "/alerts", "bell", "Cảnh báo"],
  ]
    .map(
      ([name, url, symbol, label]) =>
        `<a href="${url}" class="${active === name ? "active" : ""}" ${active === name ? 'aria-current="page"' : ""}>${icon(symbol)}${label}</a>`,
    )
    .join(
      "",
    )}</nav><div class="sidebar-bottom"><div class="account"><span class="avatar">${DEMO ? "DM" : "AD"}</span><div class="account-text"><strong>${DEMO ? "Demo workspace" : BACKEND ? "Dữ liệu cục bộ" : "Quản trị viên"}</strong>${DEMO ? "" : "<small>Không gian nội bộ</small>"}</div><button class="icon-button" id="logout" aria-label="${DEMO ? "Về màn hình bắt đầu" : "Đăng xuất"}" title="${DEMO ? "Màn hình bắt đầu" : "Đăng xuất"}">${icon("logout")}</button></div></div></aside><div class="main-shell"><header class="topbar"><div class="breadcrumb"><button class="icon-button mobile-menu" aria-controls="navigation" aria-expanded="false" aria-label="Mở điều hướng">${icon("menu")}</button><span>Không gian nội bộ</span><span>/</span><b>${title}</b></div><div class="topbar-right"><span class="live-label"><span class="live-dot"></span>${DEMO ? "Dữ liệu demo" : BACKEND ? "Dữ liệu thật · Kiểm tra thủ công" : "Tự cập nhật mỗi 10 giây"}</span><a class="icon-button" href="/alerts" aria-label="Xem cảnh báo">${icon("bell")}</a><span class="avatar">${DEMO ? "DM" : "AD"}</span></div></header><main class="content" id="main" tabindex="-1"><div id="heading"></div><div id="error" role="alert"></div><div id="content"><div class="skeleton-cards">${Array.from({ length: 4 }, () => '<div><p class="skeleton"></p><p class="skeleton"></p></div>').join("")}</div><div class="loading">Đang tải dữ liệu…</div></div><footer class="footer"><span>© ${new Date().getFullYear()} QLWebsite · Không gian giám sát nội bộ</span><span id="last-updated">${icon("refresh")} Đang đồng bộ dữ liệu</span></footer></main></div>`;
  document.querySelector(".mobile-menu").onclick = () => toggleMenu();
  document.querySelector(".mobile-shade").onclick = () => toggleMenu(false);
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") toggleMenu(false);
  });
  document.querySelector("#logout").onclick = async () => {
    try {
      if (!DEMO && !BACKEND) await request("/session", { method: "DELETE" });
      location.assign("/login");
    } catch (e) {
      toast(e.message);
    }
  };
  drawHeading();
}
function toggleMenu(force) {
  const open =
    force ?? !document.querySelector(".sidebar").classList.contains("open");
  document.querySelector(".sidebar").classList.toggle("open", open);
  document.querySelector(".mobile-shade").classList.toggle("visible", open);
  document
    .querySelector(".mobile-menu")
    .setAttribute("aria-expanded", String(open));
}
function drawHeading(w) {
  const headings = {
    dashboard: [
      "TỔNG QUAN HỆ THỐNG",
      "Mọi website. Một góc nhìn.",
      "Theo dõi tình trạng và hiệu suất website trong không gian của bạn.",
    ],
    websites: [
      "QUẢN LÝ TẬP TRUNG",
      "Website của bạn",
      "Thêm, cấu hình và quản lý các website đang được giám sát.",
    ],
    alerts: [
      "NHẬT KÝ THÔNG BÁO",
      "Trung tâm cảnh báo",
      "Theo dõi sự cố và các lần khôi phục kết nối.",
    ],
    detail: [
      "CHI TIẾT WEBSITE",
      w?.name || "Chi tiết website",
      w?.url || "Trạng thái, hiệu suất và lịch sử kiểm tra.",
    ],
  };
  const [eyebrow, title, description] = headings[view];
  document.querySelector("#heading").innerHTML =
    `${view === "detail" ? `<a class="back-link" href="/websites">${icon("left")}Tất cả website</a>` : ""}<div class="page-heading"><div><div class="eyebrow">${eyebrow}</div><h1>${esc(title)}</h1><p>${view === "detail" && w ? `<a href="${safeUrl(w.url)}" target="_blank" rel="noopener noreferrer">${esc(description)} ↗</a>` : esc(description)}</p></div><div class="actions"><button class="button" data-action="refresh">${icon("refresh")}Làm mới</button>${view === "detail" && w ? `<button class="button" data-action="edit" data-id="${esc(w.id)}">${icon("edit")}Chỉnh sửa</button><button class="button primary" data-action="check" data-id="${esc(w.id)}" ${w.enabled ? "" : "disabled"}>${icon("pulse")}${DEMO ? "Kiểm tra demo" : "Kiểm tra ngay"}</button>` : view !== "alerts" ? `<button class="button primary" data-action="add">${icon("plus")}Thêm website</button>` : ""}</div></div>`;
}
function stat(label, value, suffix, foot, symbol, highlight = false) {
  return `<div class="stat ${highlight ? "highlight" : ""}"><div class="stat-label">${label}<span class="stat-icon">${icon(symbol)}</span></div><div class="stat-value mono">${value}<small>${suffix}</small></div><div class="stat-foot">${foot}</div></div>`;
}
function rangeButtons() {
  return `<div class="segments" role="group" aria-label="Khoảng thời gian">${[
    ["1h", "1 giờ"],
    ["24h", "24 giờ"],
    ["7d", "7 ngày"],
  ]
    .map(
      ([value, label]) =>
        `<button data-range="${value}" class="${state.range === value ? "active" : ""}" aria-pressed="${state.range === value}">${label}</button>`,
    )
    .join("")}</div>`;
}
function chartPanel(metrics, websites) {
  return `<section class="panel"><div class="panel-header"><div><h2>Thời gian phản hồi</h2><p>${websites ? "Hiệu suất website theo thời gian" : "Mỗi điểm là phản hồi trung bình trong một khoảng"}</p></div><div class="chart-header">${websites ? `<label class="sr-only" for="chart-website">Website trên biểu đồ</label><select id="chart-website" style="width:190px;font-size:11px;min-height:32px;padding:5px 9px" ${websites.length ? "" : "disabled"}>${websites.length ? websites.map((w) => `<option value="${esc(w.id)}" ${state.chartId === w.id ? "selected" : ""}>${esc(w.name)}</option>`).join("") : "<option>Chưa có website</option>"}</select>` : '<span class="chart-key">Phản hồi trung bình</span>'}${rangeButtons()}</div></div><div class="chart-body">${responseChart(metrics)}</div><div class="chart-footer"><span><strong>${fmt(metrics.sampleCount)}</strong> mẫu · ${esc(date(metrics.from))} — ${esc(date(metrics.to))}</span><span>Thành công: <strong>${fmt(metrics.successRate)}${metrics.successRate == null ? "" : "%"}</strong> · Điểm tô đen: có lỗi</span></div></section>`;
}
function empty(title, message, action = "") {
  return `<div class="empty-state">${icon("globe")}<h3>${title}</h3>${message ? `<p>${message}</p>` : ""}${action}</div>`;
}
function pagination(data, kind = "page") {
  return `<div class="pagination"><button class="icon-button" data-page-kind="${kind}" data-page="${data.number - 1}" ${data.number <= 0 ? "disabled" : ""} aria-label="Trang trước">${icon("left")}</button><span>${data.totalPages ? data.number + 1 : 0} / ${data.totalPages}</span><button class="icon-button" data-page-kind="${kind}" data-page="${data.number + 1}" ${data.number + 1 >= data.totalPages ? "disabled" : ""} aria-label="Trang sau">${icon("right")}</button></div>`;
}
function rows(websites, management = false) {
  return websites
    .map(
      (w) =>
        `<tr><td><div class="website-cell"><span class="website-symbol">${esc(
          w.name
            .split(" ")
            .slice(0, 2)
            .map((s) => s[0])
            .join("")
            .toUpperCase(),
        )}</span><div><a class="website-name" href="${websiteLink(w.id)}">${esc(w.name)}</a><span class="website-url">${esc(host(w.url))}</span></div></div></td><td>${badge(w)}${!management ? `<span class="stale" title="${esc(date(w.lastCheckedAt))}">${relative(w.lastCheckedAt)}</span>` : ""}${w.stale ? '<span class="stale">Dữ liệu quá cũ</span>' : ""}${w.enabled && w.consecutiveFailures > 0 && w.currentStatus !== "DOWN" ? `<span class="stale">Lỗi ${w.consecutiveFailures}/${w.failureThreshold}</span>` : ""}</td><td class="latency">${fmt(w.responseTimeMs)} ${w.responseTimeMs == null ? "" : "<small>ms</small>"}</td>${management ? `<td class="tiny muted" title="${esc(date(w.lastCheckedAt))}">${relative(w.lastCheckedAt)}</td><td><div class="row-actions"><button class="icon-button" data-action="check" data-id="${esc(w.id)}" aria-label="Kiểm tra ${esc(w.name)}" title="${DEMO ? "Kiểm tra mô phỏng" : "Kiểm tra ngay"}" ${w.enabled ? "" : "disabled"}>${icon("pulse")}</button><button class="icon-button" data-action="toggle" data-id="${esc(w.id)}" aria-label="${w.enabled ? "Tạm dừng" : "Bật"} ${esc(w.name)}" title="${w.enabled ? "Tạm dừng" : "Bật giám sát"}">${icon(w.enabled ? "pause" : "play")}</button><button class="icon-button" data-action="edit" data-id="${esc(w.id)}" aria-label="Sửa ${esc(w.name)}" title="Chỉnh sửa">${icon("edit")}</button><button class="icon-button" data-action="archive" data-id="${esc(w.id)}" aria-label="Lưu trữ ${esc(w.name)}" title="Lưu trữ">${icon("archive")}</button></div></td>` : `<td><a class="icon-button" href="${websiteLink(w.id)}" aria-label="Chi tiết ${esc(w.name)}">${icon("right")}</a></td>`}</tr>`,
    )
    .join("");
}
function alertItems(alerts) {
  return alerts
    .map(
      (a) =>
        `<article class="alert-item"><span class="alert-icon ${a.eventType === "DOWN" ? "dark" : ""}">${icon(a.eventType === "DOWN" ? "down" : "check")}</span><div><h3><a href="${websiteLink(a.websiteId)}">${esc(a.websiteName)}</a> · ${a.eventType === "DOWN" ? "Ngừng hoạt động" : "Đã phục hồi"}</h3><p>${esc(a.message)}</p><time datetime="${esc(a.createdAt)}" title="${esc(date(a.createdAt))}">${relative(a.createdAt)}</time></div></article>`,
    )
    .join("");
}
function renderDashboard(data) {
  const { summary: s, websites, alerts, metrics } = data;
  return `${s.down ? `<div class="notice">${icon("info")}<div><strong>${s.down} website đang ngừng hoạt động</strong><p>Kiểm tra chi tiết để xem kết quả và nguyên nhân gần nhất.</p></div><a class="text-button" href="/websites?status=DOWN">Xem website ${icon("arrow")}</a></div>` : ""}<div class="stats">${stat("Tổng website", fmt(s.total), "website", `${s.active} đang giám sát · ${s.paused} tạm dừng`, "globe")}${stat("Đang hoạt động", fmt(s.up), "website", `${icon("check")} Trạng thái đã xác nhận: UP`, "check", true)}${stat("Cần chú ý", fmt(s.down + s.slow), "website", `${s.down} ngừng hoạt động · ${s.slow} phản hồi chậm`, "pulse")}${stat("Kiểm tra thành công", fmt(s.successRate), s.successRate == null ? "" : "%", `${fmt(s.sampleCount)} mẫu trong ${state.range === "1h" ? "1 giờ" : state.range === "7d" ? "7 ngày" : "24 giờ"}`, "shield")}</div>${chartPanel(metrics, websites.content)}<div class="dashboard-bottom"><section class="panel"><div class="panel-header"><div><h2>Website đang theo dõi</h2><p>Trạng thái mới nhất của các website nội bộ</p></div><a class="text-button" href="/websites">Tất cả ${icon("arrow")}</a></div>${websites.content.length ? `<div class="table-wrap"><table><thead><tr><th>Website</th><th>Trạng thái</th><th>Phản hồi</th><th><span class="sr-only">Chi tiết</span></th></tr></thead><tbody>${rows(websites.content.slice(0, 5))}</tbody></table></div>` : empty("Bắt đầu với website đầu tiên", "Thêm một website để thiết lập không gian giám sát.", `<button class="button primary" data-action="add">${icon("plus")}Thêm website</button>`)}<div class="table-footer"><span>${Math.min(websites.content.length, 5)} / ${websites.totalElements} website</span><span>${s.unknown} chưa xác định · ${s.stale} dữ liệu cũ</span></div></section><section class="panel"><div class="panel-header"><div><h2>Cảnh báo gần đây</h2><p>Những thay đổi cần quan tâm</p></div>${icon("bell")}</div><div class="alert-list">${alerts.content.length ? alertItems(alerts.content.slice(0, 3)) : empty("Chưa có cảnh báo", "Các thông báo mới sẽ xuất hiện tại đây.")}</div><div class="alert-summary">${icon("info")}${DEMO ? "Thông báo từ dữ liệu mô phỏng" : "Cảnh báo khi DOWN hoặc phục hồi"}</div><div class="table-footer"><a class="text-button" href="/alerts">Xem tất cả cảnh báo ${icon("arrow")}</a></div></section></div>`;
}
function renderWebsites(data) {
  const list = data.websites,
    s = data.summary;
  return `<div class="stats">${stat("Tất cả website", fmt(s.total), "website", "Danh sách website chưa lưu trữ", "globe")}${stat("Đang giám sát", fmt(s.active), "website", `${s.up} hoạt động · ${s.slow} phản hồi chậm`, "pulse", true)}${stat("Ngừng hoạt động", fmt(s.down), "website", "Cần kiểm tra và xử lý sự cố", "info")}${stat("Tạm dừng", fmt(s.paused), "website", `${s.unknown} chưa xác định · ${s.archived} đã lưu trữ`, "pause")}</div><section class="panel"><div class="panel-header"><div><h2>Danh sách website</h2><p>Quản lý cấu hình và trạng thái giám sát</p></div><span class="tiny muted">${list.totalElements} website</span></div><div class="filter-bar"><div class="search">${icon("search")}<label class="sr-only" for="website-search">Tìm website</label><input class="input" id="website-search" type="search" placeholder="Tìm theo tên hoặc URL…" value="${esc(state.search)}"></div><label class="sr-only" for="status-filter">Lọc trạng thái</label><select id="status-filter"><option value="">Tất cả trạng thái</option>${Object.entries(
    statusNames,
  )
    .map(
      ([value, label]) =>
        `<option value="${value}" ${state.status === value ? "selected" : ""}>${label}</option>`,
    )
    .join(
      "",
    )}</select></div>${list.content.length ? `<div class="table-wrap"><table><thead><tr><th>Website</th><th>Trạng thái</th><th>Phản hồi</th><th>Kiểm tra gần nhất</th><th style="text-align:right">Thao tác</th></tr></thead><tbody>${rows(list.content, true)}</tbody></table></div>` : empty(state.search || state.status ? "Không tìm thấy website" : "Chưa có website nào", state.search || state.status ? "Thử thay đổi từ khóa hoặc bộ lọc." : "Thêm website đầu tiên để bắt đầu.", `<button class="button" data-action="${state.search || state.status ? "clear-filters" : "add"}">${state.search || state.status ? "Xóa bộ lọc" : "Thêm website"}</button>`)}<div class="table-footer"><span>${list.totalElements ? `${list.number * 8 + 1}–${Math.min((list.number + 1) * 8, list.totalElements)} / ${list.totalElements} website` : "0 website"}</span>${pagination(list)}</div></section>`;
}
function renderHistory(history) {
  return `<section class="panel"><div class="panel-header"><div><h2>Lịch sử kiểm tra</h2><p>Kết quả trong khoảng thời gian đã chọn trên biểu đồ</p></div><label class="sr-only" for="history-result">Lọc kết quả kiểm tra</label><select id="history-result" style="width:155px"><option value="">Tất cả kết quả</option><option value="SUCCESS" ${state.historyResult === "SUCCESS" ? "selected" : ""}>Thành công</option><option value="FAILURE" ${state.historyResult === "FAILURE" ? "selected" : ""}>Thất bại</option></select></div>${history.content.length ? `<div class="table-wrap"><table><thead><tr><th>Thời điểm</th><th>Kết quả</th><th>HTTP</th><th>Phản hồi</th><th>Chi tiết</th></tr></thead><tbody>${history.content.map((h) => `<tr><td class="tiny mono">${esc(date(h.completedAt))}<span class="website-url">${h.triggerType === "MANUAL" ? "Thủ công" : "Theo lịch"}</span></td><td><span class="status ${h.success ? "" : "down"}">${icon(h.success ? "check" : "down")}${h.success ? "Thành công" : "Thất bại"}</span></td><td class="tiny mono">${h.httpCode ?? "—"}</td><td class="latency">${fmt(h.responseTimeMs)} ${h.responseTimeMs == null ? "" : "<small>ms</small>"}</td><td class="tiny muted" title="${esc(h.errorMessage || "")}">${esc(h.errorType || "—")}${!h.success ? `<span class="website-url">Đã chờ ${fmt(h.elapsedMs)} ms</span>` : ""}</td></tr>`).join("")}</tbody></table></div>` : empty("Chưa có kết quả phù hợp", "Thử chọn khoảng thời gian hoặc bộ lọc khác.")}<div class="table-footer"><span>${fmt(history.totalElements)} kết quả</span>${pagination(history, "historyPage")}</div></section>`;
}
function renderDetail(data) {
  const { website: w, metrics, history, incidents } = data;
  const latest = w.latestResult;
  return `${w.stale ? `<div class="notice">${icon("clock")}<div><strong>Dữ liệu đã quá thời gian cập nhật</strong><p>Trạng thái đang hiển thị là trạng thái được xác nhận gần nhất.</p></div></div>` : ""}${w.enabled && w.consecutiveFailures > 0 ? `<div class="notice">${icon("info")}<div><strong>${w.currentStatus === "DOWN" ? "Website đang ngừng hoạt động" : `Ghi nhận lỗi ${w.consecutiveFailures}/${w.failureThreshold} lần liên tiếp`}</strong><p>${esc(latest?.errorMessage || latest?.errorType || "Xem lịch sử kiểm tra để biết thêm chi tiết.")}</p></div></div>` : ""}<div class="stats">${stat("Trạng thái hiện tại", badge(w), "", `Kiểm tra: ${relative(w.lastCheckedAt)}`, "globe", false)}${stat("Phản hồi gần nhất", fmt(w.responseTimeMs), "ms", `HTTP ${latest?.httpCode ?? "—"} · ${latest ? (latest.success ? "Thành công" : "Thất bại") : "Chưa có kết quả"}`, "pulse", true)}${stat("Phản hồi trung bình", fmt(metrics.averageResponseTimeMs), "ms", "Các mẫu có phản hồi HTTP trong khoảng", "clock")}${stat("Kiểm tra thành công", fmt(metrics.successRate), metrics.successRate == null ? "" : "%", `${fmt(metrics.successCount)} / ${fmt(metrics.sampleCount)} mẫu thành công`, "shield")}</div><div class="detail-grid"><div>${chartPanel(metrics)}${renderHistory(history)}<section class="panel"><div class="panel-header"><div><h2>Lịch sử sự cố</h2><p>Các sự cố đã xác nhận của website</p></div></div>${incidents.content.length ? incidents.content.map((i) => `<div class="incident"><div><strong>${esc(i.reason)}</strong><p>Bắt đầu: ${esc(date(i.firstFailureAt))}</p><p>Phát hiện: ${esc(date(i.detectedAt))}</p>${i.resolvedAt ? `<p>Kết thúc: ${esc(date(i.resolvedAt))}</p>` : ""}</div><span class="status ${i.status === "OPEN" ? "down" : ""}">${i.status === "OPEN" ? "Đang diễn ra" : i.status === "MONITORING_STOPPED" ? "Đã dừng giám sát" : "Đã khôi phục"}</span></div>`).join("") : BACKEND ? empty("Chưa hỗ trợ quản lý sự cố", "Xem kết quả thực tế trong lịch sử kiểm tra phía trên.") : empty("Chưa ghi nhận sự cố", "Các sự cố sẽ hiển thị khi đủ ngưỡng lỗi.")}<div class="table-footer"><span>${incidents.totalElements} sự cố</span>${pagination(incidents, "incidentPage")}</div></section></div><aside><section class="panel"><div class="panel-header"><h2>Cấu hình giám sát</h2><button class="icon-button" data-action="edit" data-id="${esc(w.id)}" aria-label="Sửa cấu hình">${icon("edit")}</button></div><dl class="config-list">${[
    ["Chu kỳ kiểm tra", `${fmt(w.intervalSeconds)} giây`],
    ["Timeout", `${fmt(w.timeoutMs)} ms`],
    ["Ngưỡng chậm", `${fmt(w.slowThresholdMs)} ms`],
    ["Ngưỡng lỗi", `${w.failureThreshold} lần liên tiếp`],
    ["HTTP chấp nhận", w.expectedCodes],
    ["Ngày tạo", date(w.createdAt)],
    ["Giám sát", w.enabled ? "Đang bật" : "Tạm dừng"],
  ]
    .map(
      ([key, value]) =>
        `<div class="config-row"><dt>${key}</dt><dd>${esc(value)}</dd></div>`,
    )
    .join(
      "",
    )}</dl>${w.description ? `<p class="description">${esc(w.description)}</p>` : ""}<div class="table-footer"><button class="text-button" data-action="toggle" data-id="${esc(w.id)}">${icon(w.enabled ? "pause" : "play")}${w.enabled ? "Tạm dừng" : "Bật giám sát"}</button><button class="text-button" data-action="archive" data-id="${esc(w.id)}">${icon("archive")}Lưu trữ</button></div></section><div class="notice">${icon("info")}<div><strong>Cách đọc số liệu</strong><p>Tỷ lệ thành công = số mẫu thành công / tổng số mẫu. Đây không phải uptime SLA theo thời gian. Dấu “—” nghĩa là chưa có dữ liệu.</p></div></div></aside></div>`;
}
function renderAlerts(data) {
  return `<section class="panel alerts-page"><div class="panel-header"><div><h2>Tất cả thông báo</h2><p>DOWN · Ngừng hoạt động &nbsp; / &nbsp; RECOVERY · Khôi phục</p></div><span class="tiny muted">${data.alerts.totalElements} thông báo</span></div><div class="alert-list">${data.alerts.content.length ? alertItems(data.alerts.content) : empty("Mọi thứ đang yên tĩnh", "Cảnh báo mới sẽ xuất hiện tại đây khi có sự cố hoặc phục hồi.")}</div><div class="table-footer"><span>${DEMO ? "Dữ liệu cảnh báo mô phỏng" : "Mới nhất trước"}</span>${pagination(data.alerts)}</div></section>`;
}
async function fetchData() {
  const params = rangeParams();
  if (view === "detail") {
    const [website, metrics, history, incidents] = await Promise.all([
      request(`/websites/${detailId}`),
      request(`/websites/${detailId}/metrics`, { params }),
      request(`/websites/${detailId}/history`, {
        params: {
          ...params,
          page: state.historyPage,
          size: 8,
          result: state.historyResult,
        },
      }),
      request(`/websites/${detailId}/incidents`, {
        params: { page: state.incidentPage, size: 5 },
      }),
    ]);
    return { website, metrics, history, incidents };
  }
  if (view === "alerts")
    return {
      alerts: await request("/alerts", {
        params: { page: state.page, size: 8 },
      }),
    };
  const [summary, websites] = await Promise.all([
    request("/dashboard/summary", { params }),
    request("/websites", {
      params: {
        search: state.search,
        status: state.status,
        page: view === "dashboard" ? 0 : state.page,
        size: view === "dashboard" ? 100 : 8,
      },
    }),
  ]);
  if (view === "websites") return { summary, websites };
  if (!websites.content.some((w) => w.id === state.chartId))
    state.chartId = websites.content[0]?.id || "";
  const [alerts, metrics] = await Promise.all([
    request("/alerts", { params: { page: 0, size: 3 } }),
    state.chartId
      ? request(`/websites/${state.chartId}/metrics`, { params })
      : Promise.resolve({
          ...params,
          sampleCount: 0,
          successRate: null,
          points: [],
        }),
  ]);
  return { summary, websites, alerts, metrics };
}
async function refresh(background = false) {
  if (state.refreshing) {
    if (!background) state.reloadQueued = true;
    return;
  }
  if (background && document.hidden) return;
  if (background && (state.busy || modal.open)) {
    clearTimeout(state.timer);
    state.timer = setTimeout(() => refresh(true), 10000);
    return;
  }
  state.refreshing = true;
  clearTimeout(state.timer);
  const focused = document.activeElement,
    focusId = focused?.id,
    selection = focused?.selectionStart;
  try {
    const data = await fetchData();
    state.data = data;
    // A filter may change while a request is in flight; fetch its new value before rendering.
    if (state.reloadQueued) return;
    const list = data.websites || data.alerts;
    if (list && state.page > 0 && !list.content.length) {
      state.page = Math.max(0, list.totalPages - 1);
      state.reloadQueued = true;
      return;
    }
    document.querySelector("#content").innerHTML =
      view === "dashboard"
        ? renderDashboard(data)
        : view === "websites"
          ? renderWebsites(data)
          : view === "detail"
            ? renderDetail(data)
            : renderAlerts(data);
    document.querySelector("#error").innerHTML = "";
    if (view === "detail") drawHeading(data.website);
    state.updatedAt = new Date().toISOString();
    document.querySelector("#last-updated").innerHTML =
      `${icon("refresh")}Cập nhật lúc ${time(state.updatedAt)} · ${DEMO ? "Demo" : "Tự động mỗi 10 giây"}`;
    bindView();
    if (focusId && !modal.open) {
      const next = document.getElementById(focusId);
      if (next) {
        next.focus({ preventScroll: true });
        if (selection != null && next.type === "search")
          next.setSelectionRange(selection, selection);
      }
    }
  } catch (error) {
    document.querySelector("#error").innerHTML =
      `<div class="notice error">${icon("info")}<div><strong>${esc(error.message)}</strong><p>${state.data ? "Đang giữ dữ liệu đã tải trước đó. Trạng thái website không bị thay đổi." : "Chưa thể tải nội dung. Vui lòng thử lại."}</p></div><button class="button small" data-action="refresh">Thử lại</button></div>`;
    if (!state.data)
      document.querySelector("#content").innerHTML = empty(
        error.status === 404
          ? "Không tìm thấy website"
          : "Không thể tải dữ liệu",
        error.status === 404
          ? "Website có thể đã được lưu trữ."
          : "Kiểm tra kết nối máy chủ rồi thử lại.",
        '<a class="button" href="/websites">Danh sách website</a>',
      );
    document.querySelector("#last-updated").innerHTML =
      `${icon("info")}Lỗi đồng bộ${state.updatedAt ? ` · Lần cuối ${time(state.updatedAt)}` : ""}`;
  } finally {
    state.refreshing = false;
    if (state.reloadQueued) {
      state.reloadQueued = false;
      refresh();
    } else if (!document.hidden)
      state.timer = setTimeout(() => refresh(true), 10000);
  }
}
let searchTimer;
function bindView() {
  if (BACKEND) {
    document.querySelectorAll('[data-action="archive"]').forEach((button) => button.remove());
    document.querySelector('#logout')?.remove();
    document.querySelectorAll('.config-row').forEach((row) => {
      if (["Ngưỡng chậm", "Ngưỡng lỗi"].includes(row.querySelector('dt')?.textContent)) row.remove();
    });
  }
  bindChart(document.querySelector("#content"));
  document.querySelectorAll(".table-wrap").forEach((table) => {
    const hint = document.createElement("p");
    hint.className = "table-hint";
    hint.textContent = "Vuốt ngang để xem đầy đủ bảng →";
    table.before(hint);
    table.tabIndex = 0;
    table.setAttribute("role", "region");
    table.setAttribute("aria-label", "Bảng dữ liệu có thể cuộn ngang");
  });
  const search = document.querySelector("#website-search");
  if (search)
    search.oninput = (e) => {
      state.search = e.target.value;
      state.page = 0;
      clearTimeout(searchTimer);
      searchTimer = setTimeout(() => refresh(), 250);
    };
  const filter = document.querySelector("#status-filter");
  if (filter)
    filter.onchange = (e) => {
      state.status = e.target.value;
      state.page = 0;
      refresh();
    };
  const selector = document.querySelector("#chart-website");
  if (selector)
    selector.onchange = (e) => {
      state.chartId = e.target.value;
      refresh();
    };
  const result = document.querySelector("#history-result");
  if (result)
    result.onchange = (e) => {
      state.historyResult = e.target.value;
      state.historyPage = 0;
      refresh();
    };
}
async function mutate(button, operation, message) {
  if (state.busy) return;
  state.busy = true;
  button.disabled = true;
  try {
    await operation();
    toast(message);
    await refresh();
  } catch (error) {
    toast(error.message);
  } finally {
    state.busy = false;
    button.disabled = false;
  }
}
function showModal(html) {
  modal.innerHTML = html;
  modal.showModal();
}
function formField(name, label, value, type = "text", full = false, help = "") {
  return `<div class="field ${full ? "full" : ""}"><label for="field-${name}">${label}</label><input id="field-${name}" class="input" name="${name}" type="${type}" value="${esc(value)}" aria-describedby="error-${name}${help ? " help-" + name : ""}" ${type === "number" ? 'step="1"' : ""}><span class="field-error" id="error-${name}"></span>${help ? `<p class="help" id="help-${name}">${help}</p>` : ""}</div>`;
}
async function openForm(id) {
  let w = {
    name: "",
    url: "",
    description: "",
    intervalSeconds: 30,
    timeoutMs: 5000,
    slowThresholdMs: 2000,
    failureThreshold: 3,
    expectedCodes: "200-299",
  };
  if (id) {
    try {
      w = await request(`/websites/${id}`);
    } catch (error) {
      toast(error.message);
      return;
    }
  }
  showModal(
    `<form id="website-form" novalidate><div class="modal-header"><div><h2 id="modal-title">${id ? "Chỉnh sửa website" : "Thêm website"}</h2><p>Thiết lập thông tin và chu kỳ giám sát website.</p></div><button type="button" class="icon-button" data-close aria-label="Đóng">${icon("down")}</button></div><div class="modal-body"><div class="form-message" role="alert"></div><div class="form-grid">${formField("name", "Tên website", w.name, "text", true)}${formField("url", "Địa chỉ URL", w.url, "url", true, "Bao gồm http:// hoặc https://. Ví dụ: https://portal.company.vn")}${formField("intervalSeconds", "Chu kỳ kiểm tra <small>(giây)</small>", w.intervalSeconds, "number")}${formField("timeoutMs", "Timeout <small>(ms)</small>", w.timeoutMs, "number")}${formField("slowThresholdMs", "Ngưỡng phản hồi chậm <small>(ms)</small>", w.slowThresholdMs, "number")}${formField("failureThreshold", "Số lỗi liên tiếp để báo DOWN", w.failureThreshold, "number")}${formField("expectedCodes", "Mã HTTP chấp nhận", w.expectedCodes, "text", true, "Mặc định 200–299. Có thể nhập: 200-299, 301, 302.")}<div class="field full"><label for="field-description">Mô tả <small>(không bắt buộc)</small></label><textarea id="field-description" name="description" rows="2" maxlength="500" aria-describedby="error-description">${esc(w.description)}</textarea><span class="field-error" id="error-description"></span></div></div></div><div class="modal-footer"><button type="button" class="button" data-close>Hủy</button><button class="button primary" type="submit">${icon("check")}${id ? "Lưu thay đổi" : "Thêm website"}</button></div></form>`,
  );
  if (BACKEND) {
    for (const name of ['timeoutMs', 'slowThresholdMs', 'failureThreshold', 'expectedCodes', 'description']) {
      modal.querySelector(`[name="${name}"]`).closest('.field').remove();
    }
    modal.querySelector('.modal-header p').textContent = 'Lưu website vào cơ sở dữ liệu. Chu kỳ chỉ được lưu cấu hình; hiện cần bấm kiểm tra thủ công.';
  }
  modal.querySelector("[name=name]").focus();
  modal.querySelector("form").onsubmit = async (event) => {
    event.preventDefault();
    if (state.busy) return;
    const form = event.currentTarget,
      values = Object.fromEntries(new FormData(form));
    for (const key of [
      "intervalSeconds",
      "timeoutMs",
      "slowThresholdMs",
      "failureThreshold",
    ])
      values[key] = Number(values[key]);
    values.name = values.name.trim();
    values.url = values.url.trim();
    values.expectedCodes = (values.expectedCodes || "").trim();
    values.description = (values.description || "").trim();
    if (id) values.version = w.version;
    form.querySelectorAll(".field-error").forEach((e) => (e.textContent = ""));
    form
      .querySelectorAll("[aria-invalid]")
      .forEach((e) => e.removeAttribute("aria-invalid"));
    form.querySelector(".form-message").textContent = "";
    const errors = validateWebsite(values);
    const showErrors = (errors) => {
      for (const [key, message] of Object.entries(errors)) {
        const input = form.elements.namedItem(key);
        if (input) {
          input.setAttribute("aria-invalid", "true");
          document.getElementById("error-" + key).textContent = Array.isArray(
            message,
          )
            ? message.join(" ")
            : message;
        }
      }
      form.querySelector("[aria-invalid]")?.focus();
    };
    if (Object.keys(errors).length) {
      showErrors(errors);
      return;
    }
    state.busy = true;
    form.querySelectorAll("button").forEach((b) => (b.disabled = true));
    try {
      await request(id ? `/websites/${id}` : "/websites", {
        method: id ? "PUT" : "POST",
        body: values,
      });
      modal.close();
      toast(
        id
          ? "Đã lưu thay đổi."
          : "Đã thêm website. Trạng thái ban đầu: chưa xác định.",
      );
      await refresh();
    } catch (error) {
      showErrors(error.fieldErrors || {});
      form.querySelector(".form-message").textContent = error.message;
    } finally {
      state.busy = false;
      form.querySelectorAll("button").forEach((b) => (b.disabled = false));
    }
  };
}
async function archiveWebsite(id) {
  let w;
  try {
    w = await request(`/websites/${id}`);
  } catch (error) {
    toast(error.message);
    return;
  }
  showModal(
    `<div class="modal-header"><div><h2 id="modal-title">Lưu trữ website?</h2><p>${esc(w.name)}</p></div><button class="icon-button" data-close aria-label="Đóng">${icon("down")}</button></div><div class="modal-body"><p class="tiny muted">Website sẽ dừng giám sát và được ẩn khỏi danh sách. Lịch sử kiểm tra và sự cố vẫn được giữ lại.</p><div class="form-message" role="alert"></div></div><div class="modal-footer"><button class="button" data-close>Hủy</button><button class="button primary" id="confirm-archive">${icon("archive")}Lưu trữ website</button></div>`,
  );
  modal.querySelector("#confirm-archive").onclick = async (event) => {
    if (state.busy) return;
    state.busy = true;
    modal.querySelectorAll("button").forEach((b) => (b.disabled = true));
    try {
      await request(`/websites/${id}`, { method: "DELETE" });
      modal.close();
      if (view === "detail") location.assign("/websites");
      else {
        toast("Đã lưu trữ website.");
        await refresh();
      }
    } catch (error) {
      modal.querySelector(".form-message").textContent = error.message;
    } finally {
      state.busy = false;
      modal.querySelectorAll("button").forEach((b) => (b.disabled = false));
    }
  };
}
modal.addEventListener("click", (e) => {
  if (e.target.closest("[data-close]") && !state.busy) modal.close();
});
modal.addEventListener("cancel", (e) => {
  if (state.busy) e.preventDefault();
});
document.addEventListener("click", async (event) => {
  const button = event.target.closest(
    "[data-action],[data-range],[data-page-kind]",
  );
  if (!button || button.disabled) return;
  if (button.dataset.range) {
    state.range = button.dataset.range;
    state.historyPage = 0;
    refresh();
    return;
  }
  if (button.dataset.pageKind) {
    state[button.dataset.pageKind] = Number(button.dataset.page);
    refresh();
    return;
  }
  const { action, id } = button.dataset;
  if (action === "refresh") refresh();
  if (action === "add") openForm();
  if (action === "edit") openForm(id);
  if (action === "archive") archiveWebsite(id);
  if (action === "clear-filters") {
    state.search = "";
    state.status = "";
    state.page = 0;
    refresh();
  }
  if (action === "check")
    mutate(
      button,
      () => request(`/websites/${id}/checks`, { method: "POST" }),
      DEMO
        ? "Đã thêm kết quả kiểm tra mô phỏng."
        : "Đã hoàn tất kiểm tra website.",
    );
  if (action === "toggle")
    mutate(
      button,
      async () => {
        const w = await request(`/websites/${id}`);
        await request(`/websites/${id}/monitoring`, {
          method: "PATCH",
          body: { enabled: !w.enabled },
        });
      },
      "Đã cập nhật trạng thái giám sát.",
    );
});
async function login() {
  if (BACKEND) {
    location.replace('/');
    return;
  }
  document.title = "Đăng nhập · QLWebsite";
  app.innerHTML = `<main class="login-page" id="main"><section class="login-story"><a class="brand" href="/"><span class="brand-mark">${icon("pulse")}</span>QLWebsite</a><div class="login-copy"><div class="eyebrow">GIỮ KẾT NỐI. GIỮ NHỊP HOẠT ĐỘNG.</div><h1>Mọi website.<br>Một góc nhìn.</h1><p>Một không gian gọn gàng để theo dõi tình trạng, hiệu suất và những thay đổi của website nội bộ.</p><svg class="login-signal" viewBox="0 0 450 100" aria-hidden="true"><path d="M0 50h100l15-20 20 40 25-60 25 85 20-45h245"/></svg></div><footer>© ${new Date().getFullYear()} QLWebsite · Website monitoring</footer></section><section class="login-form-side"><form class="login-form" id="login-form"><div class="eyebrow">KHÔNG GIAN NỘI BỘ</div><h2>${DEMO ? "Chào mừng bạn." : "Chào mừng trở lại."}</h2><p>${DEMO ? "Khám phá không gian quản lý và giám sát website." : "Đăng nhập để tiếp tục quản lý website của bạn."}</p><div class="form-message" role="alert"></div>${DEMO ? "" : `<div class="field"><label for="username">Tên đăng nhập</label><input class="input" id="username" name="username" autocomplete="username" required></div><div class="field"><label for="password">Mật khẩu</label><input class="input" type="password" id="password" name="password" autocomplete="current-password" required></div>`}<button class="button primary" type="submit">${DEMO ? "Mở dashboard demo" : "Đăng nhập"}${icon("arrow")}</button>${DEMO ? '<div class="notice"><strong>Phiên bản giao diện · Task 2</strong>Dữ liệu được mô phỏng và lưu trong trình duyệt. Không cần tài khoản. Chức năng giám sát thực tế sẽ được kết nối khi backend sẵn sàng.</div>' : ""}</form></section></main>`;
  document.querySelector("#login-form").onsubmit = async (e) => {
    e.preventDefault();
    const form = e.currentTarget,
      button = form.querySelector("button");
    button.disabled = true;
    try {
      if (!DEMO)
        await request("/session", {
          method: "POST",
          body: Object.fromEntries(new FormData(form)),
        });
      location.assign("/");
    } catch (error) {
      form.querySelector(".form-message").textContent = error.message;
      button.disabled = false;
    }
  };
}
if (view === "login") login();
else {
  state.status = new URLSearchParams(location.search).get("status") || "";
  shell();
  refresh();
  document.addEventListener("visibilitychange", () => {
    clearTimeout(state.timer);
    if (!document.hidden) refresh(true);
  });
  window.addEventListener("pagehide", () => {
    clearTimeout(state.timer);
    clearTimeout(searchTimer);
  });
}

window.matchMedia("(max-width: 700px)").addEventListener("change", () => {
  if (state.data?.metrics) {
    const body = document.querySelector(".chart-body");
    if (body) {
      body.innerHTML = responseChart(state.data.metrics);
      bindChart(body);
    }
  }
});
