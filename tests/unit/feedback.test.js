import assert from "node:assert/strict";
import test from "node:test";
import {
  createFeedbackService,
  createMockFeedbackTransport,
  createFeedbackTransportPayload,
} from "../../src/js/application/feedback.js";
import {
  createLiveFeedbackTransport,
  FEEDBACK_RECEIVER_URL,
} from "../../src/js/application/feedback-transport.js";
import {
  collectFeedbackDiagnostics,
  createFeedbackReport,
  createFeedbackReportId,
  FEEDBACK_DIAGNOSTIC_ALLOWLIST,
  readFeedbackScreenshotDimensions,
  validateFeedbackScreenshotDimensions,
  validateFeedbackScreenshotFile,
} from "../../src/js/domain/feedback.js";
import {
  createFeedbackOutbox,
  FEEDBACK_OUTBOX_KEY,
} from "../../src/js/storage/feedback-outbox.js";

class MemoryStorage {
  values = new Map();
  failNextWrite = false;

  getItem(key) {
    return this.values.has(key) ? this.values.get(key) : null;
  }

  setItem(key, value) {
    if (this.failNextWrite) {
      this.failNextWrite = false;
      throw new Error("Simulated quota failure");
    }
    this.values.set(key, String(value));
  }
}

function reportDraft(overrides = {}) {
  return {
    category: "bug",
    message: "The save button did not respond.",
    diagnostics: null,
    screenshot: null,
    ...overrides,
  };
}

function reportOptions(id = "report-1") {
  return {
    createId: () => id,
    now: () => "2026-08-08T15:00:00.000Z",
  };
}

test("feedback IDs fall back to getRandomValues when randomUUID is unavailable", () => {
  const id = createFeedbackReportId({
    cryptoObject: {
      getRandomValues(bytes) {
        bytes.forEach((_, index) => {
          bytes[index] = index;
        });
        return bytes;
      },
    },
  });

  assert.equal(id, "00010203-0405-4607-8809-0a0b0c0d0e0f");
});

test("feedback IDs recover when an exposed randomUUID throws", () => {
  const id = createFeedbackReportId({
    cryptoObject: {
      randomUUID() {
        throw new Error("Unavailable outside a secure context");
      },
      getRandomValues(bytes) {
        bytes.fill(17);
        return bytes;
      },
    },
  });

  assert.match(
    id,
    /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/u,
  );
});

test("the portable last-resort feedback IDs remain valid and distinct", () => {
  const options = {
    cryptoObject: null,
    now: () => 1_786_240_000_000,
    random: () => 0.5,
  };
  const first = createFeedbackReportId(options);
  const second = createFeedbackReportId(options);

  assert.notEqual(first, second);
  assert.match(
    first,
    /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/u,
  );
  assert.match(
    second,
    /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/u,
  );
});

test("diagnostics use only the explicit safe allowlist", () => {
  const diagnostics = collectFeedbackDiagnostics({
    viewportWidth: 412,
    viewportHeight: 915,
    standalone: true,
    online: false,
    currentScreen: "feedback",
    accessToken: "must-not-appear",
    localStorage: "must-not-appear",
    url: "must-not-appear",
  });

  assert.deepEqual(
    Object.keys(diagnostics),
    Object.keys(FEEDBACK_DIAGNOSTIC_ALLOWLIST),
  );
  assert.deepEqual(diagnostics, {
    appVersion: "1.0.0",
    viewport: "412x915",
    displayMode: "installed",
    connection: "offline",
    currentScreen: "feedback",
  });
  assert.equal(JSON.stringify(diagnostics).includes("must-not-appear"), false);
});

test("reports reject diagnostics outside the allowlist", () => {
  assert.throws(
    () =>
      createFeedbackReport(
        reportDraft({
          diagnostics: {
            ...collectFeedbackDiagnostics({}),
            arbitraryStorage: "no",
          },
        }),
        reportOptions(),
      ),
    /unsupported data/u,
  );
});

test("the future transport payload exposes only the client contract", () => {
  const report = createFeedbackReport(
    reportDraft({ diagnostics: collectFeedbackDiagnostics({}) }),
    reportOptions(),
  );
  const payload = createFeedbackTransportPayload(report);

  assert.deepEqual(Object.keys(payload), [
    "schemaVersion",
    "id",
    "category",
    "message",
    "diagnostics",
    "screenshot",
    "createdAt",
  ]);
  assert.equal("attempts" in payload, false);
  assert.equal("lastFailure" in payload, false);
});

