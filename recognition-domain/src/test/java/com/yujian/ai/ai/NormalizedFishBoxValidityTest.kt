package com.yujian.ai.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizedFishBoxValidityTest {
    @Test
    fun acceptsOnlyFiniteOrderedCoordinatesInsideNormalizedBounds() {
        assertTrue(NormalizedFishBox(.1f, .2f, .9f, .8f).isValidNormalized())
        assertFalse(NormalizedFishBox(Float.NaN, .2f, .9f, .8f).isValidNormalized())
        assertFalse(NormalizedFishBox(-.01f, .2f, .9f, .8f).isValidNormalized())
        assertFalse(NormalizedFishBox(.1f, .2f, 1.01f, .8f).isValidNormalized())
        assertFalse(NormalizedFishBox(.5f, .2f, .5f, .8f).isValidNormalized())
        assertFalse(NormalizedFishBox(.7f, .2f, .3f, .8f).isValidNormalized())
    }
}
