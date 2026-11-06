package com.codyphil.liftlog.data

/** Stored as their names (TEXT). Never rename an entry: old databases and backups refer to these names. */

enum class SetType(val label: String, val short: String) {
    NORMAL("Normal set", ""),
    WARMUP("Warm-up", "W"),
    FAILURE("To failure", "F"),
    DROP("Drop set", "D"),
}

enum class Progression(val label: String) {
    /** Pre-fill with whatever you did last time. */
    NONE("None"),
    /** Hit every rep on every set → add weight. Two missed sessions in a row → back off 10%. */
    LINEAR("Linear"),
    /** Work up a rep range; when every set hits the top of the range → add weight, reset to the bottom. */
    DOUBLE("Double"),
    /** Each set is a percentage of the exercise's training max; an AMRAP set adjusts the training max. */
    PERCENT("% TM"),
}

enum class Muscle(val label: String) {
    CHEST("Chest"), BACK("Back"), SHOULDERS("Shoulders"), BICEPS("Biceps"), TRICEPS("Triceps"),
    QUADS("Quads"), HAMSTRINGS("Hamstrings"), GLUTES("Glutes"), CALVES("Calves"), CORE("Core"),
    OTHER("Other"),
}

enum class MetricKind { WEIGHT, LENGTH, PERCENT }

enum class BodyMetric(val label: String, val kind: MetricKind) {
    BODY_WEIGHT("Body weight", MetricKind.WEIGHT),
    BODY_FAT("Body fat", MetricKind.PERCENT),
    WAIST("Waist", MetricKind.LENGTH),
    CHEST("Chest", MetricKind.LENGTH),
    ARMS("Arms", MetricKind.LENGTH),
    THIGHS("Thighs", MetricKind.LENGTH),
    HIPS("Hips", MetricKind.LENGTH),
}
