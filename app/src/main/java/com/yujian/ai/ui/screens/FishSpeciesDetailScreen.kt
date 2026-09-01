package com.yujian.ai.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.knowledge.FishGuideItem
import com.yujian.ai.knowledge.FishKnowledgeCard
import com.yujian.ai.knowledge.FishKnowledgeDetail
import com.yujian.ai.knowledge.FishKnowledgeGalleryImage
import com.yujian.ai.knowledge.FishKnowledgeVideo
import com.yujian.ai.knowledge.toFallbackDetail
import com.yujian.ai.ui.components.FishIllustration
import com.yujian.ai.ui.components.RemoteImage
import com.yujian.ai.ui.components.TagChip
import com.yujian.ai.ui.components.YujianTopBar
import com.yujian.ai.ui.theme.CardWhite
import com.yujian.ai.ui.theme.DeepInk
import com.yujian.ai.ui.theme.MutedInk
import com.yujian.ai.ui.theme.SoftWater
import com.yujian.ai.ui.theme.WarmBackground
import com.yujian.ai.ui.theme.WaterTeal

private val cardLabels = mapOf(
    "HERO" to "英雄卡",
    "IDENTIFICATION" to "识别卡",
    "ECO" to "生态卡",
    "GEAR" to "装备卡",
    "SKILL" to "作钓技术卡",
)

private val cardGold = Color(0xFFD6B56D)
private val cardInk = Color(0xFF171717)

