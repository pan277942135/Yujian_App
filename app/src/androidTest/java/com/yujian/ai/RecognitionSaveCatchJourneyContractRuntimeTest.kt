package com.yujian.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.catches.BsideStatus
import com.yujian.ai.catches.GuestCatchRepository
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.model.RecognitionCandidate
import com.yujian.ai.model.RecognitionPrediction
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.recorddetail.FishRecordDetailPresentation
import com.yujian.ai.ui.recorddetail.FishRecordDetailRoute
import com.yujian.ai.ui.recorddetail.FishRecordDetailScreen
import com.yujian.ai.ui.recorddetail.FishRecordDetailUiState
import com.yujian.ai.ui.screens.HomeScreen
import com.yujian.ai.ui.screens.RecognitionResultScreen
import com.yujian.ai.ui.screens.RecognitionSaveDestination
import com.yujian.ai.ui.theme.YujianTheme
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Controlled instrumentation journey across production Result, Home and
 * FishRecordDetail composables with the real guest archive. Camera/inference
 * and B-side generation are not exercised; the ready B-side is an explicit
 * local fixture so the same persisted record can traverse A and B.
 */
class RecognitionSaveCatchJourneyContractRuntimeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context by lazy { InstrumentationRegistry.getInstrumentation().targetContext }

    @Test
    fun savePersistsBeforeHomeRecentCatchOpensSameRecordABAndBackReturnsHome() {
        val clientRecordId = AtomicReference<String?>(null)
        val savedImagePath = AtomicReference<String?>(null)
        val savedRecordId = AtomicReference<String?>(null)
        val detailRecordId = AtomicReference<String?>(null)
        val selectedRecentId = AtomicReference<String?>(null)
        val currentRoute = AtomicReference<String>("")
        val preferences = context.getSharedPreferences("yujian_guest_archive", Context.MODE_PRIVATE)
        val repository = GuestCatchRepository(context)
        val source = jpegFixture("m1-source-${UUID.randomUUID()}.jpg", 0xFF719A87.toInt())
        val memory = pngFixture("m1-memory-${UUID.randomUUID()}.png", 0xFF628B9A.toInt())
        val image = SelectedImage(
            source.absolutePath,
            requireNotNull(BitmapFactory.decodeFile(source.absolutePath)),
            "m1-qa-fixture",
        )
        val prediction = RecognitionPrediction(
            modelVersion = "m1-qa-controlled",
            modelSha256 = "controlled-test-only",
            top1 = RecognitionCandidate(0, "grass_carp", "草鱼", 0.91f),
            candidates = listOf(RecognitionCandidate(0, "grass_carp", "草鱼", 0.91f)),
            latencyMs = 1L,
        )

        try {
            composeRule.setContent {
                YujianTheme {
                    val nav = rememberNavController()
                    val scope = rememberCoroutineScope()
                    var catches by remember { mutableStateOf(emptyList<RemoteCatch>()) }
                    LaunchedEffect(nav) {
                        nav.currentBackStackEntryFlow.collect { entry ->
                            currentRoute.set(entry.destination.route.orEmpty())
                        }
                    }

                    NavHost(navController = nav, startDestination = "result") {
                        composable("result") {
                            RecognitionResultScreen(
                                image = image,
                                prediction = prediction,
                                onBack = { nav.popBackStack() },
                                onRetry = { nav.popBackStack() },
                                onSave = { draft, _, destination ->
                                    assertEquals(RecognitionSaveDestination.HOME, destination)
                                    clientRecordId.set(draft.clientRecordId)
                                    scope.launch {
                                        val saved = repository.saveCatch(source, draft)
                                        savedImagePath.set(saved.imageUrl)
                                        savedRecordId.set(saved.id)
                                        check(File(saved.imageUrl).isFile) { "Save must persist the catch photo before Home" }
                                        installReadyBsideFixture(
                                            preferences = preferences,
                                            catchId = saved.id,
                                            memoryUri = Uri.fromFile(memory).toString(),
                                        )
                                        val persisted = repository.listCatches().single { it.id == saved.id }
                                        check(persisted.clientRecordId == draft.clientRecordId)
                                        check(persisted.speciesId == draft.speciesId)
                                        check(persisted.speciesName == draft.speciesName)
                                        check(persisted.bsideStatus == BsideStatus.READY)
                                        check(persisted.imageUrl == saved.imageUrl)
                                        catches = listOf(persisted)
                                        nav.navigate("home") {
                                            popUpTo("result") { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                },
                            )
                        }
                        composable("home") {
                            HomeScreen(
                                nickname = "访客",
                                statistics = repository.statistics(catches),
                                recentCatches = catches,
                                resolveImageUrl = ::localImageUrl,
                                accessToken = "",
                                isLoggedIn = false,
                                avatarUrl = null,
                                showEmptyState = catches.isEmpty(),
                                onIdentify = {},
                                onAlbumClick = {},
                                onLoginClick = {},
                                onSpeciesClick = {},
                                onCatchesClick = {},
                                onProfileClick = {},
                                onCatchClick = { id ->
                                    selectedRecentId.set(id)
                                    nav.navigate("catch/${Uri.encode(id)}")
                                },
                            )
                        }
                        composable(
                            route = FishRecordDetailRoute,
                            arguments = listOf(
                                navArgument("catchId") { type = NavType.StringType },
                                navArgument("section") { type = NavType.StringType; defaultValue = "" },
                            ),
                        ) { entry ->
                            val catchId = entry.arguments?.getString("catchId").orEmpty()
                            val state = FishRecordDetailPresentation.resolve(
                                catchId = catchId,
                                records = catches,
                                loading = false,
                                error = null,
                            )
                            val record = (state as? FishRecordDetailUiState.Success)?.record
                            DisposableEffect(record?.id) {
                                detailRecordId.set(record?.id)
                                onDispose { }
                            }
                            FishRecordDetailScreen(
                                uiState = state,
                                imageUrlFor = { localImageUrl(it.imageUrl) },
                                bsideUrlFor = { it.bsideUri },
                                accessToken = "",
                                onBack = { nav.popBackStack() },
                                onRetry = {},
                                onOpenFishGuide = {},
                                onShare = {},
                                onEditRecord = {},
                                onAddMedia = {},
                                onContinuePhoto = {},
                                onRecordVideo = {},
                                onGenerateMemory = null,
                                onRefreshBsideStatus = { true },
                            )
                        }
                    }
                }
            }

            composeRule.onNodeWithText("保存本次鱼获").performScrollTo().performClick()
            composeRule.waitUntil(10_000L) {
                val id = savedRecordId.get()
                id != null && composeRule.onAllNodesWithTag("normal-home-catch-card-$id")
                    .fetchSemanticsNodes().isNotEmpty()
            }

            val id = requireNotNull(savedRecordId.get())
            composeRule.onNodeWithTag("normal-home-catch-card-$id").assertIsDisplayed().performClick()
            composeRule.waitUntil(10_000L) {
                currentRoute.get().startsWith("catch/") && detailRecordId.get() == id
            }
            composeRule.onNodeWithText("鱼获详情").assertIsDisplayed()
            assertEquals("The recent-catch route must preserve the saved stable ID", id, selectedRecentId.get())
            assertEquals("Detail must resolve the same persisted record", id, detailRecordId.get())

            composeRule.waitUntil(10_000L) {
                composeRule.onAllNodesWithContentDescription("草鱼 鱼获记忆")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithContentDescription("切回鱼获照片").performClick()
            composeRule.onNodeWithContentDescription("草鱼 鱼获照片").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("切换鱼获记忆").performClick()
            composeRule.onNodeWithContentDescription("草鱼 鱼获记忆").assertIsDisplayed()

            // Verify the frozen top Back returns to Home after the A/B round trip.
            composeRule.onNodeWithContentDescription("返回").performClick()
            composeRule.waitUntil(10_000L) { currentRoute.get() == "home" }
            composeRule.onNodeWithText("最近鱼获").assertIsDisplayed()
            composeRule.onNodeWithTag("normal-home-catch-card-$id").assertIsDisplayed()

            // Re-enter the same record and exercise Android System Back as well.
            composeRule.onNodeWithTag("normal-home-catch-card-$id").performClick()
            composeRule.waitUntil(10_000L) {
                currentRoute.get().startsWith("catch/") && detailRecordId.get() == id
            }
            composeRule.onNodeWithContentDescription("草鱼 鱼获照片").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("切换鱼获记忆").assertIsDisplayed()
            composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
            composeRule.waitUntil(10_000L) { currentRoute.get() == "home" }
            composeRule.onNodeWithText("最近鱼获").assertIsDisplayed()
            composeRule.onNodeWithTag("normal-home-catch-card-$id").assertIsDisplayed()
            composeRule.onNodeWithText("鱼获详情").assertDoesNotExist()
            assertEquals("Saved catch journey must finish on Home", "home", currentRoute.get())
        } finally {
            image.bitmap.recycle()
            source.delete()
            memory.delete()
            removeGuestRecord(preferences, clientRecordId.get())
            savedImagePath.get()?.let { File(it).delete() }
            savedRecordId.get()?.let { id ->
                context.getSharedPreferences("fish_record_detail_v1", Context.MODE_PRIVATE)
                    .edit().remove("first_b_reveal_done:$id").commit()
            }
        }
    }

    private fun jpegFixture(name: String, color: Int): File = imageFixture(name, color, Bitmap.CompressFormat.JPEG)

    private fun pngFixture(name: String, color: Int): File = imageFixture(name, color, Bitmap.CompressFormat.PNG)

    private fun imageFixture(name: String, color: Int, format: Bitmap.CompressFormat): File {
        val bitmap = Bitmap.createBitmap(128, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return File(context.cacheDir, name).apply {
            outputStream().use { stream -> check(bitmap.compress(format, 96, stream)) }
            bitmap.recycle()
        }
    }

    private fun localImageUrl(value: String?): String? = value?.let { path ->
        val file = File(path)
        if (file.isFile) Uri.fromFile(file).toString() else path
    }

    private fun installReadyBsideFixture(
        preferences: android.content.SharedPreferences,
        catchId: String,
        memoryUri: String,
    ) {
        val original = runCatching { JSONArray(preferences.getString("records", "[]") ?: "[]") }
            .getOrDefault(JSONArray())
        val updated = JSONArray()
        var matches = 0
        for (index in 0 until original.length()) {
            val row = original.optJSONObject(index) ?: continue
            if (row.optString("id") == catchId) {
                row.put("bside_status", BsideStatus.READY.name)
                row.put("bside_uri", memoryUri)
                matches += 1
            }
            updated.put(row)
        }
        check(matches == 1) { "Expected exactly one just-saved guest catch to receive the ready B-side fixture" }
        check(preferences.edit().putString("records", updated.toString()).commit())
    }

    private fun removeGuestRecord(
        preferences: android.content.SharedPreferences,
        clientRecordId: String?,
    ) {
        if (clientRecordId.isNullOrBlank()) return
        val original = runCatching { JSONArray(preferences.getString("records", "[]") ?: "[]") }
            .getOrDefault(JSONArray())
        val kept = JSONArray()
        for (index in 0 until original.length()) {
            val row = original.optJSONObject(index) ?: continue
            if (row.optString("client_record_id") != clientRecordId) kept.put(row)
        }
        preferences.edit().putString("records", kept.toString()).commit()
    }
}
