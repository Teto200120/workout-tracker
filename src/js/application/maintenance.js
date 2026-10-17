// Maintenance and ordinary database writes must use the same coordinator.
export {
  createDataCoordinator as createMaintenanceCoordinator,
  runDataOperation as runMaintenanceOperation,
} from "../storage/operation.js";