@Composable
fun FishSpeciesDetailScreen(
    detail: FishKnowledgeDetail?,
    fallback: FishGuideItem?,
    loading: Boolean,
    offlinePreview: Boolean,
    error: String?,
    resolveAssetUrl: (String?) -> String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpenCatch: () -> Unit,
) {
    val content = detail ?: fallback?.toFallbackDetail()
    if (content == null) {
        Box(Modifier.fillMaxSize().background(WarmBackground), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (loading) "正在加载鱼种详情…" else "暂时无法读取鱼种详情", color = MutedInk, fontSize = 14.sp)
                if (!loading) {
                    Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "重试", modifier = Modifier.size(17.dp))
                        Text("重试", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        return
    }

    val species = content.species
    val aliases = species.aliases.joinToString("、")
    val catchCount = fallback?.catches ?: 0
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(WarmBackground),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { YujianTopBar(title = species.nameCn, subtitle = aliases.ifBlank { species.id }, onBack = onBack) }

        if (error != null || offlinePreview) {
            item {
                Row(
                    Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(Color(0xFFFFF3E8), RoundedCornerShape(16.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (offlinePreview) "离线预览：${error ?: "当前内容来自本地预览"}" else (error ?: "内容暂不可用"),
                        color = Color(0xFF9A5B16), fontSize = 11.sp, modifier = Modifier.weight(1f),
                    )
                    Button(onClick = onRetry, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                        Text("重试", fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            HeroCard(content, resolveAssetUrl)
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(species.nameCn, color = DeepInk, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        if (!species.scientificName.isNullOrBlank()) {
                            Text(species.scientificName, color = MutedInk, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                    Box(Modifier.size(44.dp).background(CardWhite, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Bookmark, contentDescription = "收藏", tint = WaterTeal)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
                    if (species.category.isNotBlank()) TagChip(species.category, true)
                    if (!species.family.isNullOrBlank()) TagChip(species.family!!)
                    if (!species.genus.isNullOrBlank()) TagChip(species.genus!!)
                }
                if (species.summary.isNotBlank()) {
                    Text(species.summary, color = DeepInk, fontSize = 14.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 18.dp))
                }
            }
        }

        item {
            SectionCard(title = "五张鱼鉴卡") {
                if (content.cards.isEmpty()) {
                    EmptyAsset("鱼鉴卡内容待发布")
                } else {
                    LazyRow(contentPadding = PaddingValues(end = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(content.cards, key = { it.id }) { card ->
                            FishCardView(card = card, imageUrl = resolveAssetUrl(card.imageUrl))
                        }
                    }
                }
            }
        }

        item {
            KnowledgeSection(content)
        }

        if (content.gallery.isNotEmpty()) {
            item {
                SectionCard(title = "真实照片") {
                    LazyRow(contentPadding = PaddingValues(end = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(content.gallery, key = { it.id }) { image ->
                            GalleryView(image, resolveAssetUrl(image.url))
                        }
                    }
                }
            }
        }

        item {
            FishingSection(content, resolveAssetUrl)
        }

        item {
            DynamicSection(catchCount = catchCount, onOpenCatch = onOpenCatch)
        }

        if (content.similarity.isNotEmpty()) {
            item {
                SectionCard(title = "相似鱼辨识") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        content.similarity.forEach { similar ->
                            Column(Modifier.fillMaxWidth().background(WarmBackground, RoundedCornerShape(12.dp)).padding(12.dp)) {
                                Text(similar.similarSpeciesNameCn, color = DeepInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(similar.difference, color = MutedInk, fontSize = 12.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 5.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(detail: FishKnowledgeDetail, resolveAssetUrl: (String?) -> String?) {
    val imageUrl = detail.cover?.takeIf { it.status == "ACTIVE" }?.imageUrl?.let(resolveAssetUrl)
        ?: detail.species.coverImage?.let(resolveAssetUrl)
    Box(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(286.dp)
            .shadow(18.dp, RoundedCornerShape(28.dp), ambientColor = WaterTeal.copy(alpha = .08f), spotColor = WaterTeal.copy(alpha = .08f))
            .clip(RoundedCornerShape(28.dp)).background(SoftWater),
    ) {
        if (imageUrl != null) {
            RemoteImage(imageUrl, Modifier.fillMaxSize(), contentDescription = detail.species.nameCn, contentScale = ContentScale.Crop)
        } else {
            FishIllustration(Modifier.align(Alignment.Center), size = 210.dp, bodyColor = WaterTeal.copy(alpha = .72f))
        }
        Box(Modifier.align(Alignment.TopStart).padding(18.dp).background(Color.White.copy(alpha = .88f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 7.dp)) {
            Text("${detail.species.nameCn} · ${detail.cover?.style ?: "FISH KNOWLEDGE"}", color = WaterTeal, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FishCardView(card: FishKnowledgeCard, imageUrl: String?) {
    Box(
        Modifier.width(190.dp).height(238.dp).clip(RoundedCornerShape(16.dp)).background(Brush.verticalGradient(listOf(cardInk, Color(0xFF302718)))),
    ) {
        if (imageUrl != null) RemoteImage(imageUrl, Modifier.fillMaxSize(), contentDescription = card.title, contentScale = ContentScale.Crop)
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(card.cardType, color = cardGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(cardLabels[card.cardType] ?: "鱼鉴卡", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
            }
            Column {
                Text(card.title.ifBlank { "内容待补充" }, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (card.description.isNotBlank()) Text(card.description, color = Color.White.copy(alpha = .78f), fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
                if (imageUrl == null) Text("暂无真实图片", color = cardGold.copy(alpha = .82f), fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun KnowledgeSection(detail: FishKnowledgeDetail) {
    val profile = detail.profile
    SectionCard(title = "结构化知识") {
        val facts = listOfNotNull(
            profile.bodyShape?.takeIf { it.isNotBlank() }?.let { "体型" to it },
            profile.food?.takeIf { it.isNotBlank() }?.let { "食性" to it },
            profile.season.takeIf { it.isNotEmpty() }?.let { "活跃季节" to it.joinToString("、") },
            profile.habitat.takeIf { it.isNotEmpty() }?.let { "常见水域" to it.joinToString("、") },
        )
        if (facts.isNotEmpty()) {
            facts.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (label, value) ->
                        InfoItem(label, value, Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                if (row != facts.chunked(2).last()) Spacer(Modifier.height(12.dp))
            }
        }
        if (profile.features.isNotEmpty()) {
            Text("视觉特征", color = MutedInk, fontSize = 11.sp, modifier = Modifier.padding(top = 16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 7.dp)) {
                profile.features.forEach { TagChip(it, true) }
            }
        }
        if (facts.isEmpty() && profile.features.isEmpty()) EmptyAsset("结构化知识待补充")
    }
}

@Composable
private fun FishingSection(detail: FishKnowledgeDetail, resolveAssetUrl: (String?) -> String?) {
    SectionCard(title = "怎么钓这条鱼") {
        val fishing = detail.fishing
        if (fishing.waterLayer != null) InfoItem("水层", fishing.waterLayer, Modifier.fillMaxWidth())
        if (fishing.bait.isNotEmpty()) {
            Text("饵料", color = MutedInk, fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 7.dp)) { fishing.bait.forEach { TagChip(it, true) } }
        }
        if (fishing.method.isNotEmpty()) {
            Text("钓法", color = MutedInk, fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 7.dp)) { fishing.method.forEach { TagChip(it) } }
        }
        if (fishing.summary.isNotBlank()) Text(fishing.summary, color = DeepInk, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 14.dp))
        if (fishing.waterLayer == null && fishing.bait.isEmpty() && fishing.method.isEmpty() && fishing.summary.isBlank()) EmptyAsset("钓鱼知识待补充")
        if (detail.videos.isNotEmpty()) {
            Text("相关视频", color = MutedInk, fontSize = 11.sp, modifier = Modifier.padding(top = 17.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 7.dp)) {
                detail.videos.forEach { video -> VideoRow(video, resolveAssetUrl(video.coverUrl)) }
            }
        }
    }
}

@Composable
private fun VideoRow(video: FishKnowledgeVideo, coverUrl: String?) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(video.videoUrl))) } }
            .background(WarmBackground, RoundedCornerShape(12.dp)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(58.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFFE2E8E6)), contentAlignment = Alignment.Center) {
            if (coverUrl != null) RemoteImage(coverUrl, Modifier.fillMaxSize(), contentDescription = video.title)
            Icon(Icons.Rounded.PlayArrow, contentDescription = "播放", tint = WaterTeal)
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(video.title, color = DeepInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("${video.type} · ${video.duration}s${if (video.tags.isEmpty()) "" else " · ${video.tags.joinToString("、")}"}", color = MutedInk, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Text("打开", color = WaterTeal, fontSize = 11.sp)
    }
}

@Composable
private fun GalleryView(image: FishKnowledgeGalleryImage, imageUrl: String?) {
    Column(Modifier.width(170.dp)) {
        Box(Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(14.dp)).background(SoftWater), contentAlignment = Alignment.Center) {
            if (imageUrl != null) RemoteImage(imageUrl, Modifier.fillMaxSize(), contentDescription = image.title, contentScale = ContentScale.Crop)
            else Text("暂无图片", color = MutedInk, fontSize = 11.sp)
        }
        Text(image.title ?: image.type, color = DeepInk, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun DynamicSection(catchCount: Int, onOpenCatch: () -> Unit) {
    SectionCard(title = "我的鱼获与排行榜") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onOpenCatch, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = WaterTeal), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(if (catchCount > 0) "查看我的鱼获 · $catchCount" else "记录我的鱼获", fontSize = 12.sp)
            }
            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("排行榜即将开放", fontSize = 12.sp)
            }
        }
        Text("动态内容入口已预留，当前接口不会伪造用户统计。", color = MutedInk, fontSize = 10.sp, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(CardWhite, RoundedCornerShape(24.dp)).padding(18.dp)) {
        Text(title, color = DeepInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Column(Modifier.padding(top = 13.dp)) { content() }
    }
}

@Composable
private fun InfoItem(label: String, value: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = MutedInk, fontSize = 11.sp)
        Text(value.orEmpty(), color = DeepInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun EmptyAsset(text: String) {
    Box(Modifier.fillMaxWidth().background(WarmBackground, RoundedCornerShape(12.dp)).padding(14.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MutedInk, fontSize = 12.sp)
    }
}
