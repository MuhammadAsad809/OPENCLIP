package com.openclip.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.openclip.studio.data.ProjectRepository
import com.openclip.studio.ui.editor.EditorScreen
import com.openclip.studio.ui.editor.EditorViewModel
import com.openclip.studio.ui.gallery.GalleryScreen
import com.openclip.studio.ui.theme.DarkBackground
import com.openclip.studio.ui.theme.OpenClipTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = ProjectRepository(applicationContext)

        setContent {
            OpenClipTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "gallery"
                    ) {
                        composable("gallery") {
                            GalleryScreen(
                                repository = repository,
                                onOpenProject = { projectId ->
                                    navController.navigate("editor/$projectId")
                                }
                            )
                        }

                        composable(
                            route = "editor/{projectId}",
                            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                            val viewModel = remember(projectId) {
                                EditorViewModel(
                                    application = application,
                                    repository = repository,
                                    projectId = projectId
                                )
                            }

                            EditorScreen(
                                viewModel = viewModel,
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
