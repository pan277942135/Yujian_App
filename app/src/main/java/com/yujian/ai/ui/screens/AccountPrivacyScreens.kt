package com.yujian.ai.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.yujian.ai.R
import com.yujian.ai.auth.AccountProfile
import com.yujian.ai.auth.ApiException
import com.yujian.ai.auth.AuthRepository
import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.media.RecognitionImageStore
import com.yujian.ai.session.UserSession
import com.yujian.ai.ui.components.RemoteImage
import com.yujian.ai.ui.theme.CardWhite
import com.yujian.ai.ui.theme.DeepInk
import com.yujian.ai.ui.theme.Hairline
import com.yujian.ai.ui.theme.MutedInk
import com.yujian.ai.ui.theme.SoftWater
import com.yujian.ai.ui.theme.WaterTeal
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.components.YuJianBackTitleTopBar
import com.yujian.ai.ui.designsystem.components.YuJianIconAction
import com.yujian.ai.ui.designsystem.components.YuJianIconActionFamily
import com.yujian.ai.ui.designsystem.components.YuJianIconActionTone
import com.yujian.ai.ui.designsystem.components.YuJianPrimaryButton
import com.yujian.ai.ui.designsystem.components.YuJianTextAction
import com.yujian.ai.ui.designsystem.components.YuJianTextActionRole
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ComingSoonKind {
    FORGOT_PASSWORD,
    EXPORT_DATA,
    DELETE_ACCOUNT,
}

@Composable
internal fun AccountPrivacyPageScaffold(
    title: String,
    onBack: () -> Unit,
    backEnabled: Boolean = true,
    whiteContentTransition: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        BgContentSurface()
        if (whiteContentTransition) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.40f to Color.White.copy(alpha = 0.10f),
                        0.76f to Color.White.copy(alpha = 0.78f),
                        1f to Color.White,
                    ),
                ),
            )
        }
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            YuJianBackTitleTopBar(
                title = title,
                onBack = onBack,
                backEnabled = backEnabled,
                statusBarInset = true,
            )
            Box(Modifier.weight(1f).fillMaxWidth().imePadding()) {
                content()
            }
        }
    }
}

@Composable
private fun Avatar(
    profile: UserSession,
    modifier: Modifier = Modifier,
) {
    RemoteImage(
        url = profile.avatarUrl,
        authToken = profile.accessToken,
        contentDescription = "头像",
        modifier = modifier.clip(CircleShape),
        placeholder = {
            Image(
                painter = painterResource(R.drawable.profile_fallback_v13),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        },
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.93f),
        tonalElevation = 0.dp,
    ) { Column { content() } }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = {
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MutedInk)
    },
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = DeepInk, fontSize = 16.sp)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = MutedInk, fontSize = 12.sp)
        }
        trailing?.invoke()
    }
}

@Composable
fun AccountMyScreen(
    profile: UserSession,
    statistics: CatchStatistics,
    recordDays: Int,
    onEditProfile: () -> Unit,
    onAccountLogin: () -> Unit,
    onAbout: () -> Unit,
    onBack: () -> Unit,
) {
    AccountPrivacyPageScaffold(title = "我的", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp, end = 18.dp, bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Surface(shape = RoundedCornerShape(26.dp), color = CardWhite, tonalElevation = 1.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(profile, Modifier.size(72.dp))
                        Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(profile.nickname.ifBlank { profile.username }, color = DeepInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("拍照收藏每次渔获", color = MutedInk, fontSize = 13.sp)
                        }
                    }
                }
            }
            item {
                Surface(shape = RoundedCornerShape(22.dp), color = CardWhite, tonalElevation = 1.dp) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 17.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MyStat(statistics.speciesCount.toString(), "鱼种")
                        MyStat(statistics.totalCatches.toString(), "鱼获")
                        MyStat(recordDays.toString(), "记录天数")
                    }
                }
            }
            item {
                SettingsCard {
                    SettingsRow("编辑个人资料", onClick = onEditProfile)
                    HairlineDivider()
                    SettingsRow("账号与登录", onClick = onAccountLogin)
                    HairlineDivider()
                    SettingsRow("关于渔见", onClick = onAbout)
                }
            }
        }
    }
}

