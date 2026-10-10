package com.yujian.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.knowledge.FishKnowledgeRepository
import com.yujian.ai.ui.fishguide.toKnowledgeCardPresentations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FishKnowledgeContractTest {
    @Test
    fun species_list_parser_keeps_aliases_and_pinyin_search_fields() {
        val item = FishKnowledgeRepository("https://api.example").parseSpeciesJson(
            """[{"id":"grass_carp","name_cn":"草鱼","alias":["鲩鱼","草鲩"],"pinyin":"cao yu","pinyin_initials":"cy"}]""",
        ).single()

        assertEquals(listOf("鲩鱼", "草鲩"), item.aliases)
        assertEquals("cao yu", item.pinyin)
        assertEquals("cy", item.pinyinInitials)
    }

    @Test
    fun species_list_parser_excludes_non_active_catalog_entries() {
        val items = FishKnowledgeRepository("https://api.example").parseSpeciesJson(
            """[
              {"id":"active","name_cn":"白条","status":"active"},
              {"id":"draft","name_cn":"草稿鱼","status":"DRAFT"},
              {"id":"retired","name_cn":"停用鱼","status":"INACTIVE"}
            ]""",
        )

        assertEquals(listOf("active"), items.map { it.id })
    }

    @Test
    fun detail_contract_parses_full_asset_package_and_sorts_cards() {
        val repository = FishKnowledgeRepository("https://api.example")
        val detail = repository.parseDetailJson(
            """
            {
              "species": {
                "id": "sharpbelly",
                "name_cn": "白条",
                "alias": ["餐条"],
                "scientific_name": "Hemiculter leucisculus",
                "category": "淡水鱼",
                "family": "鲤科",
                "summary": "常见中上层小型鱼",
                "status": "ACTIVE",
                "cover_image": "https://cdn.example/baitiao-cover.png"
              },
              "cover": {"image_url":"https://cdn.example/baitiao-cover.png","style":"ANIME_CARD","title":"白条图鉴卡","status":"ACTIVE"},
              "cards": [
                {"id":2,"species_id":"sharpbelly","type":"IDENTIFICATION","title":"识别卡","image_url":"https://cdn.example/id.png","description":"","content":{"type":"IDENTIFICATION","features":[{"title":"体色","text":"银白"}],"similar":[{"name":"翘嘴","difference":"更大"}]},"sort_order":1,"status":"ACTIVE"},
                {"id":1,"species_id":"sharpbelly","card_type":"HERO","title":"英雄卡","image_url":"https://cdn.example/hero.png","description":"","content":{"type":"HERO","tag":"中上层快鱼","rarity":2,"power":3,"challenge":1,"description":"小而灵活"},"sort_order":0,"status":"ACTIVE"}
              ],
              "gallery": {"species_id":"sharpbelly","images":[{"id":4,"type":"side","url":"https://cdn.example/side.jpg","title":"侧身","order":0}]},
              "profile": {"body_shape":"细长侧扁","features":["背部青灰"],"habitat":["江河"],"food":"杂食","season":["春夏"]},
              "fishing": {"water_layer":"中上层","season":["春夏"],"bait":["蚯蚓"],"method":["浮钓"],"summary":"从鱼层开始找口"},
              "videos": [{"id":8,"title":"白条怎么钓","type":"HOW_TO_FISH","cover_url":null,"video_url":"https://video.example/1","duration":30,"tags":["入门"]}],
              "similarity": [{"species_id":"sharpbelly","similar_species_id":"crucian_carp","similar_species_name_cn":"鲫鱼","difference":"体型不同"}],
              "knowledge": {"display_tag":"中上层快鱼","ecology":{"water_layer":"中上层"}},
              "dynamic": {}
            }
            """.trimIndent(),
        )

        assertEquals("sharpbelly", detail.species.id)
        assertEquals("Hemiculter leucisculus", detail.species.scientificName)
        assertEquals(listOf("HERO", "IDENTIFICATION"), detail.cards.map { it.cardType })
        assertEquals("中上层快鱼", detail.cards.first().content.tag)
        assertEquals(2, detail.cards.first().content.rarity)
        assertEquals("中上层快鱼", detail.knowledge.displayTag)
        assertEquals("中上层", detail.fishing.waterLayer)
        assertEquals(1, detail.gallery.size)
        assertEquals("HOW_TO_FISH", detail.videos.single().type)
        assertFalse(detail.dynamicAvailable)
    }

    @Test
    fun relative_managed_media_is_resolved_against_configured_api() {
        val repository = FishKnowledgeRepository("https://api.example/")
        assertEquals("https://api.example/api/v1/fish/gallery/9/media", repository.resolveAssetUrl("/api/v1/fish/gallery/9/media"))
        assertEquals("https://cdn.example/image.png", repository.resolveAssetUrl("https://cdn.example/image.png"))
        assertNull(repository.resolveAssetUrl("//cdn.example/image.png"))
        assertNull(repository.resolveAssetUrl("javascript:alert(1)"))
        assertTrue(repository.isConfigured())
    }

    @Test
    fun cms_v13_fixture_parses_public_five_role_assets_and_keeps_legacy_cards_separate() {
        val detail = repository().parseDetailJson(fixture("fish_knowledge_detail_v13_active_assets.json"))

        assertTrue(detail.knowledgeAssetsContractPresent)
        assertEquals(listOf("HERO", "IDENTIFICATION", "ECO", "GEAR", "SKILL"), detail.knowledgeAssets.keys.sortedBy {
            listOf("HERO", "IDENTIFICATION", "ECO", "GEAR", "SKILL").indexOf(it)
        })
        assertTrue(detail.knowledgeAssets.values.all { it.status == "ACTIVE" && it.speciesId == "grass_carp" })
        assertEquals("v2", detail.knowledgeAssets.getValue("HERO").version)
        assertEquals(listOf("DRAFT", "ACTIVE"), detail.cards.map { it.status })
    }

    @Test
    fun cms_v14_fixture_parses_active_hero_and_versioned_asset_identity() {
        val detail = repository().parseDetailJson(fixture("fish_knowledge_detail_v14_versioned_active.json"))

        assertEquals("/api/v1/fish/knowledge-media/grass_carp/cover_hero/43.webp", detail.coverHeroImage)
        assertEquals(4301, detail.coverHeroVersionId)
        assertEquals("ACTIVE", detail.coverHeroStatus)
        assertEquals("9101", detail.knowledgeAssets.getValue("HERO").versionId)
        assertEquals("ca-hero-91", detail.knowledgeAssets.getValue("HERO").resourceId)
        assertEquals("9101", detail.cards.single().versionId)
        assertTrue(detail.knowledgeAssets.values.all { it.status == "ACTIVE" && it.speciesId == "grass_carp" })
        val presentations = detail.toKnowledgeCardPresentations(repository()::resolveAssetUrl)
        assertEquals(5, presentations.size)
        assertTrue(presentations.all { it.available && it.imageStatus == "ACTIVE" })
        assertEquals("https://api.example/media/grass/hero-91.webp", presentations.first().imageUrl)
    }

    @Test
    fun legacy_api_fixture_does_not_promote_cover_image_or_draft_card() {
        val detail = repository().parseDetailJson(fixture("fish_knowledge_detail_legacy_api.json"))

        assertFalse(detail.knowledgeAssetsContractPresent)
        assertEquals("https://cdn.example/legacy-only.webp", detail.species.coverImage)
        assertEquals("LEGACY_API", detail.coverHeroStatus)
        assertNull(detail.coverHeroImage)
        assertEquals(listOf("ACTIVE", "DRAFT"), detail.cards.map { it.status })
    }

    @Test
    fun asset_edge_fixture_keeps_published_state_and_rejects_unrecognized_roles() {
        val detail = repository().parseDetailJson(fixture("fish_knowledge_detail_asset_edge_cases.json"))

        assertTrue(detail.knowledgeAssetsContractPresent)
        assertEquals("DRAFT", detail.knowledgeAssets.getValue("HERO").status)
        assertEquals("ARCHIVED", detail.knowledgeAssets.getValue("IDENTIFICATION").status)
        assertFalse(detail.knowledgeAssets.containsKey("ECO"))
        assertEquals("another-fish", detail.knowledgeAssets.getValue("GEAR").speciesId)
        assertNull(detail.knowledgeAssets.getValue("SKILL").versionId)
        assertEquals("javascript:alert(1)", detail.knowledgeAssets.getValue("SKILL").imageUrl)
        assertFalse(detail.knowledgeAssets.containsKey("UNEXPECTED"))
    }

    @Test
    fun species_list_fixture_preserves_cover_hero_states_without_legacy_promotion() {
        val species = repository().parseSpeciesJson(fixture("fish_species_cover_hero_states.json"))

        assertEquals(listOf("ACTIVE", "DRAFT", "ARCHIVED", "ACTIVE", "LEGACY_API"), species.map { it.coverHeroStatus })
        assertNull(species.last().coverHeroImage)
        assertEquals("https://cdn.example/legacy-api.webp", species.last().coverImage)
        assertEquals(10, species.first().coverHeroVersionId)
        assertNull(species[3].coverHeroImage)
    }

    private fun repository() = FishKnowledgeRepository("https://api.example")

    private fun fixture(name: String): String = InstrumentationRegistry.getInstrumentation().context.assets
        .open("fixtures/$name")
        .bufferedReader()
        .use { it.readText() }
}
