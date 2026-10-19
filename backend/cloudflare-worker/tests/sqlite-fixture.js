import { URL } from "node:url";
import { DatabaseSync } from "node:sqlite";
import { readFileSync, readdirSync } from "node:fs";

export function database() {
  const db = new DatabaseSync(":memory:");
  const migrations = new URL("../migrations/", import.meta.url);
  for (const file of readdirSync(migrations).sort())
    db.exec(readFileSync(new URL(file, migrations), "utf8"));
  return {
    db,
    prepare(sql) {
      const statement = db.prepare(sql);
      const bound = (values) => ({
        first: async () => statement.get(...values) || null,
        all: async () => ({ results: statement.all(...values) }),
        run: async () => ({ meta: statement.run(...values) }),
        execute: () => statement.run(...values),
      });
      return { ...bound([]), bind: (...values) => bound(values) };
    },
    async batch(items) {
      db.exec("BEGIN");
      try {
        const results = items.map((item) => item.execute());
        db.exec("COMMIT");
        return results;
      } catch (error) {
        db.exec("ROLLBACK");
        throw error;
      }
    },
  };
}