@Composable
private fun MyStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, color = DeepInk, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text(label, color = MutedInk, fontSize = 12.sp)
    }
}

@Composable
private fun HairlineDivider() {
    androidx.compose.foundation.layout.Spacer(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    profile: UserSession,
    authRepository: AuthRepository,
    onProfileUpdated: (AccountProfile) -> Unit,
    onAuthenticationExpired: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var nickname by remember(profile.userId, profile.nickname) { mutableStateOf(profile.nickname) }
    var nicknameTouched by remember { mutableStateOf(false) }
    var selectedAvatarUri by remember { mutableStateOf<Uri?>(null) }
    var cameraTarget by remember { mutableStateOf<RecognitionImageStore.CameraTarget?>(null) }
    var showAvatarSheet by remember { mutableStateOf(false) }
    var cropSourceUri by remember { mutableStateOf<Uri?>(null) }
    var cropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cropLoading by remember { mutableStateOf(false) }
    var cropProcessing by remember { mutableStateOf(false) }
    var cropError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    val nicknameValidation = validateNicknameDraft(nickname)
    val nicknameChanged = nicknameValidation.normalized != profile.nickname.trim()
    val saveEnabled = isProfileSaveEnabled(
        serverNickname = profile.nickname,
        draft = nicknameValidation,
        hasPendingAvatar = selectedAvatarUri != null,
        saving = saving || cropSourceUri != null,
    )

    fun saveProfile() {
        if (!saveEnabled || saving) return
        scope.launch {
            saving = true
            error = null
            saved = false
            var serverProfile: AccountProfile? = null
            try {
                if (nicknameChanged) {
                    serverProfile = authRepository.updateProfile(profile.accessToken, nicknameValidation.normalized)
                }
                val pendingAvatar = selectedAvatarUri
                if (pendingAvatar != null) {
                    val avatarProfile = try {
                        authRepository.updateAvatar(context, profile.accessToken, pendingAvatar)
                    } catch (failure: Throwable) {
                        if (serverProfile != null) onProfileUpdated(serverProfile!!)
                        if ((failure as? ApiException)?.statusCode == 401) onAuthenticationExpired()
                        error = if ((failure as? ApiException)?.statusCode == 401) {
                            "登录状态已失效，请重新登录"
                        } else {
                            "头像上传失败，请重试"
                        }
                        return@launch
                    }
                    serverProfile = avatarProfile
                    deletePendingAvatar(pendingAvatar)
                    selectedAvatarUri = null
                }
                serverProfile?.let(onProfileUpdated)
                saved = true
                scope.launch { delay(1_700); saved = false }
            } catch (failure: Throwable) {
                if ((failure as? ApiException)?.statusCode == 401) onAuthenticationExpired()
                error = if ((failure as? ApiException)?.statusCode == 401) {
                    "登录状态已失效，请重新登录"
                } else {
                    failure.userMessage("保存资料失败，请重试")
                }
            } finally {
                saving = false
            }
        }
    }

    fun releaseCropSource() {
        cropBitmap?.recycle()
        cropBitmap = null
        cameraTarget?.file?.delete()
        cropSourceUri?.let { uri ->
            if (uri.scheme == "file") runCatching { uri.path?.let { path -> File(path).delete() } }
        }
        cropSourceUri = null
        cropLoading = false
        cropProcessing = false
        cropError = null
        cameraTarget = null
    }

    fun beginCrop(uri: Uri) {
        cropBitmap?.recycle()
        cropBitmap = null
        cropError = null
        cropSourceUri = uri
        cropLoading = true
        saved = false
        error = null
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            cameraTarget?.file?.delete()
            cameraTarget = null
            beginCrop(uri)
        }
    }
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraTarget?.uri?.let(::beginCrop)
        } else {
            cameraTarget?.file?.delete()
            cameraTarget = null
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val target = RecognitionImageStore.createCameraTarget(context)
            cameraTarget = target
            takePictureLauncher.launch(target.uri)
        } else {
            error = "需要相机权限才能拍照更换头像"
        }
    }
    LaunchedEffect(cropSourceUri) {
        val uri = cropSourceUri ?: return@LaunchedEffect
        cropLoading = true
        cropError = null
        val result = runCatching { decodeAvatarCropSource(context, uri) }
        result.onSuccess { bitmap ->
            cropBitmap?.recycle()
            cropBitmap = bitmap
        }.onFailure { failure ->
            cropError = (failure as? AvatarImageException)?.message ?: "无法读取这张图片，请重新选择"
        }
        if (uri.scheme == "file") runCatching { uri.path?.let { path -> File(path).delete() } }
        if (cameraTarget?.uri == uri) {
            cameraTarget?.file?.delete()
            cameraTarget = null
        }
        cropLoading = false
    }

    BackHandler(enabled = cropSourceUri != null) {
        if (!cropProcessing) releaseCropSource()
    }

    AccountPrivacyPageScaffold(
        title = "编辑资料",
        onBack = {
            releaseCropSource()
            deletePendingAvatar(selectedAvatarUri)
            onBack()
        },
        backEnabled = !saving && !cropProcessing,
        whiteContentTransition = true,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp, 4.dp, 18.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(116.dp)) {
                        if (selectedAvatarUri == null) Avatar(profile, Modifier.size(108.dp).align(Alignment.TopStart))
                        else AvatarPreview(selectedAvatarUri, Modifier.size(108.dp).align(Alignment.TopStart))
                        Box(
                            Modifier
                                .size(48.dp)
                                .align(Alignment.BottomEnd)
                                .offset(x = 2.dp, y = 2.dp)
                                .background(YuJianColors.ActionPrimary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            YuJianIconAction(
                                icon = Icons.Rounded.CameraAlt,
                                contentDescription = "更换头像",
                                onClick = { if (!saving) showAvatarSheet = true },
                                family = YuJianIconActionFamily.UTILITY,
                                tone = YuJianIconActionTone.ON_MEDIA,
                                enabled = !saving,
                            )
                        }
                    }
                    YuJianTextAction(
                        text = "更换头像",
                        onClick = { if (!saving) showAvatarSheet = true },
                        role = YuJianTextActionRole.NORMAL,
                        enabled = !saving,
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("账号", color = DeepInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF3F6F8).copy(alpha = 0.96f),
                        border = BorderStroke(1.dp, Hairline),
                    ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.PersonOutline, contentDescription = null, tint = MutedInk)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(profile.username, color = MutedInk, fontSize = 14.sp)
                        }
                        Text("不可修改", color = MutedInk, fontSize = 12.sp)
                    }
                }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("昵称", color = DeepInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                            value = nickname,
                            onValueChange = { nickname = it; nicknameTouched = true; saved = false; error = null },
                            modifier = Modifier.fillMaxWidth().testTag("edit_profile_nickname"),
                            singleLine = true,
                            enabled = !saving,
                            isError = nicknameTouched && !nicknameValidation.isValid,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); saveProfile() }),
                            supportingText = {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    if (nicknameTouched && !nicknameValidation.isValid) {
                                        Text(nicknameValidation.error.orEmpty(), color = Color(0xFFB24A3A))
                                    } else {
                                        Spacer(Modifier.width(1.dp))
                                    }
                                    Text("${nicknameValidation.codePointCount}/20")
                                }
                            },
                            trailingIcon = {
                                if (nickname.isNotEmpty() && !saving) IconButton(onClick = { nickname = ""; nicknameTouched = true; error = null; saved = false }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "清空昵称")
                                }
                            },
                            leadingIcon = { Icon(Icons.Rounded.PersonOutline, contentDescription = null, tint = MutedInk) },
                    )
                }
            }
            if (selectedAvatarUri != null) {
                item {
                    Text(
                        "头像预览已暂存，点击保存修改后才会上传",
                        color = MutedInk,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 4.dp).semantics { contentDescription = "头像预览已暂存，尚未上传" },
                    )
                }
            }
            if (!error.isNullOrBlank()) item { ErrorMessage(error!!) }
            if (saved) item {
                Text("已保存", color = WaterTeal, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp).semantics { stateDescription = "已保存" })
            }
            item {
                YuJianPrimaryButton(
                    text = if (saving) "正在保存" else "保存修改",
                    onClick = ::saveProfile,
                    enabled = saveEnabled,
                    loading = saving,
                    modifier = Modifier.fillMaxWidth().testTag("edit_profile_save").semantics { if (saving) stateDescription = "正在保存" },
                )
            }
        }
    }
    if (cropSourceUri != null) {
        AvatarCropScreen(
            bitmap = cropBitmap,
            loading = cropLoading,
            error = cropError,
            processing = cropProcessing,
            onBack = { if (!cropProcessing) releaseCropSource() },
            onReselect = {
                if (!cropProcessing) {
                    releaseCropSource()
                    showAvatarSheet = true
                }
            },
            onUseAvatar = { crop ->
                val source = cropBitmap ?: return@AvatarCropScreen
                cropProcessing = true
                scope.launch {
                    try {
                        val output = withContext(Dispatchers.IO) { writeAvatarCrop(context, source, crop) }
                        deletePendingAvatar(selectedAvatarUri)
                        selectedAvatarUri = output
                        cropBitmap?.recycle()
                        cropBitmap = null
                        cropSourceUri?.let { uri -> if (uri.scheme == "file") runCatching { uri.path?.let { path -> File(path).delete() } } }
                        cropSourceUri = null
                        cropError = null
                        cropProcessing = false
                    } catch (failure: Throwable) {
                        cropError = (failure as? AvatarImageException)?.message ?: "无法保存这张图片，请重新选择"
                        cropProcessing = false
                    }
                }
            },
        )
    }
    if (showAvatarSheet && cropSourceUri == null) {
        ModalBottomSheet(onDismissRequest = { showAvatarSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("更换头像", color = DeepInk, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                TextButton(
                    onClick = {
                        showAvatarSheet = false
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            val target = RecognitionImageStore.createCameraTarget(context)
                            cameraTarget = target
                            takePictureLauncher.launch(target.uri)
                        } else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.CameraAlt, null); Spacer(Modifier.size(12.dp)); Text("拍照") } }
                TextButton(onClick = {
                    showAvatarSheet = false
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.PhotoLibrary, null); Spacer(Modifier.size(12.dp)); Text("从相册选择") }
                }
                TextButton(onClick = { showAvatarSheet = false }, modifier = Modifier.fillMaxWidth()) { Text("取消") }
            }
        }
    }
}

