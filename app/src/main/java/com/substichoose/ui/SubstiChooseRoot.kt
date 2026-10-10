package com.substichoose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.substichoose.AppContainer
import com.substichoose.ui.allergies.AllergiesScreen
import com.substichoose.ui.home.HomeScreen
import com.substichoose.ui.nav.Routes
import com.substichoose.ui.recipe.RecipeScreen
import com.substichoose.ui.review.ReviewScreen
import com.substichoose.ui.scan.ScanScreen
import com.substichoose.ui.theme.SC
import com.substichoose.ui.theme.SubstiChooseTheme

@Composable
fun SubstiChooseRoot(container: AppContainer) {
    SubstiChooseTheme {
        val onboardingDone by container.settings.onboardingDone
            .collectAsStateWithLifecycle<Boolean?>(initialValue = null)

        // Decide the start destination once, from the first stored value; later changes
        // (saving the profile flips onboardingDone) must not rebuild the graph.
        var start by rememberSaveable { mutableStateOf<String?>(null) }
        LaunchedEffect(onboardingDone) {
            val done = onboardingDone
            if (start == null && done != null) start = if (done) Routes.HOME else Routes.ONBOARDING
        }

        Box(Modifier.fillMaxSize().background(SC.Background)) {
            val startRoute = start
            if (startRoute != null) {
                AppNavHost(container, startRoute, rememberNavController())
            }
        }
    }
}

@Composable
private fun AppNavHost(container: AppContainer, startRoute: String, nav: NavHostController) {
    val idArg = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType })

    NavHost(navController = nav, startDestination = startRoute) {
        composable(Routes.ONBOARDING) {
            AllergiesScreen(
                container = container,
                onboarding = true,
                onBack = null,
                onSaved = {
                    nav.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.ALLERGIES) {
            AllergiesScreen(
                container = container,
                onboarding = false,
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack() },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                container = container,
                onOpenAllergies = { nav.navigate(Routes.ALLERGIES) { launchSingleTop = true } },
                onScan = { nav.navigate(Routes.SCAN) { launchSingleTop = true } },
                onOpenRecipe = { id -> nav.navigate(Routes.recipe(id)) { launchSingleTop = true } },
                onOpenReview = { id -> nav.navigate(Routes.review(id)) { launchSingleTop = true } },
            )
        }
        composable(Routes.SCAN) {
            ScanScreen(
                container = container,
                // Also called right after a photo is taken: the scan carries on in the background.
                onClose = { nav.popBackStack() },
            )
        }
        composable(Routes.REVIEW, arguments = idArg) { entry ->
            val id = entry.arguments?.getLong(Routes.ARG_ID) ?: return@composable
            ReviewScreen(
                container = container,
                recipeId = id,
                onBack = { nav.popBackStack() },
                onDone = {
                    nav.navigate(Routes.recipe(id)) {
                        popUpTo(Routes.HOME)
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.RECIPE, arguments = idArg) { entry ->
            val id = entry.arguments?.getLong(Routes.ARG_ID) ?: return@composable
            RecipeScreen(
                container = container,
                recipeId = id,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.review(id)) { launchSingleTop = true } },
                onDeleted = {
                    if (!nav.popBackStack(Routes.HOME, inclusive = false)) {
                        nav.navigate(Routes.HOME) { launchSingleTop = true }
                    }
                },
            )
        }
    }
}
