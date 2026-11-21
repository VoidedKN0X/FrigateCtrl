package com.vknxworks.frigatectrl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vknxworks.frigatectrl.App
import com.vknxworks.frigatectrl.R
import com.vknxworks.frigatectrl.data.ExploreFilter
import com.vknxworks.frigatectrl.data.ExploreRequests
import com.vknxworks.frigatectrl.data.TrackedObject
import com.vknxworks.frigatectrl.notify.AlertNotifier
import com.vknxworks.frigatectrl.ui.components.CtrlBottomSheet
import com.vknxworks.frigatectrl.ui.components.CtrlButton
import com.vknxworks.frigatectrl.ui.components.CtrlChip
import com.vknxworks.frigatectrl.ui.components.ctrlFieldColors
import com.vknxworks.frigatectrl.ui.theme.CtrlColors
import com.vknxworks.frigatectrl.ui.theme.CtrlShape
import com.vknxworks.frigatectrl.ui.theme.CtrlType
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private val EXPLORE_RANGES = listOf(24, 24 * 7, 24 * 30, 0)

private fun prettyLabel(text: String) = text.replace('_', ' ').replaceFirstChar { it.uppercase() }

/**
 * Explore: Frigate's tracked objects (every person, car, ... it followed), newest first, filtered by
 * camera, object and time, and searched by text when the server has semantic search on. Tapping one shows
 * its snapshot and details, and opens History at that moment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(onYou: () -> Unit, onOpenAt: (camera: String, time: Double) -> Unit, bottomPadding: Dp = 0.dp) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cameras by remember { mutableStateOf<List<String>>(emptyList()) }
    var labels by remember { mutableStateOf<List<String>>(emptyList()) }
    var semantic by remember { mutableStateOf(App.api.semanticSearch) }
    var camera by rememberSaveable { mutableStateOf<String?>(null) }
    var label by rememberSaveable { mutableStateOf<String?>(null) }
    var hours by rememberSaveable { mutableIntStateOf(24) }
    var typed by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var items by remember { mutableStateOf<List<TrackedObject>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var more by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<TrackedObject?>(null) }
    var thumbBase by remember { mutableStateOf<String?>(null) }

    fun filter() = ExploreFilter(setOfNotNull(camera), setOfNotNull(label), hours, query)

    LaunchedEffect(Unit) {
        runCatching { App.api.cameras().filter { it.enabled }.map { it.name } }.onSuccess { cameras = it; semantic = App.api.semanticSearch }
        labels = App.api.labels()
        // "…/thumbnail.webp" or ".jpg", depending on the server's version; the id is filled in per object.
        thumbBase = runCatching { App.api.eventThumbnailUrl("ID") }.getOrNull()
    }
    LaunchedEffect(camera, label, hours, query, reload) {
        error = null
        if (!refreshing) items = null
        try {
            val page = App.api.trackedObjects(filter())
            items = page
            // A text search gives its best matches in one go; the plain list continues in pages.
            more = page.size >= ExploreRequests.PAGE && (query.isBlank() || !semantic)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = e.message ?: ctx.getString(R.string.explore_load_failed)
            if (items == null) items = emptyList()
        }
        refreshing = false
    }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { refreshing = true; reload++ },
        modifier = Modifier.fillMaxSize().background(CtrlColors.Background),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(top = top, bottom = bottomPadding + 16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") {
                TabTitle(stringResource(R.string.tab_explore), onYou, label = stringResource(R.string.explore_label))
            }
            if (semantic) item(key = "search") {
                OutlinedTextField(
                    value = typed, onValueChange = { typed = it.take(200) },
                    placeholder = { Text(stringResource(R.string.explore_search_hint)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true, shape = CtrlShape.Field, colors = ctrlFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { query = typed.trim() }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 10.dp),
                )
            }
            item(key = "ranges") {
                ChipRow {
                    EXPLORE_RANGES.forEach { h ->
                        CtrlChip(
                            stringResource(
                                when (h) {
                                    24 -> R.string.explore_range_day
                                    24 * 7 -> R.string.explore_range_week
                                    24 * 30 -> R.string.explore_range_month
                                    else -> R.string.explore_range_all
                                },
                            ),
                            hours == h, { hours = h },
                        )
                    }
                }
            }
            if (cameras.size > 1) item(key = "cameras") {
                ChipRow {
                    CtrlChip(stringResource(R.string.explore_all_cameras), camera == null, { camera = null })
                    cameras.forEach { c -> CtrlChip(AlertNotifier.cameraLabel(c), camera == c, { camera = if (camera == c) null else c }) }
                }
            }
            if (labels.isNotEmpty()) item(key = "labels") {
                ChipRow {
                    CtrlChip(stringResource(R.string.explore_all_objects), label == null, { label = null })
                    labels.forEach { l -> CtrlChip(prettyLabel(l), label == l, { label = if (label == l) null else l }) }
                }
            }
            val list = items
            when {
                list == null -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().height(240.dp), Alignment.Center) { CircularProgressIndicator(color = CtrlColors.AccentText) }
                }
                error != null && list.isEmpty() -> item(key = "error") {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(error.orEmpty(), color = CtrlColors.Muted)
                        CtrlButton(stringResource(R.string.retry), { reload++ }, Modifier.fillMaxWidth())
                    }
                }
                list.isEmpty() -> item(key = "empty") {
                    Text(
                        stringResource(if (query.isNotBlank()) R.string.explore_no_match else R.string.explore_empty),
                        color = CtrlColors.Muted, modifier = Modifier.padding(24.dp),
                    )
                }
                else -> {
                    items(list, key = { it.id }, contentType = { "object" }) { o ->
                        ObjectRow(o, thumbBase) { open = o }
                    }
                    if (more) item(key = "more") {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                            CtrlButton(
                                stringResource(if (loadingMore) R.string.explore_loading_more else R.string.explore_load_more),
                                {
                                    if (!loadingMore) scope.launch {
                                        loadingMore = true
                                        try {
                                            val last = list.minOfOrNull { it.start } ?: return@launch
                                            val page = App.api.trackedObjects(filter(), before = last)
                                            val known = list.map { it.id }.toSet()
                                            items = list + page.filter { it.id !in known }
                                            more = page.size >= ExploreRequests.PAGE
                                        } catch (e: Exception) {
                                            if (e is kotlinx.coroutines.CancellationException) throw e
                                            more = false
                                        } finally {
                                            loadingMore = false
                                        }
                                    }
                                },
                                Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    open?.let { o ->
        ObjectSheet(o, onDismiss = { open = null }, onHistory = { open = null; onOpenAt(o.camera, o.start) })
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

private fun timeText(seconds: Double): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date((seconds * 1000).toLong()))

@Composable
private fun ObjectRow(o: TrackedObject, thumbBase: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(72.dp).clip(CtrlShape.Field).background(CtrlColors.Surface).border(1.dp, CtrlColors.Outline, CtrlShape.Field),
        ) {
            RemoteImage(thumbBase?.replace("/ID/", "/" + java.net.URLEncoder.encode(o.id, "UTF-8") + "/"), Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                listOfNotNull(prettyLabel(o.label), o.subLabel).joinToString(" · "),
                style = MaterialTheme.typography.titleMedium, color = CtrlColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(AlertNotifier.cameraLabel(o.camera), style = CtrlType.Mono, color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    timeText(o.start),
                    o.score?.let { stringResource(R.string.explore_score, (it * 100).toInt().coerceIn(0, 100)) },
                    if (o.end == null) stringResource(R.string.explore_in_progress) else null,
                ).joinToString(stringResource(R.string.list_separator)),
                style = CtrlType.Mono, color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A tracked object's snapshot and details, and the way into History at that moment. */
