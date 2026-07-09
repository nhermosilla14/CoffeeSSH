package cl.segfault.coffeessh.ui.connections

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.segfault.coffeessh.R
import cl.segfault.coffeessh.data.db.ConnectionWithRefs
import cl.segfault.coffeessh.data.db.IdentityEntity
import kotlinx.coroutines.launch

private const val UNGROUPED_KEY = -1L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(
    onBack: () -> Unit,
    onNewConnection: () -> Unit,
    onConnect: (Long) -> Unit,
    onEditConnection: (Long) -> Unit,
    onNewIdentity: () -> Unit,
    onEditIdentity: (Long) -> Unit,
    onManageGroups: () -> Unit,
    onOpenTerminalDemo: () -> Unit,
    viewModel: ConnectionsViewModel = viewModel(factory = ConnectionsViewModel.Factory),
) {
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val identities by viewModel.identities.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()

    var overflowOpen by remember { mutableStateOf(false) }
    var connectionMenuFor by remember { mutableStateOf<ConnectionWithRefs?>(null) }
    var identityMenuFor by remember { mutableStateOf<IdentityEntity?>(null) }
    var confirmDeleteConnection by remember { mutableStateOf<ConnectionWithRefs?>(null) }
    var confirmDeleteIdentity by remember { mutableStateOf<IdentityEntity?>(null) }
    val collapsedGroups = remember { mutableStateMapOf<Long, Boolean>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_connections_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { overflowOpen = true }) {
                        Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.manage_groups)) },
                            onClick = {
                                overflowOpen = false
                                onManageGroups()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.terminal_demo_menu_item)) },
                            onClick = {
                                overflowOpen = false
                                onOpenTerminalDemo()
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (pagerState.currentPage == 0) onNewConnection() else onNewIdentity() },
            ) {
                Icon(Icons.Filled.Add, stringResource(R.string.add))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text(stringResource(R.string.connections_tab)) },
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text(stringResource(R.string.identities_tab)) },
                )
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> ConnectionsTab(
                        sections = sections,
                        collapsedGroups = collapsedGroups,
                        onClick = { onConnect(it.connection.id) },
                        onLongClick = { connectionMenuFor = it },
                    )
                    1 -> IdentitiesTab(
                        identities = identities,
                        onClick = { onEditIdentity(it.id) },
                        onLongClick = { identityMenuFor = it },
                    )
                }
            }
        }
    }

    connectionMenuFor?.let { target ->
        ModalBottomSheet(onDismissRequest = { connectionMenuFor = null }) {
            SheetAction(Icons.Filled.Edit, stringResource(R.string.action_edit)) {
                connectionMenuFor = null
                onEditConnection(target.connection.id)
            }
            SheetAction(Icons.Filled.ContentCopy, stringResource(R.string.action_duplicate)) {
                viewModel.duplicateConnection(target.connection.id)
                connectionMenuFor = null
            }
            SheetAction(Icons.Filled.Delete, stringResource(R.string.action_delete), destructive = true) {
                confirmDeleteConnection = target
                connectionMenuFor = null
            }
        }
    }

    identityMenuFor?.let { target ->
        ModalBottomSheet(onDismissRequest = { identityMenuFor = null }) {
            SheetAction(Icons.Filled.Edit, stringResource(R.string.action_edit)) {
                identityMenuFor = null
                onEditIdentity(target.id)
            }
            SheetAction(Icons.Filled.Delete, stringResource(R.string.action_delete), destructive = true) {
                confirmDeleteIdentity = target
                identityMenuFor = null
            }
        }
    }

    confirmDeleteConnection?.let { target ->
        val name = target.connection.nickname ?: target.connection.host
        AlertDialog(
            onDismissRequest = { confirmDeleteConnection = null },
            title = { Text(stringResource(R.string.delete_connection_title)) },
            text = { Text(stringResource(R.string.delete_connection_text, name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteConnection(target.connection.id)
                        confirmDeleteConnection = null
                    },
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteConnection = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    confirmDeleteIdentity?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDeleteIdentity = null },
            title = { Text(stringResource(R.string.delete_identity_title)) },
            text = { Text(stringResource(R.string.delete_identity_text, target.nickname)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteIdentity(target.id)
                        confirmDeleteIdentity = null
                    },
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteIdentity = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ConnectionsTab(
    sections: List<GroupSection>,
    collapsedGroups: MutableMap<Long, Boolean>,
    onClick: (ConnectionWithRefs) -> Unit,
    onLongClick: (ConnectionWithRefs) -> Unit,
) {
    if (sections.isEmpty()) {
        EmptyHint(stringResource(R.string.connections_empty))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        sections.forEach { section ->
            val key = section.group?.id ?: UNGROUPED_KEY
            val collapsed = collapsedGroups[key] == true
            item(key = "header-$key") {
                GroupHeader(
                    name = section.group?.name ?: stringResource(R.string.ungrouped),
                    count = section.connections.size,
                    collapsed = collapsed,
                    onToggle = { collapsedGroups[key] = !collapsed },
                )
            }
            if (!collapsed) {
                items(section.connections, key = { "c-$key-${it.connection.id}" }) { item ->
                    ConnectionRow(
                        item = item,
                        onClick = { onClick(item) },
                        onLongClick = { onLongClick(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun IdentitiesTab(
    identities: List<IdentityEntity>,
    onClick: (IdentityEntity) -> Unit,
    onLongClick: (IdentityEntity) -> Unit,
) {
    if (identities.isEmpty()) {
        EmptyHint(stringResource(R.string.identities_empty))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(identities, key = { it.id }) { identity ->
            ListRow(
                icon = Icons.Filled.Person,
                title = identity.nickname,
                subtitle = identity.username,
                onClick = { onClick(identity) },
                onLongClick = { onLongClick(identity) },
            )
        }
    }
}

@Composable
private fun GroupHeader(
    name: String,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    val rotation by animateFloatAsState(targetValue = if (collapsed) -90f else 0f, label = "chevron")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.graphicsLayer { rotationZ = rotation },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConnectionRow(
    item: ConnectionWithRefs,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ListRow(
        icon = Icons.Filled.Public,
        title = item.connection.nickname ?: item.connection.host,
        subtitle = item.identity?.username ?: stringResource(R.string.no_identity),
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.width(20.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(label, color = tint) },
        leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.combinedClickable(onClick = onClick),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(32.dp),
        )
    }
}
