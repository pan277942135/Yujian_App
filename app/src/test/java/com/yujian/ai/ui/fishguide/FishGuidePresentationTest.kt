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
    fun legacyCoverImageIsNeverPromotedAndLitStateRemainsRecordDriven() {
        val source = species.first().copy(
            coverImage = "https://cdn.example/legacy-cover.webp",
            coverHeroStatus = "LEGACY_API",
        )
        val item = listOf(source).toFishGuidePresentation { value -> "resolved:$value" }.first()
        assertEquals("grass", item.id)
        assertEquals("草鱼", item.name)
        assertNull(item.imageUrl)
        assertEquals("LEGACY_API", item.imageStatus)
        assertTrue(item.discovered)
        assertEquals(3, item.catches)
    }

    @Test
    fun homeUsesOnlyActiveVersionedCoverHeroAndLITAndUNLITShareItsImage() {
        val active = FishGuideItem(
            id = "grass",
            nameCn = "草鱼",
            coverImage = "https://cdn.example/legacy.webp",
            coverHeroImage = "/api/v1/fish/knowledge-media/grass/cover_hero/v3.webp",
            coverHeroVersionId = 303,
            coverHeroStatus = "ACTIVE",
            discovered = true,
            catches = 1,
        )
        val resolve: (String?) -> String? = { value -> value?.let { "https://api.example$it" } }
        val lit = listOf(active).toFishGuidePresentation(resolve).single()
        val unlit = listOf(active.copy(discovered = false, catches = 0)).toFishGuidePresentation(resolve).single()

        assertEquals("https://api.example/api/v1/fish/knowledge-media/grass/cover_hero/v3.webp", lit.imageUrl)
        assertEquals(lit.imageUrl, unlit.imageUrl)
        assertEquals(303, lit.imageVersionId)
        assertEquals("ACTIVE", lit.imageStatus)
        assertTrue(lit.discovered)
        assertFalse(unlit.discovered)
        assertTrue(lit.imageCacheIdentity.orEmpty().contains("303"))
    }

    @Test
    fun draftArchivedMissingVersionAndBadUrlCoverHeroesStayUnavailable() {
        val cases = listOf(
            FishGuideItem("draft", "草稿鱼", coverHeroImage = "/draft.webp", coverHeroVersionId = 2, coverHeroStatus = "DRAFT"),
            FishGuideItem("archived", "归档鱼", coverHeroImage = "/archived.webp", coverHeroVersionId = 3, coverHeroStatus = "ARCHIVED"),
            FishGuideItem("no-version", "无版本鱼", coverHeroImage = "/active.webp", coverHeroStatus = "ACTIVE"),
            FishGuideItem("no-image", "未提供图片鱼", coverHeroVersionId = 5, coverHeroStatus = "ACTIVE"),
            FishGuideItem("bad-url", "错误地址鱼", coverHeroImage = "javascript:alert(1)", coverHeroVersionId = 4, coverHeroStatus = "ACTIVE"),
        ).toFishGuidePresentation { value -> value?.let { "https://api.example$it" } }

        assertTrue(cases.all { it.imageUrl == null })
        assertEquals(listOf("DRAFT", "ARCHIVED", "MISSING_VERSION", "MISSING_URL", "INVALID_URL"), cases.map { it.imageStatus })
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
            knowledgeAssetsContractPresent = true,
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
    fun publishedAssetsMapInFixedRoleOrderWithResolverAndVersionedCacheIdentity() {
        val roles = listOf("HERO", "IDENTIFICATION", "ECO", "GEAR", "SKILL")
        val assets = roles.associateWith { role ->
            FishKnowledgeAsset(
                role = role,
                imageUrl = "/grass/$role/v7.webp",
                version = "v7",
                versionId = "700",
                resourceId = "grass-$role-700",
                status = "ACTIVE",
                speciesId = "grass",
            )
        }
        val projected = knowledgeDetail(knowledgeAssets = assets, knowledgeAssetsContractPresent = true)
            .toKnowledgeCardPresentations { value -> value?.let { "https://cdn.example$it" } }

        assertEquals(roles, projected.map { it.type })
        assertEquals(roles.map { "https://cdn.example/grass/$it/v7.webp" }, projected.map { it.imageUrl })
        assertTrue(projected.all { it.imageSource == "VERSIONED_KNOWLEDGE_ASSET" })
        assertTrue(projected.all { it.imageStatus == "ACTIVE" && it.available })
        assertTrue(projected.zip(roles).all { (card, role) ->
            card.imageCacheIdentity.orEmpty().contains("grass|$role|VERSIONED_KNOWLEDGE_ASSET|grass-$role-700|700|v7|")
        })
    }

    @Test
    fun invalidOrUnpublishedVersionedRolesNeverFallBackToLegacyCards() {
        val roles = listOf("HERO", "IDENTIFICATION", "ECO", "GEAR", "SKILL")
        val assets = mapOf(
            "HERO" to FishKnowledgeAsset("HERO", "/draft.webp", "v1", "1", "h1", "DRAFT", "grass"),
            "IDENTIFICATION" to FishKnowledgeAsset("IDENTIFICATION", "/archived.webp", "v1", "2", "i1", "ARCHIVED", "grass"),
            "ECO" to FishKnowledgeAsset("GEAR", "/wrong-role.webp", "v1", "3", "e1", "ACTIVE", "grass"),
            "GEAR" to FishKnowledgeAsset("GEAR", "/wrong-species.webp", "v1", "4", "g1", "ACTIVE", "another"),
            "SKILL" to FishKnowledgeAsset("SKILL", "/no-version.webp", null, null, "s1", "ACTIVE", "grass"),
        )
        val legacyCards = roles.mapIndexed { index, role ->
            card("grass", role, index, FishKnowledgeCardContent()).copy(imageUrl = "/legacy-$role.webp")
        }
        val projected = knowledgeDetail(
            cards = legacyCards,
            knowledgeAssets = assets,
            knowledgeAssetsContractPresent = true,
        ).toKnowledgeCardPresentations { value -> value?.let { "https://cdn.example$it" } }

        assertEquals(roles, projected.map { it.type })
        assertTrue(projected.none { it.imageUrl != null })
        assertEquals(
            listOf("DRAFT", "ARCHIVED", "ROLE_MISMATCH", "SPECIES_MISMATCH", "MISSING_VERSION"),
            projected.map { it.imageStatus },
        )
    }

    @Test
    fun legacyCardsAreUsedOnlyWhenTheVersionedContractIsAbsentAndAreTraceable() {
        val cards = listOf(
            card("grass", "HERO", 0, FishKnowledgeCardContent()).copy(imageUrl = "/active-hero.webp"),
            card("grass", "IDENTIFICATION", 1, FishKnowledgeCardContent()).copy(status = "DRAFT", imageUrl = "/draft-id.webp"),
            card("another", "ECO", 2, FishKnowledgeCardContent()).copy(imageUrl = "/other-fish.webp"),
            card("grass", "GEAR", 3, FishKnowledgeCardContent()).copy(imageUrl = "javascript:alert(1)"),
        )
        val projected = knowledgeDetail(cards = cards).toKnowledgeCardPresentations { value ->
            value?.takeIf { !it.startsWith("javascript:") }?.let { "https://cdn.example$it" }
        }

        assertEquals("https://cdn.example/active-hero.webp", projected[0].imageUrl)
        assertEquals("LEGACY_ACTIVE_CARD", projected[0].imageSource)
        assertEquals("ACTIVE", projected[0].imageStatus)
        assertTrue(projected.drop(1).all { it.imageUrl == null })
    }

    @Test
    fun distinctRolesCannotReuseOneImageAndCacheChangesWhenVersionChanges() {
        fun detail(version: String) = knowledgeDetail(
            knowledgeAssets = mapOf(
                "HERO" to FishKnowledgeAsset("HERO", "https://cdn.example/same.webp", version, "11", "shared", "ACTIVE", "grass"),
                "IDENTIFICATION" to FishKnowledgeAsset("IDENTIFICATION", "https://cdn.example/same.webp", version, "11", "shared", "ACTIVE", "grass"),
            ),
            knowledgeAssetsContractPresent = true,
        )
        val v1 = detail("v1").toKnowledgeCardPresentations()
        val v2 = detail("v2").toKnowledgeCardPresentations()

        assertEquals("https://cdn.example/same.webp", v1[0].imageUrl)
        assertNull(v1[1].imageUrl)
        assertTrue(v1[0].imageCacheIdentity != v2[0].imageCacheIdentity)
    }

    @Test
    fun catchStatesUseOnlyMatchingSavedRecords() {
        val empty = savedRecordsForSpecies(species.first(), emptyList())
        val one = savedRecordsForSpecies(species.first(), listOf(catch("one", "grass", "草鱼", "", "")))
        val multiple = savedRecordsForSpecies(
            species.first(),
            listOf(catch("one", "grass", "草鱼", "", ""), catch("two", "grass", "草鱼", "", ""), catch("wrong", "carp", "草鱼", "", "")),
        )

        assertEquals(0, empty.size)
        assertEquals("0次记录", "${empty.size}次记录")
        assertEquals(listOf("one"), one.map { it.id })
        assertEquals(2, multiple.size)
    }

    private fun knowledgeDetail(
        cards: List<FishKnowledgeCard> = emptyList(),
        knowledgeAssets: Map<String, FishKnowledgeAsset> = emptyMap(),
        knowledgeAssetsContractPresent: Boolean = false,
        summary: String = "鱼种摘要",
        cover: String? = "fish.png",
        category: String = "淡水鱼",
        profile: FishKnowledgeProfile = FishKnowledgeProfile("细长", emptyList(), emptyList(), null, emptyList()),
    ) = FishKnowledgeDetail(
        species = FishKnowledgeSpecies(
            id = "grass",
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
        knowledgeAssetsContractPresent = knowledgeAssetsContractPresent,
    )

    private fun card(
        speciesId: String,
        type: String,
        order: Int,
        content: FishKnowledgeCardContent,
    ) = FishKnowledgeCard(
        id = order + 1,
        speciesId = speciesId,
        cardType = type,
        title = type,
        imageUrl = "should-not-be-used.png",
        description = "",
        sortOrder = order,
        status = "ACTIVE",
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
