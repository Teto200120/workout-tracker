import { expect, test } from "@playwright/test";
import { loadApp, readStore, seedStores } from "../helpers/app.js";

const routine = {
  id: "shared-routine",
  name: "Shared routine",
  exercises: ["Flat Bench Press"],
  createdAt: "2026-01-01T12:00:00.000Z",
  updatedAt: "2026-01-01T12:00:00.000Z",
  extension: { keep: true },
};

async function openEditor(page) {
  await page.evaluate(() =>
    globalThis.document
      .querySelector(
        '[data-routine-action="edit"][data-template-id="shared-routine"]',
      )
      .click(),
  );
  await expect(page.locator("#templateName")).toHaveValue(routine.name);
}

for (const change of ["updated", "deleted"]) {
  test(`a stale routine editor preserves its draft after the saved routine is ${change}`, async ({
    page,
    context,
  }) => {
    await loadApp(page);
    await seedStores(page, { templates: [routine] });
    await openEditor(page);
    await page.locator("#templateName").fill("My unsaved changes");
    const other = await context.newPage();
    await loadApp(other);
    await other.evaluate(
      async ({ record, change }) => {
        const storage = await import("/src/js/storage/indexed-db.js");
        if (change === "deleted") await storage.deleteRoutine(record.id);
        else
          await storage.saveRoutine({ ...record, name: "Newer saved routine" });
      },
      { record: routine, change },
    );
    const saved = await page.evaluate(async () =>
      (await import("/src/js/screens/routines.js")).saveTemplate(),
    );
    expect(saved).toBe(false);
    await expect(page.locator("#toast")).toContainText(
      "This routine changed or was deleted",
    );
    await expect(page.locator("#templateName")).toHaveValue(
      "My unsaved changes",
    );
    const persisted = (await readStore(page, "templates")).find(
      (item) => item.id === routine.id,
    );
    if (change === "deleted") expect(persisted).toBeUndefined();
    else expect(persisted.name).toBe("Newer saved routine");
    await other.close();
  });
}

test("stale library delete cannot remove a changed routine", async ({
  page,
}) => {
  await loadApp(page);
  await seedStores(page, { templates: [routine] });
  page.on("dialog", (dialog) => dialog.accept());
  await page.evaluate(async (record) => {
    const storage = await import("/src/js/storage/indexed-db.js");
    await storage.saveRoutine({ ...record, exercises: ["Squat"] });
    globalThis.document
      .querySelector(
        '[data-routine-action="delete"][data-template-id="shared-routine"]',
      )
      .click();
  }, routine);
  await expect(page.locator("#toast")).toContainText(
    "Refresh the library before deleting",
  );
  expect(
    (await readStore(page, "templates")).find((item) => item.id === routine.id)
      .exercises,
  ).toEqual(["Squat"]);
});

test("a current routine edit preserves extension fields", async ({ page }) => {
  await loadApp(page);
  await seedStores(page, { templates: [routine] });
  await openEditor(page);
  await page.locator("#templateName").fill("Renamed routine");
  expect(
    await page.evaluate(async () =>
      (await import("/src/js/screens/routines.js")).saveTemplate(),
    ),
  ).toBe(true);
  const saved = (await readStore(page, "templates")).find(
    (item) => item.id === routine.id,
  );
  expect(saved.name).toBe("Renamed routine");
  expect(saved.createdAt).toBe(routine.createdAt);
  expect(saved.extension).toEqual(routine.extension);
});

test("two conditional saves from one snapshot have only one winner", async ({
  page,
  context,
}) => {
  await loadApp(page);
  await seedStores(page, { templates: [routine] });
  const other = await context.newPage();
  await loadApp(other);
  const save = (tab, name) =>
    tab.evaluate(
      async ({ record, name }) => {
        const storage = await import("/src/js/storage/indexed-db.js");
        try {
          await storage.saveRoutine({ ...record, name }, { expected: record });
          return "saved";
        } catch (error) {
          return error.code;
        }
      },
      { record: routine, name },
    );
  expect(
    (
      await Promise.all([save(page, "First edit"), save(other, "Second edit")])
    ).sort(),
  ).toEqual(["saved", "stale_record"]);
  await other.close();
});
