package com.yujian.ai.ui.recognition.reveal

import com.yujian.ai.knowledge.FishGuideItem

/** Scientific names are shown only from the exact selected species ID in the catalog. */
fun resolveRecognitionScientificName(speciesId: String, species: List<FishGuideItem>): String? =
    species.firstOrNull { it.id == speciesId }
        ?.scientificName
        ?.trim()
        ?.takeIf(String::isNotBlank)
