import assert from "node:assert/strict";
import test from "node:test";
import {
  DataSchemaError,
  backupFailureMessage,
  startupFailureMessage,
} from "../../src/js/schema/errors.js";

test("failed compensation never promises unchanged data", () => {
  const error = new DataSchemaError("failure", {
    code: "backup_restore_failed",
    rollbackErrors: [new Error("rollback failed")],
  });
  assert.match(backupFailureMessage(error), /Some data may have changed/);
  assert.match(startupFailureMessage(error), /do not clear storage/);
});

test("clear failure reports restoration only after successful compensation", () => {
  assert.match(
    backupFailureMessage(
      new DataSchemaError("failure", { code: "data_clear_failed" }),
    ),
    /previous data was restored/,
  );
});
