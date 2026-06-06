package ru.vsu.csschedule.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.vsu.csschedule.data.local.ScheduleOwnerType
import ru.vsu.csschedule.ui.details.ScheduleDetailsRoute
import ru.vsu.csschedule.ui.home.HomeRoute

object Routes {
    const val HOME = "home"
    const val DETAILS = "details/{type}/{referenceId}/{title}"

    fun details(type: ScheduleOwnerType, referenceId: Long, title: String): String {
        return "details/${type.name}/$referenceId/${Uri.encode(title)}"
    }
}

@Composable
fun AppNavGraph(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            HomeRoute(
                onOpenSchedule = { type, referenceId, title ->
                    navController.navigate(Routes.details(type, referenceId, title))
                },
            )
        }

        composable(
            route = Routes.DETAILS,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("referenceId") { type = NavType.LongType },
                navArgument("title") { type = NavType.StringType },
            ),
        ) {
            ScheduleDetailsRoute(onBack = { navController.popBackStack() })
        }
    }
}
