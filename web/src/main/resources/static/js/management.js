import { addCheckFields, enhanceChecks } from "./checks.js";
export { readCheckFields, validateCheckFields } from "./checks.js";
import { request, currentUser } from "./data.js";
const esc = (value) =>
  String(value ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
const localDate = (value) =>
  value ? new Date(value).toLocaleString("vi-VN") : "—";
const canEdit = () => currentUser?.role !== "VIEWER";
function field(name, label, value = "", type = "text", extra = "") {
  return `<div class="field"><label for="extra-${name}">${label}</label><input class="input" id="extra-${name}" name="${name}" type="${type}" value="${esc(value)}" ${extra}></div>`;
}
function message(text) {
  const el = document.querySelector("#extra-message");
  if (el) el.textContent = text;
}
function modalForm(title, body, submit = "Lưu") {
  const modal = document.querySelector("#modal");
  modal.innerHTML = `<form id="extra-form"><div class="modal-header"><h2 id="modal-title">${title}</h2><button class="button" type="button" data-close>Đóng</button></div><div class="modal-body"><div class="form-grid">${body}</div><p id="extra-message" role="alert"></p></div><div class="modal-footer"><button class="button primary" type="submit">${submit}</button></div></form>`;
  modal.showModal();
  return modal;
}
async function runForm(form, action, done) {
  const button = form.querySelector('[type="submit"]');
  button.disabled = true;
  try {
    await action();
    await done();
  } catch (e) {
    message(e.message);
  } finally {
    button.disabled = false;
  }
}
export async function mountManagement() {
  const target = document.querySelector("#content");
  document.querySelector("#heading").innerHTML =
    `<div class="page-heading"><div><div class="eyebrow">QUẢN TRỊ</div><h1>Tài khoản và phân quyền</h1><p>Phân quyền quản trị, vận hành và chỉ xem.</p></div></div>`;
  if (currentUser?.role !== "ADMIN") {
    target.innerHTML =
      '<div class="notice">Chức năng này dành cho quản trị viên.</div>';
    return;
  }
  try {
    await usersPage();
  } catch (e) {
    target.innerHTML = `<div class="notice error">${esc(e.message)}</div>`;
  }
}
async function usersPage() {
  const rows = await request("/users");
  document.querySelector("#content").innerHTML =
    `<section class="panel"><div class="panel-header"><h2>Tài khoản</h2><button class="button primary" id="add-user">Thêm tài khoản</button></div><div class="table-wrap"><table><thead><tr><th>Tên đăng nhập</th><th>Vai trò</th><th>Trạng thái</th><th></th></tr></thead><tbody>${rows.map((u) => `<tr><td>${esc(u.username)}</td><td>${esc(u.role)}</td><td>${u.enabled ? "Hoạt động" : "Đã khóa"}</td><td><button class="button small" data-user="${u.id}">Chỉnh sửa</button></td></tr>`).join("")}</tbody></table></div><div class="table-footer">ADMIN: toàn quyền · OPERATOR: quản lý giám sát · VIEWER: chỉ xem</div></section>`;
  const edit = (u) => {
    const modal = modalForm(
      u ? "Sửa tài khoản" : "Thêm tài khoản",
      `${u ? `<p>${esc(u.username)}</p>` : field("username", "Tên đăng nhập", "", "text", 'required minlength="3" maxlength="80" autocomplete="off"')}${field("password", u ? "Mật khẩu mới (để trống để giữ nguyên)" : "Mật khẩu (10–72 byte)", "", "password", `${u ? "" : "required"} minlength="10" maxlength="72" autocomplete="new-password"`)}<div class="field"><label for="extra-role">Vai trò</label><select id="extra-role" name="role">${["VIEWER", "OPERATOR", "ADMIN"].map((r) => `<option ${u?.role === r ? "selected" : ""}>${r}</option>`).join("")}</select></div>${u ? `<label class="check-field"><input type="checkbox" name="enabled" ${u.enabled ? "checked" : ""}> Tài khoản hoạt động</label>` : ""}`,
    );
    modal.querySelector("form").onsubmit = (e) => {
      e.preventDefault();
      const form = e.currentTarget;
      const body = Object.fromEntries(new FormData(form));
      body.enabled = u ? form.elements.enabled.checked : true;
      runForm(
        form,
        () =>
          request(u ? `/users/${u.id}` : "/users", {
            method: u ? "PUT" : "POST",
            body,
          }),
        async () => {
          modal.close();
          await usersPage();
        },
      );
    };
  };
  document.querySelector("#add-user").onclick = () => edit();
  document
    .querySelectorAll("[data-user]")
    .forEach(
      (b) =>
        (b.onclick = () =>
          edit(rows.find((u) => String(u.id) === b.dataset.user))),
    );
}

export function addWebsiteFields(modal, w) {
  modal
    .querySelector(".form-grid")
    .insertAdjacentHTML(
      "beforeend",
      `${field("groupName", "Nhóm / dự án", w.groupName, "text", 'maxlength="100"')}${field("environment", "Môi trường", w.environment, "text", 'placeholder="production, staging, dev" maxlength="100"')}${field("tags", "Tag (phân cách bằng dấu phẩy)", w.tags, "text", 'maxlength="1000"')}${field("sslWarnDays", "Cảnh báo SSL trước (ngày)", w.sslWarnDays ?? 30, "number", 'min="1" max="90"')}`,
    );
  addCheckFields(modal, w);
}
export async function enhanceView(view, data, refresh, state) {
  if (view === "websites") {
    const add = document.querySelector('#heading [data-action="add"]');
    if (add && canEdit() && !document.querySelector("#import-csv")) {
      add.insertAdjacentHTML(
        "beforebegin",
        '<button class="button" id="import-csv">Nhập CSV</button>',
      );
      document.querySelector("#import-csv").onclick = () => importCsv(refresh);
    }
    const bar = document.querySelector(".filter-bar");
    if (bar) {
      for (const [name, label, values] of [
        ["groupName", "Tất cả nhóm", data.websites.groups || []],
        ["environment", "Tất cả môi trường", data.websites.environments || []],
      ]) {
        bar.insertAdjacentHTML(
          "beforeend",
          `<label class="sr-only" for="filter-${name}">${label}</label><select id="filter-${name}" aria-label="${label}"><option value="">${label}</option>${values.map((v) => `<option value="${esc(v)}" ${state[name] === v ? "selected" : ""}>${esc(v)}</option>`).join("")}</select>`,
        );
        document.querySelector(`#filter-${name}`).onchange = (e) => {
          state[name] = e.target.value;
          state.page = 0;
          refresh();
        };
      }
    }
    const input = document.querySelector("#website-search");
    if (input) input.placeholder = "Tìm tên, URL, nhóm, môi trường, tag…";
  }
  if (view === "detail") {
    const w = data.website;
    await enhanceChecks(w);
    const side = document.querySelector(".detail-grid aside");
    if (side)
      side.insertAdjacentHTML(
        "afterbegin",
        `<section class="panel"><div class="panel-header"><h2>Nhóm và SSL</h2></div><dl class="config-list"><div class="config-row"><dt>Nhóm / dự án</dt><dd>${esc(w.groupName || "—")}</dd></div><div class="config-row"><dt>Môi trường</dt><dd>${esc(w.environment || "—")}</dd></div><div class="config-row"><dt>Tag</dt><dd>${esc(w.tags || "—")}</dd></div><div class="config-row"><dt>SSL hết hạn</dt><dd>${esc(localDate(w.sslExpiresAt))}</dd></div></dl><p class="panel-note">${esc(w.sslError || ((w.checkType || "HTTP") !== "HTTP" ? "Chứng chỉ SSL chỉ được kiểm tra với kiểu HTTP." : w.url.startsWith("https:") ? `Cảnh báo trước ${w.sslWarnDays} ngày. Cập nhật cùng lần kiểm tra HTTPS.` : "Website sử dụng HTTP."))}</p></section>`,
      );
    const content = document.querySelector("#content");
    content.insertAdjacentHTML(
      "afterbegin",
      `<section class="panel report-strip"><span>P95: <strong>${data.metrics.p95ResponseTimeMs ?? "—"} ms</strong></span><span>Sự cố: <strong>${data.metrics.incidentCount ?? 0}</strong></span><span>Gián đoạn đã ghi nhận: <strong>${data.metrics.downtimeSeconds ?? 0} giây</strong></span><button class="button" id="export-report">Xuất báo cáo CSV</button><span id="report-error" role="alert"></span></section>`,
    );
    document.querySelector("#export-report").onclick = async (e) => {
      e.target.disabled = true;
      try {
        const q = new URLSearchParams({
          websiteId: w.id,
          from: data.metrics.from,
          to: data.metrics.to,
        });
        const r = await fetch(`/backend-api/reports/export?${q}`, {
          credentials: "same-origin",
        });
        if (!r.ok)
          throw Error((await r.json()).message || "Không xuất được báo cáo.");
        download(await r.blob(), `website-${w.id}-report.csv`);
      } catch (ex) {
        document.querySelector("#report-error").textContent = ex.message;
      } finally {
        e.target.disabled = false;
      }
    };
    if (side)
      side.insertAdjacentHTML(
        "beforeend",
        `<section class="panel"><div class="panel-header"><h2>Lịch bảo trì</h2>${canEdit() ? '<button class="button small" id="add-maintenance">Thêm</button>' : ""}</div><div id="maintenance-list"><p class="panel-note">Đang tải…</p></div></section>`,
      );
    document
      .querySelector("#add-maintenance")
      ?.addEventListener("click", () => maintenanceForm(w.id, refresh));
    document.querySelectorAll(".incident").forEach((node, index) => {
      const incident = data.incidents.content[index];
      node
        .querySelector("div")
        .insertAdjacentHTML(
          "beforeend",
          `<p class="incident-notes">${esc(incident.notes || "Chưa có ghi chú.")}</p>${canEdit() ? `<button class="button small" data-note="${incident.id}">Ghi chú xử lý</button>` : ""}`,
        );
      node.querySelector("[data-note]")?.addEventListener("click", () => {
        const modal = modalForm(
          "Ghi chú xử lý sự cố",
          `<div class="field full"><label for="incident-note">Nội dung</label><textarea id="incident-note" name="notes" rows="6" maxlength="4000">${esc(incident.notes)}</textarea></div>`,
        );
        modal.querySelector("form").onsubmit = (e) => {
          e.preventDefault();
          runForm(
            e.currentTarget,
            () =>
              request(`/incidents/${incident.id}`, {
                method: "PATCH",
                body: { notes: e.currentTarget.elements.notes.value },
              }),
            async () => {
              modal.close();
              await refresh();
            },
          );
        };
      });
    });
    if (w.maintenanceActive)
      document
        .querySelectorAll('[data-action="check"]')
        .forEach((b) => (b.disabled = true));
    try {
      const rows = await request(`/websites/${w.id}/maintenance`);
      const target = document.querySelector("#maintenance-list");
      if (target) {
        target.innerHTML = rows.length
          ? rows
              .map(
                (m) =>
                  `<div class="maintenance-row"><strong>${esc(m.title)}</strong><p>${esc(localDate(m.startsAt))} → ${esc(localDate(m.endsAt))}</p>${canEdit() ? `<button class="button small" data-remove-maintenance="${m.id}">Xóa lịch</button>` : ""}</div>`,
              )
              .join("")
          : '<p class="panel-note">Chưa có lịch bảo trì.</p>';
        target.querySelectorAll("[data-remove-maintenance]").forEach(
          (b) =>
            (b.onclick = async () => {
              b.disabled = true;
              try {
                await request(
                  `/websites/${w.id}/maintenance/${b.dataset.removeMaintenance}`,
                  { method: "DELETE" },
                );
                await refresh();
              } catch (ex) {
                b.textContent = ex.message;
                b.disabled = false;
              }
            }),
        );
      }
    } catch (ex) {
      const t = document.querySelector("#maintenance-list");
      if (t) t.textContent = ex.message;
    }
  }
  if (!canEdit())
    document
      .querySelectorAll(
        '[data-action="add"],[data-action="edit"],[data-action="archive"],[data-action="check"],[data-action="toggle"]',
      )
      .forEach((b) => b.remove());
}
function datetimeValue(date) {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 16);
}
function maintenanceForm(id, refresh) {
  const start = new Date();
  const modal = modalForm(
    "Thêm lịch bảo trì",
    `${field("title", "Tên lịch bảo trì", "", "text", 'required maxlength="200"')}${field("startsAt", "Bắt đầu (giờ địa phương)", datetimeValue(start), "datetime-local", "required")}${field("endsAt", "Kết thúc (giờ địa phương)", datetimeValue(new Date(start.getTime() + 3600000)), "datetime-local", "required")}<p class="help full">Trong khoảng bảo trì, hệ thống ngừng kiểm tra và không gửi cảnh báo gián đoạn.</p>`,
  );
  modal.querySelector("form").onsubmit = (e) => {
    e.preventDefault();
    const form = e.currentTarget;
    runForm(
      form,
      () =>
        request(`/websites/${id}/maintenance`, {
          method: "POST",
          body: {
            title: form.elements.title.value,
            startsAt: new Date(form.elements.startsAt.value).toISOString(),
            endsAt: new Date(form.elements.endsAt.value).toISOString(),
          },
        }),
      async () => {
        modal.close();
        await refresh();
      },
    );
  };
}
function download(blob, name) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
function importCsv(refresh) {
  const modal = modalForm(
    "Nhập website từ CSV",
    `<div class="field full"><label for="csv-file">File CSV UTF-8 (tối đa 500 dòng / 1 MB)</label><input id="csv-file" name="file" type="file" accept=".csv,text/csv" required><p class="help">Cột bắt buộc: url. Cột tùy chọn: name, groupName, environment, tags. Dùng dấu phẩy, đặt giá trị có dấu phẩy trong ngoặc kép.</p><a class="button" id="csv-template" href="/samples/websites-sample.csv" download="websites-sample.csv">Tải CSV mẫu</a></div><div class="full" id="csv-preview"></div>`,
    "Kiểm tra dữ liệu",
  );
  let content = "",
    approved = false;
  const form = modal.querySelector("form");
  form.elements.file.onchange = () => {
    approved = false;
    form.querySelector('[type="submit"]').textContent = "Kiểm tra dữ liệu";
    modal.querySelector("#csv-preview").innerHTML = "";
  };
  form.onsubmit = async (e) => {
    e.preventDefault();
    await runForm(
      form,
      async () => {
        if (approved) {
          const result = await request("/websites/import", {
            method: "POST",
            body: { csv: content },
          });
          modal.close();
          await refresh();
          return;
        }
        const file = form.elements.file.files[0];
        if (!file || file.size > 1000000)
          throw Error("Chọn file CSV tối đa 1 MB.");
        content = await file.text();
        const preview = await request("/websites/import/preview", {
          method: "POST",
          body: { csv: content },
        });
        modal.querySelector("#csv-preview").innerHTML = preview.errors.length
          ? `<div class="notice error">${preview.errors.map(esc).join("<br>")}</div>`
          : `<p><strong>${preview.websites.length} website hợp lệ.</strong> Kiểm tra danh sách trước khi nhập:</p><ul>${preview.websites.map((w) => `<li>${esc(w.name)} · ${esc(w.url)} · ${esc(w.groupName)} / ${esc(w.environment)}</li>`).join("")}</ul>`;
        approved = !preview.errors.length;
        form.querySelector('[type="submit"]').textContent = approved
          ? `Nhập ${preview.websites.length} website`
          : "Kiểm tra lại dữ liệu";
      },
      () => {},
    );
  };
}
