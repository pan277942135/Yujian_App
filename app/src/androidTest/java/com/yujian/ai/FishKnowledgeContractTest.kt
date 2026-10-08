package com.yujian.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.knowledge.FishKnowledgeRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertTrue(detail.knowledgeAssets.isEmpty())
    }

    @Test
    fun cms_v13_active_asset_fixture_parses_role_version_and_legacy_cards() {
        val fixture = InstrumentationRegistry.getInstrumentation().context.assets
            .open("fixtures/fish_knowledge_detail_v13_active_assets.json")
            .bufferedReader()
            .use { it.readText() }
        val repository = FishKnowledgeRepository("https://api.example")
        val detail = repository.parseDetailJson(fixture)

        assertEquals("/api/v1/fish/knowledge-media/grass_carp/hero/v2.webp", detail.knowledgeAssets["HERO"]?.imageUrl)
        assertEquals("grass-hero-2", detail.knowledgeAssets["HERO"]?.resourceId)
        assertEquals("v2", detail.knowledgeAssets["HERO"]?.version)
        assertEquals("ACTIVE", detail.knowledgeAssets["HERO"]?.status)
        assertEquals(5, detail.knowledgeAssets.size)
        assertEquals(listOf("DRAFT", "ACTIVE"), detail.cards.map { it.status })
        assertEquals(
            "https://api.example/api/v1/fish/knowledge-media/grass_carp/hero/v2.webp",
            repository.resolveAssetUrl(detail.knowledgeAssets.getValue("HERO").imageUrl),
        )
    }

    @Test
    fun relative_managed_media_is_resolved_against_configured_api() {
        val repository = FishKnowledgeRepository("https://api.example/")
        assertEquals("https://api.example/api/v1/fish/gallery/9/media", repository.resolveAssetUrl("/api/v1/fish/gallery/9/media"))
        assertEquals("https://cdn.example/image.png", repository.resolveAssetUrl("https://cdn.example/image.png"))
        assertTrue(repository.isConfigured())
    }
}
