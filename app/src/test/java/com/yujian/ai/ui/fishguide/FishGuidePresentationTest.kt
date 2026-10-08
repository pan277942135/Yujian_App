package com.yujian.ai.ui.fishguide

import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.knowledge.FishGuideItem
import com.yujian.ai.knowledge.FishKnowledgeAsset
import com.yujian.ai.knowledge.FishKnowledgeCard
import com.yujian.ai.knowledge.FishKnowledgeCardContent
import com.yujian.ai.knowledge.FishKnowledgeDetail
import com.yujian.ai.knowledge.FishKnowledgeEcology
import com.yujian.ai.knowledge.FishKnowledgeFishing
import com.yujian.ai.knowledge.FishKnowledgeGear
import com.yujian.ai.knowledge.FishKnowledgeProfile
import com.yujian.ai.knowledge.FishKnowledgeSkill
import com.yujian.ai.knowledge.FishKnowledgeSpecies
import com.yujian.ai.knowledge.FishKnowledgeStructured
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FishGuidePresentationTest {
    private val species = listOf(
        FishGuideItem("grass", "草鱼", aliases = listOf("鲩鱼"), discovered = true, catches = 3),
        FishGuideItem("crucian", "鲫鱼", aliases = listOf("鲫"), discovered = false),
        FishGuideItem("carp", "鲤鱼", discovered = true, catches = 1),
    )

    @Test
    fun countsLitSpeciesAndProgressFromRuntimeData() {
        assertEquals(2, species.litCount())
        assertEquals(3, species.size)
        assertEquals(2f / 3f, species.progressFraction(), 0.0001f)
    }

    @Test
    fun carouselSelectionKeepsKnownSpeciesAndFallsBackToFirst() {
        assertEquals(2, selectionIndex(species, "carp"))
        assertEquals(0, selectionIndex(species, "missing"))
    }

    @Test
    fun datasetShapesCoverEmptyThroughCarousel() {
        assertEquals(FishGuideDatasetShape.EMPTY, emptyList<FishGuideItem>().datasetShape())
        assertEquals(FishGuideDatasetShape.SINGLE, species.take(1).datasetShape())
        assertEquals(FishGuideDatasetShape.TWO, species.take(2).datasetShape())
        assertEquals(FishGuideDatasetShape.CAROUSEL, species.datasetShape())
    }

    @Test
    fun recordCountIsQuietAndOmitsZero() {
        assertEquals("3 次记录", formatRecordCount(3))
        assertNull(formatRecordCount(0))
        assertNull(formatRecordCount(-1))
    }

    @Test
    fun presentationMapsRuntimeImageAndLitStateWithoutChangingDomainItem() {
        val item = species.toFishGuidePresentation { value -> "resolved:$value" }.first()
        assertEquals("grass", item.id)
        assertEquals("草鱼", item.name)
        assertEquals("resolved:null", item.imageUrl)
        assertTrue(item.discovered)
        assertEquals(3, item.catches)
    }

    @Test
    fun savedRecordAssociationUsesStableSpeciesIdAndRecentFirst() {
        val matching = listOf(
            catch("legacy-new", "", "草鱼", "2026-09-28", "2026-09-28"),
            catch("stable-old", "grass", "别名", "2026-09-20", "2026-09-20"),
            catch("stable-new", "GRASS", "不匹配的旧名称", "", "2026-09-29"),
            catch("other", "carp", "草鱼", "2026-09-30", "2026-09-30"),
            catch("", "grass", "草鱼", "2026-09-30", "2026-09-30"),
        )

        assertEquals(
            listOf("stable-new", "legacy-new", "stable-old"),
            savedRecordsForSpecies(species.first().copy(nameCn = "草鱼"), matching).map { it.id },
        )
    }

    @Test
    fun knowledgeCarouselAlwaysHasFiveTruthfulSlotsAndIgnoresGameFields() {
        val detail = knowledgeDetail(
            cards = listOf(
                card("grass", "SKILL", 4, FishKnowledgeCardContent(find = "寻找缓流")),
                card("another-species", "GEAR", 3, FishKnowledgeCardContent(rod = "不可借用的竿")),
                card("grass", "GEAR", 3, FishKnowledgeCardContent(method = "底钓", rod = "中长竿", rarity = 9, power = 8, challenge = 7)),
                card("grass", "HERO", 0, FishKnowledgeCardContent(description = "草食性鱼类", rarity = 5, power = 5, challenge = 5)),
                card("grass", "ECOLOGY", 2, FishKnowledgeCardContent(habitat = listOf("湖库"), season = "春夏")),
                card("grass", "IDENTIFICATION", 1, FishKnowledgeCardContent(features = listOf(com.yujian.ai.knowledge.FishKnowledgeFeature("体形", "细长")))),
                card("grass", "SKILL", 4, FishKnowledgeCardContent(find = "后置重复数据")),
            ),
        )

        val cards = detail.toKnowledgeCardPresentations()
        assertEquals(listOf("HERO", "IDENTIFICATION", "ECO", "GEAR", "SKILL"), cards.map { it.type })
        assertEquals(listOf("01 / 05", "02 / 05", "03 / 05", "04 / 05", "05 / 05"), cards.map { it.pageLabel })
        assertEquals("鱼种摘要", cards[0].summary)
        assertNull(cards[0].imageUrl)
        assertFalse(cards[0].available)
        assertEquals("湖库", cards[2].facts.first { it.label == "常见水域" }.value)
        assertEquals("中长竿", cards[3].facts.first { it.label == "鱼竿" }.value)
        assertEquals("寻找缓流", cards[4].facts.first { it.label == "找鱼" }.value)
        assertFalse(cards.flatMap { it.facts }.any { fact ->
            fact.label.contains("稀有") || fact.label.contains("力量") || fact.label.contains("挑战")
        })
        assertFalse(cards.flatMap { it.facts }.any { fact -> fact.value.contains("不可借用") || fact.value.contains("后置重复") })
    }

    @Test
    fun missingKnowledgeKeepsEveryPositionWithUnavailableState() {
        val detail = knowledgeDetail(
            summary = "",
            cover = null,
            category = "",
            profile = FishKnowledgeProfile(null, emptyList(), emptyList(), null, emptyList()),
        )
        val cards = detail.toKnowledgeCardPresentations()
        assertEquals(5, cards.size)
        assertEquals(listOf(1, 2, 3, 4, 5), cards.map { it.position })
        assertTrue(cards.none { it.available })
    }

    @Test
    fun serializedBackendPayloadIsNeverProjectedAsKnowledgeCardCopy() {
        val detail = knowledgeDetail(
            summary = "{ \"type\": \"species_summary\", \"value\": \"raw\" }",
            cards = listOf(
                card(
                    "grass",
                    "HERO",
                    0,
                    FishKnowledgeCardContent(description = "[ { \"type\": \"summary\" } ]"),
                ).copy(description = "{ \"type\": \"legacy_description\" }"),
                card(
                    "grass",
                    "IDENTIFICATION",
                    1,
                    FishKnowledgeCardContent(
                        features = listOf(
                            com.yujian.ai.knowledge.FishKnowledgeFeature(
                                "{ \"type\": \"feature\" }",
                                "[ { \"value\": \"raw\" } ]",
                            ),
                            com.yujian.ai.knowledge.FishKnowledgeFeature("体形", "细长"),
                        ),
                    ),
                ),
            ),
        )

        val cards = detail.toKnowledgeCardPresentations()
        val visibleCopy = buildList {
            cards.forEach { card ->
                card.summary?.let { add(it) }
                card.facts.forEach { fact ->
                    add(fact.label)
                    add(fact.value)
                }
            }
        }

        assertNull(cards[0].summary)
        assertTrue(visibleCopy.any { it == "细长" })
        assertFalse(visibleCopy.any { it.trimStart().startsWith('{') || it.trimStart().startsWith('[') })
    }

    @Test
    fun longStructuredKnowledgeFactIsPreservedForNaturalReflow() {
        val longFact = "适合在水草边缘观察鱼群活动与水流变化。".repeat(24)
        val detail = knowledgeDetail(
            cards = listOf(
                card(
                    "grass",
                    "SKILL",
                    4,
                    FishKnowledgeCardContent(find = longFact),
                ),
            ),
        )

        val projected = detail.toKnowledgeCardPresentations().last().facts.single { it.label == "找鱼" }
        assertEquals(longFact, projected.value)
    }

    @Test
    fun speciesAndRolePairsMapToTheirOwnV2Assets() {
        val cases = listOf(
            Triple("grass_carp", "HERO", "grass-hero.webp"),
            Triple("sharpbelly", "IDENTIFICATION", "sharpbelly-id.webp"),
            Triple("tilapia", "ECO", "tilapia-eco.webp"),
        )

        cases.forEach { (speciesId, role, url) ->
            val cards = knowledgeDetail(
                speciesId = speciesId,
                knowledgeAssets = mapOf(role to FishKnowledgeAsset(role, url, version = "v2")),
            ).toKnowledgeCardPresentations()

            assertEquals(url, cards.single { it.type == role }.imageUrl)
            assertEquals(1, cards.count { it.imageUrl != null })
        }
    }

    @Test
    fun fiveRoleMappingIsStableAndResolvesUrlsAndCacheIdentity() {
        val assets = linkedMapOf(
            "SKILL" to FishKnowledgeAsset("SKILL", "skill.webp", version = "v2"),
            "ECO" to FishKnowledgeAsset("ECO", "eco.webp", resourceId = "eco-7"),
            "HERO" to FishKnowledgeAsset("HERO", "hero.webp", version = "v2"),
            "GEAR" to FishKnowledgeAsset("GEAR", "gear.webp", version = "v2"),
            "IDENTIFICATION" to FishKnowledgeAsset("IDENTIFICATION", "id.webp", version = "v2"),
        )
        val cards = knowledgeDetail(knowledgeAssets = assets)
            .toKnowledgeCardPresentations { "https://cdn.example/$it" }

        assertEquals(listOf("HERO", "IDENTIFICATION", "ECO", "GEAR", "SKILL"), cards.map { it.type })
        assertEquals(
            listOf("hero.webp", "id.webp", "eco.webp", "gear.webp", "skill.webp"),
            cards.map { it.imageUrl?.substringAfterLast('/') },
        )
        assertTrue(cards.all { it.imageCacheIdentity?.contains("v2") == true || it.type == "ECO" })
        assertTrue(cards[2].imageCacheIdentity.orEmpty().contains("eco-7"))
    }

    @Test
    fun activeCardFallbackRequiresExactSpeciesRoleAndActiveStatus() {
        val cards = listOf(
            card("other", "IDENTIFICATION", 0, FishKnowledgeCardContent(), imageUrl = "wrong-species.webp"),
            card("grass", "ECO", 1, FishKnowledgeCardContent(), imageUrl = "draft.webp", status = "DRAFT"),
            card("grass", "HERO", 2, FishKnowledgeCardContent(), imageUrl = "hero.webp"),
            card("grass", "GEAR", 3, FishKnowledgeCardContent(), imageUrl = "gear.webp"),
        )
        val result = knowledgeDetail(cards = cards).toKnowledgeCardPresentations()

        assertEquals("hero.webp", result[0].imageUrl)
        assertNull(result[1].imageUrl)
        assertNull(result[2].imageUrl)
        assertEquals("gear.webp", result[3].imageUrl)
        assertNull(result[4].imageUrl)
    }

    @Test
    fun knowledgeAssetIsPrimaryButNeverReusedForAnotherRole() {
        val detail = knowledgeDetail(
            cards = listOf(card("grass", "IDENTIFICATION", 0, FishKnowledgeCardContent(), imageUrl = "id-fallback.webp")),
            knowledgeAssets = mapOf(
                "HERO" to FishKnowledgeAsset("HERO", "shared.webp", version = "1"),
                "IDENTIFICATION" to FishKnowledgeAsset("IDENTIFICATION", "shared.webp", version = "1"),
                "ECO" to FishKnowledgeAsset("ECO", "javascript:alert(1)"),
            ),
        )
        val result = detail.toKnowledgeCardPresentations()

        assertEquals("shared.webp", result[0].imageUrl)
        assertEquals("id-fallback.webp", result[1].imageUrl)
        assertNull(result[2].imageUrl)
        assertEquals(2, result.mapNotNull { it.imageUrl }.distinct().size)
    }

    @Test
    fun primaryAssetWinsOnlyWhenActiveAndCacheIdentityTracksVersion() {
        val activeCard = card("grass", "HERO", 0, FishKnowledgeCardContent(), imageUrl = "card-fallback.webp")
        val primary = knowledgeDetail(
            cards = listOf(activeCard),
            knowledgeAssets = mapOf("HERO" to FishKnowledgeAsset("HERO", "hero.webp", version = "v2", status = "ACTIVE")),
        ).toKnowledgeCardPresentations().first()
        val draftPrimary = knowledgeDetail(
            cards = listOf(activeCard),
            knowledgeAssets = mapOf("HERO" to FishKnowledgeAsset("HERO", "draft.webp", version = "v3", status = "DRAFT")),
        ).toKnowledgeCardPresentations().first()
        val wrongSpeciesPrimary = knowledgeDetail(
            cards = listOf(activeCard),
            knowledgeAssets = mapOf("HERO" to FishKnowledgeAsset("HERO", "wrong.webp", speciesId = "tilapia")),
        ).toKnowledgeCardPresentations().first()
        val versionOne = knowledgeDetail(
            knowledgeAssets = mapOf("HERO" to FishKnowledgeAsset("HERO", "same.webp", version = "v1")),
        ).toKnowledgeCardPresentations().first()
        val versionTwo = knowledgeDetail(
            knowledgeAssets = mapOf("HERO" to FishKnowledgeAsset("HERO", "same.webp", version = "v2")),
        ).toKnowledgeCardPresentations().first()

        assertEquals("hero.webp", primary.imageUrl)
        assertEquals("card-fallback.webp", draftPrimary.imageUrl)
        assertEquals("card-fallback.webp", wrongSpeciesPrimary.imageUrl)
        assertTrue(versionOne.imageCacheIdentity != versionTwo.imageCacheIdentity)
    }

    @Test
    fun cardApiOrderDoesNotChangeRoleMappingAndCoverNeverBecomesHero() {
        val cards = listOf(
            card("grass", "SKILL", 4, FishKnowledgeCardContent(), imageUrl = "skill.webp"),
            card("grass", "HERO", 0, FishKnowledgeCardContent(), imageUrl = "hero-card.webp"),
            card("grass", "ECOLOGY", 2, FishKnowledgeCardContent(), imageUrl = "eco.webp"),
        )
        val shuffled = knowledgeDetail(cards = cards.reversed())
        val ordered = knowledgeDetail(cards = cards)

        assertEquals(
            ordered.toKnowledgeCardPresentations().map { it.type to it.imageUrl },
            shuffled.toKnowledgeCardPresentations().map { it.type to it.imageUrl },
        )
        val coverOnly = knowledgeDetail(cards = emptyList(), cover = "cover-must-not-be-hero.webp")
            .toKnowledgeCardPresentations()
        assertTrue(coverOnly.all { it.imageUrl == null })
    }

    private fun knowledgeDetail(
        cards: List<FishKnowledgeCard> = emptyList(),
        speciesId: String = "grass",
        knowledgeAssets: Map<String, FishKnowledgeAsset> = emptyMap(),
        summary: String = "鱼种摘要",
        cover: String? = "fish.png",
        category: String = "淡水鱼",
        profile: FishKnowledgeProfile = FishKnowledgeProfile("细长", emptyList(), emptyList(), null, emptyList()),
    ) = FishKnowledgeDetail(
        species = FishKnowledgeSpecies(
            id = speciesId,
            nameCn = "草鱼",
            aliases = emptyList(),
            scientificName = null,
            category = category,
            family = null,
            genus = null,
            summary = summary,
            status = "ACTIVE",
            coverImage = cover,
        ),
        cover = null,
        cards = cards,
        gallery = emptyList(),
        profile = profile,
        fishing = FishKnowledgeFishing(null, emptyList(), emptyList(), emptyList(), ""),
        videos = emptyList(),
        similarity = emptyList(),
        knowledge = FishKnowledgeStructured(
            ecology = FishKnowledgeEcology(),
            gear = FishKnowledgeGear(),
            skill = FishKnowledgeSkill(),
        ),
        knowledgeAssets = knowledgeAssets,
    )

    private fun card(
        speciesId: String,
        type: String,
        order: Int,
        content: FishKnowledgeCardContent,
        imageUrl: String = "",
        status: String = "ACTIVE",
    ) = FishKnowledgeCard(
        id = order + 1,
        speciesId = speciesId,
        cardType = type,
        title = type,
        imageUrl = imageUrl,
        description = "",
        sortOrder = order,
        status = status,
        content = content,
    )

    private fun catch(id: String, speciesId: String, speciesName: String, capturedAt: String, createdAt: String) = RemoteCatch(
        id = id,
        imageUrl = "",
        speciesId = speciesId,
        speciesName = speciesName,
        confidence = 0.9f,
        modelVersion = "test",
        capturedAt = capturedAt,
        createdAt = createdAt,
    )
}
