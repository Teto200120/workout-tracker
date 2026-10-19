import { setDataRecoveryHandler } from "../storage/operation.js";
import {
  readRecoverySnapshot,
  writeRecoverySnapshot,
  removeRecoverySnapshot,
  replaceApplicationRecords,
} from "../storage/indexed-db.js";
import {
  restoreApplicationLocalStorage,
  restoreRawEducationRecord,
} from "../storage/local.js";
import { forgetEducationState } from "./education.js";
import { DataSchemaError } from "../schema/errors.js";

function requireRecoveryLock() {
  if (!globalThis.navigator?.locks?.request) {
    throw new DataSchemaError(
      "This browser cannot coordinate recovery across tabs.",
      { code: "recovery_lock_unavailable" },
    );
  }
}

export function validateRecoverySnapshot(snapshot) {
  if (
    snapshot?.id !== "application" ||
    snapshot.version !== 1 ||
    !snapshot.records ||
    !["workouts", "legacyWeights", "routines"].every((field) =>
      Array.isArray(snapshot.records[field]),
    ) ||
    !snapshot.localSnapshot ||
    ![
      "settings",
      "goals",
      "draft",
      "backupMeta",
      "applicationSchemaVersion",
    ].every(
      (field) =>
        snapshot.localSnapshot[field] === null ||
        typeof snapshot.localSnapshot[field] === "string",
    ) ||
    !(snapshot.education === null || typeof snapshot.education === "string")
  ) {
    throw new Error("Recovery snapshot is unsupported or incomplete.");
  }
  return snapshot;
}

// The undo snapshot commits before either storage system changes. Every outer
// writer checks for it under the same Web Lock, including writers in other tabs.
// Success is acknowledged only after both systems finish and this record clears.
export async function beginRecoverySnapshot(snapshot, operation) {
  requireRecoveryLock();
  const entry = validateRecoverySnapshot({
    ...snapshot,
    id: "application",
    version: 1,
  });
  if (await readRecoverySnapshot())
    throw new Error("Recovery must finish before another change starts.");
  try {
    await writeRecoverySnapshot(entry, { operation });
  } catch (cause) {
    throw new DataSchemaError(
      "Recovery snapshot could not be saved before the change.",
      { code: "recovery_prepare_failed", cause },
    );
  }
}

export function finishRecoverySnapshot(operation) {
  return removeRecoverySnapshot({ operation });
}

async function recoverPendingOperation(operation) {
  let snapshot;
  try {
    snapshot = await readRecoverySnapshot();
    if (!snapshot) return;
    requireRecoveryLock();
    validateRecoverySnapshot(snapshot);
    await replaceApplicationRecords(snapshot.records, {
      validate: false,
      operation,
    });
    await restoreApplicationLocalStorage(snapshot.localSnapshot, { operation });
    await restoreRawEducationRecord(snapshot.education, { operation });
    forgetEducationState();
    await finishRecoverySnapshot(operation);
  } catch (cause) {
    throw new DataSchemaError(
      "An interrupted data operation could not be recovered.",
      {
        code: "recovery_required",
        cause,
        rollbackErrors: [cause],
      },
    );
  }
}

export function enableDataRecovery() {
  setDataRecoveryHandler(recoverPendingOperation);
}
