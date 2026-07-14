export class DataSchemaError extends Error {
  constructor(message, options = {}) {
    super(message, options.cause ? { cause: options.cause } : undefined);
    this.name = "DataSchemaError";
    this.code = options.code || "data_schema_error";
    this.path = options.path || "";
    this.source = options.source || "application";
    this.fromVersion = options.fromVersion ?? null;
    this.toVersion = options.toVersion ?? null;
    this.validationErrors = options.validationErrors || [];
    this.rollbackErrors = options.rollbackErrors || [];
  }
}

export function createValidationError(errors, options = {}) {
  const first = errors[0];
  const location = first?.path ? ` at ${first.path}` : "";
  return new DataSchemaError(`Persisted data validation failed${location}.`, {
    ...options,
    code: options.code || "schema_validation_failed",
    path: first?.path || options.path || "",
    validationErrors: errors
  });
}

export function startupFailureMessage(error) {
  if (error?.code === "future_application_schema_version") {
    return "Stored data was created by a newer app version. Update the tracker before continuing.";
  }
  if (error?.code === "invalid_application_schema_version") {
    return "Stored data has an invalid schema version. Your data was left unchanged.";
  }
  return "Stored data could not be migrated. Your data was left unchanged.";
}

export function backupFailureMessage(error) {
  if (error?.code === "future_backup_file_version" || error?.code === "future_application_schema_version") {
    return "Could not import backup. It was created by a newer app version.";
  }
  if (error?.code === "backup_restore_failed") {
    return "Could not import backup. Existing data was left unchanged.";
  }
  if (error?.code === "backup_guardrail_failed") {
    const message = error.validationErrors?.[0]?.message;
    return message
      ? `Could not import backup. ${message}`
      : "Could not import backup. The file exceeds the app's safety limits.";
  }
  return "Could not import backup. The file is malformed or unsupported.";
}
