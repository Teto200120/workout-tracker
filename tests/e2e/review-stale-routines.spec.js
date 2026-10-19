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

test("competing routine creates with different IDs cannot claim the same normalized name", async ({
  page,
  context,
}) => {
  await loadApp(page);
  const other = await context.newPage();
  await loadApp(other);
  const create = (tab, id, name) =>
    tab.evaluate(
      async ({ record, id, name }) => {
        const storage = await import("/src/js/storage/indexed-db.js");
        try {
          await storage.saveRoutine(
            { ...record, id, name },
            { expected: null },
          );
          return "saved";
        } catch (error) {
          return error.code;
        }
      },
      { record: routine, id, name },
    );
  expect(
    (
      await Promise.all([
        create(page, "first-new", "Concurrent routine"),
        create(other, "second-new", " concurrent ROUTINE "),
      ])
    ).sort(),
  ).toEqual(["routine_name_conflict", "saved"]);
  expect(
    (await readStore(page, "templates")).filter((item) =>
      ["first-new", "second-new"].includes(item.id),
    ),
  ).toHaveLength(1);
  await other.close();
});

test("the final library slot has one winner and existing routines remain editable at capacity", async ({
  page,
  context,
}) => {
  await loadApp(page);
  const limit = await page.evaluate(async (record) => {
    const { INPUT_LIMITS } = await import("/src/js/domain/input-guardrails.js");
    const storage = await import("/src/js/storage/indexed-db.js");
    await storage.replaceApplicationRecords({
      workouts: [],
      legacyWeights: [],
      routines: Array.from(
        { length: INPUT_LIMITS.routines - 1 },
        (_, index) => ({
          ...record,
          id: `capacity-${index}`,
          name: `Capacity ${index}`,
        }),
      ),
    });
    return INPUT_LIMITS.routines;
  }, routine);
  const other = await context.newPage();
  await loadApp(other);
  const create = (tab, id) =>
    tab.evaluate(
      async ({ record, id }) => {
        const storage = await import("/src/js/storage/indexed-db.js");
        try {
          await storage.saveRoutine(
            { ...record, id, name: id },
            { expected: null },
          );
          return "saved";
        } catch (error) {
          return error.code;
        }
      },
      { record: routine, id },
    );
  expect(
    (
      await Promise.all([
        create(page, "Last slot A"),
        create(other, "Last slot B"),
      ])
    ).sort(),
  ).toEqual(["routine_limit", "saved"]);
  expect(await readStore(page, "templates")).toHaveLength(limit);
  await page.evaluate(async () => {
    const storage = await import("/src/js/storage/indexed-db.js");
    const [record] = await storage.getRoutines();
    await storage.saveRoutine(
      { ...record, exercises: ["Squat"] },
      { expected: record },
    );
  });
  expect(
    (await readStore(page, "templates")).filter(
      (item) => item.exercises[0] === "Squat",
    ),
  ).toHaveLength(1);
  await other.close();
});

test("a guarded rename cannot reuse another routine name", async ({ page }) => {
  await loadApp(page);
  const sibling = { ...routine, id: "other-routine", name: "Other routine" };
  await seedStores(page, { templates: [routine, sibling] });
  const result = await page.evaluate(
    async ({ original, sibling }) => {
      const storage = await import("/src/js/storage/indexed-db.js");
      try {
        await storage.saveRoutine(
          { ...original, name: sibling.name.toUpperCase() },
          { expected: original },
        );
        return "saved";
      } catch (error) {
        return error.code;
      }
    },
    { original: routine, sibling },
  );
  expect(result).toBe("routine_name_conflict");
  expect(
    (await readStore(page, "templates")).find((item) => item.id === routine.id),
  ).toEqual(routine);
});

test("confirmed same-name replacement still updates the existing routine ID", async ({
  page,
}) => {
  await loadApp(page);
  await seedStores(page, { templates: [routine] });
  page.on("dialog", (dialog) => dialog.accept());
  expect(
    await page.evaluate(async (name) => {
      const routines = await import("/src/js/screens/routines.js");
      globalThis.document.querySelector("#templateName").value = name;
      globalThis.document.querySelector("#templateExerciseInput").value =
        "Squat";
      routines.addTemplateExercise();
      return routines.saveTemplate();
    }, routine.name),
  ).toBe(true);
  const stored = (await readStore(page, "templates")).find(
    (item) => item.id === routine.id,
  );
  expect(stored.exercises).toEqual(["Squat"]);
  expect(stored.createdAt).toBe(routine.createdAt);
  expect(stored.extension).toEqual(routine.extension);
});
