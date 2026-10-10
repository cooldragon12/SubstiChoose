package com.substichoose.ui.nav

/** String routes for the NavHost. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val ALLERGIES = "allergies"
    const val HOME = "home"
    const val SCAN = "scan"
    const val REVIEW = "review/{id}"
    const val RECIPE = "recipe/{id}"
    const val ARG_ID = "id"

    fun review(id: Long) = "review/$id"
    fun recipe(id: Long) = "recipe/$id"
}
