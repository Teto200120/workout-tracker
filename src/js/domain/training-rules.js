export function inferMuscleTag(exerciseName) {
  const name = String(exerciseName || "").toLowerCase();
  if (/soccer|sprint|agility|conditioning|cooldown|warm.?up/.test(name)) return "Conditioning";
  if (/rear delt|face pull|reverse fly/.test(name)) return "Rear Delts";
  if (/bench|chest|fly|pec|incline/.test(name)) return "Chest";
  if (/tricep|pushdown|skull|dip/.test(name)) return "Triceps";
  if (/shoulder|overhead|lateral|front raise|press/.test(name)) return "Shoulders";
  if (/lat|pulldown|row|back|pull.?up|chin.?up/.test(name)) return "Back";
  if (/bicep|curl|hammer/.test(name)) return "Biceps";
  if (/shrug|trap/.test(name)) return "Traps";
  if (/squat|leg press|quad|lunge|extension/.test(name)) return "Quads";
  if (/romanian|rdl|deadlift|hamstring|leg curl/.test(name)) return "Hamstrings";
  if (/calf/.test(name)) return "Calves";
  return "";
}

export function getWorkoutTags(templateName, template) {
  const tags = [];
  for (const exercise of template?.exercises || []) {
    const tag = inferMuscleTag(exercise);
    if (tag && !tags.includes(tag)) tags.push(tag);
  }
  if (!tags.length) {
    const name = String(templateName || "").toLowerCase();
    if (/chest|push/.test(name)) tags.push("Chest");
    if (/back|pull/.test(name)) tags.push("Back");
    if (/shoulder/.test(name)) tags.push("Shoulders");
    if (/tricep/.test(name)) tags.push("Triceps");
    if (/bicep/.test(name)) tags.push("Biceps");
    if (/leg/.test(name)) tags.push("Legs");
    if (/soccer|conditioning/.test(name)) tags.push("Conditioning");
  }
  return tags.slice(0, 4);
}

export function estimateWorkoutDuration(template) {
  const count = template?.exercises?.length || 0;
  if (!count) return "~30 min";
  const minutes = Math.round(Math.min(100, Math.max(30, 12 + count * 8)) / 5) * 5;
  return `~${minutes} min`;
}

export function workSetsOnly(sets = []) {
  return sets.filter((set) => !set.warmup && (set.weight || set.reps));
}

export function averageRpe(sets = []) {
  const values = sets.map((set) => Number(set.rpe || 0)).filter(Boolean);
  if (!values.length) return 0;
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

export function highestRpe(sets = []) {
  return Math.max(0, ...sets.map((set) => Number(set.rpe || 0)).filter(Boolean));
}

export function getExerciseProfile(exerciseName, settings) {
  const name = String(exerciseName || "").toLowerCase();
  const increment = Number(settings.defaultWeightJump || 5);
  const profiles = [
    { keys: ["lateral raise", "rear delt", "fly", "curl", "pushdown", "tricep", "extension", "calf", "shrug"], min: settings.isolationMin, max: settings.isolationMax, increment, type: "isolation" },
    { keys: ["bench", "squat", "deadlift", "romanian", "rdl", "leg press", "shoulder press", "press"], min: settings.compoundMin, max: settings.compoundMax, increment, type: "compound" },
    { keys: ["row", "pulldown", "pull up", "pull-up", "chin"], min: settings.pullMin, max: settings.pullMax, increment, type: "compound pull" }
  ];
  return profiles.find((profile) => profile.keys.some((key) => name.includes(key))) || { min: settings.generalMin, max: settings.generalMax, increment, type: "general" };
}

export function buildTargetFromLastSets(exerciseName, lastSets, settings) {
  const profile = getExerciseProfile(exerciseName, settings);
  const sets = workSetsOnly(lastSets);
  if (!sets.length) return { weight: "-", repsText: "Log clean work sets", reason: `${profile.min}-${profile.max} rep range · build a baseline first`, targetSets: [] };

  const maxRpe = settings.rpeAware ? highestRpe(sets) : 0;
  const avg = settings.rpeAware ? averageRpe(sets) : 0;
  const weightCounts = new Map();
  sets.filter((set) => set.weight).forEach((set) => weightCounts.set(String(set.weight), (weightCounts.get(String(set.weight)) || 0) + 1));
  const primaryWeight = Number(Array.from(weightCounts.entries()).sort((a, b) => b[1] - a[1])[0]?.[0]);
  const sameWeightSets = Number.isFinite(primaryWeight) ? sets.filter((set) => Number(set.weight) === primaryWeight) : sets;
  const reps = sameWeightSets.map((set) => Number(set.reps || 0)).filter(Boolean);
  const allAtTop = reps.length > 0 && reps.every((rep) => rep >= profile.max);
  const veryHard = maxRpe >= 9.5;
  const hard = maxRpe >= 9;
  let targetWeight = primaryWeight || Number(sets[0].weight || 0);
  let targetReps = reps.length ? [...reps] : sets.map((set) => Number(set.reps || profile.min));
  let reason = `${profile.min}-${profile.max} range`;

  if (veryHard) {
    reason += ` · last RPE ${maxRpe.toFixed(1)}, repeat and clean it up`;
  } else if (hard) {
    const lowestIndex = targetReps.indexOf(Math.min(...targetReps));
    targetReps[lowestIndex] = Math.min(profile.max, targetReps[lowestIndex] + 1);
    reason += ` · last RPE ${maxRpe.toFixed(1)}, progress carefully`;
  } else if (allAtTop) {
    targetWeight += profile.increment;
    targetReps = targetReps.map(() => profile.min);
    reason += ` · top reached, add ${profile.increment} lb`;
  } else {
    const belowTop = targetReps.map((rep, index) => ({ rep, index })).filter((item) => item.rep < profile.max).sort((a, b) => a.rep - b.rep)[0];
    if (belowTop) targetReps[belowTop.index] = Math.min(profile.max, belowTop.rep + 1);
    reason += avg ? ` · avg RPE ${avg.toFixed(1)}` : " · build reps first";
  }

  const targetSets = sameWeightSets.length ? sameWeightSets.map((set, index) => ({ ...set, weight: targetWeight ? String(targetWeight) : set.weight, reps: targetReps[index] ? String(targetReps[index]) : set.reps, done: false })) : [];
  return { weight: targetWeight ? `${targetWeight} lb` : "-", repsText: targetReps.length ? `${targetReps.join(" / ")} reps` : "Hold form", reason, targetSets };
}

export function getProgressionRecommendation(bestSet, exerciseName, settings) {
  if (!bestSet || !bestSet.weight || !bestSet.reps) return "";
  const profile = getExerciseProfile(exerciseName, settings);
  const weight = Number(bestSet.weight);
  const reps = Number(bestSet.reps);
  const rpe = Number(bestSet.rpe || 0);
  if (settings.rpeAware && rpe >= 9.5) return `repeat ${weight} × ${reps}; last RPE was high`;
  if (reps < profile.max) return `try ${weight} × ${reps + 1}`;
  return `try ${weight + profile.increment} × ${profile.min}`;
}