test("the live transport sends only the contract plus a fresh Turnstile token", async () => {
  const report = createFeedbackReport(
    reportDraft(),
    reportOptions("live-report"),
  );
  let request;
  const transport = createLiveFeedbackTransport({
    getTurnstileToken: async () => "turnstile-token",
    fetcher: async (url, options) => {
      request = { url, options };
      return Response.json({ ok: true });
    },
  });

  assert.deepEqual(await transport.send(report), { ok: true });
  assert.equal(request.url, FEEDBACK_RECEIVER_URL);
  assert.deepEqual(JSON.parse(request.options.body), {
    report: createFeedbackTransportPayload(report),
    turnstileToken: "turnstile-token",
  });
});

test("the live transport retains reports when the receiver does not confirm delivery", async () => {
  const report = createFeedbackReport(
    reportDraft(),
    reportOptions("live-failure"),
  );
  const transport = createLiveFeedbackTransport({
    getTurnstileToken: async () => "turnstile-token",
    fetcher: async () => Response.json({ ok: false }, { status: 503 }),
  });

  assert.deepEqual(await transport.send(report), {
    ok: false,
    code: "temporary_failure",
  });
});

test("the live transport preserves safe receiver failure codes for the local outbox", async () => {
  const report = createFeedbackReport(
    reportDraft(),
    reportOptions("live-challenge-failure"),
  );
  const transport = createLiveFeedbackTransport({
    getTurnstileToken: async () => "turnstile-token",
    fetcher: async () =>
      Response.json({ ok: false, code: "challenge_failed" }, { status: 403 }),
  });

  assert.deepEqual(await transport.send(report), {
    ok: false,
    code: "challenge_failed",
  });
});

test("screenshot validation accepts only safe image types and bounded source size", () => {
  assert.equal(
    validateFeedbackScreenshotFile({ type: "image/png", size: 1024 }).valid,
    true,
  );
  assert.deepEqual(
    validateFeedbackScreenshotFile({ type: "image/svg+xml", size: 1024 }),
    { valid: false, message: "Choose a PNG, JPEG, or WebP screenshot." },
  );
  assert.equal(
    validateFeedbackScreenshotFile({
      type: "image/png",
      size: 8 * 1024 * 1024 + 1,
    }).valid,
    false,
  );
});

function pngHeader(width, height) {
  const header = Buffer.alloc(24);
  Buffer.from("89504e470d0a1a0a", "hex").copy(header, 0);
  header.writeUInt32BE(13, 8);
  header.write("IHDR", 12, "ascii");
  header.writeUInt32BE(width, 16);
  header.writeUInt32BE(height, 20);
  return new Blob([header], { type: "image/png" });
}

function jpegHeader(width, height) {
  const header = Buffer.alloc(23);
  Buffer.from([0xff, 0xd8, 0xff, 0xc0, 0x00, 0x11, 0x08]).copy(header, 0);
  header.writeUInt16BE(height, 7);
  header.writeUInt16BE(width, 9);
  return new Blob([header], { type: "image/jpeg" });
}

function webpExtendedHeader(width, height) {
  const header = Buffer.alloc(30);
  header.write("RIFF", 0, "ascii");
  header.writeUInt32LE(22, 4);
  header.write("WEBP", 8, "ascii");
  header.write("VP8X", 12, "ascii");
  header.writeUInt32LE(10, 16);
  header.writeUIntLE(width - 1, 24, 3);
  header.writeUIntLE(height - 1, 27, 3);
  return new Blob([header], { type: "image/webp" });
}

test("bounded screenshot headers expose PNG, JPEG, and WebP dimensions", async () => {
  assert.deepEqual(
    await readFeedbackScreenshotDimensions(pngHeader(192, 192)),
    {
      width: 192,
      height: 192,
    },
  );
  assert.deepEqual(
    await readFeedbackScreenshotDimensions(jpegHeader(640, 480)),
    {
      width: 640,
      height: 480,
    },
  );
  assert.deepEqual(
    await readFeedbackScreenshotDimensions(webpExtendedHeader(800, 600)),
    { width: 800, height: 600 },
  );
});

