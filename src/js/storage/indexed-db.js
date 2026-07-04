import "../core/globals.js";

function openDatabase() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);

    request.onupgradeneeded = (event) => {
      const database = event.target.result;

      if (!database.objectStoreNames.contains("workouts")) {
        const store = database.createObjectStore("workouts", { keyPath: "id" });
        store.createIndex("date", "date", { unique: false });
        store.createIndex("type", "type", { unique: false });
      }

      if (!database.objectStoreNames.contains("weights")) {
        const store = database.createObjectStore("weights", { keyPath: "id" });
        store.createIndex("date", "date", { unique: false });
      }

      if (!database.objectStoreNames.contains("templates")) {
        const store = database.createObjectStore("templates", { keyPath: "id" });
        store.createIndex("name", "name", { unique: false });
      }
    };

    request.onsuccess = () => { db = request.result; resolve(db); };
    request.onerror = () => reject(request.error);
  });
}

function store(name, mode = "readonly") { return db.transaction(name, mode).objectStore(name); }

function saveItem(storeName, item) {
  return new Promise((resolve, reject) => {
    const request = store(storeName, "readwrite").put(item);
    request.onsuccess = () => resolve(item);
    request.onerror = () => reject(request.error);
  });
}

function getItems(storeName) {
  return new Promise((resolve, reject) => {
    const request = store(storeName).getAll();
    request.onsuccess = () => resolve(request.result || []);
    request.onerror = () => reject(request.error);
  });
}

function deleteItem(storeName, itemId) {
  return new Promise((resolve, reject) => {
    const request = store(storeName, "readwrite").delete(itemId);
    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error);
  });
}

function clearStore(storeName) {
  return new Promise((resolve, reject) => {
    const request = store(storeName, "readwrite").clear();
    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error);
  });
}

async function getTemplates() {
  return (await getItems("templates")).sort((a, b) => a.name.localeCompare(b.name));
}

async function seedDefaultTemplates() {
  const existing = await getTemplates();
  if (existing.length) return;

  for (const [name, exercises] of Object.entries(defaultTemplates)) {
    await saveItem("templates", {
      id: id(),
      name,
      exercises,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    });
  }
}

async function refreshTemplateDropdowns(selected = null) {
  const templates = await getTemplates();
  const currentWorkout = selected || $("workoutType").value || "Chest / Triceps";
  const currentHistory = $("historyFilter").value || "All";

  $("workoutType").innerHTML = templates.map((template) => `<option value="${cleanText(template.name)}">${cleanText(template.name)}</option>`).join("");
  if (Array.from($("workoutType").options).some((option) => option.value === currentWorkout)) $("workoutType").value = currentWorkout;

  $("historyFilter").innerHTML = `<option value="All">All</option>` + templates.map((template) => `<option value="${cleanText(template.name)}">${cleanText(template.name)}</option>`).join("");
  if (Array.from($("historyFilter").options).some((option) => option.value === currentHistory)) $("historyFilter").value = currentHistory;
}

Object.assign(globalThis, { openDatabase, store, saveItem, getItems, deleteItem, clearStore, getTemplates, seedDefaultTemplates, refreshTemplateDropdowns });
