import { request, currentUser } from "./data.js";
const esc = (s) =>
  String(s ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
const canEdit = () => currentUser?.role !== "VIEWER";
const scanStates = new Map();
const labels = {
  QUEUED: "Đang chờ",
  RUNNING: "Đang quét",
  COMPLETED: "Hoàn tất",
  PARTIAL: "Quét một phần",
  FAILED: "Không hoàn tất",
  INTERRUPTED: "Bị gián đoạn",
};
const time = (s) =>
  s
    ? new Date(/[zZ]|[+-]\d\d:\d\d$/.test(s) ? s : s + "Z").toLocaleString(
        "vi-VN",
      )
    : "—";
function input(name, label, value, attrs = "", help = "") {
  return `<div class="field"><label for="field-${name}">${label}</label><input id="field-${name}" name="${name}" value="${esc(value)}" ${attrs}><span class="field-error" id="error-${name}"></span>${help ? `<p class="help">${help}</p>` : ""}</div>`;
}
function select(name, label, value, options) {
  return `<div class="field"><label for="field-${name}">${label}</label><select id="field-${name}" name="${name}">${options.map(([v, t]) => `<option value="${v}" ${v === value ? "selected" : ""}>${t}</option>`).join("")}</select><span class="field-error" id="error-${name}"></span></div>`;
}
export function addCheckFields(modal, w) {
  const grid = modal.querySelector(".form-grid");
  grid.insertAdjacentHTML(
    "beforeend",
    `<section class="check-fields full"><h3>Loại kiểm tra</h3><div class="form-grid">
    ${select("checkType", "Kiểu giám sát", w.checkType || "HTTP", [
      ["HTTP", "HTTP · Website / API"],
      ["DNS", "DNS · Bản ghi tên miền"],
      ["TCP", "TCP · Cổng dịch vụ"],
    ])}
    <p class="help">DNS và TCP lấy tên miền/IP từ URL ở trên. Có thể thêm cùng URL với các kiểu kiểm tra khác nhau để theo dõi song song. Chu kỳ, lịch bảo trì và cảnh báo áp dụng cho cả ba kiểu.</p>
    </div><div data-check-config="HTTP"><h3>Kiểm tra nội dung HTTP</h3><div class="form-grid">
    ${select("keywordMode", "Điều kiện từ khóa", w.keywordMode || "OFF", [
      ["OFF", "Không kiểm tra"],
      ["CONTAINS", "Phải chứa từ khóa"],
      ["NOT_CONTAINS", "Không được chứa từ khóa"],
    ])}
    ${input("keyword", "Từ khóa / cụm từ", w.keyword || "", 'maxlength="1000"', "Tìm trong nội dung phản hồi, kể cả HTML nguồn.")}
    <label class="check-option full"><input type="checkbox" name="keywordCaseSensitive" ${w.keywordCaseSensitive ? "checked" : ""}> Phân biệt chữ hoa/thường (mặc định không phân biệt)</label>
    <label class="check-option full"><input type="checkbox" name="jsonEnabled" ${w.jsonEnabled ? "checked" : ""}> Kiểm tra JSON</label>
    ${input("jsonPointer", "JSON Pointer", w.jsonPointer || "", 'maxlength="500" placeholder="/data/status"', "Ví dụ /data/status hoặc /items/0/id. ~1 là dấu /; ~0 là dấu ~. Để trống để kiểm tra toàn bộ JSON.")}
    ${select("jsonComparison", "Điều kiện JSON", w.jsonComparison || "EQUALS", [
      ["EQUALS", "Bằng giá trị mong đợi"],
      ["EXISTS", "Trường tồn tại (kể cả null)"],
    ])}
    ${input("jsonExpected", "Giá trị JSON mong đợi", w.jsonExpected || "", 'maxlength="2000" placeholder="&quot;ok&quot;"', "Nhập JSON hợp lệ: &quot;ok&quot;, true, 200, null, {&quot;status&quot;:&quot;ok&quot;}. Chuỗi cần dấu ngoặc kép; so sánh đúng kiểu dữ liệu.")}
    </div><p class="help">Các điều kiện đã bật phải cùng đạt và mã HTTP phải được chấp nhận. Nội dung được kiểm tra tối đa 2 MiB; vượt giới hạn sẽ báo lỗi rõ ràng. Không thực thi JavaScript của trang.</p></div>
    <div data-check-config="DNS"><h3>Kiểm tra DNS</h3><div class="form-grid">
    ${select("dnsRecordType", "Loại bản ghi", w.dnsRecordType || "A", [
      ["A", "A · IPv4"],
      ["AAAA", "AAAA · IPv6"],
    ])}
    ${input("dnsExpected", "IP mong đợi (không bắt buộc)", w.dnsExpected || "", 'maxlength="1000" placeholder="203.0.113.10, 203.0.113.11"', "Phân cách bằng dấu phẩy hoặc khoảng trắng. Tất cả IP mong đợi phải có trong kết quả. Để trống để chỉ kiểm tra có bản ghi.")}
    </div><p class="help">Sử dụng bộ phân giải DNS của máy chạy backend; kết quả có thể chịu ảnh hưởng cache DNS.</p></div>
    <div data-check-config="TCP"><h3>Kiểm tra cổng TCP</h3><div class="form-grid">${input(
      "tcpPort",
      "Cổng TCP",
      w.tcpPort ??
        (() => {
          try {
            const u = new URL(w.url);
            return u.port || (u.protocol === "http:" ? 80 : 443);
          } catch {
            return 443;
          }
        })(),
      'type="number" min="1" max="65535" step="1"',
      "Ví dụ 443 (HTTPS), 80 (HTTP), 3306 (MySQL). Chỉ kiểm tra mở kết nối TCP.",
    )}</div></div>
  </section>`,
  );
  const form = modal.querySelector("form");
  const update = () => {
    const type = form.elements.checkType.value;
    grid.querySelectorAll("[data-check-config]").forEach((el) => {
      el.hidden = el.dataset.checkConfig !== type;
    });
    for (const name of ["expectedCodes", "sslWarnDays"]) {
      const field = form.elements[name]?.closest(".field");
      if (field) field.hidden = type !== "HTTP";
    }
    form.elements.keyword.disabled = form.elements.keywordMode.value === "OFF";
    form.elements.keywordCaseSensitive.disabled =
      form.elements.keywordMode.value === "OFF";
    for (const name of ["jsonPointer", "jsonComparison", "jsonExpected"])
      form.elements[name].disabled = !form.elements.jsonEnabled.checked;
    form.elements.jsonExpected.disabled ||=
      form.elements.jsonComparison.value === "EXISTS";
  };
  ["checkType", "keywordMode", "jsonEnabled", "jsonComparison"].forEach(
    (name) => form.elements[name].addEventListener("change", update),
  );
  update();
}
export function readCheckFields(form, values) {
  values.keywordCaseSensitive = form.elements.keywordCaseSensitive.checked;
  values.jsonEnabled = form.elements.jsonEnabled.checked;
  values.tcpPort = Number(form.elements.tcpPort.value);
  // Disabled fields are still saved so toggling a check does not discard its setup.
  for (const name of [
    "keyword",
    "jsonPointer",
    "jsonComparison",
    "jsonExpected",
  ])
    values[name] = form.elements[name].value;
  if (values.checkType !== "HTTP") {
    values.keywordMode = "OFF";
    values.jsonEnabled = false;
  }
}
export function validateCheckFields(input) {
  const errors = {};
  if (input.checkType === "HTTP") {
    if (input.keywordMode !== "OFF" && !input.keyword?.trim())
      errors.keyword = "Nhập từ khóa cần kiểm tra.";
    if (input.jsonEnabled) {
      if (
        input.jsonPointer &&
        (!input.jsonPointer.startsWith("/") ||
          /~(?![01])/.test(input.jsonPointer))
      )
        errors.jsonPointer = "Dùng JSON Pointer, ví dụ /data/status.";
      if (input.jsonComparison === "EQUALS") {
        try {
          JSON.parse(input.jsonExpected);
        } catch {
          errors.jsonExpected = 'Nhập JSON hợp lệ, ví dụ "ok", true hoặc 200.';
        }
      }
    }
  }
  if (
    input.checkType === "TCP" &&
    (!Number.isInteger(input.tcpPort) ||
      input.tcpPort < 1 ||
      input.tcpPort > 65535)
  )
    errors.tcpPort = "Cổng TCP phải từ 1 đến 65535.";
  return errors;
}
export async function enhanceChecks(w) {
  const side = document.querySelector(".detail-grid aside");
  const latest = w.latestResult;
  const state = scanStates.get(w.id) || {
    selected: null,
    errorsOnly: true,
    maxPages: 5,
    maxLinks: 100,
    external: false,
  };
  scanStates.set(w.id, state);
  const rows = [["Kiểu giám sát", w.checkType || "HTTP"]];
  if (w.checkType === "DNS")
    rows.push(
      ["Bản ghi", w.dnsRecordType],
      ["IP mong đợi", w.dnsExpected || "Có ít nhất một bản ghi"],
    );
  else if (w.checkType === "TCP") rows.push(["Cổng", w.tcpPort]);
  else {
    rows.push([
      "Từ khóa",
      w.keywordMode === "CONTAINS"
        ? `Phải chứa: ${w.keyword}`
        : w.keywordMode === "NOT_CONTAINS"
          ? `Không được chứa: ${w.keyword}`
          : "Đã tắt",
    ]);
    if (w.keywordMode && w.keywordMode !== "OFF")
      rows.push([
        "Hoa/thường",
        w.keywordCaseSensitive ? "Phân biệt" : "Không phân biệt",
      ]);
    rows.push([
      "JSON",
      w.jsonEnabled
        ? `${w.jsonPointer || "(toàn bộ)"} ${w.jsonComparison === "EXISTS" ? "tồn tại" : "= " + w.jsonExpected}`
        : "Đã tắt",
    ]);
  }
  side?.insertAdjacentHTML(
    "afterbegin",
    `<section class="panel"><div class="panel-header"><h2>Điều kiện kiểm tra</h2></div><dl class="config-list">${rows.map(([k, v]) => `<div class="config-row"><dt>${esc(k)}</dt><dd>${esc(v)}</dd></div>`).join("")}</dl><p class="panel-note check-details">${esc(latest?.checkDetails || latest?.errorMessage || "Chạy Kiểm tra ngay để xem kết quả.")}</p></section>`,
  );
  document.querySelector(".detail-grid > div")?.insertAdjacentHTML(
    "beforeend",
    `<section class="panel" id="link-scan-panel"><div class="panel-header"><div><h2>Quét liên kết hỏng</h2><p>Liên kết a/area trong HTML; lần quét gần nhất được lưu lại</p></div></div>
  ${canEdit() ? `<form id="link-scan-form" class="scan-controls"><label>Trang HTML tối đa<input type="number" name="maxPages" min="1" max="20" value="${esc(state.maxPages)}" required></label><label>Liên kết tối đa<input type="number" name="maxLinks" min="1" max="200" value="${esc(state.maxLinks)}" required></label><label class="check-option"><input type="checkbox" name="external" ${state.external ? "checked" : ""}> Kiểm tra cả liên kết ngoài</label><button class="button primary" type="submit">Bắt đầu quét</button></form>` : ""}
  <p class="panel-note">Chỉ duyệt tiếp trang cùng nguồn, tối đa 2 cấp và 90 giây. Không kiểm tra phần tử do JavaScript sinh ra hoặc đích fragment (#). Kết quả quét độc lập với cảnh báo uptime.</p>
  <p class="panel-note" id="scan-error" role="alert"></p><div class="scan-controls"><label for="scan-select">Lần quét</label><select id="scan-select"></select><label class="check-option"><input id="scan-errors-only" type="checkbox" checked> Chỉ hiện lỗi / cần xem lại</label></div><div id="scan-report" aria-live="polite"><p class="panel-note">Đang tải báo cáo…</p></div></section>`,
  );
  const panel = document.querySelector("#link-scan-panel");
  if (!panel) return;
  const only = panel.querySelector("#scan-errors-only");
  only.checked = state.errorsOnly;
  const draw = async () => {
    try {
      const all = await request(`/websites/${w.id}/link-scans`);
      if (!panel.isConnected) return;
      const select = panel.querySelector("#scan-select");
      if (!all.some((s) => String(s.id) === String(state.selected)))
        state.selected = all[0]?.id;
      select.innerHTML = all.length
        ? all
            .map(
              (s) =>
                `<option value="${s.id}" ${String(s.id) === String(state.selected) ? "selected" : ""}>${esc(time(s.startedAt))} · ${esc(labels[s.status] || s.status)}</option>`,
            )
            .join("")
        : '<option value="">Chưa có lần quét</option>';
      const busy = all.some((s) => ["RUNNING", "QUEUED"].includes(s.status));
      const submit = panel.querySelector('[type="submit"]');
      if (submit) {
        submit.disabled = busy;
        submit.textContent = busy ? "Đang quét…" : "Bắt đầu quét";
      }
      const report = panel.querySelector("#scan-report");
      if (!state.selected) {
        report.innerHTML =
          '<p class="empty">Chưa có báo cáo. Bắt đầu quét để tìm liên kết hỏng.</p>';
        return;
      }
      const { scan, results } = await request(
        `/websites/${w.id}/link-scans/${state.selected}`,
      );
      if (!panel.isConnected) return;
      const filtered = state.errorsOnly
        ? results.filter((r) => r.status !== "OK")
        : results;
      const statusLabels = {
        OK: "Đạt",
        BROKEN: "Liên kết hỏng",
        ERROR: "Lỗi kết nối",
        REVIEW: "Cần xem lại",
      };
      report.innerHTML = `<div class="scan-summary"><strong>${esc(labels[scan.status] || scan.status)}</strong><span>${scan.pages} trang · ${scan.checked} liên kết · ${scan.broken} hỏng · ${scan.uncertain} cần xem lại</span></div><p class="panel-note">${esc(scan.message || "Đang kiểm tra các liên kết. Kết quả tự cập nhật cùng trang.")}</p>${filtered.length ? `<div class="table-wrap" tabindex="0" role="region" aria-label="Link scan results"><table class="scan-table"><thead><tr><th>Liên kết / Trang chứa</th><th>HTTP</th><th>Kết quả</th></tr></thead><tbody>${filtered.map((r) => `<tr><td><a href="${esc(r.url)}" target="_blank" rel="noopener noreferrer">${esc(r.url)}</a><small class="website-url">Từ: ${esc(r.sourceUrl)}</small>${r.finalUrl && r.finalUrl !== r.url ? `<small class="website-url">Chuyển tới: ${esc(r.finalUrl)}</small>` : ""}</td><td>${r.httpCode ?? "—"}</td><td><span class="status ${r.status === "BROKEN" ? "down" : ""}">${esc(statusLabels[r.status])}</span><small class="website-url">${esc(r.message || "")}</small></td></tr>`).join("")}</tbody></table></div>` : `<p class="empty">${scan.checked ? "Không có lỗi trong bộ lọc hiện tại." : "Chưa có kết quả."}</p>`}`;
    } catch (ex) {
      if (panel.isConnected)
        panel.querySelector("#scan-error").textContent = ex.message;
    }
  };
  panel.querySelector("#scan-select").onchange = (e) => {
    state.selected = e.target.value;
    draw();
  };
  only.onchange = () => {
    state.errorsOnly = only.checked;
    draw();
  };
  panel
    .querySelector("#link-scan-form")
    ?.addEventListener("submit", async (e) => {
      e.preventDefault();
      const form = e.currentTarget;
      const button = form.querySelector("button");
      button.disabled = true;
      const error = panel.querySelector("#scan-error");
      error.textContent = "";
      try {
        const scan = await request(`/websites/${w.id}/link-scans`, {
          method: "POST",
          body: {
            maxPages: Number(form.elements.maxPages.value),
            maxLinks: Number(form.elements.maxLinks.value),
            external: form.elements.external.checked,
          },
        });
        state.selected = scan.id;
        await draw();
      } catch (ex) {
        error.textContent = ex.message;
        button.disabled = false;
      }
    });
  const controls = panel.querySelector("#link-scan-form");
  controls?.addEventListener("input", () => {
    state.maxPages = controls.elements.maxPages.value;
    state.maxLinks = controls.elements.maxLinks.value;
    state.external = controls.elements.external.checked;
  });
  await draw();
}
