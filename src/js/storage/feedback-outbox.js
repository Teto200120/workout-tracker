import { isFeedbackReport } from "../domain/feedback.js";

export const FEEDBACK_OUTBOX_KEY = "hector_workout_feedback_outbox_v1";
export const FEEDBACK_OUTBOX_MAX_REPORTS = 5;

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function outboxError(message, code, cause) {
  const error = new Error(message, cause ? { cause } : undefined);
  error.code = code;
  return error;
}

export function createFeedbackOutbox(storage = globalThis.localStorage) {
  function read() {
    const rawValue = storage.getItem(FEEDBACK_OUTBOX_KEY);
    if (rawValue === null) return [];
    let reports;
    try {
      reports = JSON.parse(rawValue);
    } catch (cause) {
      throw outboxError(
        "Pending feedback could not be read. It was left unchanged.",
        "feedback_outbox_invalid",
        cause,
      );
    }
    if (!Array.isArray(reports) || !reports.every(isFeedbackReport)) {
      throw outboxError(
        "Pending feedback has an unsupported format. It was left unchanged.",
        "feedback_outbox_invalid",
      );
    }
    return clone(reports);
  }

  function commit(reports) {
    try {
      storage.setItem(FEEDBACK_OUTBOX_KEY, JSON.stringify(reports));
    } catch (cause) {
      throw outboxError(
        "Pending feedback could not be saved. Existing reports were left unchanged.",
        "feedback_outbox_write_failed",
        cause,
      );
    }
  }

  return Object.freeze({
    list() {
      return read().sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    },
    get(id) {
      return read().find((report) => report.id === id) || null;
    },
    save(report) {
      if (!isFeedbackReport(report)) {
        throw outboxError(
          "That feedback report cannot be saved.",
          "feedback_report_invalid",
        );
      }
      const reports = read();
      const index = reports.findIndex((candidate) => candidate.id === report.id);
      if (index === -1) {
        if (reports.length >= FEEDBACK_OUTBOX_MAX_REPORTS) {
          throw outboxError(
            `This device already has ${FEEDBACK_OUTBOX_MAX_REPORTS} pending reports. Retry or delete one before adding another.`,
            "feedback_outbox_full",
          );
        }
        reports.push(clone(report));
      } else {
        reports[index] = clone(report);
      }
      commit(reports);
      return clone(report);
    },
    remove(id) {
      const reports = read();
      const nextReports = reports.filter((report) => report.id !== id);
      if (nextReports.length === reports.length) return false;
      commit(nextReports);
      return true;
    },
  });
}
