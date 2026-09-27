package com.yujian.ai.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.yujian.ai.ui.theme.WarmBackground
import com.yujian.ai.ui.theme.WaterTeal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ComingSoonKind {
    FORGOT_PASSWORD,
    EXPORT_DATA,
    DELETE_ACCOUNT,
}

@Composable
private fun PageScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmBackground)
            .padding(top = safeInsets.calculateTopPadding()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = DeepInk)
            }
            Text(title, color = DeepInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        content()
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
        shape = RoundedCornerShape(22.dp),
        color = CardWhite,
        tonalElevation = 1.dp,
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
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 16.dp),
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
    PageScaffold(title = "我的", onBack = onBack) {
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
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var nickname by remember(profile.nickname) { mutableStateOf(profile.nickname) }
    var selectedAvatarUri by remember { mutableStateOf<Uri?>(null) }
    var cameraTarget by remember { mutableStateOf<RecognitionImageStore.CameraTarget?>(null) }
    var showAvatarSheet by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            selectedAvatarUri = uri
            showPreview = true
        }
    }
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            selectedAvatarUri = cameraTarget?.uri
            showPreview = true
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
    val nicknameTrimmed = nickname.trim()
    val nicknameValid = nicknameTrimmed.isNotEmpty() && nicknameTrimmed.length <= 20
    val changed = nicknameTrimmed != profile.nickname || selectedAvatarUri != null

    PageScaffold(title = "编辑个人资料", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp, 4.dp, 18.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SettingsCard {
                    Row(
                        Modifier.fillMaxWidth().clickable { showAvatarSheet = true }.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (selectedAvatarUri == null) Avatar(profile, Modifier.size(64.dp))
                        else AvatarPreview(context, selectedAvatarUri, Modifier.size(64.dp))
                        Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("头像", color = DeepInk, fontSize = 16.sp)
                            Text("更换头像", color = MutedInk, fontSize = 13.sp)
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MutedInk)
                    }
                }
            }
            item {
                SettingsCard {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("昵称", color = DeepInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = nickname,
                            onValueChange = { nickname = it.take(20); saved = false; error = null },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            supportingText = { Text("${nickname.length}/20") },
                            trailingIcon = {
                                if (nickname.isNotEmpty()) IconButton(onClick = { nickname = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "清空昵称")
                                }
                            },
                        )
                    }
                }
            }
            if (!error.isNullOrBlank()) item { ErrorMessage(error!!) }
            if (saved) item { Text("已保存", color = WaterTeal, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp)) }
            item {
                Button(
                    onClick = {
                        scope.launch {
                            saving = true
                            error = null
                            runCatching {
                                var updated = if (nicknameTrimmed != profile.nickname) {
                                    authRepository.updateProfile(profile.accessToken, nicknameTrimmed)
                                } else {
                                    AccountProfile(profile.userId, profile.username, profile.nickname, profile.avatarUrl)
                                }
                                selectedAvatarUri?.let { uri ->
                                    updated = authRepository.updateAvatar(context, profile.accessToken, uri)
                                }
                                updated
                            }.onSuccess {
                                onProfileUpdated(it)
                                selectedAvatarUri = null
                                saved = true
                                showPreview = false
                            }.onFailure { error = it.userMessage("保存资料失败，请重试") }
                            saving = false
                        }
                    },
                    enabled = !saving && changed && nicknameValid,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WaterTeal),
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("保存", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    if (showAvatarSheet) {
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
    if (showPreview && selectedAvatarUri != null) {
        AlertDialog(
            onDismissRequest = { showPreview = false },
            title = { Text("预览头像") },
            text = { AvatarPreview(context, selectedAvatarUri, Modifier.size(180.dp).clip(CircleShape)) },
            confirmButton = { TextButton(onClick = { showPreview = false }) { Text("使用此头像") } },
            dismissButton = { TextButton(onClick = { selectedAvatarUri = null; showPreview = false }) { Text("重新选择") } },
        )
    }
}

@Composable
private fun AvatarPreview(context: Context, uri: Uri?, modifier: Modifier) {
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            uri?.let { runCatching { context.contentResolver.openInputStream(it)?.use(BitmapFactory::decodeStream) }.getOrNull() }
        }
    }
    if (bitmap != null) Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = "头像预览", modifier = modifier, contentScale = ContentScale.Crop)
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
    PageScaffold(title = "账号与登录", onBack = onBack) {
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
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    PageScaffold(title = "修改密码", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp, 4.dp, 18.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SettingsCard {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PasswordField("当前密码", current) { current = it; error = null; message = null }
                        PasswordField("新密码", newPassword) { newPassword = it; error = null; message = null }
                        PasswordField("确认新密码", confirmation) { confirmation = it; error = null; message = null }
                        Text("新密码至少 6 位", color = MutedInk, fontSize = 12.sp)
                    }
                }
            }
            if (!error.isNullOrBlank()) item { ErrorMessage(error!!) }
            if (!message.isNullOrBlank()) item { Text(message!!, color = WaterTeal, fontSize = 13.sp) }
            item {
                val validation = validateChangePassword(current, newPassword, confirmation)
                Button(
                    onClick = {
                        scope.launch {
                            saving = true
                            error = null
                            runCatching { authRepository.changePassword(accessToken, current, newPassword) }
                                .onSuccess { message = "密码已修改"; current = ""; newPassword = ""; confirmation = "" }
                                .onFailure { failure ->
                                    error = when ((failure as? ApiException)?.statusCode) {
                                        403 -> "当前密码错误"
                                        401 -> "登录状态已失效，请重新登录"
                                        400, 422 -> "新密码不符合要求"
                                        else -> failure.userMessage("修改密码失败，请重试")
                                    }
                                }
                            saving = false
                        }
                    },
                    enabled = !saving && validation == null,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WaterTeal),
                ) { if (saving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("保存", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun PasswordField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(72)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataPrivacyScreen(
    authRepository: AuthRepository,
    accessToken: String,
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
            .onFailure { error = it.userMessage("隐私设置加载失败，请重试") }
        loading = false
    }
    val locationLabel = locationPermissionLabel(context)
    PageScaffold(title = "数据与隐私", onBack = onBack) {
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
                        subtitle = if (loading) "读取中…" else if (settings?.enabled == true) "已开启" else "已关闭",
                        onClick = { if (!loading) consentSheet = settings?.enabled != true },
                        trailing = {
                            Switch(
                                checked = settings?.enabled == true,
                                onCheckedChange = { if (!loading) consentSheet = it },
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
            if (!error.isNullOrBlank()) item { ErrorMessage(error!!) }
        }
    }
    if (consentSheet != null) {
        val enabling = consentSheet == true
        ModalBottomSheet(onDismissRequest = { consentSheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(if (enabling) "帮助改善鱼种识别" else "关闭模型改进？", color = DeepInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (enabling) "仅会使用照片中框选出的鱼体部分和你确认的鱼种，用于训练和改进鱼种识别模型。"
                    else "关闭后，新的纠错鱼体和你确认的鱼种将不再用于训练鱼种识别模型。\n\n不会影响：\n拍照识鱼\n保存鱼获\n修改识别结果",
                    color = MutedInk,
                    fontSize = 14.sp,
                )
                Button(
                    onClick = {
                        consentSheet = null
                        scope.launch {
                            loading = true
                            runCatching {
                                authRepository.setAiModelImprovementConsent(accessToken, enabling, "settings")
                            }.onSuccess {
                                settings = it
                                if (!enabling) onManualWithdrawal()
                            }.onFailure { error = it.userMessage("隐私设置保存失败，请重试") }
                            loading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WaterTeal),
                ) { Text(if (enabling) "允许用于模型改进" else "关闭") }
                TextButton(onClick = { consentSheet = null }, modifier = Modifier.fillMaxWidth()) { Text(if (enabling) "暂不开启" else "保持开启", color = WaterTeal) }
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
    PageScaffold(title = "关于渔见", onBack = onBack) {
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
    PageScaffold(title = title, onBack = onBack) {
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
        ComingSoonKind.FORGOT_PASSWORD -> "忘记密码功能将在后续版本开放，敬请期待。"
        ComingSoonKind.EXPORT_DATA -> "数据导出功能将在后续版本开放，敬请期待。"
        ComingSoonKind.DELETE_ACCOUNT -> "账号注销功能将在后续版本开放，敬请期待。"
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("功能正在准备中", color = DeepInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
