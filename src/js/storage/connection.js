import { DataSchemaError } from "../schema/errors.js";

// A blocked open cannot be cancelled. Reject promptly, then abort a late upgrade
// or close a late connection so an abandoned startup never retains a handle.
export function openManagedDatabase({
  factory,
  name,
  version,
  upgrade,
  onUnavailable,
}) {
  return new Promise((resolve, reject) => {
    const request = factory.open(name, version);
    let abandoned = false;
    request.onblocked = () => {
      abandoned = true;
      reject(
        new DataSchemaError("Database upgrade is blocked by another tab.", {
          code: "database_upgrade_blocked",
        }),
      );
    };
    request.onupgradeneeded = (event) => {
      if (abandoned) {
        request.transaction.abort();
        return;
      }
      upgrade(event);
    };
    request.onerror = () => reject(request.error);
    request.onsuccess = () => {
      const connection = request.result;
      if (abandoned) {
        connection.close();
        return;
      }
      connection.onversionchange = () => {
        connection.close();
        onUnavailable(connection, "versionchange");
      };
      connection.onclose = () => onUnavailable(connection, "closed");
      resolve(connection);
    };
  });
}
