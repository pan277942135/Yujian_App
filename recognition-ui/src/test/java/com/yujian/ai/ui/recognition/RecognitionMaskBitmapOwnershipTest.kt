package com.yujian.ai.ui.recognition

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionMaskBitmapOwnershipTest {
    @Test
    fun publishedMaskIsNotRecycledWhileComposeCanRetainItsImageBitmap() {
        val screen = sourceFile(
            "app/src/main/java/com/yujian/ai/ui/screens/RecognizingScreen.kt",
            "src/main/java/com/yujian/ai/ui/screens/RecognizingScreen.kt",
        ).readText()
        val fishFocus = sourceFile(
            "app/src/main/java/com/yujian/ai/ui/recognition/RecognitionFishFocus.kt",
            "src/main/java/com/yujian/ai/ui/recognition/RecognitionFishFocus.kt",
        ).readText()

        assertFalse(
            "A DisposableEffect keyed by the published Mask can recycle it while a draw is in flight",
            Regex("DisposableEffect\\s*\\(\\s*subjectBitmap\\s*\\)").containsMatchIn(screen),
        )
        assertTrue(screen.contains("subjectBitmap = loaded"))
        assertTrue(screen.contains("maskBitmapPublishedToCompose = true"))
        assertTrue(screen.contains("if (!maskBitmapPublishedToCompose) loaded.recycle()"))

        // RecognitionFishFocus keeps the Bitmap-backed ImageBitmap as a borrowed
        // drawing reference and must never own disposal of the native pixels.
        assertTrue(fishFocus.contains("remember(subjectBitmap) { subjectBitmap.asImageBitmap() }"))
        assertTrue(fishFocus.contains("drawImage("))
        assertFalse("The renderer must not recycle a Bitmap it is drawing", fishFocus.contains(".recycle()"))

        // A Mask is published only after the result geometry is checked against
        // this attempt and photo, preventing an old asynchronous Mask replacing it.
        assertTrue(screen.contains("metadata?.matches("))
        assertTrue(screen.contains("selected.imageId, selected.photoSha256"))
        assertTrue(screen.contains("recognitionAttemptId.orEmpty()"))
    }

    private fun sourceFile(vararg relativePaths: String): File {
        var directory: File? = File(".").canonicalFile
        while (directory != null) {
            relativePaths.forEach { relativePath ->
                val candidate = File(directory, relativePath)
                if (candidate.isFile) return candidate
            }
            directory = directory.parentFile
        }
        error("Could not locate source file: ${relativePaths.joinToString()}")
    }
}