@Composable
private fun ObjectSheet(o: TrackedObject, onDismiss: () -> Unit, onHistory: () -> Unit) {
    CtrlBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                listOfNotNull(prettyLabel(o.label), o.subLabel).joinToString(" · "),
                style = MaterialTheme.typography.titleLarge, color = CtrlColors.Text,
            )
            if (o.hasSnapshot) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(CtrlShape.Tile).background(CtrlColors.VideoBackdrop)) {
                    RemoteImage(App.api.eventSnapshotUrl(o.id, 720), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
            DetailLine(stringResource(R.string.explore_camera), AlertNotifier.cameraLabel(o.camera))
            DetailLine(stringResource(R.string.explore_started), timeText(o.start))
            DetailLine(
                stringResource(R.string.explore_ended),
                o.end?.let { timeText(it) } ?: stringResource(R.string.explore_in_progress),
            )
            o.score?.let { DetailLine(stringResource(R.string.explore_confidence), stringResource(R.string.explore_score, (it * 100).toInt().coerceIn(0, 100))) }
            if (o.zones.isNotEmpty()) DetailLine(stringResource(R.string.explore_zones), o.zones.joinToString(stringResource(R.string.list_separator)) { prettyLabel(it) })
            o.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Text) }
            CtrlButton(stringResource(R.string.explore_open_history), onHistory, Modifier.fillMaxWidth(), icon = Icons.Outlined.History)
        }
    }
}

@Composable
private fun DetailLine(name: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(name.uppercase(), style = CtrlType.Label, color = CtrlColors.Muted, modifier = Modifier.width(110.dp))
        Text(value, style = CtrlType.Mono, color = CtrlColors.Text, modifier = Modifier.weight(1f))
    }
}
