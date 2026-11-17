package io.homeassistant.companion.android.todo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.home.HomeActivity
import io.homeassistant.companion.android.home.TodoListViewModel
import io.homeassistant.companion.android.home.views.TodoListView
import io.homeassistant.companion.android.home.views.TodoListsView
import io.homeassistant.companion.android.theme.WearAppTheme
import javax.inject.Inject
import kotlinx.coroutines.launch

private const val ROUTE_LISTS = "lists"
private const val ROUTE_LIST = "list"
private const val ARG_ENTITY_ID = "entityId"

/**
 * A separate app (own launcher icon and task, like Assist) to view and modify the to-do lists of
 * the server. Launched without [EXTRA_ENTITY_ID] it shows all the lists, with it that list directly.
 */
@AndroidEntryPoint
class TodoListActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ENTITY_ID = "todo_entity_id"

        fun newInstance(context: Context, entityId: String? = null): Intent =
            Intent(context, TodoListActivity::class.java).putExtra(EXTRA_ENTITY_ID, entityId)
    }

    @Inject
    lateinit var serverManager: ServerManager

    private val viewModel by viewModels<TodoListViewModel>()

    // A launch while the activity already exists, compared by identity so repeating the same list counts
    private class LaunchRequest(val entityId: String?)

    private var newLaunchRequest by mutableStateOf<LaunchRequest?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        newLaunchRequest = LaunchRequest(intent.getStringExtra(EXTRA_ENTITY_ID))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            if (!serverManager.isRegistered()) {
                startActivity(HomeActivity.newInstance(this@TodoListActivity))
                finish()
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.observeLists() }
        }

        val startEntityId = intent.getStringExtra(EXTRA_ENTITY_ID)
        setContent {
            val lists by viewModel.lists.collectAsStateWithLifecycle()
            val listsStatus by viewModel.listsStatus.collectAsStateWithLifecycle()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val navController = rememberSwipeDismissableNavController()

            // The task is reused (singleTask), so a tile opening another list comes in as a new intent
            LaunchedEffect(newLaunchRequest) {
                newLaunchRequest?.let { request ->
                    val route = request.entityId?.let { "$ROUTE_LIST/$it" } ?: ROUTE_LISTS
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                    }
                }
            }

            WearAppTheme {
                SwipeDismissableNavHost(
                    navController = navController,
                    startDestination = if (startEntityId != null) "$ROUTE_LIST/$startEntityId" else ROUTE_LISTS,
                ) {
                    composable(ROUTE_LISTS) {
                        TodoListsView(
                            lists = lists,
                            status = listsStatus,
                            onSelectList = { listId -> navController.navigate("$ROUTE_LIST/$listId") },
                            onRetry = viewModel::retryLists,
                        )
                    }
                    composable(
                        route = "$ROUTE_LIST/{$ARG_ENTITY_ID}",
                        arguments = listOf(navArgument(ARG_ENTITY_ID) { type = NavType.StringType }),
                    ) { backStackEntry ->
                        val entityId = backStackEntry.arguments?.getString(ARG_ENTITY_ID) ?: return@composable
                        LaunchedEffect(entityId) { viewModel.load(entityId) }
                        TodoListView(
                            listName = lists.firstOrNull { it.entityId == entityId }?.name ?: entityId,
                            state = state,
                            onSetCompleted = { item, completed -> viewModel.setCompleted(entityId, item, completed) },
                            onAddItem = { viewModel.addItem(entityId, it) },
                            onRemoveItem = { viewModel.removeItem(entityId, it) },
                            onClearCompleted = { viewModel.clearCompleted(entityId) },
                            onRetry = { viewModel.load(entityId) },
                        )
                    }
                }
            }
        }
    }
}
