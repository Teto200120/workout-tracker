import { buildRescueExport } from "../storage/rescue.js";

export function offerRescueExport(container) {
  if (!container || document.getElementById("rescueExport")) return;
  const button = document.createElement("button");
  button.id = "rescueExport";
  button.type = "button";
  button.textContent = "Download recovery data";
  const status = document.createElement("p");
  status.setAttribute("role", "status");
  status.className = "muted small";
  status.textContent = "Downloads raw saved data for recovery. Keep this file private; it is separate from a normal backup.";
  button.addEventListener("click", async () => {
    button.disabled = true;
    try {
      const rescue = await buildRescueExport();
      const url = URL.createObjectURL(new Blob([JSON.stringify(rescue, null, 2)], { type: "application/json" }));
      const link = document.createElement("a");
      link.href = url;
      link.download = `WorkoutTracker-rescue-${rescue.exportedAt.slice(0, 10)}.json`;
      document.body.append(link);
      try { link.click(); }
      finally { link.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000); }
      status.textContent = "Recovery download prepared. Keep the file and this browser's data until recovery is complete.";
    } catch (error) {
      console.info("Recovery export failed.", error);
      status.textContent = "Could not read recovery data. Close other tracker tabs and retry. Keep this browser's storage.";
    } finally {
      button.disabled = false;
    }
  });
  container.append(button, status);
}
