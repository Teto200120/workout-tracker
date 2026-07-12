import { DAY_LABELS, DEFAULT_APP_SETTINGS, DEFAULT_SCHEDULE } from "../core/constants.js";
import { getWeeklyActivityData as buildWeeklyActivityData, resolveWorkoutDay } from "../domain/schedule.js";
import { getAppSettings } from "../storage/local.js";

export function getTodayPlan(date) {
  return resolveWorkoutDay(date, getAppSettings(), DEFAULT_SCHEDULE, DEFAULT_APP_SETTINGS, DAY_LABELS);
}

export function getWeeklyActivityData(workouts, currentDate) {
  return buildWeeklyActivityData(workouts, currentDate, getTodayPlan);
}
