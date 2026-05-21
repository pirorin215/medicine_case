package com.pirorin215.medicinecasemob.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pirorin215.medicinecasemob.ui.screen.DebugScreen
import com.pirorin215.medicinecasemob.ui.screen.DetectionSettingsScreen
import com.pirorin215.medicinecasemob.ui.screen.MainScreen
import com.pirorin215.medicinecasemob.ui.screen.ScheduleSettingsScreen

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object DetectionSettings : Screen("detection_settings")
    object ScheduleSettings : Screen("schedule_settings")
    object Debug : Screen("debug")
}

@Composable
fun MedicineNavGraph(
    navController: NavHostController = androidx.navigation.compose.rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Main.route
    ) {
        composable(Screen.Main.route) {
            MainScreen(
                onNavigateToDetectionSettings = {
                    navController.navigate(Screen.DetectionSettings.route)
                },
                onNavigateToScheduleSettings = {
                    navController.navigate(Screen.ScheduleSettings.route)
                },
                onNavigateToDebug = {
                    navController.navigate(Screen.Debug.route)
                }
            )
        }

        composable(Screen.DetectionSettings.route) {
            DetectionSettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.ScheduleSettings.route) {
            ScheduleSettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Debug.route) {
            DebugScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
