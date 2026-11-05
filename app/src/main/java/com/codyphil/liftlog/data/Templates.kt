package com.codyphil.liftlog.data

/** Starter programs so a new user has something to run on day one. Weights start empty (0) on purpose. */
object Templates {
    data class Item(val exercise: String, val sets: Int, val reps: Int, val rest: Int)
    data class Day(val name: String, val items: List<Item>)
    data class Template(val name: String, val description: String, val days: List<Day>)

    val all = listOf(
        Template(
            "Full Body A/B", "3 days a week, alternating A and B. Great for beginners.",
            listOf(
                Day("Workout A", listOf(
                    Item("Back Squat", 3, 5, 180), Item("Bench Press", 3, 5, 180), Item("Barbell Row", 3, 8, 120),
                    Item("Dumbbell Curl", 2, 12, 60),
                )),
                Day("Workout B", listOf(
                    Item("Deadlift", 1, 5, 180), Item("Overhead Press", 3, 5, 180), Item("Lat Pulldown", 3, 10, 90),
                    Item("Tricep Pushdown", 2, 12, 60),
                )),
            ),
        ),
        Template(
            "Push / Pull / Legs", "A classic split for 3 or 6 days a week.",
            listOf(
                Day("Push", listOf(
                    Item("Bench Press", 4, 6, 150), Item("Overhead Press", 3, 8, 120), Item("Incline Dumbbell Press", 3, 10, 90),
                    Item("Lateral Raise", 3, 15, 60), Item("Tricep Pushdown", 3, 12, 60),
                )),
                Day("Pull", listOf(
                    Item("Deadlift", 3, 5, 180), Item("Pull-Up", 3, 8, 120), Item("Seated Cable Row", 3, 10, 90),
                    Item("Face Pull", 3, 15, 60), Item("Hammer Curl", 3, 12, 60),
                )),
                Day("Legs", listOf(
                    Item("Back Squat", 4, 6, 180), Item("Romanian Deadlift", 3, 8, 120), Item("Leg Press", 3, 12, 90),
                    Item("Leg Curl", 3, 12, 60), Item("Calf Raise", 4, 15, 60),
                )),
            ),
        ),
    )
}
