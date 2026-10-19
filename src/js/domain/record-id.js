// DOM datasets are strings; preserve the original IndexedDB key type.
export function decodeRecordId(value, type = "string") {
  if (type === "number") {
    if (
      typeof value !== "string" ||
      !value.trim() ||
      !Number.isFinite(Number(value))
    ) {
      throw new TypeError("Invalid numeric record ID.");
    }
    return Number(value);
  }
  if (type !== "string" || typeof value !== "string" || !value.length) {
    throw new TypeError("Invalid record ID.");
  }
  return value;
}
