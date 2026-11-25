package pro.iyou.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import pro.iyou.DeepLink
import pro.iyou.data.Collection
import pro.iyou.playback.PlayerConnection
import pro.iyou.ui.player.FullPlayer
import pro.iyou.ui.player.VideoFullscreenHost
import pro.iyou.ui.player.MiniPlayer
import pro.iyou.ui.screens.CollectionScreen
import pro.iyou.ui.screens.HomeScreen
import pro.iyou.ui.screens.LibraryScreen
import pro.iyou.ui.screens.SearchScreen
import pro.iyou.ui.theme.Palette

val LocalPlayer = staticCompositionLocalOf<PlayerConnection> { error("No PlayerConnection") }

@Serializable data object HomeRoute
@Serializable data object SearchRoute
@Serializable data object LibraryRoute
@Serializable data class CollectionRoute(
    val id: String,
    val kind: String,
    val title: String,
    val subtitle: String? = null,
    val thumbnail: String? = null,
) {
    fun toCollection() = Collection(id, Collection.Kind.valueOf(kind), title, subtitle, thumbnail)
}

private fun Collection.route() = CollectionRoute(id, kind.name, title, subtitle, thumbnail)

private data class Tab(val route: Any, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(HomeRoute, "Beranda", Icons.Outlined.Home, Icons.Rounded.Home),
    Tab(SearchRoute, "Cari", Icons.Outlined.Search, Icons.Rounded.Search),
    Tab(LibraryRoute, "Koleksi", Icons.Rounded.FavoriteBorder, Icons.Rounded.Favorite),
)

@Composable
fun IyouRoot(player: PlayerConnection, link: DeepLink? = null, onLinkHandled: () -> Unit = {}) {
    val nav = rememberNavController()
    val state by player.state.collectAsState()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val hasTrack = state.current != null
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination

    // room below the content for the mini player + tab bar
    val bottomSpace = PaddingValues(bottom = if (hasTrack) 160.dp else 92.dp)
    val open: (Collection) -> Unit = { nav.navigate(it.route()) }

    var searchRequest by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(link) {
        when (link) {
            is DeepLink.Play -> {
                player.playVideoId(link.videoId)
                expanded = true
            }
            is DeepLink.Search -> {
                expanded = false
                searchRequest = link.query
                nav.navigate(SearchRoute) {
                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                }
            }
            null -> return@LaunchedEffect
        }
        onLinkHandled()
    }


    androidx.compose.runtime.CompositionLocalProvider(LocalPlayer provides player) {
        Box(Modifier.fillMaxSize().background(Palette.Background)) {
            NavHost(nav, startDestination = HomeRoute, modifier = Modifier.fillMaxSize()) {
                composable<HomeRoute> { HomeScreen(onOpen = open, contentPadding = bottomSpace) }
                composable<SearchRoute> {
                    SearchScreen(
                        onOpen = open,
                        contentPadding = bottomSpace,
                        request = searchRequest,
                        onRequestHandled = { searchRequest = null },
                    )
                }
                composable<LibraryRoute> { LibraryScreen(contentPadding = bottomSpace) }
                composable<CollectionRoute> { entry ->
                    CollectionScreen(
                        target = entry.toRoute<CollectionRoute>().toCollection(),
                        onBack = { nav.popBackStack() },
                        contentPadding = bottomSpace,
                    )
                }
            }

            // bottom chrome: a soft fade, the mini player, then the tabs
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Palette.Background.copy(alpha = 0f),
                            0.25f to Palette.Background.copy(alpha = 0.9f),
                            0.5f to Palette.Background,
                            1f to Palette.Background,
                        ),
                    )
                    .navigationBarsPadding()
                    .padding(top = 20.dp),
            ) {
                AnimatedVisibility(
                    visible = hasTrack,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    MiniPlayer(state, onExpand = { expanded = true })
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEach { tab ->
                        val selected = destination?.hasRoute(tab.route::class) == true
                        TabItem(tab, selected) {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded && hasTrack,
                enter = slideInVertically(tween(360)) { it } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(300)) { it } + fadeOut(tween(250)),
            ) {
                // declared after NavHost so it wins over the nav back stack
                BackHandler { expanded = false }
                FullPlayer(state, onCollapse = { expanded = false })
            }

            // last, so it sits over everything and its back handler wins
            VideoFullscreenHost(state, playerVisible = expanded && hasTrack)
        }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clickable(
                interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            if (selected) tab.selectedIcon else tab.icon,
            tab.label,
            tint = if (selected) Palette.Text else Palette.TextFaint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            tab.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Palette.Text else Palette.TextFaint,
        )
    }
}
