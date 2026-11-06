package com.codyphil.liftlog.data

/**
 * Starter programs. Weights start empty (0) on purpose: you enter your own the first time,
 * then progression takes over. Progression defaults come from Progressions.defaultFor unless set here.
 */
object Templates {
    data class Item(
        val exercise: String,
        val sets: Int,
        val reps: Int,
        val rest: Int,
        /** Items with the same number in a day are done as a superset. */
        val superset: Int? = null,
        val repMax: Int? = null,
        val progression: Progression? = null,
        val autoWarmup: Boolean = false,
    )
    data class Day(val name: String, val items: List<Item>)
    data class Template(
        val name: String,
        val description: String,
        val focus: String,
        val daysPerWeek: String,
        val days: List<Day>,
    )

    val all = listOf(
        Template(
            "Full Body A/B", "Alternate A and B. A simple, balanced start.", "Beginner", "3×/week",
            listOf(
                Day("Workout A", listOf(
                    Item("Back Squat", 3, 5, 180, autoWarmup = true), Item("Bench Press", 3, 5, 180, autoWarmup = true),
                    Item("Barbell Row", 3, 8, 120), Item("Dumbbell Curl", 2, 10, 60),
                )),
                Day("Workout B", listOf(
                    Item("Deadlift", 1, 5, 180, autoWarmup = true), Item("Overhead Press", 3, 5, 180, autoWarmup = true),
                    Item("Lat Pulldown", 3, 8, 90), Item("Tricep Pushdown", 2, 10, 60),
                )),
            ),
        ),
        Template(
            "5×5 Strength", "Heavy compound lifts, five sets of five, add weight every session.", "Strength", "3×/week",
            listOf(
                Day("Day A", listOf(
                    Item("Back Squat", 5, 5, 180, autoWarmup = true), Item("Bench Press", 5, 5, 180, autoWarmup = true),
                    Item("Barbell Row", 5, 5, 150, autoWarmup = true),
                )),
                Day("Day B", listOf(
                    Item("Back Squat", 5, 5, 180, autoWarmup = true), Item("Overhead Press", 5, 5, 180, autoWarmup = true),
                    Item("Deadlift", 1, 5, 180, autoWarmup = true),
                )),
            ),
        ),
        Template(
            "Upper / Lower", "Two heavy days and two volume days.", "Strength + size", "4×/week",
            listOf(
                Day("Upper (heavy)", listOf(
                    Item("Bench Press", 4, 5, 180, autoWarmup = true), Item("Barbell Row", 4, 6, 150),
                    Item("Overhead Press", 3, 6, 120), Item("Chin-Up", 3, 6, 120),
                    Item("Dumbbell Curl", 2, 10, 60, superset = 1), Item("Tricep Pushdown", 2, 10, 60, superset = 1),
                )),
                Day("Lower (heavy)", listOf(
                    Item("Back Squat", 4, 5, 180, autoWarmup = true), Item("Romanian Deadlift", 3, 6, 150),
                    Item("Leg Press", 3, 10, 120), Item("Calf Raise", 4, 10, 60),
                )),
                Day("Upper (volume)", listOf(
                    Item("Incline Dumbbell Press", 3, 8, 90), Item("Seated Cable Row", 3, 10, 90),
                    Item("Dumbbell Shoulder Press", 3, 10, 90), Item("Lat Pulldown", 3, 10, 90),
                    Item("Lateral Raise", 3, 12, 60, superset = 1), Item("Face Pull", 3, 12, 60, superset = 1),
                )),
                Day("Lower (volume)", listOf(
                    Item("Deadlift", 3, 5, 180, autoWarmup = true), Item("Bulgarian Split Squat", 3, 8, 90),
                    Item("Leg Curl", 3, 10, 60, superset = 1), Item("Leg Extension", 3, 10, 60, superset = 1),
                    Item("Hanging Leg Raise", 3, 10, 60),
                )),
            ),
        ),
        Template(
            "Push / Pull / Legs", "Classic split. Run it once or twice a week.", "Muscle", "3–6×/week",
            listOf(
                Day("Push", listOf(
                    Item("Bench Press", 4, 6, 150, autoWarmup = true), Item("Overhead Press", 3, 8, 120),
                    Item("Incline Dumbbell Press", 3, 8, 90),
                    Item("Lateral Raise", 3, 12, 60, superset = 1), Item("Tricep Pushdown", 3, 10, 60, superset = 1),
                )),
                Day("Pull", listOf(
                    Item("Deadlift", 3, 5, 180, autoWarmup = true), Item("Pull-Up", 3, 6, 120),
                    Item("Seated Cable Row", 3, 10, 90),
                    Item("Face Pull", 3, 12, 60, superset = 1), Item("Hammer Curl", 3, 10, 60, superset = 1),
                )),
                Day("Legs", listOf(
                    Item("Back Squat", 4, 6, 180, autoWarmup = true), Item("Romanian Deadlift", 3, 8, 120),
                    Item("Leg Press", 3, 10, 90), Item("Leg Curl", 3, 10, 60), Item("Calf Raise", 4, 12, 60),
                )),
            ),
        ),
        Template(
            "Full Body Hypertrophy", "Moderate weights, higher reps, supersets to save time.", "Muscle", "3×/week",
            listOf(
                Day("Day 1", listOf(
                    Item("Back Squat", 3, 8, 150, autoWarmup = true), Item("Incline Dumbbell Press", 3, 8, 90),
                    Item("Lat Pulldown", 3, 10, 90),
                    Item("Lateral Raise", 3, 12, 45, superset = 1), Item("Dumbbell Curl", 3, 10, 45, superset = 1),
                )),
                Day("Day 2", listOf(
                    Item("Romanian Deadlift", 3, 8, 120), Item("Dumbbell Bench Press", 3, 10, 90),
                    Item("Seated Cable Row", 3, 10, 90),
                    Item("Leg Curl", 3, 10, 45, superset = 1), Item("Overhead Tricep Extension", 3, 10, 45, superset = 1),
                )),
                Day("Day 3", listOf(
                    Item("Leg Press", 3, 10, 120), Item("Overhead Press", 3, 8, 120), Item("Chin-Up", 3, 8, 90),
                    Item("Cable Fly", 3, 12, 45, superset = 1), Item("Rear Delt Fly", 3, 12, 45, superset = 1),
                    Item("Calf Raise", 3, 12, 45),
                )),
            ),
        ),
        Template(
            "Dumbbell Only", "Home or hotel gym: just dumbbells and a bench.", "Beginner", "3×/week",
            listOf(
                Day("Day A", listOf(
                    Item("Goblet Squat", 3, 10, 90), Item("Dumbbell Bench Press", 3, 10, 90),
                    Item("Dumbbell Row", 3, 10, 90), Item("Lateral Raise", 2, 12, 60),
                )),
                Day("Day B", listOf(
                    Item("Dumbbell Romanian Deadlift", 3, 10, 90), Item("Dumbbell Shoulder Press", 3, 10, 90),
                    Item("Bulgarian Split Squat", 3, 8, 90),
                    Item("Hammer Curl", 2, 12, 60, superset = 1), Item("Overhead Tricep Extension", 2, 12, 60, superset = 1),
                )),
            ),
        ),
    )
}