test("oversized decoded dimensions are rejected from small accepted image headers", async () => {
  const images = [
    pngHeader(10_000, 10_000),
    jpegHeader(10_000, 10_000),
    webpExtendedHeader(10_000, 10_000),
  ];
  for (const image of images) {
    assert.ok(image.size < 100);
    assert.deepEqual(
      validateFeedbackScreenshotDimensions(
        await readFeedbackScreenshotDimensions(image),
      ),
      { valid: false, message: "That screenshot has unsupported dimensions." },
    );
  }
});

test("a failed send keeps the queued report and a successful retry removes it", async () => {
  const storage = new MemoryStorage();
  const outbox = createFeedbackOutbox(storage);
  const transport = createMockFeedbackTransport(["failure", "success"]);
  const times = [
    "2026-08-08T15:00:00.000Z",
    "2026-08-08T15:00:01.000Z",
    "2026-08-08T15:00:02.000Z",
  ];
  const service = createFeedbackService({
    outbox,
    transport,
    createId: () => "report-transaction",
    now: () => times.shift(),
  });

  const first = await service.submit(reportDraft());
  assert.equal(first.sent, false);
  assert.equal(first.code, "temporary_failure");
  assert.equal(outbox.list().length, 1);
  assert.equal(outbox.get("report-transaction").attempts, 1);
  assert.equal(
    outbox.get("report-transaction").lastFailure,
    "temporary_failure",
  );

  const retried = await service.retry("report-transaction");
  assert.equal(retried.sent, true);
  assert.equal(outbox.list().length, 0);
  assert.equal(transport.calls.length, 2);
});

test("a safe receiver failure code remains saved with the pending report", async () => {
  const outbox = createFeedbackOutbox(new MemoryStorage());
  const service = createFeedbackService({
    outbox,
    transport: {
      async send() {
        return { ok: false, code: "challenge_failed" };
      },
    },
    ...reportOptions("challenge-failure"),
  });

  const result = await service.submit(reportDraft());
  assert.equal(result.sent, false);
  assert.equal(result.code, "challenge_failed");
  assert.equal(
    outbox.get("challenge-failure")?.lastFailure,
    "challenge_failed",
  );
});

test("the report is committed before transport receives it", async () => {
  const storage = new MemoryStorage();
  const outbox = createFeedbackOutbox(storage);
  let existedDuringSend = false;
  const transport = {
    async send(report) {
      existedDuringSend = outbox.get(report.id)?.id === report.id;
      return { ok: false, code: "temporary_failure" };
    },
  };
  const service = createFeedbackService({
    outbox,
    transport,
    ...reportOptions("queued-first"),
  });

  await service.submit(reportDraft());
  assert.equal(existedDuringSend, true);
  assert.equal(outbox.get("queued-first")?.id, "queued-first");
});

test("a failed outbox write leaves the previous snapshot unchanged", () => {
  const storage = new MemoryStorage();
  const outbox = createFeedbackOutbox(storage);
  const first = createFeedbackReport(reportDraft(), reportOptions("existing"));
  outbox.save(first);
  const before = storage.getItem(FEEDBACK_OUTBOX_KEY);

  storage.failNextWrite = true;
  const second = createFeedbackReport(
    reportDraft({ message: "A second report remains in the form." }),
    reportOptions("second"),
  );
  assert.throws(() => outbox.save(second), /left unchanged/u);
  assert.equal(storage.getItem(FEEDBACK_OUTBOX_KEY), before);
  assert.deepEqual(
    outbox.list().map((report) => report.id),
    ["existing"],
  );
});

test("a confirmed send with failed local cleanup remains visible for safety", async () => {
  const storage = new MemoryStorage();
  const baseOutbox = createFeedbackOutbox(storage);
  const outbox = {
    list: () => baseOutbox.list(),
    get: (id) => baseOutbox.get(id),
    save: (report) => baseOutbox.save(report),
    remove() {
      throw new Error("Simulated cleanup failure");
    },
  };
  const service = createFeedbackService({
    outbox,
    transport: createMockFeedbackTransport(["success"]),
    ...reportOptions("cleanup-safe"),
  });

  const result = await service.submit(reportDraft());
  assert.equal(result.sent, false);
  assert.equal(result.code, "local_cleanup_failed");
  assert.equal(baseOutbox.get("cleanup-safe")?.id, "cleanup-safe");
});
