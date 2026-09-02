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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.yujian.ai.knowledge.FishKnowledgeCardContent
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

private val cardOrder = listOf("HERO", "IDENTIFICATION", "ECO", "GEAR", "SKILL")
private val cardLabels = mapOf(
    "HERO" to "英雄卡",
    "IDENTIFICATION" to "识别卡",
    "ECO" to "生态卡",
    "GEAR" to "装备卡",
    "SKILL" to "作钓技术卡",
)
private val cardGold = Color(0xFFD6B56D)
private val cardInk = Color(0xFF171717)

private enum class DetailTab(val label: String) {
    INTRO("鱼种介绍"),
    CATCH("我的鱼获"),
    RANKING("排行榜"),
}

private fun cardSlots(detail: FishKnowledgeDetail): List<FishKnowledgeCard> {
    val existing = detail.cards.associateBy { it.cardType.trim().uppercase() }
    return cardOrder.mapIndexed { index, type ->
        existing[type] ?: FishKnowledgeCard(
            id = -(index + 1),
            speciesId = detail.species.id,
            cardType = type,
            title = "${detail.species.nameCn}${cardLabels[type] ?: "鱼鉴卡"}",
            imageUrl = "",
            description = "",
            content = FishKnowledgeCardContent(type = type),
            sortOrder = index,
            status = "DRAFT",
        )
    }
}

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
    val cards = remember(content) { cardSlots(content) }
    var selectedTab by remember(species.id) { mutableStateOf(DetailTab.INTRO) }

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

        item { HeroCard(content, resolveAssetUrl) }

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
            SectionCard(title = "五张鱼鉴卡 · 左右滑动") {
                LazyRow(contentPadding = PaddingValues(end = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(cards, key = { it.id }) { card ->
                        FishCardView(card = card, imageUrl = resolveAssetUrl(card.imageUrl))
                    }
                }
            }
        }

        item { DetailTabs(selected = selectedTab, onSelected = { selectedTab = it }) }

        when (selectedTab) {
            DetailTab.INTRO -> {
                item { KnowledgeSection(content) }

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

                item { FishingSection(content, resolveAssetUrl) }

                if (content.similarity.isNotEmpty()) {
                    item { SimilaritySection(content) }
                }
            }

            DetailTab.CATCH -> item { CatchSection(catchCount = catchCount, onOpenCatch = onOpenCatch) }
            DetailTab.RANKING -> item { RankingSection() }
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
            Text(
                "${detail.species.nameCn} · ${detail.knowledge.displayTag ?: detail.cover?.style ?: "FISH KNOWLEDGE"}",
                color = WaterTeal,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun FishCardView(card: FishKnowledgeCard, imageUrl: String?) {
    val structured = card.content
    val supportingText = when (card.cardType) {
        "IDENTIFICATION" -> structured.features.take(2).joinToString(" · ") { "${it.title}：${it.text}" }
        "ECO" -> listOf(structured.waterLayer, structured.behavior).filter { it.isNotBlank() }.joinToString(" · ")
        "GEAR" -> listOf(structured.rod, structured.hook).filter { it.isNotBlank() }.joinToString(" · ")
        "SKILL" -> structured.tip
        else -> structured.description.ifBlank { card.description }
    }
    Box(
        Modifier.width(190.dp).height(238.dp).clip(RoundedCornerShape(16.dp)).background(Brush.verticalGradient(listOf(cardInk, Color(0xFF302718)))),
    ) {
        if (imageUrl != null) RemoteImage(imageUrl, Modifier.fillMaxSize(), contentDescription = card.title, contentScale = ContentScale.Crop)
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(card.cardType, color = cardGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(cardLabels[card.cardType] ?: "鱼鉴卡", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
                if (structured.tag.isNotBlank()) Text(structured.tag, color = cardGold, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
                if (structured.rarity > 0 || structured.power > 0 || structured.challenge > 0) {
                    Text("稀有 ${structured.rarity}  ·  力量 ${structured.power}  ·  挑战 ${structured.challenge}", color = Color.White.copy(alpha = .75f), fontSize = 9.sp, modifier = Modifier.padding(top = 5.dp))
                }
            }
            Column {
                Text(card.title.ifBlank { "内容待补充" }, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (supportingText.isNotBlank()) {
                    Text(supportingText, color = Color.White.copy(alpha = .78f), fontSize = 10.sp, lineHeight = 15.sp, maxLines = 3, modifier = Modifier.padding(top = 6.dp))
                }
                if (imageUrl == null) Text("暂无真实图片 · ${if (card.status == "ACTIVE") "可展示" else "待发布"}", color = cardGold.copy(alpha = .82f), fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun DetailTabs(selected: DetailTab, onSelected: (DetailTab) -> Unit) {
    Row(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(CardWhite, RoundedCornerShape(18.dp)).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DetailTab.entries.forEach { tab ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (tab == selected) DeepInk else Color.Transparent)
                    .clickable { onSelected(tab) }.padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(tab.label, color = if (tab == selected) Color.White else MutedInk, fontSize = 12.sp, fontWeight = if (tab == selected) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun KnowledgeSection(detail: FishKnowledgeDetail) {
    val profile = detail.profile
    val facts = listOfNotNull(
        profile.bodyShape?.takeIf { it.isNotBlank() }?.let { "体型" to it },
        profile.food?.takeIf { it.isNotBlank() }?.let { "食性" to it },
        profile.season.takeIf { it.isNotEmpty() }?.let { "活跃季节" to it.joinToString("、") },
        profile.habitat.takeIf { it.isNotEmpty() }?.let { "常见水域" to it.joinToString("、") },
        detail.knowledge.ecology.waterLayer.takeIf { it.isNotBlank() }?.let { "活动水层" to it },
    )
    SectionCard(title = "结构化知识") {
        if (facts.isNotEmpty()) {
            facts.chunked(2).forEachIndexed { index, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (label, value) -> InfoItem(label, value, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                if (index < facts.chunked(2).lastIndex) Spacer(Modifier.height(12.dp))
            }
        }
        if (profile.features.isNotEmpty()) {
            Text("视觉特征", color = MutedInk, fontSize = 11.sp, modifier = Modifier.padding(top = 16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 7.dp)) {
                profile.features.forEach { Text("• $it", color = DeepInk, fontSize = 13.sp, lineHeight = 19.sp) }
            }
        }
        val behavior = detail.knowledge.ecology.behavior
        if (behavior.isNotBlank()) {
            Text("习性", color = MutedInk, fontSize = 11.sp, modifier = Modifier.padding(top = 16.dp))
            Text(behavior, color = DeepInk, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 5.dp))
        }
        if (facts.isEmpty() && profile.features.isEmpty() && behavior.isBlank()) EmptyAsset("结构化知识待补充")
    }
}

@Composable
private fun FishingSection(detail: FishKnowledgeDetail, resolveAssetUrl: (String?) -> String?) {
    SectionCard(title = "怎么钓这条鱼") {
        val fishing = detail.fishing
        val gear = detail.knowledge.gear
        val skill = detail.knowledge.skill
        val waterLayer = fishing.waterLayer
        val bait = gear.bait.ifEmpty { fishing.bait }
        val methods = fishing.method
        if (waterLayer != null) InfoItem("水层", waterLayer, Modifier.fillMaxWidth())
        if (bait.isNotEmpty()) {
            Text("饵料", color = MutedInk, fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 7.dp)) { bait.forEach { TagChip(it, true) } }
        }
        if (methods.isNotEmpty()) {
            Text("钓法", color = MutedInk, fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 7.dp)) { methods.forEach { TagChip(it) } }
        }
        val equipment = listOf(gear.rod, gear.line, gear.hook).filter { it.isNotBlank() }
        if (equipment.isNotEmpty()) {
            Text("装备建议", color = MutedInk, fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 7.dp)) {
                equipment.forEach { Text("• $it", color = DeepInk, fontSize = 12.sp) }
            }
        }
        if (fishing.summary.isNotBlank()) Text(fishing.summary, color = DeepInk, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 14.dp))
        if (skill.tip.isNotBlank()) Text("提醒：${skill.tip}", color = WaterTeal, fontSize = 12.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 12.dp))
        if (waterLayer == null && bait.isEmpty() && methods.isEmpty() && fishing.summary.isBlank() && equipment.isEmpty() && skill.tip.isBlank()) EmptyAsset("钓鱼知识待补充")
        if (detail.videos.isNotEmpty()) {
            Text("相关视频", color = MutedInk, fontSize = 11.sp, modifier = Modifier.padding(top = 17.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 7.dp)) {
                detail.videos.forEach { video -> VideoRow(video, resolveAssetUrl(video.coverUrl)) }
            }
        }
    }
}

@Composable
private fun SimilaritySection(detail: FishKnowledgeDetail) {
    SectionCard(title = "相似鱼辨识") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            detail.similarity.forEach { similar ->
                Column(Modifier.fillMaxWidth().background(WarmBackground, RoundedCornerShape(12.dp)).padding(12.dp)) {
                    Text(similar.similarSpeciesNameCn, color = DeepInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(similar.difference, color = MutedInk, fontSize = 12.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
    }
}

@Composable
private fun CatchSection(catchCount: Int, onOpenCatch: () -> Unit) {
    SectionCard(title = "我的鱼获") {
        if (catchCount == 0) EmptyAsset("还没有鱼获记录") else CatchPreviewRow(catchCount)
        Button(
            onClick = onOpenCatch,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WaterTeal),
        ) {
            Text(if (catchCount > 0) "查看我的鱼获 · $catchCount" else "记录我的鱼获")
        }
        Text("鱼获详情会保留照片、时间、重量、长度和地点。", color = MutedInk, fontSize = 10.sp, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun CatchPreviewRow(count: Int) {
    Row(Modifier.fillMaxWidth().background(WarmBackground, RoundedCornerShape(14.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(68.dp).clip(RoundedCornerShape(10.dp)).background(SoftWater), contentAlignment = Alignment.Center) {
            FishIllustration(size = 48.dp, bodyColor = WaterTeal.copy(alpha = .6f))
        }
        Column(Modifier.padding(start = 10.dp)) {
            Text("最近鱼获", color = DeepInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("共 $count 条 · 照片待查看", color = MutedInk, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            Text("时间 / 重量 / 长度 / 地点", color = MutedInk, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun RankingSection() {
    SectionCard(title = "排行榜") {
        EmptyAsset("暂无排行榜")
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            Text("敬请期待", color = MutedInk, fontSize = 12.sp)
        }
        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text("排行榜即将开放")
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
