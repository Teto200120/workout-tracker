import assert from "node:assert/strict";
import test from "node:test";
import {
  createFeedbackService,
  createMockFeedbackTransport,
  createFeedbackTransportPayload,
} from "../../src/js/application/feedback.js";
import {
  collectFeedbackDiagnostics,
  createFeedbackReport,
  createFeedbackReportId,
  FEEDBACK_DIAGNOSTIC_ALLOWLIST,
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
