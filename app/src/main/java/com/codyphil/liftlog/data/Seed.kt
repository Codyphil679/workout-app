package com.codyphil.liftlog.data

object Seed {
    /** Bump when built-in exercises are added, so existing installs get them once. */
    const val VERSION = 2

    /** Built-in exercises and their primary muscle. */
    val muscles: Map<String, Muscle> = linkedMapOf(
        "Back Squat" to Muscle.QUADS, "Front Squat" to Muscle.QUADS, "Deadlift" to Muscle.HAMSTRINGS,
        "Romanian Deadlift" to Muscle.HAMSTRINGS, "Bench Press" to Muscle.CHEST, "Incline Bench Press" to Muscle.CHEST,
        "Dumbbell Bench Press" to Muscle.CHEST, "Incline Dumbbell Press" to Muscle.CHEST, "Overhead Press" to Muscle.SHOULDERS,
        "Dumbbell Shoulder Press" to Muscle.SHOULDERS, "Barbell Row" to Muscle.BACK, "Dumbbell Row" to Muscle.BACK,
        "Pull-Up" to Muscle.BACK, "Chin-Up" to Muscle.BACK, "Lat Pulldown" to Muscle.BACK, "Seated Cable Row" to Muscle.BACK,
        "Face Pull" to Muscle.SHOULDERS, "Lateral Raise" to Muscle.SHOULDERS, "Barbell Curl" to Muscle.BICEPS,
        "Dumbbell Curl" to Muscle.BICEPS, "Hammer Curl" to Muscle.BICEPS, "Tricep Pushdown" to Muscle.TRICEPS,
        "Skull Crusher" to Muscle.TRICEPS, "Dip" to Muscle.TRICEPS, "Leg Press" to Muscle.QUADS,
        "Leg Extension" to Muscle.QUADS, "Leg Curl" to Muscle.HAMSTRINGS, "Walking Lunge" to Muscle.QUADS,
        "Bulgarian Split Squat" to Muscle.QUADS, "Hip Thrust" to Muscle.GLUTES, "Calf Raise" to Muscle.CALVES,
        "Plank" to Muscle.CORE, "Hanging Leg Raise" to Muscle.CORE, "Cable Fly" to Muscle.CHEST,
        "Goblet Squat" to Muscle.QUADS, "Dumbbell Romanian Deadlift" to Muscle.HAMSTRINGS,
        "Overhead Tricep Extension" to Muscle.TRICEPS, "Rear Delt Fly" to Muscle.SHOULDERS,
        "Push-Up" to Muscle.CHEST, "Cable Crunch" to Muscle.CORE, "Glute Bridge" to Muscle.GLUTES,
    )

    val exercises: List<String> get() = muscles.keys.toList()

    fun muscleFor(name: String): Muscle =
        muscles.entries.firstOrNull { it.key.equals(name.trim(), ignoreCase = true) }?.value ?: Muscle.OTHER
}
