package com.yujian.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.ui.designsystem.components.YuJianTextAction
import com.yujian.ai.ui.designsystem.components.YuJianTextActionRole
import com.yujian.ai.ui.recognition.result.RecognitionPlace
import com.yujian.ai.ui.recognition.result.RecognitionPlaceRecentStore
import com.yujian.ai.ui.recognition.result.RecognitionResultInputValidation
import com.yujian.ai.ui.recognition.result.searchRecognitionPlaces
import com.yujian.ai.ui.theme.DeepInk
import com.yujian.ai.ui.theme.MutedInk
import com.yujian.ai.ui.theme.WaterTeal
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ResultLocationPickerSheet(
    value: String,
    resolving: Boolean,
    currentLocationError: String?,
    onDismiss: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    onSelectPlace: (RecognitionPlace) -> Unit,
    onClearLocation: () -> Unit,
    onClearCurrentLocationError: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context) { RecognitionPlaceRecentStore(context) }
    var recent by remember(context) { mutableStateOf(store.read()) }
    var query by remember { mutableStateOf("") }
    var settledQuery by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<RecognitionPlace>()) }
    var searching by remember { mutableStateOf(false) }
    var providerError by remember { mutableStateOf(false) }
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val sheetHeight = (screenHeightDp * if (query.isBlank()) 0.40f else 0.56f)
        .coerceIn(if (query.isBlank()) 280f else 320f, if (query.isBlank()) 420f else 520f).dp

    LaunchedEffect(query) {
        if (query.isBlank()) {
            settledQuery = ""
            results = emptyList()
            searching = false
            providerError = false
        } else {
            searching = true
            providerError = false
            delay(300L)
            settledQuery = query
            val found = searchRecognitionPlaces(context, query)
            results = found.orEmpty()
            providerError = found == null
            searching = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().height(sheetHeight).padding(horizontal = 20.dp).padding(top = 2.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("鱼获地点", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = DeepInk, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = RecognitionResultInputValidation.takeUnicodeCodePoints(
                        it.replace(Regex("\\R+"), " "), 40,
                    )
                    onClearCurrentLocationError()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("recognition-location-search"),
                singleLine = true,
                placeholder = { Text("搜索地点（如：千岛湖、富春江）", color = MutedInk) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MutedInk) },
                trailingIcon = if (query.isNotEmpty()) {
                    { IconButton(onClick = { query = ""; onClearCurrentLocationError() }) { Icon(Icons.Rounded.Close, contentDescription = "清除搜索", tint = MutedInk) } }
                } else null,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xDDF7FAFB),
                    unfocusedContainerColor = Color(0xCFF7FAFB),
                    focusedBorderColor = WaterTeal,
                    unfocusedBorderColor = Color(0x47FFFFFF),
                ),
            )

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (query.isBlank()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("最近使用", modifier = Modifier.weight(1f), color = DeepInk, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
                        if (recent.isNotEmpty()) {
                            YuJianTextAction(text = "清除", onClick = { store.clear(); recent = emptyList() }, role = YuJianTextActionRole.MUTED)
                        }
                    }
                    if (recent.isEmpty()) {
                        Text("暂无最近使用地点", color = MutedInk, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(vertical = 4.dp))
                    } else {
                        recent.forEachIndexed { index, place ->
                            PlaceRow(place, testTag = "recognition-location-recent-$index") {
                                recent = store.commit(place)
                                onSelectPlace(place)
                            }
                        }
                    }
                } else if (searching || settledQuery != query) {
                    Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = WaterTeal, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else if (providerError) {
                    Text("地点搜索暂不可用，请稍后重试", color = MutedInk, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(vertical = 12.dp))
                } else if (results.isEmpty()) {
                    Text("没有找到相关地点", color = MutedInk, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    results.forEachIndexed { index, place ->
                        PlaceRow(place, testTag = "recognition-location-result-$index") {
                            recent = store.commit(place)
                            onSelectPlace(place)
                        }
                    }
                }
                currentLocationError?.let {
                    Text(it, color = MutedInk, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp).testTag("recognition-location-current-error"))
                }
            }

            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
                    .background(Color(0xCFF7FAFB)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                    .clickable(enabled = !resolving, role = Role.Button, onClick = onUseCurrentLocation)
                    .semantics { contentDescription = if (resolving) "正在获取位置" else "使用当前位置" }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (resolving) "正在获取位置…" else "使用当前位置", modifier = Modifier.weight(1f), color = DeepInk, fontSize = 15.sp, lineHeight = 22.sp)
                if (resolving) CircularProgressIndicator(color = WaterTeal, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            if (value.isNotBlank()) {
                YuJianTextAction(text = "清除", onClick = onClearLocation, role = YuJianTextActionRole.MUTED)
            }
        }
    }
}

@Composable
private fun PlaceRow(place: RecognitionPlace, testTag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
            .background(Color(0xCFF7FAFB)).clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = listOf(place.name, place.secondaryAddress).filter(String::isNotBlank).joinToString("，") }
            .testTag(testTag).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⌖", color = WaterTeal, fontSize = 20.sp, modifier = Modifier.width(24.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(place.name, color = DeepInk, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (place.secondaryAddress.isNotBlank()) Text(place.secondaryAddress, color = MutedInk, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
