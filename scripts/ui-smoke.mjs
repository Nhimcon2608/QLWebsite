// Start Spring Boot first. Optional: BASE_URL, PLAYWRIGHT_MODULE, CHROME_PATH.
import assert from "node:assert/strict";
import { mkdir } from "node:fs/promises";
const { chromium } = await import(
  process.env.PLAYWRIGHT_MODULE || "playwright"
);
const browser = await chromium.launch(
  process.env.CHROME_PATH
    ? { executablePath: process.env.CHROME_PATH, headless: true }
    : { headless: true },
);
const base = process.env.BASE_URL || "http://127.0.0.1:8080";
const errors = [],
  results = [];
const context = await browser.newContext({
  viewport: { width: 1440, height: 1050 },
});
const page = await context.newPage();
page.on("pageerror", (error) => errors.push(error.message));
const go = async (path) => {
  await page.goto(base + path);
  await page
    .locator("#last-updated")
    .filter({ hasText: "Cập nhật lúc" })
    .waitFor();
};
const done = (name) => {
  results.push(name);
  console.log("PASS", name);
};
const submit = async () => {
  await page.locator("#website-form button[type=submit]").click();
  await page.locator("#modal").waitFor({ state: "hidden" });
};
const search = async (value) => {
  await page.getByRole("searchbox").fill(value);
  await page.waitForTimeout(400);
};
try {
  await go("/");
  await page.locator(".chart-line").waitFor();
  assert.equal(await page.locator(".stat").count(), 4);
  await page.getByRole("button", { name: "7 ngày", exact: true }).click();
  await page.locator('[data-range="7d"][aria-pressed="true"]').waitFor();
  await page.locator("#chart-website").selectOption("4");
  await page.locator(".chart-point.failed").first().waitFor();
  await page.locator(".chart-point.failed").first().focus();
  assert.equal(await page.locator(".chart-tooltip").isVisible(), true);
  done("Dashboard, time range, website selection, keyboard chart tooltip");
  await mkdir("target/ui-screenshots", { recursive: true });
  await page.getByRole("button", { name: "24 giờ", exact: true }).click();
  await page.locator("#chart-website").selectOption("1");
  await page.screenshot({
    path: "target/ui-screenshots/dashboard.png",
    fullPage: true,
  });
  await go("/websites");
  await page.getByRole("button", { name: "Thêm website", exact: true }).click();
  await page.locator("#website-form button[type=submit]").click();
  assert.equal(
    await page.locator("#field-name").getAttribute("aria-invalid"),
    "true",
  );
  await page
    .getByLabel("Tên website", { exact: true })
    .fill("Website kiểm thử");
  await page
    .getByLabel("Địa chỉ URL", { exact: true })
    .fill("javascript:alert(1)");
  await page.locator("#website-form button[type=submit]").click();
  assert.equal(
    await page.locator("#field-url").getAttribute("aria-invalid"),
    "true",
  );
  await page
    .getByLabel("Địa chỉ URL", { exact: true })
    .fill("https://test.example.com");
  await page.locator("#field-slowThresholdMs").fill("6000");
  await page.locator("#website-form button[type=submit]").click();
  assert.equal(
    await page.locator("#field-slowThresholdMs").getAttribute("aria-invalid"),
    "true",
  );
  await page.locator("#field-slowThresholdMs").fill("2000");
  await submit();
  await search("Website kiểm thử");
  const row = page.locator("tbody tr").filter({ hasText: "Website kiểm thử" });
  await row.waitFor();
  assert.match(await row.innerText(), /Chưa xác định/);
  await page.reload();
  await page
    .locator("#last-updated")
    .filter({ hasText: "Cập nhật lúc" })
    .waitFor();
  await search("Website kiểm thử");
  await row.waitFor();
  done("Form validation, create UNKNOWN, local persistence after reload");
  await row
    .getByRole("button", { name: "Kiểm tra Website kiểm thử", exact: true })
    .click();
  await row.locator(".status").filter({ hasText: "Hoạt động" }).waitFor();
  await row
    .getByRole("button", { name: "Tạm dừng Website kiểm thử", exact: true })
    .click();
  await row.locator(".status").filter({ hasText: "Tạm dừng" }).waitFor();
  assert.equal(
    await row
      .getByRole("button", { name: "Kiểm tra Website kiểm thử", exact: true })
      .isDisabled(),
    true,
  );
  await row
    .getByRole("button", { name: "Bật Website kiểm thử", exact: true })
    .click();
  await row.locator(".status").filter({ hasText: "Chưa xác định" }).waitFor();
  await row
    .getByRole("button", { name: "Sửa Website kiểm thử", exact: true })
    .click();
  await page.locator("#field-name").fill("Website đã chỉnh sửa");
  await submit();
  await search("Website đã chỉnh sửa");
  await page
    .getByRole("link", { name: "Website đã chỉnh sửa", exact: true })
    .click();
  await page
    .getByRole("heading", { name: "Website đã chỉnh sửa", exact: true })
    .waitFor();
  await page
    .getByRole("heading", { name: "Lịch sử kiểm tra", exact: true })
    .waitFor();
  assert.equal(await page.locator("tbody tr").count(), 1);
  done("Manual demo check, pause/resume, edit, detail and persisted history");
  await page.getByRole("button", { name: "Lưu trữ", exact: true }).click();
  await page
    .getByRole("button", { name: "Lưu trữ website", exact: true })
    .click();
  await page.waitForURL(base + "/websites");
  await search("Website đã chỉnh sửa");
  await page
    .getByRole("heading", { name: "Không tìm thấy website", exact: true })
    .waitFor();
  done("Archive confirmation, redirect, removal from active list");
  await go("/websites?status=DOWN");
  assert.equal(await page.locator("tbody tr").count(), 1);
  await page.getByRole("link", { name: "Dịch vụ API", exact: true }).click();
  await page.locator("#history-result").waitFor();
  await page.locator("#history-result").selectOption("FAILURE");
  await page.waitForTimeout(100);
  const rows = await page.locator("tbody tr").allTextContents();
  assert(rows.length > 0 && rows.every((row) => row.includes("Thất bại")));
  await page.locator("#history-result").selectOption("");
  await page
    .locator('[data-page-kind="historyPage"][aria-label="Trang sau"]')
    .click();
  await page
    .locator(
      '[data-page-kind="historyPage"][aria-label="Trang trước"]:not(:disabled)',
    )
    .waitFor();
  await page.getByRole("button", { name: "1 giờ", exact: true }).click();
  await page
    .locator(
      '[data-page-kind="historyPage"][aria-label="Trang trước"]:disabled',
    )
    .waitFor();
  await page.screenshot({
    path: "target/ui-screenshots/detail.png",
    fullPage: true,
  });
  done("DOWN filter, failure history, pagination and reset on range change");
  await page.goto(base + "/websites/not-found");
  await page
    .getByRole("heading", { name: "Không tìm thấy website", exact: true })
    .waitFor();
  done("Missing or archived website state");
  await go("/alerts");
  assert((await page.locator(".alert-item").count()) >= 2);
  await page.goto(base + "/login");
  await page.getByRole("button", { name: "Mở dashboard demo" }).click();
  await page.waitForURL(base + "/");
  done("Alerts and demo welcome flow");
  await page.setViewportSize({ width: 390, height: 844 });
  await go("/");
  if (
    await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)
  ) {
    console.log(
      "OVERFLOW",
      await page.evaluate(() =>
        [...document.querySelectorAll("body *")]
          .filter((e) => e.getBoundingClientRect().right > innerWidth + 1)
          .map((e) => [e.tagName, e.className, e.getBoundingClientRect().width])
          .slice(0, 25),
      ),
    );
    await page.screenshot({
      path: "target/ui-screenshots/overflow.png",
      fullPage: true,
    });
  }
  assert(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  );
  await page
    .getByRole("button", { name: "Mở điều hướng", exact: true })
    .click();
  await page.locator(".sidebar.open").waitFor();
  await page
    .locator(".nav")
    .getByRole("link", { name: "Quản lý website" })
    .click();
  await page.getByRole("searchbox").waitFor();
  assert(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  );
  await page.getByRole("button", { name: "Thêm website", exact: true }).click();
  await page.locator("#field-name").waitFor();
  assert(
    await page.evaluate(
      () =>
        document.querySelector("#modal").getBoundingClientRect().width <=
        innerWidth,
    ),
  );
  await page.keyboard.press("Escape");
  await page.screenshot({
    path: "target/ui-screenshots/mobile.png",
    fullPage: true,
  });
  done("Mobile navigation, no horizontal page overflow, responsive dialog");
  const apiSnapshot = await page.evaluate(async () => {
    const { request } = await import("/js/data.js");
    const params = {
      from: new Date(Date.now() - 86400000).toISOString(),
      to: new Date().toISOString(),
      bucket: "30m",
    };
    return {
      summary: await request("/dashboard/summary", { params }),
      websites: await request("/websites", { params: { size: 100 } }),
      alerts: await request("/alerts"),
      metrics: await request("/websites/1/metrics", { params }),
    };
  });
  // Inject edge-case fixtures into this isolated browser context, never user storage.
  await page.evaluate(() => {
    const key = "qlwebsite.demo.v1",
      d = JSON.parse(localStorage.getItem(key));
    d.websites.forEach((w) => (w.deletedAt = new Date().toISOString()));
    d.alerts = [];
    localStorage.setItem(key, JSON.stringify(d));
  });
  await go("/");
  await page
    .getByRole("heading", { name: "Bắt đầu với website đầu tiên" })
    .waitFor();
  await page
    .getByText("Chưa có dữ liệu trong khoảng này", { exact: true })
    .waitFor();
  done("Empty dashboard and chart");
  const apiContext = await browser.newContext({
    viewport: { width: 1280, height: 900 },
  });
  const apiPage = await apiContext.newPage();
  await apiPage.clock.install();
  apiPage.on("pageerror", (error) => errors.push(error.message));
  let failRead = false,
    csrfHeader,
    readCount = 0;
  await apiPage.route(
    (url) =>
      url.origin === new URL(base).origin &&
      ["/", "/login"].includes(url.pathname),
    async (route) => {
      const response = await route.fetch();
      const html = (await response.text())
        .replace('data-demo="true"', 'data-demo="false"')
        .replace(
          "</head>",
          '<meta name="_csrf" content="test-csrf"><meta name="_csrf_header" content="X-CSRF-TOKEN"></head>',
        );
      await route.fulfill({ response, body: html });
    },
  );
  await apiPage.route("**/api/**", async (route) => {
    const request = route.request(),
      path = new URL(request.url()).pathname;
    if (request.method() === "GET") readCount++;
    if (request.method() === "POST" && path === "/api/websites") {
      csrfHeader = request.headers()["x-csrf-token"];
      return route.fulfill({
        status: 400,
        json: {
          message: "Dữ liệu không hợp lệ.",
          fieldErrors: { url: "URL đã tồn tại trên máy chủ." },
        },
      });
    }
    if (failRead)
      return route.fulfill({
        status: 503,
        json: { message: "Máy chủ tạm thời không sẵn sàng." },
      });
    const data = path.endsWith("/summary")
      ? apiSnapshot.summary
      : path === "/api/websites"
        ? apiSnapshot.websites
        : path === "/api/alerts"
          ? apiSnapshot.alerts
          : apiSnapshot.metrics;
    return route.fulfill({ json: data });
  });
  await apiPage.goto(base + "/");
  await apiPage
    .locator("#last-updated")
    .filter({ hasText: "Cập nhật lúc" })
    .waitFor();
  await apiPage
    .getByRole("button", { name: "Thêm website", exact: true })
    .click();
  const readsWhileEditing = readCount;
  await apiPage.clock.fastForward(11000);
  assert.equal(readCount, readsWhileEditing);
  await apiPage.keyboard.press("Escape");
  const pollResponse = apiPage.waitForResponse((response) =>
    response.url().includes("/api/dashboard/summary"),
  );
  await apiPage.clock.fastForward(11000);
  await pollResponse;
  await apiPage.locator(".chart-line").waitFor();
  assert(readCount > readsWhileEditing);
  done("Polling pauses during editing and resumes after closing the dialog");
  assert.equal(
    await apiPage.evaluate(() => localStorage.getItem("qlwebsite.demo.v1")),
    null,
  );
  const before = await apiPage.locator(".stats").innerText();
  failRead = true;
  await apiPage.getByRole("button", { name: "Làm mới", exact: true }).click();
  await apiPage
    .locator("#error")
    .getByText("Máy chủ tạm thời không sẵn sàng.", { exact: true })
    .waitFor();
  assert.equal(await apiPage.locator(".stats").innerText(), before);
  done(
    "REST adapter uses API only and preserves last statuses after server error",
  );
  failRead = false;
  await apiPage.getByRole("button", { name: "Thử lại", exact: true }).click();
  await apiPage.locator("#error .notice").waitFor({ state: "hidden" });
  await apiPage
    .getByRole("button", { name: "Thêm website", exact: true })
    .click();
  await apiPage.locator("#field-name").fill("API test");
  await apiPage.locator("#field-url").fill("https://api-test.example.com");
  await apiPage.locator("#website-form button[type=submit]").click();
  await apiPage
    .getByText("URL đã tồn tại trên máy chủ.", { exact: true })
    .waitFor();
  assert.equal(csrfHeader, "test-csrf");
  assert.equal(
    await apiPage.locator("#field-url").getAttribute("aria-invalid"),
    "true",
  );
  await apiPage.keyboard.press("Escape");
  done("REST write sends CSRF and displays server field validation");
  await apiPage.unroute("**/api/**");
  await apiPage.route("**/api/**", (route) =>
    route.fulfill({
      status: 401,
      json: { message: "Phiên đăng nhập đã hết hạn." },
    }),
  );
  await apiPage.getByRole("button", { name: "Làm mới", exact: true }).click();
  await apiPage.waitForURL(base + "/login");
  await apiPage
    .getByLabel("Tên đăng nhập", { exact: true })
    .fill("invalid-user");
  await apiPage
    .getByLabel("Mật khẩu", { exact: true })
    .fill("invalid-password");
  await apiPage.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await apiPage
    .getByRole("alert")
    .getByText("Phiên đăng nhập đã hết hạn.", { exact: true })
    .waitFor();
  await apiContext.close();
  done(
    "Concurrent REST 401 redirects once and login displays credential errors",
  );
  assert.deepEqual(errors, []);
  console.log(
    `\n${results.length} UI groups passed. Screenshots: target/ui-screenshots/`,
  );
} finally {
  await browser.close();
}