@Composable
private fun AvatarPreview(uri: Uri?, modifier: Modifier) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            uri?.let { selected ->
                runCatching { context.contentResolver.openInputStream(selected)?.use { input -> BitmapFactory.decodeStream(input) } }.getOrNull()
            }
        }
    }
    DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
    if (bitmap != null) Image(
        bitmap = bitmap!!.asImageBitmap(),
        contentDescription = "头像预览",
        modifier = modifier.semantics { contentDescription = "头像预览" },
        contentScale = ContentScale.Crop,
    )
    else Box(modifier = modifier.background(SoftWater, CircleShape), contentAlignment = Alignment.Center) { Text("预览", color = WaterTeal, fontSize = 12.sp) }
}

@Composable
fun AccountLoginScreen(
    profile: UserSession,
    onChangePassword: () -> Unit,
    onDataPrivacy: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
) {
    AccountPrivacyPageScaffold(title = "账号与登录", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp, 4.dp, 18.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SettingsCard {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("当前账号", color = MutedInk, fontSize = 13.sp)
                        Text(profile.username, color = DeepInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item {
                SettingsCard {
                    SettingsRow("修改密码", onClick = onChangePassword)
                    HairlineDivider()
                    SettingsRow("数据与隐私", onClick = onDataPrivacy)
                }
            }
            item {
                TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Logout, contentDescription = null, tint = Color(0xFFB24A3A))
                    Spacer(Modifier.size(8.dp))
                    Text("退出当前账号", color = Color(0xFFB24A3A))
                }
            }
        }
    }
}

