package com.example.wardrobeapp.domain.model

/** Closed vocabularies for clothing item tagging, used by both the item form UI and outfit scoring. */
object TagOptions {
    val OCCASIONS = listOf(
        "Formal",
        "Business Casual",
        "Casual",
        "Workout",
        "Loungewear",
        "Outdoor",
        "Party",
        "Sleepwear",
        "Swim"
    )

    val SEASONS = listOf(
        "All-Season",
        "Spring",
        "Summer",
        "Fall",
        "Winter"
    )

    val COLOR_FAMILIES = listOf(
        "Neutral",
        "Warm",
        "Cool",
        "Bright",
        "Pastel",
        "Multicolor"
    )
}
