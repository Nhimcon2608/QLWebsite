const escape = (s) =>
  String(s).replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
const number = (n) =>
  new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 1 }).format(n);
export function responseChart(metrics, label = "Thời gian phản hồi") {
  if (!metrics.sampleCount)
    return '<div class="chart-empty"><span>⌁</span><strong>Chưa có dữ liệu trong khoảng này</strong><p>Biểu đồ sẽ xuất hiện sau khi có kết quả kiểm tra.</p></div>';
  const points = metrics.points,
    values = points.filter((p) => p.responseTimeMs !== null);
  const compact = window.matchMedia("(max-width: 700px)").matches;
  const missingResponse = points.some(
    (p) => p.responseTimeMs === null && p.failureCount > 0,
  );
  const width = compact ? 360 : 900,
    height = 230,
    left = compact ? 42 : 52,
    top = 18,
    right = 22,
    bottom = missingResponse ? 48 : 32;
  const max = Math.max(100, ...values.map((p) => p.responseTimeMs)) * 1.2;
  const from = Date.parse(metrics.from),
    to = Date.parse(metrics.to);
  const x = (p) =>
    left +
    ((Date.parse(p.at) - from) / Math.max(1, to - from)) *
      (width - left - right);
  const y = (value) => top + (1 - value / max) * (height - top - bottom);
  let line = "",
    open = false;
  for (const p of points) {
    if (p.responseTimeMs === null) {
      open = false;
      continue;
    }
    line += `${open ? "L" : "M"}${x(p).toFixed(1)},${y(p.responseTimeMs).toFixed(1)} `;
    open = true;
  }
  const grids = Array.from({ length: 4 }, (_, i) => {
    const value = (max * i) / 3;
    return `<line x1="${left}" x2="${width - right}" y1="${y(value)}" y2="${y(value)}" class="chart-grid"/><text x="${left - 12}" y="${y(value) + 4}" text-anchor="end">${number(value)}</text>`;
  }).join("");
  const tickCount = compact ? 3 : 5;
  const ticks = Array.from({ length: tickCount }, (_, i) => {
    const date = new Date(from + ((to - from) * i) / (tickCount - 1));
    const text =
      to - from > 86400000
        ? date.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" })
        : date.toLocaleTimeString("vi-VN", {
            hour: "2-digit",
            minute: "2-digit",
          });
    return `<text x="${left + ((width - left - right) * i) / (tickCount - 1)}" y="${height - 5}" text-anchor="${i === 0 ? "start" : i === tickCount - 1 ? "end" : "middle"}">${text}</text>`;
  }).join("");
  const dots = points
    .map((p) => {
      const failed = p.failureCount > 0;
      if (p.responseTimeMs === null && !failed) return "";
      const tooltip = `${new Date(p.at).toLocaleString("vi-VN")} · ${p.responseTimeMs === null ? "Không có phản hồi" : number(p.responseTimeMs) + " ms"} · ${p.sampleCount} mẫu, ${p.failureCount} lỗi`;
      return `<circle tabindex="0" role="img" aria-label="${escape(tooltip)}" data-tooltip="${escape(tooltip)}" cx="${x(p)}" cy="${p.responseTimeMs === null ? height - bottom + 17 : y(p.responseTimeMs)}" r="${failed ? 4 : compact ? 2 : 3}" class="chart-point ${failed ? "failed" : ""}"><title>${escape(tooltip)}</title></circle>`;
    })
    .join("");
  return `<div class="chart-wrap"><span class="chart-unit">ms</span><svg viewBox="0 0 ${width} ${height}" role="group" aria-label="${escape(label)}. ${metrics.sampleCount} mẫu. Điểm thiếu phản hồi được ngắt quãng.">${grids}${missingResponse ? `<text x="${left - 12}" y="${height - bottom + 20}" text-anchor="end">Lỗi</text>` : ""}<path d="${line}" class="chart-line"/>${dots}${ticks}</svg><div class="chart-tooltip" role="status" hidden></div></div>`;
}
export function bindChart(container) {
  const tooltip = container.querySelector(".chart-tooltip");
  if (!tooltip) return;
  container.querySelectorAll(".chart-point").forEach((point) => {
    const show = () => {
      tooltip.textContent = point.dataset.tooltip;
      tooltip.hidden = false;
    };
    const hide = () => {
      tooltip.hidden = true;
    };
    point.addEventListener("mouseenter", show);
    point.addEventListener("mouseleave", hide);
    point.addEventListener("focus", show);
    point.addEventListener("blur", hide);
  });
}
