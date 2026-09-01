package com.yujian.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yujian.ai.knowledge.FishKnowledgeRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FishKnowledgeContractTest {
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
                {"id":2,"species_id":"sharpbelly","type":"IDENTIFICATION","title":"识别卡","image_url":"https://cdn.example/id.png","description":"","sort_order":1,"status":"ACTIVE"},
                {"id":1,"species_id":"sharpbelly","card_type":"HERO","title":"英雄卡","image_url":"https://cdn.example/hero.png","description":"","sort_order":0,"status":"ACTIVE"}
              ],
              "gallery": {"species_id":"sharpbelly","images":[{"id":4,"type":"side","url":"https://cdn.example/side.jpg","title":"侧身","order":0}]},
              "profile": {"body_shape":"细长侧扁","features":["背部青灰"],"habitat":["江河"],"food":"杂食","season":["春夏"]},
              "fishing": {"water_layer":"中上层","season":["春夏"],"bait":["蚯蚓"],"method":["浮钓"],"summary":"从鱼层开始找口"},
              "videos": [{"id":8,"title":"白条怎么钓","type":"HOW_TO_FISH","cover_url":null,"video_url":"https://video.example/1","duration":30,"tags":["入门"]}],
              "similarity": [{"species_id":"sharpbelly","similar_species_id":"crucian_carp","similar_species_name_cn":"鲫鱼","difference":"体型不同"}],
              "dynamic": {}
            }
            """.trimIndent(),
        )

        assertEquals("sharpbelly", detail.species.id)
        assertEquals("Hemiculter leucisculus", detail.species.scientificName)
        assertEquals(listOf("HERO", "IDENTIFICATION"), detail.cards.map { it.cardType })
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
        assertTrue(repository.isConfigured())
    }
}