fun validateChangePassword(current: String, newPassword: String, confirmation: String): String? = when {
    current.isBlank() -> "请输入当前密码"
    newPassword.length < 6 -> "新密码至少 6 位"
    newPassword.length > 72 -> "新密码不能超过 72 位"
    newPassword != confirmation -> "两次新密码不一致"
    current == newPassword -> "新密码不能与当前密码相同"
    else -> null
}

@Composable
fun ChangePasswordScreen(
    authRepository: AuthRepository,
    accessToken: String,
    onAuthenticationExpired: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var currentVisible by remember { mutableStateOf(false) }
    var newVisible by remember { mutableStateOf(false) }
    var confirmationVisible by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    AccountPrivacyPageScaffold(title = "修改密码", onBack = onBack, backEnabled = !saving) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp, 4.dp, 18.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SettingsCard {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PasswordField("当前密码", "change_password_current", current, currentVisible, ImeAction.Next, onVisibleChange = { currentVisible = it }) { current = it; error = null; message = null }
                        PasswordField("新密码", "change_password_new", newPassword, newVisible, ImeAction.Next, onVisibleChange = { newVisible = it }) { newPassword = it; error = null; message = null }
                        PasswordField("确认新密码", "change_password_confirmation", confirmation, confirmationVisible, ImeAction.Done, onVisibleChange = { confirmationVisible = it }) { confirmation = it; error = null; message = null }
                        Text("新密码至少 6 位", color = MutedInk, fontSize = 12.sp)
                    }
                }
            }
            if (!error.isNullOrBlank()) item { ErrorMessage(error!!) }
            if (!message.isNullOrBlank()) item { Text(message!!, color = WaterTeal, fontSize = 13.sp) }
            item {
                val validation = validateChangePassword(current, newPassword, confirmation)
                YuJianPrimaryButton(
                    text = if (saving) "正在保存" else "保存",
                    onClick = {
                        scope.launch {
                            saving = true
                            error = null
                            try {
                                authRepository.changePassword(accessToken, current, newPassword)
                                message = "密码已修改"
                                current = ""
                                newPassword = ""
                                confirmation = ""
                                scope.launch { delay(1_700); message = null }
                            } catch (failure: Throwable) {
                                val status = (failure as? ApiException)?.statusCode
                                if (status == 401) onAuthenticationExpired()
                                error = when (status) {
                                    403 -> "当前密码错误"
                                    401 -> "登录状态已失效，请重新登录"
                                    400, 422 -> "新密码不符合要求"
                                    else -> failure.userMessage("修改密码失败，请重试")
                                }
                            } finally {
                                saving = false
                            }
                        }
                    },
                    enabled = validation == null && !saving,
                    loading = saving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PasswordField(
    label: String,
    testTag: String,
    value: String,
    visible: Boolean,
    imeAction: ImeAction,
    onVisibleChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        singleLine = true,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { onVisibleChange(!visible) }) {
                Icon(
                    imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (visible) "隐藏$label" else "显示$label",
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataPrivacyScreen(
    authRepository: AuthRepository,
    accessToken: String,
    onAuthenticationExpired: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onComingSoon: (ComingSoonKind) -> Unit,
    onManualWithdrawal: () -> Unit = {},
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf<com.yujian.ai.auth.AiModelImprovementSettings?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var consentSheet by remember { mutableStateOf<Boolean?>(null) }
    var locationInfo by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(accessToken, reload) {
        loading = true
        error = null
        runCatching { authRepository.getPrivacySettings(accessToken) }
            .onSuccess { settings = it }
            .onFailure {
                if ((it as? ApiException)?.statusCode == 401) onAuthenticationExpired()
                error = it.userMessage("隐私设置加载失败，请重试")
            }
        loading = false
    }
    val locationLabel = locationPermissionLabel(context)
    AccountPrivacyPageScaffold(title = "数据与隐私", onBack = onBack, backEnabled = !loading) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp, 4.dp, 18.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("数据使用", color = MutedInk, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp)) }
            item {
                SettingsCard {
                    SettingsRow(
                        "AI 模型改进",
                        subtitle = when {
                            loading -> "读取中…"
                            settings == null -> "暂时无法读取授权状态"
                            settings?.enabled == true -> "已开启"
                            else -> "已关闭"
                        },
                        onClick = { if (!loading && settings != null) consentSheet = settings?.enabled != true },
                        trailing = {
                            Switch(
                                checked = settings?.enabled == true,
                                onCheckedChange = { if (!loading && settings != null) consentSheet = it },
                                enabled = !loading && settings != null,
                            )
                        },
                    )
                }
            }
            item {
                SettingsCard {
                    SettingsRow(
                        "位置权限",
                        subtitle = locationLabel,
                        onClick = { locationInfo = true },
                        trailing = { Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = MutedInk) },
                    )
                    HairlineDivider()
                    SettingsRow("导出我的数据", onClick = { onComingSoon(ComingSoonKind.EXPORT_DATA) })
                }
            }
            item { Text("账号数据", color = MutedInk, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp)) }
            item {
                SettingsCard {
                    SettingsRow("注销账号", onClick = { onComingSoon(ComingSoonKind.DELETE_ACCOUNT) })
                }
            }
            item {
                SettingsCard { SettingsRow("查看《隐私政策》", onClick = onPrivacyPolicy) }
            }
            if (!error.isNullOrBlank()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ErrorMessage(error!!)
                    if (settings == null) {
                        TextButton(onClick = { if (!loading) reload++ }, enabled = !loading) {
                            Text(if (loading) "正在重试…" else "重试读取隐私设置", color = YuJianColors.ActionPrimary)
                        }
                    }
                }
            }
        }
    }
    if (consentSheet != null) {
        val enabling = consentSheet == true
        ModalBottomSheet(onDismissRequest = { if (!loading) consentSheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(if (enabling) "帮助改善鱼种识别" else "关闭模型改进？", color = DeepInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (enabling) "仅会使用照片中框选出的鱼体部分和你确认的鱼种，用于训练和改进鱼种识别模型。"
                    else "关闭后，新的纠错鱼体和你确认的鱼种将不再用于训练鱼种识别模型。\n\n不会影响：\n拍照识鱼\n保存鱼获\n修改识别结果",
                    color = MutedInk,
                    fontSize = 14.sp,
                )
                YuJianPrimaryButton(
                    text = if (loading) "正在保存" else if (enabling) "允许用于模型改进" else "关闭",
                    onClick = {
                        if (settings != null && !loading) {
                            scope.launch {
                                loading = true
                                try {
                                    settings = authRepository.setAiModelImprovementConsent(accessToken, enabling, "settings")
                                    if (!enabling) onManualWithdrawal()
                                    consentSheet = null
                                    error = null
                                } catch (failure: Throwable) {
                                    if ((failure as? ApiException)?.statusCode == 401) onAuthenticationExpired()
                                    error = failure.userMessage("隐私设置保存失败，请重试")
                                } finally {
                                    loading = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = settings != null && !loading,
                    loading = loading,
                )
                TextButton(onClick = { if (!loading) consentSheet = null }, modifier = Modifier.fillMaxWidth(), enabled = !loading) {
                    Text(if (enabling) "暂不开启" else "保持开启", color = WaterTeal)
                }
            }
        }
    }
    if (locationInfo) {
        AlertDialog(
            onDismissRequest = { locationInfo = false },
            title = { Text("位置权限") },
            text = { Text("只有当你主动选择“使用当前位置”时，渔见才会获取你的位置，用于为当前鱼获添加地点。\n\n拒绝不会影响拍照识鱼和保存鱼获。\n\n当前状态：$locationLabel") },
            confirmButton = { TextButton(onClick = { locationInfo = false }) { Text("知道了") } },
        )
    }
}

fun locationPermissionLabel(context: Context): String {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (fine || coarse) return "使用期间"
    val asked = context.getSharedPreferences("yujian_location_permission", Context.MODE_PRIVATE).getBoolean("asked", false)
    return if (asked) "已拒绝" else "未授权"
}

@Composable
fun AboutYujianScreen(onUserAgreement: () -> Unit, onPrivacyPolicy: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }.getOrDefault("")
    }
    AccountPrivacyPageScaffold(title = "关于渔见", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp, 4.dp, 18.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Surface(shape = RoundedCornerShape(26.dp), color = CardWhite, tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("渔见", color = DeepInk, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("拍照收藏每次渔获", color = MutedInk, fontSize = 13.sp)
                        Text("Version $version", color = MutedInk, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            item {
                SettingsCard {
                    SettingsRow("用户协议", onClick = onUserAgreement)
                    HairlineDivider()
                    SettingsRow("隐私政策", onClick = onPrivacyPolicy)
                }
            }
        }
    }
}

const val LEGAL_COPY_REVIEW_REQUIRED = "LEGAL COPY REVIEW REQUIRED"

@Composable
fun LegalDocumentScreen(title: String, isPrivacyPolicy: Boolean, onBack: () -> Unit) {
    val body = if (isPrivacyPolicy) {
        "渔见用于帮助你拍照识鱼并保存鱼获记录。\n\n数据使用\n我们会根据你主动使用的功能处理账号、鱼获和必要的服务数据。AI 模型改进仅在你明确同意后开启，并优先使用照片中框选出的鱼体部分和你确认的鱼种。\n\n位置权限\n只有当你主动选择“使用当前位置”时，应用才会请求系统位置权限，用于为当前鱼获添加地点。\n\n你的选择\n你可以在“数据与隐私”中查看并修改 AI 模型改进授权。\n\n$LEGAL_COPY_REVIEW_REQUIRED"
    } else {
        "欢迎使用渔见。请在使用服务前阅读并理解本用户协议。\n\n你可以使用拍照识鱼和鱼获记录功能，并应当确保上传内容和填写信息合法、真实。\n\n$LEGAL_COPY_REVIEW_REQUIRED"
    }
    AccountPrivacyPageScaffold(title = title, onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp, 4.dp, 18.dp, 32.dp),
        ) {
            item {
                Surface(shape = RoundedCornerShape(22.dp), color = CardWhite, tonalElevation = 1.dp) {
                    Text(body, color = DeepInk, fontSize = 14.sp, lineHeight = 23.sp, modifier = Modifier.padding(20.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComingSoonSheet(kind: ComingSoonKind, onDismiss: () -> Unit) {
    val body = when (kind) {
        ComingSoonKind.FORGOT_PASSWORD -> "找回密码功能正在完善中。"
        ComingSoonKind.EXPORT_DATA -> "数据导出功能将在后续版本开放，敬请期待。"
        ComingSoonKind.DELETE_ACCOUNT -> "账号注销功能将在后续版本开放，敬请期待。"
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                if (kind == ComingSoonKind.FORGOT_PASSWORD) "忘记密码" else "功能正在准备中",
                color = DeepInk,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(body, color = MutedInk, fontSize = 14.sp)
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(25.dp), colors = ButtonDefaults.buttonColors(containerColor = WaterTeal)) {
                Text("知道了")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorrectionConsentPromptSheet(
    onEnable: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("帮助改善鱼种识别", color = DeepInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("仅会使用照片中框选出的鱼体部分和你确认的鱼种，用于训练和改进鱼种识别模型。", color = MutedInk, fontSize = 14.sp)
            Button(onClick = onEnable, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(25.dp), colors = ButtonDefaults.buttonColors(containerColor = WaterTeal)) {
                Text("允许用于模型改进")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("暂不开启", color = WaterTeal) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Text(message, color = Color(0xFFB24A3A), fontSize = 13.sp, modifier = Modifier.fillMaxWidth().background(SoftWater, RoundedCornerShape(12.dp)).padding(12.dp))
}

private fun Throwable.userMessage(fallback: String): String = when (this) {
    is ApiException -> message ?: fallback
    else -> message?.takeIf(String::isNotBlank) ?: fallback
}
