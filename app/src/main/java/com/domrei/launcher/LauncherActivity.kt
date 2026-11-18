package com.domrei.launcher

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Rect as AndroidRect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import com.domrei.gameengine.DomreiGameEngine
import com.domrei.gameengine.GameEngineState
import com.domrei.gameengine.GameLaunchRequest
import com.domrei.gameengine.renderer.RendererDescriptor
import com.domrei.gameengine.renderer.RendererType
import com.domrei.launcher.minecraft.DownloadFileItem
import com.domrei.launcher.minecraft.DownloadStageStatus
import com.domrei.launcher.minecraft.FabricLoaderVersionEntry
import com.domrei.launcher.minecraft.InstallProgressSnapshot
import com.domrei.launcher.minecraft.MinecraftInstanceManager
import com.domrei.launcher.minecraft.MinecraftVersionFilter
import com.domrei.launcher.minecraft.MinecraftVersionManifest
import com.domrei.launcher.minecraft.OfficialMinecraftApi
import com.domrei.launcher.ui.theme.DomreiLauncherTheme
import com.domrei.launcher.ui.theme.LauncherColors
import com.domrei.launcher.ui.theme.LauncherColorTokens
import com.domrei.launcher.ui.theme.LauncherTheme
import com.domrei.launcher.ui.theme.colorToHsv
import com.domrei.launcher.ui.theme.formatColorHex
import com.domrei.launcher.ui.theme.hsvToColor
import com.domrei.launcher.ui.theme.parseHexColorOrNull
import com.domrei.skinview.DefaultSkin
import com.domrei.skinview.SkinAnimations
import com.domrei.skinview.SkinModel
import com.domrei.skinview.SkinUtils
import com.domrei.skinview.SkinViewer
import com.domrei.skinview.SkinViewerConfig
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val SKIN_ASSET = "skin.png"
private val PROGRESS_BAR_EXPANDED_HEIGHT = 264.dp

class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureFullScreenWithCutout()

        setContent {
            DomreiLauncherTheme {
                LauncherRoot()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun configureFullScreenWithCutout() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }

        hideSystemBars()
    }

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }
}

/**
 * Navigation rail destinations using custom vector icons and string resources.
 * When no destination is selected (`null`), the launcher is on the default Home Screen.
 */
enum class LauncherDestination(
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
) {
    INSTANCES(R.string.nav_instances, R.drawable.ic_instances),
    ACCOUNT(R.string.nav_account, R.drawable.ic_account),
    SETTINGS(R.string.nav_settings, R.drawable.ic_settings),
}

data class LauncherInstance(
    val id: String,
    val name: String,
    val mcVersion: String,
    val loaderType: InstanceLoaderType = InstanceLoaderType.VANILLA,
    val loaderVersion: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val versionJsonPath: String = "",
    val clientJarPath: String = "",
    val assetIndexId: String = "",
    val mainClass: String = "",
) {
    @get:DrawableRes
    val iconRes: Int
        get() = loaderType.iconRes
}

enum class InstanceLoaderType(
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    val enabled: Boolean,
    val keepOriginalIconColor: Boolean = false,
) {
    VANILLA(R.string.instance_type_vanilla, R.drawable.ic_instances, true),
    FABRIC(R.string.instance_type_fabric, R.drawable.ic_fabric, true, keepOriginalIconColor = true),
    OTHER(R.string.instance_type_other_not_yet, R.drawable.ic_settings, false),
}

/**
 * Renders a loader/instance icon while keeping multi-color official logos (such as Fabric)
 * completely untinted so their original colors are never modified.
 */
@Composable
private fun InstanceLoaderIcon(
    loaderType: InstanceLoaderType,
    contentDescription: String?,
    defaultTint: Color,
    modifier: Modifier = Modifier,
) {
    val painter = painterResource(id = loaderType.iconRes)
    if (loaderType.keepOriginalIconColor) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = modifier,
        )
    } else {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = defaultTint,
            modifier = modifier,
        )
    }
}

sealed interface AddInstanceStep {
    val stepIndex: Int

    data object SelectType : AddInstanceStep {
        override val stepIndex: Int = 0
    }

    data class SelectMinecraftVersion(
        val loaderType: InstanceLoaderType,
    ) : AddInstanceStep {
        override val stepIndex: Int = 1
    }

    data class SelectLoaderVersion(
        val loaderType: InstanceLoaderType,
        val mcVersion: String,
    ) : AddInstanceStep {
        override val stepIndex: Int = 2
    }

    data class EnterInstanceName(
        val loaderType: InstanceLoaderType,
        val mcVersion: String,
        val loaderVersion: String?,
    ) : AddInstanceStep {
        override val stepIndex: Int = 3
    }
}

data class InstanceInstallTask(
    val id: String,
    val instanceName: String,
    val loaderType: InstanceLoaderType,
    val mcVersion: String,
    val loaderVersion: String?,
    val isInstanceInstall: Boolean = true,
    val customStatusTitle: String? = null,
    val customIconRes: Int? = null,
)

data class LauncherAccount(
    val id: String,
    val username: String,
    @StringRes val typeRes: Int = R.string.account_type_offline,
    val uuid: String? = null,
    val accessToken: String? = null,
    val msaRefreshToken: String? = null,
)

sealed interface LauncherDialog {
    data object ChooseAccountType : LauncherDialog
    data object AddOfflineAccount : LauncherDialog
    data class RenameAccount(val account: LauncherAccount) : LauncherDialog
    data class ConfirmRemoveAccount(val account: LauncherAccount) : LauncherDialog
    data class ConfirmRemoveInstance(val instance: LauncherInstance) : LauncherDialog
    data object AddInstance : LauncherDialog
    data class PickAccentColor(val initialColor: Color) : LauncherDialog
}

/**
 * Reusable interactive press-bounce modifier that scales down smoothly on press,
 * springs back on release, and plays the launcher's soft bobble click sound when tapped.
 */
private fun Modifier.smoothPressClickable(
    enabled: Boolean = true,
    pressScale: Float = 0.95f,
    playSound: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val soundPlayer = LocalLauncherSoundPlayer.current
    val currentOnClick by rememberUpdatedState(onClick)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "pressScale",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = {
                if (playSound) {
                    soundPlayer?.playBobble()
                }
                currentOnClick()
            },
        )
}

/**
 * Root composable: shows the branded splash while the saved content is read, then hands the
 * preloaded data to [LauncherScreen] so the first frame is fully populated.
 */
@Composable
private fun LauncherRoot() {
    val context = LocalContext.current
    var preloaded by remember { mutableStateOf<PreloadedContent?>(null) }

    LaunchedEffect(Unit) {
        val localContent = async(Dispatchers.IO) { LauncherPreloader.loadLocal(context) }
        val networkWarmup = async(Dispatchers.IO) { LauncherPreloader.preloadNetwork() }
        val content = localContent.await()
        // Give the version-list prefetch a short head start. It keeps running in the
        // background if the device is offline or slow, so the splash never blocks on it.
        withTimeoutOrNull(900) { networkWarmup.await() }
        preloaded = content
    }

    Crossfade(
        targetState = preloaded,
        animationSpec = tween(durationMillis = 320),
        label = "launcherSplashCrossfade",
    ) { content ->
        if (content == null) {
            LauncherSplashScreen()
        } else {
            LauncherScreen(preloaded = content)
        }
    }
}

@Composable
fun LauncherScreen(
    modifier: Modifier = Modifier,
    backgroundConfig: BackgroundConfig? = null,
    preloaded: PreloadedContent? = null,
) {
    val context = LocalContext.current
    val isInPreview = LocalInspectionMode.current
    val appearancePrefs = remember(context) { AppearancePreferences(context) }
    var currentBackground by remember(backgroundConfig) {
        mutableStateOf(
            backgroundConfig ?: preloaded?.backgroundConfig ?: if (isInPreview) {
                BackgroundConfig(source = BackgroundSource.MinecraftSunset())
            } else {
                appearancePrefs.loadBackgroundConfig()
            },
        )
    }
    var accentColor by remember {
        mutableStateOf(
            if (isInPreview) {
                LauncherColorTokens.DefaultAccent
            } else {
                preloaded?.accentColor ?: appearancePrefs.loadAccentColor()
            },
        )
    }
    var isImportingBackground by remember { mutableStateOf(false) }
    var backgroundImportError by remember { mutableStateOf<String?>(null) }

    // null = Default Home Screen.
    // Tapping a button selects it; tapping the currently active button returns to the Home Screen (null).
    var currentDestination by remember { mutableStateOf<LauncherDestination?>(null) }

    // Controls the 300dp-wide Change panel opened by the "change" button left of Play
    var isChangePanelOpen by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val instanceManager = remember(context) { MinecraftInstanceManager(context) }
    val gameEngine = remember(context) { DomreiGameEngine(context) }
    val engineStatus by gameEngine.status.collectAsState()
    var selectedRendererDescriptor by remember {
        mutableStateOf(gameEngine.rendererManager.getSelectedRendererDescriptor())
    }
    var availableRenderers by remember {
        mutableStateOf(gameEngine.rendererManager.getAvailableRenderers())
    }
    val selectedRenderer = selectedRendererDescriptor.effectiveType
    val displayPrefs = remember(context) { DisplayPreferences(context) }
    var resolutionScale by remember { mutableIntStateOf(displayPrefs.loadResolutionScale()) }
    var requestMaxRefreshRate by remember {
        mutableStateOf(displayPrefs.loadRequestMaxRefreshRate())
    }
    var useSystemVulkanDriver by remember {
        mutableStateOf(displayPrefs.loadUseSystemVulkanDriver())
    }
    var useSurfaceViewRendering by remember {
        mutableStateOf(displayPrefs.loadUseSurfaceViewRendering())
    }
    var instances by remember {
        mutableStateOf(preloaded?.instances ?: instanceManager.loadInstances())
    }
    var selectedInstanceId by remember {
        mutableStateOf(
            preloaded?.selectedInstanceId
                ?: instanceManager.loadSelectedInstanceId()
                    .takeIf { saved -> instances.any { it.id == saved } }
                ?: instances.firstOrNull()?.id.orEmpty(),
        )
    }
    // Remember the selected instance across app restarts
    LaunchedEffect(selectedInstanceId) { instanceManager.saveSelectedInstanceId(selectedInstanceId) }
    // Persist instance ordering across app restarts
    LaunchedEffect(instances) { instanceManager.saveInstancesOrder(instances) }

    // Dynamic Island-style Install ProgressBar state (strictly for AddInstance)
    var activeInstallTask by remember { mutableStateOf<InstanceInstallTask?>(null) }
    var installSnapshot by remember { mutableStateOf<InstallProgressSnapshot?>(null) }

    // Dynamic Island-style Microsoft Account Auth & Verification state
    var activeAuthTask by remember { mutableStateOf<InstanceInstallTask?>(null) }
    var authSnapshot by remember { mutableStateOf<InstallProgressSnapshot?>(null) }
    var skinRefreshKey by remember { mutableIntStateOf(0) }

    // Dynamic Island-style Game Engine ProgressBar state (PREPARING_RUNTIME / PREPARING_RENDERER / BUILDING_SPEC)
    var activeEngineTask by remember { mutableStateOf<InstanceInstallTask?>(null) }
    var engineSnapshot by remember { mutableStateOf<InstallProgressSnapshot?>(null) }
    var activeEngineLaunchJob by remember { mutableStateOf<Job?>(null) }

    val isEnginePreparing = engineStatus.state == GameEngineState.PREPARING_RUNTIME ||
        engineStatus.state == GameEngineState.PREPARING_RENDERER ||
        engineStatus.state == GameEngineState.BUILDING_SPEC

    val effectiveProgressTask: InstanceInstallTask? = activeInstallTask ?: activeEngineTask ?: activeAuthTask ?: if (isEnginePreparing) {
        InstanceInstallTask(
            id = "engine_${engineStatus.state.name}",
            instanceName = engineStatus.activeInstanceName ?: engineStatus.activeRuntime?.displayName ?: "Runtime",
            loaderType = InstanceLoaderType.VANILLA,
            mcVersion = engineStatus.stageMessage,
            loaderVersion = null,
            isInstanceInstall = false,
            customStatusTitle = "${engineStatus.state.name}: ${engineStatus.stageMessage}",
        )
    } else {
        null
    }

    var displayedInstallTask by remember { mutableStateOf<InstanceInstallTask?>(null) }
    if (effectiveProgressTask != null) {
        displayedInstallTask = effectiveProgressTask
    }
    val effectiveSnapshot: InstallProgressSnapshot? = if (activeInstallTask != null) {
        installSnapshot
    } else if (activeEngineTask != null) {
        engineSnapshot
    } else if (activeAuthTask != null) {
        authSnapshot
    } else {
        if (isEnginePreparing) {
            InstallProgressSnapshot(
                progress = engineStatus.progress.coerceIn(0f, 1f),
                currentFileName = "${engineStatus.state.name}: ${engineStatus.stageMessage}",
                downloadedMb = engineStatus.progress * 24f,
                totalMb = 24f,
                speedMbPerSec = 5.0f,
                files = listOf(
                    DownloadFileItem(
                        fileName = "PREPARING_RUNTIME (${engineStatus.activeRuntime?.displayName ?: "OpenJDK"})",
                        sizeMb = 19f,
                        progress = if (engineStatus.state == GameEngineState.PREPARING_RUNTIME) engineStatus.progress else 1f,
                        status = if (engineStatus.state == GameEngineState.PREPARING_RUNTIME) DownloadStageStatus.DOWNLOADING else DownloadStageStatus.DONE,
                    ),
                    DownloadFileItem(
                        fileName = "PREPARING_RENDERER (${engineStatus.activeRenderer?.displayName ?: selectedRendererDescriptor.displayName})",
                        sizeMb = 5f,
                        progress = when (engineStatus.state) {
                            GameEngineState.PREPARING_RUNTIME -> 0f
                            GameEngineState.PREPARING_RENDERER -> engineStatus.progress
                            else -> 1f
                        },
                        status = when (engineStatus.state) {
                            GameEngineState.PREPARING_RUNTIME -> DownloadStageStatus.WAITING
                            GameEngineState.PREPARING_RENDERER -> DownloadStageStatus.DOWNLOADING
                            else -> DownloadStageStatus.DONE
                        },
                    ),
                ),
                isComplete = engineStatus.progress >= 1f,
            )
        } else {
            null
        }
    }
    var isProgressBarExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(activeInstallTask?.id) {
        val task = activeInstallTask ?: return@LaunchedEffect
        if (!task.isInstanceInstall) return@LaunchedEffect
        installSnapshot = null
        val result = instanceManager.installInstance(
            taskId = task.id,
            instanceName = task.instanceName,
            loaderType = task.loaderType,
            mcVersion = task.mcVersion,
            loaderVersion = task.loaderVersion,
            includeAssets = true,
            onProgress = { snapshot ->
                installSnapshot = snapshot
            },
        )
        result.onSuccess { installed ->
            instances = instanceManager.loadInstances()
            selectedInstanceId = installed.id
            delay(1100L)
        }.onFailure { err ->
            if (err is CancellationException) throw err
            Log.e("LauncherScreen", "Instance install failed for ${task.mcVersion}", err)
            delay(2600L)
        }
        activeInstallTask = null
        isProgressBarExpanded = false
    }

    val accountStore = remember(context) { AccountStore(context) }
    val initialAccounts = remember { preloaded?.accounts ?: accountStore.loadAccounts() }
    var accounts by remember { mutableStateOf(initialAccounts) }
    var selectedAccountId by remember {
        mutableStateOf(
            preloaded?.selectedAccountId
                ?: accountStore.loadSelectedAccountId()
                    .takeIf { saved -> initialAccounts.any { it.id == saved } }
                ?: initialAccounts.firstOrNull()?.id.orEmpty(),
        )
    }
    // Persist the account list and the current selection across app restarts
    LaunchedEffect(accounts) { accountStore.saveAccounts(accounts) }
    LaunchedEffect(selectedAccountId) { accountStore.saveSelectedAccountId(selectedAccountId) }
    val selectedAccount = remember(accounts, selectedAccountId) {
        accounts.firstOrNull { it.id == selectedAccountId } ?: accounts.firstOrNull()
    }

    // Auto-sync official Minecraft skin in background for Microsoft accounts if not already cached
    LaunchedEffect(selectedAccount?.id) {
        val acc = selectedAccount ?: return@LaunchedEffect
        if (AccountSkinManager.isMicrosoftAccount(acc) && (!AccountSkinManager.hasSkin(context, acc) || !AccountSkinManager.hasCapeChecked(context, acc))) {
            AccountSkinManager.syncAccountSkin(context, acc)
            skinRefreshKey++
        }
    }
    LaunchedEffect(accounts) {
        var anySynced = false
        accounts.forEach { acc ->
            if (AccountSkinManager.isMicrosoftAccount(acc) && (!AccountSkinManager.hasSkin(context, acc) || !AccountSkinManager.hasCapeChecked(context, acc))) {
                AccountSkinManager.syncAccountSkin(context, acc)
                anySynced = true
            }
        }
        if (anySynced) {
            skinRefreshKey++
        }
    }
    val defaultPlayerName = stringResource(R.string.default_player_name)
    val activeUsername = selectedAccount?.username ?: ""
    val launchUsername = selectedAccount?.username ?: defaultPlayerName

    // Full-screen dialog state (Rename Account, Add Account, Add Instance, or any dialog like them)
    var activeDialog by remember { mutableStateOf<LauncherDialog?>(null) }
    // Remember last non-null dialog so exit animation can play smoothly without disappearing on frame 1
    var displayedDialog by remember { mutableStateOf<LauncherDialog?>(null) }
    if (activeDialog != null) {
        displayedDialog = activeDialog
    }
    var dialogInputText by remember { mutableStateOf("") }
    var isMicrosoftLoginOpen by remember { mutableStateOf(false) }

    // Smoothly animate full-screen blur radius when any dialog opens or closes
    val dialogBlurRadius by animateDpAsState(
        targetValue = if (activeDialog != null) 20.dp else 0.dp,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "dialogBlurRadius",
    )

    // Shared animation clock so the real-time Gaussian-blurred rail backdrop matches the live background.
    // Freeze it while a dialog is open: everything behind the dialog is blurred anyway, so this avoids
    // redrawing the background + re-recording the blurred backdrop every frame during dialog animation.
    // Static sources (image / solid color) never advance the clock at all — no needless invalidations.
    val animSpeed = when {
        activeDialog != null -> 0f
        else -> when (val source = currentBackground.source) {
            is BackgroundSource.MinecraftSunset -> source.animationSpeed
            is BackgroundSource.Gif, is BackgroundSource.Video -> 1f
            else -> 0f
        }
    }
    val sharedTimeSeconds = rememberBackgroundTimeState(animSpeed)

    val importSelectedMedia: (Uri?) -> Unit = { pickedUri ->
        if (pickedUri != null) {
            coroutineScope.launch {
                isImportingBackground = true
                backgroundImportError = null
                appearancePrefs.importCustomBackground(pickedUri)
                    .onSuccess { newSource ->
                        val updated = currentBackground.copy(source = newSource)
                        currentBackground = updated
                        appearancePrefs.saveBackgroundConfig(updated)
                    }
                    .onFailure { err ->
                        backgroundImportError = err.message ?: context.getString(R.string.settings_bg_import_failed)
                    }
                isImportingBackground = false
            }
        }
    }

    val mediaPickerLauncher = if (!isInPreview) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri -> importSelectedMedia(uri) }
    } else {
        null
    }
    val launchMediaPicker: () -> Unit = {
        mediaPickerLauncher?.launch(arrayOf("image/*", "video/*"))
    }

    LaunchedEffect(currentDestination) {
        if (currentDestination == LauncherDestination.SETTINGS) {
            availableRenderers = gameEngine.rendererManager.getAvailableRenderers()
        }
    }

    val soundPlayer = remember(context, isInPreview) {
        if (isInPreview) null else LauncherSoundPlayer(context)
    }
    DisposableEffect(soundPlayer) {
        onDispose {
            soundPlayer?.release()
        }
    }

    val startMicrosoftAuthVerification: (String) -> Unit = { code ->
        coroutineScope.launch {
            val authTaskId = "msa_auth_${System.currentTimeMillis()}"
            activeAuthTask = InstanceInstallTask(
                id = authTaskId,
                instanceName = context.getString(R.string.microsoft_login_title),
                loaderType = InstanceLoaderType.VANILLA,
                mcVersion = "Authenticating…",
                loaderVersion = null,
                isInstanceInstall = false,
                customStatusTitle = context.getString(R.string.microsoft_login_title),
                customIconRes = R.drawable.ic_microsoft,
            )
            authSnapshot = InstallProgressSnapshot(
                progress = 0.15f,
                currentFileName = context.getString(R.string.microsoft_verify_token),
                downloadedMb = 0f,
                totalMb = 0f,
                speedMbPerSec = 0f,
            )

            MicrosoftAuthenticator.authenticateWithCode(code) { stepMessage, progress ->
                authSnapshot = InstallProgressSnapshot(
                    progress = progress,
                    currentFileName = stepMessage,
                    downloadedMb = 0f,
                    totalMb = 0f,
                    speedMbPerSec = 0f,
                )
                activeAuthTask = activeAuthTask?.copy(
                    mcVersion = stepMessage,
                    customStatusTitle = stepMessage,
                )
            }.onSuccess { result ->
                val existing = accounts.firstOrNull { it.uuid == result.uuid || it.username.equals(result.username, ignoreCase = true) }
                val accountId = existing?.id ?: "ms_${result.uuid}"
                val newAcc = LauncherAccount(
                    id = accountId,
                    username = result.username,
                    typeRes = R.string.account_type_microsoft,
                    uuid = result.uuid,
                    accessToken = result.accessToken,
                    msaRefreshToken = result.refreshToken,
                )

                if (result.skinUrl != null) {
                    authSnapshot = InstallProgressSnapshot(
                        progress = 0.95f,
                        currentFileName = context.getString(R.string.microsoft_syncing_skin),
                        downloadedMb = 0f,
                        totalMb = 0f,
                        speedMbPerSec = 0f,
                    )
                    val downloaded = AccountSkinManager.downloadAndSaveSkin(context, result.uuid, result.skinUrl)
                    if (downloaded != null) {
                        AccountSkinManager.saveSkinBitmap(context, newAcc, downloaded)
                    }
                } else {
                    AccountSkinManager.syncAccountSkin(context, newAcc, force = true)
                }

                if (result.capeUrl != null) {
                    val downloadedCape = AccountSkinManager.downloadAndSaveCape(context, result.uuid, result.capeUrl)
                    if (downloadedCape != null) {
                        AccountSkinManager.saveCapeBitmap(context, newAcc, downloadedCape)
                    }
                } else {
                    AccountSkinManager.deleteCape(context, newAcc)
                }
                val updatedAccounts = if (existing != null) {
                    accounts.map { if (it.id == existing.id) newAcc else it }
                } else {
                    accounts + newAcc
                }
                accounts = updatedAccounts
                selectedAccountId = newAcc.id
                accountStore.saveAccounts(updatedAccounts)
                accountStore.saveSelectedAccountId(newAcc.id)
                skinRefreshKey++

                authSnapshot = InstallProgressSnapshot(
                    progress = 1.0f,
                    currentFileName = context.getString(R.string.microsoft_login_success_named, result.username),
                    downloadedMb = 0f,
                    totalMb = 0f,
                    speedMbPerSec = 0f,
                )
                delay(1200)
                activeAuthTask = null
                authSnapshot = null
            }.onFailure { ex ->
                Log.e("MicrosoftAuth", "Authentication failed", ex)
                val errorMsg = ex.message ?: context.getString(R.string.microsoft_login_failed, "Unknown error")
                authSnapshot = InstallProgressSnapshot(
                    progress = 1.0f,
                    currentFileName = errorMsg,
                    downloadedMb = 0f,
                    totalMb = 0f,
                    speedMbPerSec = 0f,
                )
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                delay(2500)
                activeAuthTask = null
                authSnapshot = null
            }
        }
    }

    val refreshMicrosoftAccount: (LauncherAccount) -> Unit = { account ->
        val refreshToken = account.msaRefreshToken
        if (refreshToken.isNullOrBlank()) {
            Toast.makeText(context, context.getString(R.string.microsoft_refresh_missing_token), Toast.LENGTH_SHORT).show()
            isMicrosoftLoginOpen = true
        } else {
            coroutineScope.launch {
                val refreshTaskId = "msa_refresh_${account.id}"
                activeAuthTask = InstanceInstallTask(
                    id = refreshTaskId,
                    instanceName = account.username,
                    loaderType = InstanceLoaderType.VANILLA,
                    mcVersion = "Refreshing…",
                    loaderVersion = null,
                    isInstanceInstall = false,
                    customStatusTitle = context.getString(R.string.microsoft_refreshing_title),
                    customIconRes = R.drawable.ic_microsoft,
                )
                authSnapshot = InstallProgressSnapshot(
                    progress = 0.15f,
                    currentFileName = context.getString(R.string.microsoft_refresh_token_step),
                    downloadedMb = 0f,
                    totalMb = 0f,
                    speedMbPerSec = 0f,
                )

                MicrosoftAuthenticator.refreshAccount(refreshToken) { stepMessage, progress ->
                    authSnapshot = InstallProgressSnapshot(
                        progress = progress,
                        currentFileName = stepMessage,
                        downloadedMb = 0f,
                        totalMb = 0f,
                        speedMbPerSec = 0f,
                    )
                    activeAuthTask = activeAuthTask?.copy(
                        mcVersion = stepMessage,
                        customStatusTitle = stepMessage,
                    )
                }.onSuccess { result ->
                    val updatedAccount = account.copy(
                        username = result.username,
                        uuid = result.uuid,
                        accessToken = result.accessToken,
                        msaRefreshToken = result.refreshToken ?: account.msaRefreshToken,
                    )

                    if (result.skinUrl != null) {
                        authSnapshot = InstallProgressSnapshot(
                            progress = 0.95f,
                            currentFileName = context.getString(R.string.microsoft_syncing_skin),
                            downloadedMb = 0f,
                            totalMb = 0f,
                            speedMbPerSec = 0f,
                        )
                        val downloaded = AccountSkinManager.downloadAndSaveSkin(context, result.uuid, result.skinUrl)
                        if (downloaded != null) {
                            AccountSkinManager.saveSkinBitmap(context, updatedAccount, downloaded)
                        }
                    } else {
                        AccountSkinManager.syncAccountSkin(context, updatedAccount, force = true)
                    }

                    if (result.capeUrl != null) {
                        val downloadedCape = AccountSkinManager.downloadAndSaveCape(context, result.uuid, result.capeUrl)
                        if (downloadedCape != null) {
                            AccountSkinManager.saveCapeBitmap(context, updatedAccount, downloadedCape)
                        }
                    } else {
                        AccountSkinManager.deleteCape(context, updatedAccount)
                    }

                    val updatedAccounts = accounts.map { if (it.id == account.id) updatedAccount else it }
                    accounts = updatedAccounts
                    accountStore.saveAccounts(updatedAccounts)
                    skinRefreshKey++

                    authSnapshot = InstallProgressSnapshot(
                        progress = 1.0f,
                        currentFileName = context.getString(R.string.microsoft_refresh_success),
                        downloadedMb = 0f,
                        totalMb = 0f,
                        speedMbPerSec = 0f,
                    )
                    delay(1200)
                    activeAuthTask = null
                    authSnapshot = null
                }.onFailure { ex ->
                    Log.e("MicrosoftAuth", "Refresh failed", ex)
                    val errorMsg = context.getString(R.string.microsoft_refresh_failed, ex.message ?: "Unknown error")
                    authSnapshot = InstallProgressSnapshot(
                        progress = 1.0f,
                        currentFileName = errorMsg,
                        downloadedMb = 0f,
                        totalMb = 0f,
                        speedMbPerSec = 0f,
                    )
                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                    delay(2500)
                    activeAuthTask = null
                    authSnapshot = null
                }
            }
        }
    }

    DomreiLauncherTheme(accentColor = accentColor) {
    CompositionLocalProvider(LocalLauncherSoundPlayer provides soundPlayer) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val sceneGraphicsLayer = rememberGraphicsLayer()
        val progressBackdropLayer = rememberGraphicsLayer()
        val backgroundGraphicsLayer = rememberGraphicsLayer()
        val blurredBackgroundLayer = rememberGraphicsLayer()
        val blurRecordClockNanos = remember { longArrayOf(0L) }
        val blurRecordedSize = remember { IntArray(2) }
        val backgroundRecordedConfig = remember { arrayOfNulls<BackgroundConfig>(1) }
        val backgroundRecordedSize = remember { IntArray(2) }
        // Static backgrounds (image / solid color) never change, so the blurred backdrop only needs
        // to be recorded once per size instead of 30 times per second.
        val backgroundIsLive = remember(currentBackground.source, animSpeed) {
            animSpeed > 0f && when (currentBackground.source) {
                is BackgroundSource.MinecraftSunset,
                is BackgroundSource.Gif,
                is BackgroundSource.Video -> true
                else -> false
            }
        }
        var backgroundRootOffset by remember { mutableStateOf(Offset.Zero) }
        var backgroundFrameTick by remember { mutableIntStateOf(0) }
        val progressTransition = updateTransition(
            targetState = effectiveProgressTask != null,
            label = "progressBarVisibility",
        )
        val shouldCaptureProgressBlur =
            progressTransition.currentState || progressTransition.targetState

        val density = LocalDensity.current
        val screenWidthPx = with(density) { screenWidth.toPx() }
        val screenHeightPx = with(density) { screenHeight.toPx() }
        val bgWidthInt = screenWidthPx.roundToInt().coerceAtLeast(1)
        val bgHeightInt = screenHeightPx.roundToInt().coerceAtLeast(1)
        val topInsetPx = with(density) { 6.dp.toPx() }
        val panelBlurRadiusPx = with(density) { 20.dp.toPx() }.coerceAtLeast(1f)
        val progressBlurRadiusPx = with(density) { 10.dp.toPx() }.coerceAtLeast(1f)
        val blurPadPx = with(density) { 48.dp.toPx() }
        val maxIslandWidthPx = with(density) { 410.dp.toPx() }
        val maxIslandHeightPx = with(density) { PROGRESS_BAR_EXPANDED_HEIGHT.toPx() }
        val regionLeftPx = ((screenWidthPx - maxIslandWidthPx) * 0.5f - blurPadPx)
            .coerceAtLeast(0f)
        val regionTopPx = (topInsetPx - blurPadPx).coerceAtLeast(0f)
        val regionRightPx = ((screenWidthPx + maxIslandWidthPx) * 0.5f + blurPadPx)
            .coerceAtMost(screenWidthPx)
        val regionBottomPx = (topInsetPx + maxIslandHeightPx + blurPadPx)
            .coerceAtMost(screenHeightPx)
        val regionWidthInt = (regionRightPx - regionLeftPx).roundToInt().coerceAtLeast(1)
        val regionHeightInt = (regionBottomPx - regionTopPx).roundToInt().coerceAtLeast(1)
        val panelBlurEffect = remember(panelBlurRadiusPx) {
            BlurEffect(
                renderEffect = null,
                radiusX = panelBlurRadiusPx,
                radiusY = panelBlurRadiusPx,
                edgeTreatment = TileMode.Clamp,
            )
        }
        val sharedPanelBackdropLayer = when {
            isInPreview -> null
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> blurredBackgroundLayer
            else -> backgroundGraphicsLayer
        }
        val progressBlurEffect = remember(progressBlurRadiusPx) {
            BlurEffect(
                renderEffect = null,
                radiusX = progressBlurRadiusPx,
                radiusY = progressBlurRadiusPx,
                edgeTreatment = TileMode.Clamp,
            )
        }

        // Main screen content container: smoothly blurred across the full screen when any dialog is open.
        // The animated radius is read inside the graphics layer (draw phase) so opening/closing a
        // dialog only invalidates drawing — it no longer recomposes the whole launcher tree per frame.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val radiusPx = dialogBlurRadius.toPx()
                    if (radiusPx > 0.5f) {
                        renderEffect = BlurEffect(
                            renderEffect = null,
                            radiusX = radiusPx,
                            radiusY = radiusPx,
                            edgeTreatment = TileMode.Clamp,
                        )
                        compositingStrategy = CompositingStrategy.Offscreen
                    } else {
                        renderEffect = null
                        compositingStrategy = CompositingStrategy.Auto
                    }
                },
        ) {
            // Scene container recorded into sceneGraphicsLayer so ProgressBar blurs the live panels
            // (Instances, Account, Settings, RightPlayAndSkinPanel) and background underneath it.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        if (shouldCaptureProgressBlur) {
                            sceneGraphicsLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            drawLayer(sceneGraphicsLayer)

                            progressBackdropLayer.clip = true
                            if (progressBackdropLayer.renderEffect != progressBlurEffect) {
                                progressBackdropLayer.renderEffect = progressBlurEffect
                            }
                            progressBackdropLayer.record(
                                size = IntSize(regionWidthInt, regionHeightInt),
                            ) {
                                translate(left = -regionLeftPx, top = -regionTopPx) {
                                    drawLayer(sceneGraphicsLayer)
                                }
                            }
                        } else {
                            this@drawWithContent.drawContent()
                        }
                    },
            ) {
                // 1. Single full-screen background recorded once and blurred once into blurredBackgroundLayer
                // so Tab Panel, Change button, Version panel, and Play button share identical live blur (including Video/GIF).
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coords ->
                            if (coords.isAttached) {
                                val pos = coords.positionInRoot()
                                if (pos != backgroundRootOffset) {
                                    backgroundRootOffset = pos
                                }
                            }
                        }
                        .drawWithContent {
                            @Suppress("UNUSED_EXPRESSION")
                            backgroundFrameTick
                            if (!isInPreview) {
                                // Static backgrounds (and the frozen background behind dialogs) are
                                // recorded once and then reused, instead of re-recording a
                                // full-screen layer on every vsync.
                                val bgSizeChanged = backgroundRecordedSize[0] != bgWidthInt ||
                                    backgroundRecordedSize[1] != bgHeightInt
                                if (backgroundIsLive ||
                                    bgSizeChanged ||
                                    backgroundRecordedConfig[0] != currentBackground
                                ) {
                                    backgroundGraphicsLayer.record {
                                        this@drawWithContent.drawContent()
                                    }
                                    backgroundRecordedConfig[0] = currentBackground
                                    backgroundRecordedSize[0] = bgWidthInt
                                    backgroundRecordedSize[1] = bgHeightInt
                                }
                                drawLayer(backgroundGraphicsLayer)

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    blurredBackgroundLayer.clip = true
                                    if (blurredBackgroundLayer.renderEffect != panelBlurEffect) {
                                        blurredBackgroundLayer.renderEffect = panelBlurEffect
                                    }
                                    // Re-blur at ~30 fps instead of every vsync: the frosted panels
                                    // only need a smooth backdrop, while a full-screen 20dp blur
                                    // each frame is very expensive and causes startup jank. Static
                                    // backgrounds re-record only when the layer size changes.
                                    val nowNanos = System.nanoTime()
                                    val sizeChanged = blurRecordedSize[0] != bgWidthInt ||
                                        blurRecordedSize[1] != bgHeightInt
                                    val blurIntervalNanos =
                                        if (backgroundIsLive) 33_000_000L else Long.MAX_VALUE
                                    if (sizeChanged || nowNanos - blurRecordClockNanos[0] >= blurIntervalNanos) {
                                        blurRecordClockNanos[0] = nowNanos
                                        blurRecordedSize[0] = bgWidthInt
                                        blurRecordedSize[1] = bgHeightInt
                                        blurredBackgroundLayer.record(
                                            size = IntSize(bgWidthInt, bgHeightInt),
                                        ) {
                                            drawLayer(backgroundGraphicsLayer)
                                        }
                                    }
                                }
                            } else {
                                this@drawWithContent.drawContent()
                            }
                        },
                ) {
                    LauncherBackground(
                        modifier = Modifier.fillMaxSize(),
                        config = currentBackground,
                        sharedTimeSeconds = sharedTimeSeconds,
                        onMediaFrameRendered = {
                            backgroundFrameTick = (backgroundFrameTick + 1) and 0x7FFF_FFFF
                        },
                    )
                }

                // Tap-outside scrim to close the Change panel when open
                if (isChangePanelOpen && currentDestination == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { isChangePanelOpen = false },
                            ),
                    )
                }

                // 2. Unified Left Navigation Rail Bar + Destination Panel in the same Box
                // Separated by a vertical divider line and opening/closing with AnimatedVisibility
                UnifiedLeftRailAndPanel(
                selectedDestination = currentDestination,
                onDestinationClick = { clicked ->
                    isChangePanelOpen = false
                    currentDestination = if (currentDestination == clicked) {
                        null // Tapping current button returns to default Home Screen
                    } else {
                        clicked // Opens the selected destination panel
                    }
                },
                instances = instances,
                selectedInstanceId = selectedInstanceId,
                onInstanceSelect = { instance -> selectedInstanceId = instance.id },
                onAddInstanceClick = {
                    activeDialog = LauncherDialog.AddInstance
                },
                onRemoveInstance = { instance ->
                    activeDialog = LauncherDialog.ConfirmRemoveInstance(instance)
                },
                onReorderInstances = { instances = it },
                accounts = accounts,
                selectedAccountId = selectedAccountId,
                onAccountSelect = { account -> selectedAccountId = account.id },
                onAddAccountClick = {
                    // Start with an empty name; the Add button stays disabled until one is typed
                    dialogInputText = ""
                    activeDialog = LauncherDialog.ChooseAccountType
                },
                onRequestRenameAccount = { account ->
                    dialogInputText = account.username
                    activeDialog = LauncherDialog.RenameAccount(account)
                },
                onRefreshAccount = refreshMicrosoftAccount,
                onRemoveAccount = { account ->
                    activeDialog = LauncherDialog.ConfirmRemoveAccount(account)
                },
                onReorderAccounts = { accounts = it },
                skinRefreshKey = skinRefreshKey,
                accentColor = accentColor,
                isImportingBackground = isImportingBackground,
                backgroundImportError = backgroundImportError,
                onPickBackgroundMedia = launchMediaPicker,
                onResetBackground = {
                    backgroundImportError = null
                    appearancePrefs.clearCustomBackgroundFiles()
                    val resetConfig = BackgroundConfig(source = BackgroundSource.MinecraftSunset())
                    currentBackground = resetConfig
                    appearancePrefs.saveBackgroundConfig(resetConfig)
                },
                onAccentColorChange = { newAccent ->
                    accentColor = newAccent
                    appearancePrefs.saveAccentColor(newAccent)
                },
                onOpenAccentColorPicker = {
                    activeDialog = LauncherDialog.PickAccentColor(initialColor = accentColor)
                },
                selectedRendererDescriptor = selectedRendererDescriptor,
                availableRenderers = availableRenderers,
                onRendererSelect = { newDescriptor ->
                    gameEngine.rendererManager.setSelectedRenderer(newDescriptor)
                    selectedRendererDescriptor = newDescriptor
                },
                resolutionScale = resolutionScale,
                onResolutionScaleChange = { newScale ->
                    displayPrefs.saveResolutionScale(newScale)
                    resolutionScale = newScale
                },
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                backgroundConfig = currentBackground,
                sharedTimeSeconds = sharedTimeSeconds,
                blurredBackgroundLayer = sharedPanelBackdropLayer,
                backgroundRootOffset = backgroundRootOffset,
                backgroundFrameTick = { backgroundFrameTick },
                isSuspended = activeDialog != null,
                modifier = Modifier.align(Alignment.CenterStart),
            )

            // 3. Right Side Panel: Resizing Change Button/Panel + 3D Skin View + Play Button
            // Moves out when any left navigation rail panel (Instances, Account, Settings) opens.
            AnimatedVisibility(
                visible = currentDestination == null,
                enter = fadeIn(
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                ) + slideInHorizontally(
                    animationSpec = spring(
                        dampingRatio = 0.80f,
                        stiffness = 340f,
                    ),
                    initialOffsetX = { fullWidth -> fullWidth + 120 },
                ),
                exit = fadeOut(
                    animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing),
                ) + slideOutHorizontally(
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                    targetOffsetX = { fullWidth -> fullWidth + 120 },
                ),
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                RightPlayAndSkinPanel(
                    instances = instances,
                    selectedInstanceId = selectedInstanceId,
                    onInstanceSelect = { instance ->
                        selectedInstanceId = instance.id
                        isChangePanelOpen = false
                    },
                    onAddInstanceClick = {
                        isChangePanelOpen = false
                        activeDialog = LauncherDialog.AddInstance
                    },
                    activeUsername = activeUsername,
                    activeAccount = selectedAccount,
                    skinRefreshKey = skinRefreshKey,
                    isChangePanelOpen = isChangePanelOpen,
                    onChangeToggle = { isChangePanelOpen = !isChangePanelOpen },
                    isSuspended = activeDialog != null,
                    onReorderInstances = { instances = it },
                    onPlayClick = {
                        if (engineStatus.state == GameEngineState.RUNNING) {
                            gameEngine.stopActiveGame()
                            return@RightPlayAndSkinPanel
                        }
                        val targetInstance = instances.firstOrNull { it.id == selectedInstanceId }
                            ?: instances.firstOrNull()
                        if (targetInstance == null) {
                            activeDialog = LauncherDialog.AddInstance
                        } else {
                            activeEngineLaunchJob?.cancel()
                            activeEngineLaunchJob = coroutineScope.launch {
                                try {
                                    activeEngineTask = InstanceInstallTask(
                                        id = "launch_${targetInstance.id}_${System.currentTimeMillis()}",
                                        instanceName = targetInstance.name,
                                        loaderType = targetInstance.loaderType,
                                        mcVersion = targetInstance.mcVersion,
                                        loaderVersion = targetInstance.loaderVersion,
                                        isInstanceInstall = false,
                                        customStatusTitle = "PREPARING_RUNTIME: ${targetInstance.name}",
                                    )
                                    val displayMetrics = context.resources.displayMetrics
                                    val maxDim = maxOf(displayMetrics.widthPixels, displayMetrics.heightPixels).coerceAtLeast(1280)
                                    val minDim = minOf(displayMetrics.widthPixels, displayMetrics.heightPixels).coerceAtLeast(720)
                                    val baseWidth = maxDim
                                    val baseHeight = minDim
                                    val scaledWidth = ((baseWidth * resolutionScale) / 100).coerceAtLeast(320)
                                    val scaledHeight = ((baseHeight * resolutionScale) / 100).coerceAtLeast(180)

                                    val request = GameLaunchRequest(
                                        instanceName = targetInstance.name,
                                        minecraftVersion = targetInstance.mcVersion,
                                        loaderType = if (targetInstance.loaderType == InstanceLoaderType.FABRIC) "Fabric" else "Vanilla",
                                        loaderVersion = targetInstance.loaderVersion,
                                        minecraftRootDir = instanceManager.rootDir,
                                        instanceGameDir = instanceManager.gameDirFor(targetInstance.id),
                                        username = launchUsername,
                                        uuid = selectedAccount?.uuid
                                            ?: GameLaunchRequest.generateOfflineUuid(launchUsername),
                                        accessToken = selectedAccount?.accessToken ?: "0",
                                        preferredRenderer = selectedRenderer,
                                        windowWidth = scaledWidth,
                                        windowHeight = scaledHeight,
                                        requestMaxRefreshRate = requestMaxRefreshRate,
                                        useSystemVulkanDriver = useSystemVulkanDriver,
                                        useSurfaceViewRendering = useSurfaceViewRendering,
                                    )
                                    val launchResult = gameEngine.launchGame(
                                        request = request,
                                        scope = coroutineScope,
                                    ) { stage, fraction ->
                                        val stateLabel = gameEngine.status.value.state.name
                                        activeEngineTask = activeEngineTask?.copy(
                                            customStatusTitle = "$stateLabel: $stage",
                                        )
                                        engineSnapshot = InstallProgressSnapshot(
                                            progress = fraction.coerceIn(0f, 1f),
                                            currentFileName = "$stateLabel: $stage",
                                            downloadedMb = fraction * 25f,
                                            totalMb = 25f,
                                            speedMbPerSec = 5.0f,
                                            files = listOf(
                                                DownloadFileItem(
                                                    fileName = "PREPARING_RUNTIME (${gameEngine.status.value.activeRuntime?.displayName ?: "OpenJDK"})",
                                                    sizeMb = 19f,
                                                    progress = (fraction / 0.60f).coerceIn(0f, 1f),
                                                    status = if (fraction >= 0.60f) DownloadStageStatus.DONE else DownloadStageStatus.DOWNLOADING,
                                                ),
                                                DownloadFileItem(
                                                    fileName = "PREPARING_RENDERER (${selectedRendererDescriptor.displayName})",
                                                    sizeMb = 6f,
                                                    progress = ((fraction - 0.60f) / 0.28f).coerceIn(0f, 1f),
                                                    status = when {
                                                        fraction >= 0.88f -> DownloadStageStatus.DONE
                                                        fraction >= 0.60f -> DownloadStageStatus.DOWNLOADING
                                                        else -> DownloadStageStatus.WAITING
                                                    },
                                                ),
                                                DownloadFileItem(
                                                    fileName = "BUILDING_SPEC (${targetInstance.name})",
                                                    sizeMb = 0.5f,
                                                    progress = ((fraction - 0.88f) / 0.12f).coerceIn(0f, 1f),
                                                    status = when {
                                                        fraction >= 1.0f -> DownloadStageStatus.DONE
                                                        fraction >= 0.88f -> DownloadStageStatus.DOWNLOADING
                                                        else -> DownloadStageStatus.WAITING
                                                    },
                                                ),
                                            ),
                                            isComplete = fraction >= 1f,
                                        )
                                    }
                                    if (launchResult.isSuccess) {
                                        delay(900L)
                                    } else {
                                        delay(2200L)
                                    }
                                } finally {
                                    activeEngineTask = null
                                    engineSnapshot = null
                                    isProgressBarExpanded = false
                                    activeEngineLaunchJob = null
                                }
                            }
                        }
                    },
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    backgroundConfig = currentBackground,
                    sharedTimeSeconds = sharedTimeSeconds,
                    blurredBackgroundLayer = sharedPanelBackdropLayer,
                    backgroundRootOffset = backgroundRootOffset,
                    backgroundFrameTick = { backgroundFrameTick },
                )
            }
            }

            // Tap-outside scrim to collapse the expanded Dynamic Island ProgressBar
            if (isProgressBarExpanded && effectiveProgressTask != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { isProgressBarExpanded = false },
                        ),
                )
            }

            // 4. Dynamic Island-style Install & Engine ProgressBar at Top-Center
            // Tap it to expand/collapse details showing what it is currently downloading or preparing!
            progressTransition.AnimatedVisibility(
                visible = { it },
                enter = fadeIn(
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                ) + scaleIn(
                    initialScale = 0.65f,
                    animationSpec = spring(
                        dampingRatio = 0.72f,
                        stiffness = 380f,
                    ),
                ) + slideInVertically(
                    animationSpec = spring(
                        dampingRatio = 0.75f,
                        stiffness = 380f,
                    ),
                    initialOffsetY = { fullHeight -> -fullHeight },
                ),
                exit = fadeOut(
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                ) + scaleOut(
                    targetScale = 0.70f,
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                ) + slideOutVertically(
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    targetOffsetY = { fullHeight -> -fullHeight },
                ),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp),
            ) {
                displayedInstallTask?.let { task ->
                    ProgressBar(
                        task = task,
                        snapshot = effectiveSnapshot,
                        expanded = isProgressBarExpanded,
                        onToggleExpand = { isProgressBarExpanded = !isProgressBarExpanded },
                        onCancelTask = {
                            isProgressBarExpanded = false
                            if (activeInstallTask != null) {
                                activeInstallTask = null
                                installSnapshot = null
                            }
                            if (activeAuthTask != null) {
                                activeAuthTask = null
                                authSnapshot = null
                            }
                            if (activeEngineTask != null || isEnginePreparing || activeEngineLaunchJob != null) {
                                activeEngineLaunchJob?.cancel()
                                activeEngineLaunchJob = null
                                activeEngineTask = null
                                engineSnapshot = null
                                gameEngine.cancelPreparation()
                            }
                        },
                        screenWidth = screenWidth,
                        screenHeight = screenHeight,
                        backgroundConfig = currentBackground,
                        sharedTimeSeconds = sharedTimeSeconds,
                        sceneGraphicsLayer = sceneGraphicsLayer,
                        blurredBackdropLayer = progressBackdropLayer,
                        backdropRegionLeftPx = regionLeftPx,
                        backdropRegionTopPx = regionTopPx,
                        topInset = 6.dp,
                    )
                }
            }
        }

        // 5. Full-screen 20% opacity + full-screen blur Dialog Overlay (Rename Account, Add Account, Add Instance, Accent Color Picker)
        var isDialogInputFocused by remember(activeDialog) { mutableStateOf(false) }
        FullScreenBlurDialog(
            visible = activeDialog != null,
            isKeyboardActive = isDialogInputFocused,
            onDismiss = {
                val current = activeDialog
                if (current is LauncherDialog.PickAccentColor) {
                    accentColor = current.initialColor
                }
                activeDialog = null
            },
        ) {
            when (val dialog = displayedDialog) {
                is LauncherDialog.RenameAccount -> {
                    val trimmedName = dialogInputText.trim()
                    val renameError = if (trimmedName.isEmpty()) {
                        null
                    } else {
                        validateUsername(
                            username = trimmedName,
                            accounts = accounts,
                            ignoreAccountId = dialog.account.id,
                        )
                    }
                    LauncherTextInputDialogContent(
                        title = stringResource(R.string.rename_account),
                        value = dialogInputText,
                        onValueChange = { dialogInputText = it },
                        confirmText = stringResource(R.string.action_save),
                        label = stringResource(R.string.label_username),
                        onFocusChange = { isDialogInputFocused = it },
                        errorText = renameError?.let { stringResource(it.messageRes) },
                        confirmEnabled = trimmedName.isNotEmpty() && renameError == null,
                        onCancel = { activeDialog = null },
                        onConfirm = {
                            if (trimmedName.isNotEmpty() && renameError == null) {
                                accounts = accounts.map {
                                    if (it.id == dialog.account.id) it.copy(username = trimmedName) else it
                                }
                                activeDialog = null
                            }
                        },
                    )
                }
                is LauncherDialog.ConfirmRemoveAccount -> {
                    LauncherConfirmDialogContent(
                        title = stringResource(R.string.dialog_confirm_remove_account_title),
                        message = stringResource(R.string.dialog_confirm_remove_account_message, dialog.account.username),
                        confirmText = stringResource(R.string.action_remove),
                        onCancel = { activeDialog = null },
                        onConfirm = {
                            AccountSkinManager.deleteSkin(context, dialog.account)
                            AccountSkinManager.deleteCape(context, dialog.account, markChecked = false)
                            val remaining = accounts.filterNot { it.id == dialog.account.id }
                            accounts = remaining
                            if (selectedAccountId == dialog.account.id) {
                                selectedAccountId = remaining.firstOrNull()?.id.orEmpty()
                            }
                            activeDialog = null
                        },
                    )
                }
                is LauncherDialog.ConfirmRemoveInstance -> {
                    LauncherConfirmDialogContent(
                        title = stringResource(R.string.dialog_confirm_remove_instance_title),
                        message = stringResource(R.string.dialog_confirm_remove_instance_message, dialog.instance.name),
                        confirmText = stringResource(R.string.action_remove),
                        onCancel = { activeDialog = null },
                        onConfirm = {
                            instanceManager.removeInstance(dialog.instance.id)
                            val remaining = instanceManager.loadInstances()
                            instances = remaining
                            if (selectedInstanceId == dialog.instance.id) {
                                selectedInstanceId = remaining.firstOrNull()?.id.orEmpty()
                            }
                            activeDialog = null
                        },
                    )
                }
                is LauncherDialog.ChooseAccountType -> {
                    ChooseAccountTypeDialogContent(
                        onCancel = { activeDialog = null },
                        onSelectOffline = {
                            // Start with an empty name; Add stays disabled until one is typed.
                            dialogInputText = ""
                            isDialogInputFocused = false
                            activeDialog = LauncherDialog.AddOfflineAccount
                        },
                        onSelectMicrosoft = {
                            activeDialog = null
                            isMicrosoftLoginOpen = true
                        },
                    )
                }
                is LauncherDialog.AddOfflineAccount -> {
                    val trimmedName = dialogInputText.trim()
                    val addError = if (trimmedName.isEmpty()) {
                        null
                    } else {
                        validateUsername(username = trimmedName, accounts = accounts)
                    }
                    LauncherTextInputDialogContent(
                        title = stringResource(R.string.dialog_add_offline_account_title),
                        value = dialogInputText,
                        onValueChange = { dialogInputText = it },
                        confirmText = stringResource(R.string.action_add),
                        label = stringResource(R.string.label_username),
                        onFocusChange = { isDialogInputFocused = it },
                        errorText = addError?.let { stringResource(it.messageRes) },
                        confirmEnabled = trimmedName.isNotEmpty() && addError == null,
                        onCancel = { activeDialog = null },
                        onConfirm = {
                            if (trimmedName.isNotEmpty() && addError == null) {
                                val newAcc = LauncherAccount(
                                    id = "acc_${System.currentTimeMillis()}_${accounts.size}",
                                    username = trimmedName,
                                    typeRes = R.string.account_type_offline,
                                )
                                accounts = accounts + newAcc
                                selectedAccountId = newAcc.id
                                activeDialog = null
                            }
                        },
                    )
                }
                is LauncherDialog.AddInstance -> {
                    AddInstanceDialogContent(
                        onFocusChange = { isDialogInputFocused = it },
                        existingInstanceNames = buildSet {
                            instances.forEach { add(it.name) }
                            activeInstallTask?.instanceName?.let { add(it) }
                        },
                        onCancel = { activeDialog = null },
                        onInstall = { loaderType, mcVersion, loaderVersion, enteredName ->
                            val fallbackName = if (loaderType == InstanceLoaderType.FABRIC && loaderVersion != null) {
                                context.getString(R.string.instance_fabric_format, mcVersion, loaderVersion)
                            } else {
                                context.getString(R.string.instance_vanilla_format, mcVersion)
                            }
                            val instanceName = enteredName.trim().ifEmpty { fallbackName }
                            isProgressBarExpanded = false
                            installSnapshot = null
                            activeInstallTask = InstanceInstallTask(
                                id = "inst_${System.currentTimeMillis()}",
                                instanceName = instanceName,
                                loaderType = loaderType,
                                mcVersion = mcVersion,
                                loaderVersion = loaderVersion,
                            )
                            activeDialog = null
                        },
                    )
                }
                is LauncherDialog.PickAccentColor -> {
                    AccentColorPickerDialogContent(
                        initialColor = dialog.initialColor,
                        selectedColor = accentColor,
                        onPreviewColorChange = { preview -> accentColor = preview },
                        onFocusChange = { isDialogInputFocused = it },
                        onCancel = {
                            accentColor = dialog.initialColor
                            activeDialog = null
                        },
                        onApply = { finalColor ->
                            accentColor = finalColor
                            appearancePrefs.saveAccentColor(finalColor)
                            activeDialog = null
                        },
                    )
                }
                null -> Unit
            }
        }

        if (isMicrosoftLoginOpen) {
            MicrosoftLoginScreen(
                onClose = { isMicrosoftLoginOpen = false },
                onCodeCaptured = { code ->
                    isMicrosoftLoginOpen = false
                    startMicrosoftAuthVerification(code)
                },
            )
        }
    }
    }
    }
}

/**
 * Reusable full-screen dialog container that covers the entire screen (`fillMaxSize()`)
 * at 20% opacity (`alpha = 0.20f`) over the full-screen blurred UI.
 * Pulls content up when the soft keyboard opens (`.imePadding()`) and closes the keyboard
 * when tapping anywhere while the keyboard is open.
 */
@Composable
fun FullScreenBlurDialog(
    visible: Boolean,
    isKeyboardActive: Boolean = false,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = LauncherTheme.colors
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0 || isKeyboardActive

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        ) + scaleIn(
            initialScale = 0.86f,
            animationSpec = spring(
                dampingRatio = 0.72f,
                stiffness = 400f,
            ),
        ) + slideInVertically(
            animationSpec = spring(
                dampingRatio = 0.76f,
                stiffness = 400f,
            ),
            initialOffsetY = { fullHeight -> fullHeight / 7 },
        ),
        exit = fadeOut(
            animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        ) + scaleOut(
            targetScale = 0.90f,
            animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        ) + slideOutVertically(
            animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
            targetOffsetY = { fullHeight -> fullHeight / 10 },
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.dialogScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (isImeVisible) {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                        } else {
                            onDismiss()
                        }
                    },
                )
                .imePadding()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = 0.85f,
                        stiffness = 400f,
                    ),
                )
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
/**
 * Material 3 outlined text field, built from the official low-level building blocks
 * ([BasicTextField] + [OutlinedTextFieldDefaults.DecorationBox] + [OutlinedTextFieldDefaults.Container])
 * so it stays compact while keeping the stock behavior: the label rests inside as a placeholder,
 * floats up with the standard animation, and the top stroke gets a real notched gap around it.
 * The container fill is transparent so the glass dialog shows through the notch cleanly.
 */
private fun LauncherFloatingLabelInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    onFocusChange: (Boolean) -> Unit = {},
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {},
) {
    val colors = LauncherTheme.colors
    val interactionSource = remember { MutableInteractionSource() }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        cursorColor = colors.accent,
        focusedBorderColor = colors.accent,
        unfocusedBorderColor = colors.borderModal,
        focusedLabelColor = colors.accent,
        unfocusedLabelColor = colors.textDim,
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        ),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        cursorBrush = SolidColor(colors.accent),
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { onFocusChange(it.isFocused) },
    ) { innerTextField ->
        OutlinedTextFieldDefaults.DecorationBox(
            value = value,
            innerTextField = innerTextField,
            enabled = true,
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            interactionSource = interactionSource,
            label = { Text(text = label, maxLines = 1) },
            colors = fieldColors,
            contentPadding = PaddingValues(
                start = 14.dp,
                top = 10.dp,
                end = 14.dp,
                bottom = 10.dp,
            ),
            container = {
                OutlinedTextFieldDefaults.Container(
                    enabled = true,
                    isError = false,
                    interactionSource = interactionSource,
                    colors = fieldColors,
                    shape = RoundedCornerShape(12.dp),
                )
            },
        )
    }
}

@Composable
private fun LauncherTextInputDialogContent(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    confirmText: String,
    label: String,
    onFocusChange: (Boolean) -> Unit = {},
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    errorText: String? = null,
    confirmEnabled: Boolean = true,
) {
    val colors = LauncherTheme.colors
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val modalShape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .width(320.dp)
            .clip(modalShape)
            .background(colors.dialogSurface)
            .border(1.dp, colors.borderModal, modalShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    // Tapping anywhere on the dialog card outside the text field closes the soft keyboard
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                },
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            color = colors.textPrimary,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
        )

        LauncherFloatingLabelInput(
            value = value,
            onValueChange = onValueChange,
            label = label,
            onFocusChange = onFocusChange,
            onDone = {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            },
        )

        if (!errorText.isNullOrBlank()) {
            Text(
                text = errorText,
                color = colors.errorText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val btnShape = RoundedCornerShape(10.dp)
            Box(
                modifier = Modifier
                    .smoothPressClickable(
                        pressScale = 0.94f,
                        onClick = {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                            onCancel()
                        },
                    )
                    .clip(btnShape)
                    .background(colors.surfaceButtonSubtle)
                    .border(1.dp, colors.borderButton, btnShape)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .smoothPressClickable(
                        enabled = confirmEnabled,
                        pressScale = 0.94f,
                        onClick = {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                            onConfirm()
                        },
                    )
                    .clip(btnShape)
                    .background(
                        if (confirmEnabled) colors.surfaceButtonSelected else colors.surfaceButtonDisabled,
                    )
                    .border(
                        1.dp,
                        if (confirmEnabled) colors.borderSelectedCard else colors.borderSubtle,
                        btnShape,
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = confirmText,
                    color = if (confirmEnabled) colors.accent else colors.textActionDisabled,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

@Composable
private fun LauncherConfirmDialogContent(
    title: String,
    message: String,
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = LauncherTheme.colors
    val modalShape = RoundedCornerShape(18.dp)
    val btnShape = RoundedCornerShape(10.dp)

    Column(
        modifier = Modifier
            .width(320.dp)
            .clip(modalShape)
            .background(colors.dialogSurface)
            .border(1.dp, colors.borderModal, modalShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            color = colors.textPrimary,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
        )

        Text(
            text = message,
            color = colors.textSubtle,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
            ),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .smoothPressClickable(
                        pressScale = 0.94f,
                        onClick = onCancel,
                    )
                    .clip(btnShape)
                    .background(colors.surfaceButtonSubtle)
                    .border(1.dp, colors.borderButton, btnShape)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .smoothPressClickable(
                        pressScale = 0.94f,
                        onClick = onConfirm,
                    )
                    .clip(btnShape)
                    .background(colors.errorContainer)
                    .border(1.dp, colors.errorBorderStrong, btnShape)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = confirmText,
                    color = colors.errorText,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ChooseAccountTypeDialogContent(
    onCancel: () -> Unit,
    onSelectOffline: () -> Unit,
    onSelectMicrosoft: () -> Unit,
) {
    val colors = LauncherTheme.colors
    val modalShape = RoundedCornerShape(20.dp)

    Column(
        modifier = Modifier
            .width(360.dp)
            .clip(modalShape)
            .background(colors.dialogSurface)
            .border(1.dp, colors.borderModal, modalShape)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.add_account),
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            // Close 'X' button
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .smoothPressClickable(
                        pressScale = 0.90f,
                        onClick = onCancel,
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceButtonSubtle)
                    .border(1.dp, colors.borderButton, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close_chevron),
                    contentDescription = stringResource(R.string.action_cancel),
                    tint = colors.textMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        // Two options side by side: Left = Offline, Right = Microsoft
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Left Option: {disconnected_icon} Offline
            AccountTypeOptionCard(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.ic_offline,
                title = stringResource(R.string.account_type_offline_name),
                iconTint = colors.iconPrimary,
                onClick = onSelectOffline,
            )

            // Right Option: {Microsoft_logo} Microsoft
            AccountTypeOptionCard(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.ic_microsoft,
                title = stringResource(R.string.account_type_microsoft_name),
                iconTint = Color.Unspecified,
                onClick = onSelectMicrosoft,
            )
        }
    }
}

@Composable
private fun AccountTypeOptionCard(
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int,
    title: String,
    iconTint: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    val colors = LauncherTheme.colors
    val cardShape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier
            .smoothPressClickable(
                pressScale = 0.93f,
                onClick = onClick,
            )
            .clip(cardShape)
            .background(colors.surfaceCard)
            .border(1.dp, colors.borderButtonStrong, cardShape)
            .padding(vertical = 18.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = title,
            tint = iconTint,
            modifier = Modifier.size(36.dp),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = title,
            color = colors.textPrimary,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MicrosoftLoginScreen(
    onClose: () -> Unit,
    onCodeCaptured: (String) -> Unit,
) {
    val colors = LauncherTheme.colors
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoadingPage by remember { mutableStateOf(true) }

    BackHandler(enabled = true) {
        if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            onClose()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading()
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E)),
    ) {
        // Full-screen WebView
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        cacheMode = WebSettings.LOAD_NO_CACHE
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    }
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                    clearHistory()
                    clearCache(true)
                    clearFormData()

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                            if (MicrosoftAuthenticator.isRedirectUrl(url)) {
                                val code = MicrosoftAuthenticator.extractCode(url)
                                if (!code.isNullOrBlank()) {
                                    view.stopLoading()
                                    onCodeCaptured(code)
                                    return true
                                }
                                if (url.contains("error=") || url.contains("res=cancel")) {
                                    onClose()
                                    return true
                                }
                            }
                            return false
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoadingPage = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoadingPage = false
                        }
                    }

                    loadUrl(MicrosoftAuthenticator.LOGIN_URL)
                    webViewRef = this
                }
            },
        )

        // Page Loading Progress Bar at top edge
        if (isLoadingPage) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.TopCenter),
                color = colors.accent,
                trackColor = colors.transparent,
            )
        }

        // Floating Close Button at top-right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0x99000000))
                .border(1.dp, Color(0x33FFFFFF), CircleShape)
                .smoothPressClickable(
                    pressScale = 0.90f,
                    onClick = onClose,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close_chevron),
                contentDescription = stringResource(R.string.action_cancel),
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun AccentColorPickerDialogContent(
    initialColor: Color,
    selectedColor: Color,
    onPreviewColorChange: (Color) -> Unit,
    onFocusChange: (Boolean) -> Unit = {},
    onCancel: () -> Unit,
    onApply: (Color) -> Unit,
) {
    val colors = LauncherTheme.colors
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val initialHsv = remember(initialColor) { colorToHsv(initialColor) }
    var hue by remember(initialColor) { androidx.compose.runtime.mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(initialColor) { androidx.compose.runtime.mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember(initialColor) { androidx.compose.runtime.mutableFloatStateOf(initialHsv[2]) }
    var hexInput by remember(initialColor) { mutableStateOf(formatColorHex(selectedColor)) }
    var isHexFocused by remember { mutableStateOf(false) }

    fun updateFromHsv(newH: Float = hue, newS: Float = saturation, newV: Float = brightness) {
        hue = ((newH % 360f) + 360f) % 360f
        saturation = newS.coerceIn(0f, 1f)
        brightness = newV.coerceIn(0f, 1f)
        val picked = hsvToColor(hue, saturation, brightness)
        hexInput = formatColorHex(picked)
        onPreviewColorChange(picked)
    }

    val hexBorderColor by animateColorAsState(
        targetValue = if (isHexFocused) colors.accentBorderStrong else colors.borderModal,
        animationSpec = tween(durationMillis = 180),
        label = "hexInputBorderColor",
    )
    val hexBgColor by animateColorAsState(
        targetValue = if (isHexFocused) colors.surfaceInputFocused else colors.surfaceInput,
        animationSpec = tween(durationMillis = 180),
        label = "hexInputBgColor",
    )

    val modalShape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .width(460.dp)
            .clip(modalShape)
            .background(colors.dialogSurface)
            .border(1.dp, colors.borderModal, modalShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                },
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.dialog_color_picker_title),
            color = colors.textPrimary,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
        )

        // Interactive 2D Saturation x Value gradient + Hue spectrum + Hex
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 2D Saturation (X) x Brightness (Y) Square
            val pureHueColor = remember(hue) { hsvToColor(hue, 1f, 1f) }
            val padShape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(132.dp)
                    .clip(padShape)
                    .border(1.dp, colors.borderButton, padShape)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val sx = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            val vy = 1f - (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                            updateFromHsv(newS = sx, newV = vy)
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val sx = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            val vy = 1f - (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                            updateFromHsv(newS = sx, newV = vy)
                        }
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(LauncherColorTokens.White, pureHueColor),
                        ),
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(LauncherColorTokens.Transparent, LauncherColorTokens.Black),
                        ),
                    )
                    val cx = saturation * size.width
                    val cy = (1f - brightness) * size.height
                    drawCircle(
                        color = colors.black.copy(alpha = 0.55f),
                        radius = 8.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(cx, cy),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                    )
                    drawCircle(
                        color = colors.white,
                        radius = 6.5.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(cx, cy),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                    )
                }
            }

            // Right Controls: Hue bar + Hex input
            Column(
                modifier = Modifier.width(196.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.dialog_color_picker_hue),
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )

                val hueShape = RoundedCornerShape(10.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .clip(hueShape)
                        .border(1.dp, colors.borderButton, hueShape)
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val h = (offset.x / size.width.toFloat()).coerceIn(0f, 1f) * 360f
                                updateFromHsv(newH = h)
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val h = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f) * 360f
                                updateFromHsv(newH = h)
                            }
                        },
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(brush = Brush.horizontalGradient(LauncherColorTokens.HueSpectrum))
                        val thumbX = (hue / 360f).coerceIn(0f, 1f) * size.width
                        drawCircle(
                            color = colors.black.copy(alpha = 0.50f),
                            radius = 7.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(thumbX, size.height * 0.5f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                        )
                        drawCircle(
                            color = colors.white,
                            radius = 5.5.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(thumbX, size.height * 0.5f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                        )
                    }
                }

                // Hex input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.dialog_color_picker_hex_label),
                        color = colors.textDim,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    )
                    val inputShape = RoundedCornerShape(9.dp)
                    BasicTextField(
                        value = hexInput,
                        onValueChange = { next ->
                            val filtered = next.uppercase().filter { it == '#' || it in '0'..'9' || it in 'A'..'F' }.take(9)
                            hexInput = filtered
                            parseHexColorOrNull(filtered)?.let { parsed ->
                                val hsv = colorToHsv(parsed)
                                if (hsv[1] > 0.01f && hsv[2] > 0.01f) {
                                    hue = hsv[0]
                                }
                                saturation = hsv[1]
                                brightness = hsv[2]
                                onPreviewColorChange(parsed)
                            }
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = colors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                            },
                        ),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged {
                                isHexFocused = it.isFocused
                                onFocusChange(it.isFocused)
                            }
                            .clip(inputShape)
                            .background(hexBgColor)
                            .border(1.dp, hexBorderColor, inputShape)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
        }

        // Bottom actions: Cancel (left) + Apply (right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val btnShape = RoundedCornerShape(10.dp)
            Box(
                modifier = Modifier
                    .smoothPressClickable(
                        pressScale = 0.94f,
                        onClick = {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                            onCancel()
                        },
                    )
                    .clip(btnShape)
                    .background(colors.surfaceButtonSubtle)
                    .border(1.dp, colors.borderButton, btnShape)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }

            Box(
                modifier = Modifier
                    .smoothPressClickable(
                        pressScale = 0.94f,
                        onClick = {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                            onApply(selectedColor)
                        },
                    )
                    .clip(btnShape)
                    .background(colors.surfaceButtonSelected)
                    .border(1.dp, colors.borderSelectedCard, btnShape)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.action_apply),
                    color = colors.accent,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

/**
 * Multi-step dialog content for "Add Instance":
 * - Step 1: Select Instance Type `[ ({instance} Vanilla)   ({iconfabric} Fabric)   (Other (Not yet)) ]`
 * - Step 2: Select Minecraft Version (bigger dialog, Back button on left, Next on right)
 * - Step 3: Select Fabric Loader Version (if Fabric; bigger dialog, Back button on left, Next on right)
 * - Step 4: Enter Instance Name (Back button on left, Install button on right)
 */
@Composable
private fun AddInstanceDialogContent(
    onFocusChange: (Boolean) -> Unit = {},
    onCancel: () -> Unit,
    existingInstanceNames: Set<String> = emptySet(),
    onInstall: (loaderType: InstanceLoaderType, mcVersion: String, loaderVersion: String?, instanceName: String) -> Unit,
) {
    val colors = LauncherTheme.colors
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var currentStep by remember { mutableStateOf<AddInstanceStep>(AddInstanceStep.SelectType) }
    var selectedFilter by remember { mutableStateOf(MinecraftVersionFilter.RELEASE) }
    var selectedMcVersion by remember { mutableStateOf("") }
    var selectedLoaderVersion by remember { mutableStateOf("") }
    var instanceNameInput by remember { mutableStateOf("") }

    // Official Mojang manifest & Fabric supported game versions state
    var versionManifest by remember { mutableStateOf<MinecraftVersionManifest?>(null) }
    var fabricGameVersions by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoadingVersions by remember { mutableStateOf(false) }
    var versionsError by remember { mutableStateOf<String?>(null) }
    var manifestReloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(manifestReloadKey) {
        isLoadingVersions = true
        versionsError = null
        val manifestResult = OfficialMinecraftApi.fetchVersionManifest(forceRefresh = manifestReloadKey > 0)
        val fabricGamesResult = OfficialMinecraftApi.fetchFabricGameVersions(forceRefresh = manifestReloadKey > 0)
        manifestResult.onSuccess { manifest ->
            versionManifest = manifest
        }.onFailure { err ->
            versionsError = err.message ?: context.getString(R.string.error_loading_versions)
        }
        fabricGamesResult.onSuccess { supported ->
            fabricGameVersions = supported
        }
        isLoadingVersions = false
    }

    // Fabric Loader versions state for the chosen Minecraft version
    val activeLoaderStepMcVersion = (currentStep as? AddInstanceStep.SelectLoaderVersion)?.mcVersion
    var fabricLoaders by remember { mutableStateOf<List<FabricLoaderVersionEntry>>(emptyList()) }
    var isLoadingLoaders by remember { mutableStateOf(false) }
    var loadersError by remember { mutableStateOf<String?>(null) }
    var loaderReloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(activeLoaderStepMcVersion, loaderReloadKey) {
        val mcVer = activeLoaderStepMcVersion ?: return@LaunchedEffect
        isLoadingLoaders = true
        loadersError = null
        OfficialMinecraftApi.fetchFabricLoaders(mcVer)
            .onSuccess { list ->
                fabricLoaders = list
                if (list.isNotEmpty() && list.none { it.version == selectedLoaderVersion }) {
                    selectedLoaderVersion = list.firstOrNull { it.stable }?.version ?: list.first().version
                }
            }
            .onFailure { err ->
                loadersError = err.message ?: context.getString(R.string.error_loading_versions)
            }
        isLoadingLoaders = false
    }

    // Smoothly morph dialog width so version selection is much bigger and roomier
    val modalWidth by animateDpAsState(
        targetValue = when (currentStep) {
            is AddInstanceStep.SelectMinecraftVersion,
            is AddInstanceStep.SelectLoaderVersion -> 520.dp
            is AddInstanceStep.SelectType -> 420.dp
            is AddInstanceStep.EnterInstanceName -> 380.dp
        },
        animationSpec = spring(
            dampingRatio = 0.80f,
            stiffness = 360f,
        ),
        label = "addInstanceModalWidth",
    )

    val modalShape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .width(modalWidth)
            .clip(modalShape)
            .background(colors.dialogSurface)
            .border(1.dp, colors.borderModal, modalShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                },
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                val forward = targetState.stepIndex >= initialState.stepIndex
                (fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(
                        animationSpec = spring(dampingRatio = 0.80f, stiffness = 380f),
                        initialOffsetX = { fullWidth -> if (forward) fullWidth / 3 else -fullWidth / 3 },
                    ) +
                    scaleIn(initialScale = 0.94f, animationSpec = tween(220)))
                    .togetherWith(
                        fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing)) +
                            slideOutHorizontally(
                                animationSpec = tween(170, easing = FastOutSlowInEasing),
                                targetOffsetX = { fullWidth -> if (forward) -fullWidth / 3 else fullWidth / 3 },
                            ) +
                            scaleOut(targetScale = 0.94f, animationSpec = tween(150)),
                    )
                    .using(SizeTransform(clip = false))
            },
            label = "addInstanceStepAnimatedContent",
        ) { step ->
            when (step) {
                is AddInstanceStep.SelectType -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.select_instance_type),
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )

                        // Row of [{instance} Vanilla]  [{iconfabric} Fabric]  [Other (Not yet)]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            InstanceLoaderType.entries.forEach { loaderType ->
                                val title = stringResource(loaderType.titleRes)
                                val cardShape = RoundedCornerShape(14.dp)
                                val enabled = loaderType.enabled
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .smoothPressClickable(
                                            enabled = enabled,
                                            pressScale = 0.94f,
                                            onClick = {
                                                currentStep = AddInstanceStep.SelectMinecraftVersion(loaderType)
                                            },
                                        )
                                        .clip(cardShape)
                                        .background(
                                            if (enabled) colors.surfaceButton
                                            else colors.surfaceCardMuted,
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (enabled) colors.borderButtonStrong
                                            else colors.borderSubtle,
                                            shape = cardShape,
                                        )
                                        .padding(vertical = 18.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    InstanceLoaderIcon(
                                        loaderType = loaderType,
                                        contentDescription = title,
                                        defaultTint = if (enabled) colors.iconPrimary else colors.iconDisabled,
                                        modifier = Modifier.size(30.dp),
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = title,
                                        color = if (enabled) colors.textPrimary else colors.textDisabled,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontSize = 13.sp,
                                            fontWeight = if (enabled) FontWeight.Bold else FontWeight.Medium,
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val btnShape = RoundedCornerShape(10.dp)
                            Box(
                                modifier = Modifier
                                    .smoothPressClickable(pressScale = 0.94f, onClick = onCancel)
                                    .clip(btnShape)
                                    .background(colors.surfaceButtonSubtle)
                                    .border(1.dp, colors.borderButton, btnShape)
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.action_cancel),
                                    color = colors.textMuted,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                )
                            }
                        }
                    }
                }

                is AddInstanceStep.SelectMinecraftVersion -> {
                    val loaderTitle = stringResource(step.loaderType.titleRes)
                    val filteredVersions = remember(
                        versionManifest,
                        selectedFilter,
                        step.loaderType,
                        fabricGameVersions,
                    ) {
                        val all = versionManifest?.versions.orEmpty()
                        all.filter { entry ->
                            val matchesType = entry.matchesFilter(selectedFilter)
                            val matchesLoader = if (step.loaderType == InstanceLoaderType.FABRIC && fabricGameVersions.isNotEmpty()) {
                                entry.id in fabricGameVersions
                            } else {
                                true
                            }
                            matchesType && matchesLoader
                        }
                    }

                    LaunchedEffect(filteredVersions) {
                        if (filteredVersions.isEmpty()) {
                            selectedMcVersion = ""
                        } else if (filteredVersions.none { it.id == selectedMcVersion }) {
                            selectedMcVersion = filteredVersions.first().id
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.select_minecraft_version),
                                color = colors.textPrimary,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surfaceButton)
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                InstanceLoaderIcon(
                                    loaderType = step.loaderType,
                                    contentDescription = loaderTitle,
                                    defaultTint = colors.accent,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = loaderTitle,
                                    color = colors.accent,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }
                        }

                        // Version Type Filter Chips: Release | Snapshot | Beta / Alpha
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val filterTabs = listOf(
                                MinecraftVersionFilter.RELEASE to R.string.version_filter_release,
                                MinecraftVersionFilter.SNAPSHOT to R.string.version_filter_snapshot,
                                MinecraftVersionFilter.OLD to R.string.version_filter_beta_alpha,
                            )
                            filterTabs.forEach { (filter, labelRes) ->
                                val isFilterSelected = selectedFilter == filter
                                val chipShape = RoundedCornerShape(10.dp)
                                val chipText by animateColorAsState(
                                    targetValue = if (isFilterSelected) colors.accent else colors.textSubtle,
                                    animationSpec = tween(180),
                                    label = "filterChipText",
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .smoothPressClickable(
                                            pressScale = 0.95f,
                                            onClick = { selectedFilter = filter },
                                        )
                                        .clip(chipShape)
                                        .background(colors.surfaceCard)
                                        .border(1.dp, colors.borderDefault, chipShape)
                                        .padding(vertical = 7.dp, horizontal = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(labelRes),
                                        color = chipText,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontSize = 12.sp,
                                            fontWeight = if (isFilterSelected) FontWeight.Bold else FontWeight.Medium,
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(215.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                isLoadingVersions && versionManifest == null -> {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        CircularProgressIndicator(
                                            color = colors.accent,
                                            strokeWidth = 2.5.dp,
                                            modifier = Modifier.size(28.dp),
                                        )
                                        Text(
                                            text = stringResource(R.string.loading_versions),
                                            color = colors.textSubtle,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                        )
                                    }
                                }
                                versionsError != null && versionManifest == null -> {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    ) {
                                        Text(
                                            text = versionsError ?: stringResource(R.string.error_loading_versions),
                                            color = colors.errorText,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        val retryShape = RoundedCornerShape(10.dp)
                                        Box(
                                            modifier = Modifier
                                                .smoothPressClickable(
                                                    pressScale = 0.94f,
                                                    onClick = { manifestReloadKey++ },
                                                )
                                                .clip(retryShape)
                                                .background(colors.surfaceBadge)
                                                .border(1.dp, colors.borderHighlightTop, retryShape)
                                                .padding(horizontal = 14.dp, vertical = 7.dp),
                                        ) {
                                            Text(
                                                text = stringResource(R.string.action_retry),
                                                color = colors.accent,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                ),
                                            )
                                        }
                                    }
                                }
                                filteredVersions.isEmpty() -> {
                                    Text(
                                        text = stringResource(R.string.no_versions_for_filter),
                                        color = colors.textDim,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                    )
                                }
                                else -> {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(
                                            items = filteredVersions,
                                            key = { it.id },
                                        ) { entry ->
                                            val version = entry.id
                                            val isSelected = version == selectedMcVersion
                                            val releaseDateLabel = remember(entry.releaseTime) {
                                                OfficialMinecraftApi.formatReleaseDate(entry.releaseTime)
                                            }
                                            VersionSelectionRow(
                                                label = version,
                                                releaseDate = releaseDateLabel,
                                                isSelected = isSelected,
                                                onClick = {
                                                    selectedMcVersion = version
                                                    if (step.loaderType == InstanceLoaderType.FABRIC) {
                                                        currentStep = AddInstanceStep.SelectLoaderVersion(
                                                            loaderType = step.loaderType,
                                                            mcVersion = version,
                                                        )
                                                    } else {
                                                        instanceNameInput = context.getString(R.string.instance_vanilla_format, version)
                                                        currentStep = AddInstanceStep.EnterInstanceName(
                                                            loaderType = step.loaderType,
                                                            mcVersion = version,
                                                            loaderVersion = null,
                                                        )
                                                    }
                                                },
                                                leadingIcon = { tint ->
                                                    Icon(
                                                        painter = painterResource(id = R.drawable.ic_instances),
                                                        contentDescription = version,
                                                        tint = tint,
                                                        modifier = Modifier.size(20.dp),
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Left: Back button; Right: Next button
                        val canProceed = selectedMcVersion.isNotEmpty()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val btnShape = RoundedCornerShape(10.dp)
                            Box(
                                modifier = Modifier
                                    .smoothPressClickable(
                                        pressScale = 0.94f,
                                        onClick = { currentStep = AddInstanceStep.SelectType },
                                    )
                                    .clip(btnShape)
                                    .background(colors.surfaceButtonSubtle)
                                    .border(1.dp, colors.borderButton, btnShape)
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.action_back),
                                    color = colors.textMuted,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .smoothPressClickable(
                                        enabled = canProceed,
                                        pressScale = 0.94f,
                                        onClick = {
                                            if (step.loaderType == InstanceLoaderType.FABRIC) {
                                                currentStep = AddInstanceStep.SelectLoaderVersion(
                                                    loaderType = step.loaderType,
                                                    mcVersion = selectedMcVersion,
                                                )
                                            } else {
                                                instanceNameInput = context.getString(R.string.instance_vanilla_format, selectedMcVersion)
                                                currentStep = AddInstanceStep.EnterInstanceName(
                                                    loaderType = step.loaderType,
                                                    mcVersion = selectedMcVersion,
                                                    loaderVersion = null,
                                                )
                                            }
                                        },
                                    )
                                    .clip(btnShape)
                                    .background(if (canProceed) colors.surfaceButtonSelected else colors.surfaceButtonDisabled)
                                    .border(
                                        1.dp,
                                        if (canProceed) colors.borderSelectedCard else colors.borderSubtle,
                                        btnShape,
                                    )
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.action_next),
                                    color = if (canProceed) colors.accent else colors.textActionDisabled,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }
                        }
                    }
                }

                is AddInstanceStep.SelectLoaderVersion -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.select_loader_version),
                                color = colors.textPrimary,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surfaceButton)
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                InstanceLoaderIcon(
                                    loaderType = InstanceLoaderType.FABRIC,
                                    contentDescription = step.mcVersion,
                                    defaultTint = colors.accent,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = step.mcVersion,
                                    color = colors.accent,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(235.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                isLoadingLoaders -> {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        CircularProgressIndicator(
                                            color = colors.accent,
                                            strokeWidth = 2.5.dp,
                                            modifier = Modifier.size(28.dp),
                                        )
                                        Text(
                                            text = stringResource(R.string.loading_fabric_loaders),
                                            color = colors.white.copy(alpha = 0.80f),
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                        )
                                    }
                                }
                                loadersError != null && fabricLoaders.isEmpty() -> {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    ) {
                                        Text(
                                            text = loadersError ?: stringResource(R.string.error_loading_versions),
                                            color = colors.errorText,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        val retryShape = RoundedCornerShape(10.dp)
                                        Box(
                                            modifier = Modifier
                                                .smoothPressClickable(
                                                    pressScale = 0.94f,
                                                    onClick = { loaderReloadKey++ },
                                                )
                                                .clip(retryShape)
                                                .background(colors.surfaceBadge)
                                                .border(1.dp, colors.borderHighlightTop, retryShape)
                                                .padding(horizontal = 14.dp, vertical = 7.dp),
                                        ) {
                                            Text(
                                                text = stringResource(R.string.action_retry),
                                                color = colors.accent,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                ),
                                            )
                                        }
                                    }
                                }
                                fabricLoaders.isEmpty() -> {
                                    Text(
                                        text = stringResource(R.string.no_versions_for_filter),
                                        color = colors.white.copy(alpha = 0.70f),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                    )
                                }
                                else -> {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(
                                            items = fabricLoaders,
                                            key = { it.version },
                                        ) { loaderEntry ->
                                            val loaderVer = loaderEntry.version
                                            val isSelected = loaderVer == selectedLoaderVersion
                                            val releaseDateLabel = remember(loaderEntry.releaseTime) {
                                                OfficialMinecraftApi.formatReleaseDate(loaderEntry.releaseTime)
                                            }
                                            VersionSelectionRow(
                                                label = loaderVer,
                                                releaseDate = releaseDateLabel,
                                                isSelected = isSelected,
                                                onClick = {
                                                    selectedLoaderVersion = loaderVer
                                                    instanceNameInput = context.getString(R.string.instance_fabric_short_format, step.mcVersion)
                                                    currentStep = AddInstanceStep.EnterInstanceName(
                                                        loaderType = step.loaderType,
                                                        mcVersion = step.mcVersion,
                                                        loaderVersion = loaderVer,
                                                    )
                                                },
                                                leadingIcon = { tint ->
                                                    InstanceLoaderIcon(
                                                        loaderType = InstanceLoaderType.FABRIC,
                                                        contentDescription = loaderVer,
                                                        defaultTint = tint,
                                                        modifier = Modifier.size(20.dp),
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Left: Back button; Right: Next button
                        val canProceedLoader = selectedLoaderVersion.isNotEmpty()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val btnShape = RoundedCornerShape(10.dp)
                            Box(
                                modifier = Modifier
                                    .smoothPressClickable(
                                        pressScale = 0.94f,
                                        onClick = {
                                            currentStep = AddInstanceStep.SelectMinecraftVersion(step.loaderType)
                                        },
                                    )
                                    .clip(btnShape)
                                    .background(colors.surfaceButtonSubtle)
                                    .border(1.dp, colors.borderButton, btnShape)
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.action_back),
                                    color = colors.textMuted,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .smoothPressClickable(
                                        enabled = canProceedLoader,
                                        pressScale = 0.94f,
                                        onClick = {
                                            instanceNameInput = context.getString(R.string.instance_fabric_short_format, step.mcVersion)
                                            currentStep = AddInstanceStep.EnterInstanceName(
                                                loaderType = step.loaderType,
                                                mcVersion = step.mcVersion,
                                                loaderVersion = selectedLoaderVersion,
                                            )
                                        },
                                    )
                                    .clip(btnShape)
                                    .background(if (canProceedLoader) colors.surfaceButtonSelected else colors.surfaceButtonDisabled)
                                    .border(
                                        1.dp,
                                        if (canProceedLoader) colors.borderSelectedCard else colors.borderSubtle,
                                        btnShape,
                                    )
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.action_next),
                                    color = if (canProceedLoader) colors.accent else colors.textActionDisabled,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }
                        }
                    }
                }

                is AddInstanceStep.EnterInstanceName -> {
                    val badgeText = if (step.loaderType == InstanceLoaderType.FABRIC && step.loaderVersion != null) {
                        context.getString(R.string.instance_fabric_format, step.mcVersion, step.loaderVersion)
                    } else {
                        step.mcVersion
                    }
                    // Install stays disabled while the name is empty; typed names must also be unique.
                    val trimmedInstanceName = instanceNameInput.trim()
                    val isDuplicateName = trimmedInstanceName.isNotEmpty() &&
                        existingInstanceNames.any { it.equals(trimmedInstanceName, ignoreCase = true) }
                    val canInstall = trimmedInstanceName.isNotEmpty() && !isDuplicateName

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.enter_instance_name),
                                color = colors.textPrimary,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surfaceButton)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                InstanceLoaderIcon(
                                    loaderType = step.loaderType,
                                    contentDescription = badgeText,
                                    defaultTint = colors.accent,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = badgeText,
                                    color = colors.accent,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }
                        }

                        LauncherFloatingLabelInput(
                            value = instanceNameInput,
                            onValueChange = { instanceNameInput = it },
                            label = stringResource(R.string.label_instance_name),
                            onFocusChange = onFocusChange,
                            onDone = {
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                                onFocusChange(false)
                            },
                        )

                        if (isDuplicateName) {
                            Text(
                                text = stringResource(R.string.instance_name_duplicate),
                                color = colors.errorText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                        }

                        // Left: Back button; Right: Install button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val btnShape = RoundedCornerShape(10.dp)
                            Box(
                                modifier = Modifier
                                    .smoothPressClickable(
                                        pressScale = 0.94f,
                                        onClick = {
                                            focusManager.clearFocus(force = true)
                                            keyboardController?.hide()
                                            onFocusChange(false)
                                            currentStep = if (step.loaderType == InstanceLoaderType.FABRIC) {
                                                AddInstanceStep.SelectLoaderVersion(step.loaderType, step.mcVersion)
                                            } else {
                                                AddInstanceStep.SelectMinecraftVersion(step.loaderType)
                                            }
                                        },
                                    )
                                    .clip(btnShape)
                                    .background(colors.surfaceButtonSubtle)
                                    .border(1.dp, colors.borderButton, btnShape)
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.action_back),
                                    color = colors.textMuted,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .smoothPressClickable(
                                        enabled = canInstall,
                                        pressScale = 0.94f,
                                        onClick = {
                                            if (!canInstall) return@smoothPressClickable
                                            focusManager.clearFocus(force = true)
                                            keyboardController?.hide()
                                            onFocusChange(false)
                                            onInstall(
                                                step.loaderType,
                                                step.mcVersion,
                                                step.loaderVersion,
                                                instanceNameInput,
                                            )
                                        },
                                    )
                                    .clip(btnShape)
                                    .background(
                                        if (canInstall) colors.surfaceButtonSelected else colors.surfaceButtonSubtle,
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (canInstall) colors.borderSelectedCard else colors.borderButton,
                                        shape = btnShape,
                                    )
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.action_install),
                                    color = if (canInstall) colors.accent else colors.textActionDisabled,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shared selectable version list row used by both Minecraft version and loader version pickers.
 */
@Composable
private fun VersionSelectionRow(
    label: String,
    releaseDate: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    leadingIcon: @Composable (tint: Color) -> Unit,
) {
    val colors = LauncherTheme.colors
    val rowShape = RoundedCornerShape(14.dp)
    val rowBg by animateColorAsState(
        targetValue = if (isSelected) colors.surfaceCardSelected else colors.surfaceCard,
        animationSpec = tween(180),
        label = "versionRowBg",
    )
    val rowBorder by animateColorAsState(
        targetValue = if (isSelected) colors.borderSelectedCard else colors.borderSubtle,
        animationSpec = tween(180),
        label = "versionRowBorder",
    )
    val rowTextColor by animateColorAsState(
        targetValue = if (isSelected) colors.accent else colors.textPrimary,
        animationSpec = tween(180),
        label = "versionRowText",
    )
    val dateTextColor by animateColorAsState(
        targetValue = if (isSelected) colors.accent else colors.textSubtle,
        animationSpec = tween(180),
        label = "versionDateText",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .smoothPressClickable(
                pressScale = 0.97f,
                onClick = onClick,
            )
            .clip(rowShape)
            .background(rowBg)
            .border(1.dp, rowBorder, rowShape)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingIcon(rowTextColor)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                color = rowTextColor,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (releaseDate.isNotEmpty()) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = releaseDate,
                color = dateTextColor,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }
    }
}

/**
 * Dynamic Island-style install progress indicator named [ProgressBar].
 * - Compact pill at the top-center of the screen showing live progress percentage & bar.
 * - Tap it to smoothly morph/expand into a detailed view showing what files it is currently downloading!
 */
@Composable
fun ProgressBar(
    task: InstanceInstallTask,
    snapshot: InstallProgressSnapshot?,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    screenWidth: Dp,
    screenHeight: Dp,
    backgroundConfig: BackgroundConfig,
    sharedTimeSeconds: State<Float>,
    modifier: Modifier = Modifier,
    onCancelTask: () -> Unit = {},
    sceneGraphicsLayer: GraphicsLayer? = null,
    blurredBackdropLayer: GraphicsLayer? = null,
    backdropRegionLeftPx: Float = 0f,
    backdropRegionTopPx: Float = 0f,
    topInset: Dp = 6.dp,
) {
    val rawProgress = (snapshot?.progress ?: 0f).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing),
        label = "dynamicIslandProgress",
    )
    val percentInt = (animatedProgress * 100f).toInt().coerceIn(0, 100)
    val isComplete = snapshot?.isComplete == true || rawProgress >= 0.999f
    val hasError = snapshot?.errorMessage != null

    val expandedHeight = minOf(
        PROGRESS_BAR_EXPANDED_HEIGHT,
        (screenHeight - 16.dp).coerceAtLeast(180.dp),
    )
    val morphSpec = spring<Dp>(
        dampingRatio = 0.78f,
        stiffness = 360f,
    )
    val islandWidth by animateDpAsState(
        targetValue = if (expanded) 410.dp else 290.dp,
        animationSpec = morphSpec,
        label = "islandWidth",
    )
    val islandHeight by animateDpAsState(
        targetValue = if (expanded) expandedHeight else 38.dp,
        animationSpec = morphSpec,
        label = "islandHeight",
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (expanded) 22.dp else 19.dp,
        animationSpec = morphSpec,
        label = "islandCornerRadius",
    )
    val islandShape = RoundedCornerShape(cornerRadius)

    val files = snapshot?.files.orEmpty()
    val currentFileName = snapshot?.currentFileName
        ?: "versions/${task.mcVersion}/${task.mcVersion}.json"
    val totalMb = (snapshot?.totalMb ?: 0f).coerceAtLeast(0.01f)
    val downloadedMb = (snapshot?.downloadedMb ?: 0f).coerceAtMost(totalMb)
    val speedMbPerSec = (snapshot?.speedMbPerSec ?: 0f).coerceAtLeast(0f)

    val statusTitle = when {
        hasError -> stringResource(R.string.install_failed_format, snapshot?.errorMessage.orEmpty())
        !task.customStatusTitle.isNullOrBlank() -> task.customStatusTitle
        isComplete -> stringResource(R.string.install_complete_format, task.instanceName)
        else -> stringResource(R.string.installing_instance_format, task.instanceName)
    }

    val colors = LauncherTheme.colors
    val statusAccentColor = if (hasError) colors.error else colors.accent
    val progressGradient = if (hasError) {
        listOf(colors.errorStrong, colors.error)
    } else {
        listOf(colors.accentSecondary, colors.accent)
    }

    Box(
        modifier = modifier
            .width(islandWidth)
            .height(islandHeight)
            .smoothPressClickable(
                pressScale = 0.96f,
                onClick = onToggleExpand,
            )
            .clip(islandShape),
    ) {
        // 1. Real-time Gaussian Blurred Backdrop Layer (draws the synchronously blurred
        // top-center capture of live underlying panels: Account, Instances, Settings, etc.)
        if (blurredBackdropLayer != null) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val pillLeftPx = (screenWidth.toPx() - size.width) * 0.5f
                val pillTopPx = topInset.toPx()
                translate(
                    left = backdropRegionLeftPx - pillLeftPx,
                    top = backdropRegionTopPx - pillTopPx,
                ) {
                    drawLayer(blurredBackdropLayer)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .layout { measurable, constraints ->
                        val wPx = screenWidth.roundToPx().coerceAtLeast(constraints.maxWidth)
                        val hPx = screenHeight.roundToPx().coerceAtLeast(constraints.maxHeight)
                        val offsetX = -((wPx - constraints.maxWidth) / 2)
                        val offsetY = -topInset.roundToPx()
                        val placeable = measurable.measure(Constraints.fixed(wPx, hPx))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.place(offsetX, offsetY)
                        }
                    }
                    .blur(
                        radius = 10.dp,
                        edgeTreatment = BlurredEdgeTreatment.Rectangle,
                    ),
            ) {
                LauncherBackground(
                    modifier = Modifier.fillMaxSize(),
                    config = backgroundConfig,
                    sharedTimeSeconds = sharedTimeSeconds,
                )

                if (sceneGraphicsLayer != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                drawLayer(sceneGraphicsLayer)
                            },
                    )
                }
            }
        }

        // Frosted glass tint kept outside BlurEffect so underlying panel cards stay legible;
        // stronger fallback opacity on API < 31 where RenderEffect blur is unavailable.
        val glassTintBrush = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Brush.verticalGradient(
                0.0f to colors.progressBackdropTop,
                0.5f to colors.progressBackdropMid,
                1.0f to colors.progressBackdropBottom,
            )
        } else {
            Brush.verticalGradient(
                0.0f to colors.progressBackdropLegacyTop,
                0.5f to colors.progressBackdropLegacyMid,
                1.0f to colors.progressBackdropLegacyBottom,
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(glassTintBrush),
        )

        // 2. Frosted glass outer border
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        0.0f to statusAccentColor.copy(alpha = 0.48f),
                        0.5f to colors.borderButton,
                        1.0f to colors.white.copy(alpha = 0.30f),
                    ),
                    shape = islandShape,
                ),
        )

        // 3. Foreground Dynamic Island Content
        Crossfade(
            targetState = expanded,
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
            label = "dynamicIslandProgressBarCrossfade",
        ) { isExpanded ->
            if (!isExpanded) {
                // Compact Dynamic Island Pill
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (task.customIconRes != null) {
                            Image(
                                painter = painterResource(id = task.customIconRes),
                                contentDescription = statusTitle,
                                modifier = Modifier.size(16.dp),
                            )
                        } else {
                            InstanceLoaderIcon(
                                loaderType = task.loaderType,
                                contentDescription = statusTitle,
                                defaultTint = statusAccentColor,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusTitle,
                            color = if (hasError) colors.errorText else colors.textPrimary,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(44.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(colors.surfaceBadge),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(animatedProgress)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Brush.horizontalGradient(progressGradient)),
                            )
                        }
                        Text(
                            text = stringResource(R.string.percent_format, percentInt),
                            color = statusAccentColor,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
            } else {
                // Expanded Dynamic Island Details View: shows real-time file/stage downloads
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Header row: Icon + Installing Title + Percentage
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (task.customIconRes != null) {
                                Image(
                                    painter = painterResource(id = task.customIconRes),
                                    contentDescription = statusTitle,
                                    modifier = Modifier.size(18.dp),
                                )
                            } else {
                                InstanceLoaderIcon(
                                    loaderType = task.loaderType,
                                    contentDescription = statusTitle,
                                    defaultTint = statusAccentColor,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = statusTitle,
                                color = if (hasError) colors.errorText else colors.textPrimary,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = stringResource(R.string.percent_format, percentInt),
                            color = statusAccentColor,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                            ),
                        )
                    }

                    // Main Progress Track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(colors.white.copy(alpha = 0.15f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedProgress)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Brush.horizontalGradient(progressGradient)),
                        )
                    }

                    // Current active file & transfer rate row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val statusText = if (task.isInstanceInstall) {
                            stringResource(R.string.downloading_item_format, currentFileName)
                        } else {
                            currentFileName
                        }
                        Text(
                            text = statusText,
                            color = colors.textBody,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (task.isInstanceInstall && totalMb > 0f) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(
                                    R.string.download_speed_format,
                                    String.format(Locale.US, "%.1f", downloadedMb),
                                    String.format(Locale.US, "%.1f", totalMb),
                                    String.format(Locale.US, "%.1f", speedMbPerSec),
                                ),
                                color = statusAccentColor.copy(alpha = 0.90f),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                        }
                    }

                    // Detailed per-stage download list
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        files.forEach { item ->
                            val itemDone = isComplete || item.status == DownloadStageStatus.DONE
                            val itemActive = !isComplete && item.status == DownloadStageStatus.DOWNLOADING
                            val itemError = item.status == DownloadStageStatus.ERROR
                            val itemPercent = (item.progress * 100f).toInt().coerceIn(0, 100)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            itemError -> colors.errorSubtleContainer
                                            itemActive -> colors.surfaceButton
                                            else -> colors.surfaceCardMuted
                                        },
                                    )
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = item.fileName,
                                    color = when {
                                        itemError -> colors.errorText
                                        itemActive -> colors.accent
                                        itemDone -> colors.textMuted
                                        else -> colors.white.copy(alpha = 0.45f)
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = if (itemActive) FontWeight.Bold else FontWeight.Medium,
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when {
                                        itemError -> stringResource(R.string.download_status_error)
                                        itemDone -> stringResource(R.string.download_status_done)
                                        itemActive -> stringResource(R.string.percent_format, itemPercent)
                                        else -> stringResource(R.string.download_status_waiting)
                                    },
                                    color = when {
                                        itemError -> colors.error
                                        itemDone -> colors.success
                                        itemActive -> colors.accent
                                        else -> colors.textDisabled
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }
                        }
                    }

                    // Bottom action: Cancel active download / preparation (or dismiss error)
                    val canCancelTask = !isComplete || hasError
                    val cancelShape = RoundedCornerShape(10.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .smoothPressClickable(
                                    enabled = canCancelTask,
                                    pressScale = 0.94f,
                                    onClick = onCancelTask,
                                )
                                .clip(cancelShape)
                                .background(
                                    if (canCancelTask) colors.errorCancelContainer else colors.surfaceButtonDisabled,
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (canCancelTask) colors.errorBorder else colors.borderSubtle,
                                    shape = cancelShape,
                                )
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.action_cancel),
                                color = if (canCancelTask) colors.errorText else colors.textActionDisabled,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shared frosted glass backdrop surface used identically across [UnifiedLeftRailAndPanel] and
 * [RightPlayAndSkinPanel] (Change button, Version panel, and Play button). Draws the single
 * shared fullscreen [blurredBackgroundLayer] translated into this panel's root coordinates so
 * animations, GIFs, and Video backgrounds stay 100% blurred and in sync without spawning duplicate
 * media decoders.
 */
@Composable
private fun BoxScope.FrostedGlassBackdrop(
    blurredBackgroundLayer: GraphicsLayer?,
    backgroundRootOffset: Offset,
    backgroundFrameTick: () -> Int,
    sharedTimeSeconds: State<Float>,
    backgroundConfig: BackgroundConfig,
    screenWidth: Dp,
    screenHeight: Dp,
    fallbackOffsetX: (Int) -> Int,
    fallbackOffsetY: (Int) -> Int,
) {
    val colors = LauncherTheme.colors
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var panelRootOffset by remember { mutableStateOf(Offset.Zero) }

    if (blurredBackgroundLayer != null) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .clipToBounds()
                .onGloballyPositioned { coords ->
                    if (coords.isAttached) {
                        val pos = coords.positionInRoot()
                        if (pos != panelRootOffset) {
                            panelRootOffset = pos
                        }
                    }
                },
        ) {
            @Suppress("UNUSED_EXPRESSION")
            sharedTimeSeconds.value
            @Suppress("UNUSED_EXPRESSION")
            backgroundFrameTick()
            translate(
                left = backgroundRootOffset.x - panelRootOffset.x,
                top = backgroundRootOffset.y - panelRootOffset.y,
            ) {
                drawLayer(blurredBackgroundLayer)
            }
        }
    } else {
        Box(
            modifier = Modifier
                .matchParentSize()
                .layout { measurable, constraints ->
                    val wPx = screenWidth.roundToPx().coerceAtLeast(constraints.maxWidth)
                    val hPx = screenHeight.roundToPx().coerceAtLeast(constraints.maxHeight)
                    val offsetX = fallbackOffsetX(constraints.maxWidth)
                    val offsetY = fallbackOffsetY(constraints.maxHeight)
                    val placeable = measurable.measure(Constraints.fixed(wPx, hPx))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(offsetX, offsetY)
                    }
                }
                .blur(
                    radius = 20.dp,
                    edgeTreatment = BlurredEdgeTreatment.Rectangle,
                ),
        ) {
            LauncherBackground(
                modifier = Modifier.fillMaxSize(),
                config = backgroundConfig,
                sharedTimeSeconds = sharedTimeSeconds,
            )
        }
    }

    Box(
        modifier = Modifier
            .matchParentSize()
            .background(
                Brush.verticalGradient(
                    *colors.glassBackdropStopsFor(supportsBlur = supportsBlur).toTypedArray(),
                ),
            ),
    )
}

/**
 * Unified frosted-glass container that houses BOTH the left Navigation Rail Bar (`84.dp`)
 * and the expandable Destination Panel (`Instances`, `Account`, `Settings`) inside the same `Box`,
 * separated by a vertical divider line (`1.dp`) and animated open/closed via `AnimatedVisibility`.
 */
@Composable
fun UnifiedLeftRailAndPanel(
    selectedDestination: LauncherDestination?,
    onDestinationClick: (LauncherDestination) -> Unit,
    instances: List<LauncherInstance>,
    selectedInstanceId: String,
    onInstanceSelect: (LauncherInstance) -> Unit,
    onAddInstanceClick: () -> Unit,
    onRemoveInstance: (LauncherInstance) -> Unit,
    onReorderInstances: (List<LauncherInstance>) -> Unit = {},
    accounts: List<LauncherAccount>,
    selectedAccountId: String,
    onAccountSelect: (LauncherAccount) -> Unit,
    onAddAccountClick: () -> Unit,
    onRequestRenameAccount: (LauncherAccount) -> Unit,
    onRefreshAccount: (LauncherAccount) -> Unit = {},
    onRemoveAccount: (LauncherAccount) -> Unit,
    onReorderAccounts: (List<LauncherAccount>) -> Unit = {},
    skinRefreshKey: Int = 0,
    accentColor: Color,
    isImportingBackground: Boolean,
    backgroundImportError: String?,
    onPickBackgroundMedia: () -> Unit,
    onResetBackground: () -> Unit,
    onAccentColorChange: (Color) -> Unit,
    onOpenAccentColorPicker: () -> Unit,
    selectedRendererDescriptor: RendererDescriptor,
    availableRenderers: List<RendererDescriptor>,
    onRendererSelect: (RendererDescriptor) -> Unit,
    resolutionScale: Int,
    onResolutionScaleChange: (Int) -> Unit,
    screenWidth: Dp,
    screenHeight: Dp,
    backgroundConfig: BackgroundConfig,
    sharedTimeSeconds: State<Float>,
    modifier: Modifier = Modifier,
    blurredBackgroundLayer: GraphicsLayer? = null,
    backgroundRootOffset: Offset = Offset.Zero,
    backgroundFrameTick: () -> Int = { 0 },
    isSuspended: Boolean = false,
) {
    val colors = LauncherTheme.colors
    val outerMargin = 5.dp
    val railWidth = 84.dp
    val unifiedShape = RoundedCornerShape(20.dp)
    val panelWidth = (screenWidth - outerMargin * 2 - railWidth).coerceAtLeast(0.dp)

    // Keep track of the last active destination so the panel content stays rendered while AnimatedVisibility shrinks out
    var displayedDestination by remember { mutableStateOf(selectedDestination ?: LauncherDestination.ACCOUNT) }
    if (selectedDestination != null) {
        displayedDestination = selectedDestination
    }

    Box(
        modifier = modifier
            .padding(outerMargin)
            .fillMaxHeight()
            .clip(unifiedShape),
    ) {
        // 1. Real-time Gaussian Blurred Backdrop Layer shared across all panels
        val outerMarginPx = with(LocalDensity.current) { outerMargin.roundToPx() }
        FrostedGlassBackdrop(
            blurredBackgroundLayer = blurredBackgroundLayer,
            backgroundRootOffset = backgroundRootOffset,
            backgroundFrameTick = backgroundFrameTick,
            sharedTimeSeconds = sharedTimeSeconds,
            backgroundConfig = backgroundConfig,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            fallbackOffsetX = { -outerMarginPx },
            fallbackOffsetY = { -outerMarginPx },
        )

        // 2. Unified frosted glass outer border around the combined Rail + Panel box
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        *colors.railBorderStops.toTypedArray(),
                    ),
                    shape = unifiedShape,
                ),
        )

        // 3. Foreground Row: [Left Rail Bar] | [AnimatedVisibility(Divider Line + Destination Panel)]
        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Side: Navigation Rail Buttons (Instances, Account, Settings)
            Column(
                modifier = Modifier
                    .width(railWidth)
                    .fillMaxHeight()
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LauncherDestination.entries.forEach { destination ->
                    val isSelected = selectedDestination == destination
                    NavigationRailButton(
                        destination = destination,
                        selected = isSelected,
                        onClick = { onDestinationClick(destination) },
                    )
                }
            }

            // Right Side inside the same Box: Vertical Divider Line + Destination Panel with AnimatedVisibility
            AnimatedVisibility(
                visible = selectedDestination != null,
                enter = fadeIn(
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                ) + expandHorizontally(
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = 340f,
                    ),
                    expandFrom = Alignment.Start,
                ),
                exit = fadeOut(
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                ) + shrinkHorizontally(
                    animationSpec = spring(
                        dampingRatio = 0.88f,
                        stiffness = 380f,
                    ),
                    shrinkTowards = Alignment.Start,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .width(panelWidth)
                        .fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Vertical Divider Line between the Rail Bar side and the Panel side
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(
                                Brush.verticalGradient(
                                    *colors.railDividerStops.toTypedArray(),
                                ),
                            ),
                    )

                    // Panel Content with fancy AnimatedContent transition when switching between tabs
                    AnimatedContent(
                        targetState = displayedDestination,
                        transitionSpec = {
                            val forward = targetState.ordinal >= initialState.ordinal
                            (fadeIn(animationSpec = tween(240, easing = FastOutSlowInEasing)) +
                                slideInVertically(
                                    animationSpec = spring(dampingRatio = 0.80f, stiffness = 380f),
                                    initialOffsetY = { fullHeight -> if (forward) fullHeight / 9 else -fullHeight / 9 },
                                ) +
                                scaleIn(
                                    initialScale = 0.96f,
                                    animationSpec = spring(dampingRatio = 0.80f, stiffness = 380f),
                                ))
                                .togetherWith(
                                    fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing)) +
                                        slideOutVertically(
                                            animationSpec = tween(180, easing = FastOutSlowInEasing),
                                            targetOffsetY = { fullHeight -> if (forward) -fullHeight / 11 else fullHeight / 11 },
                                        ) +
                                        scaleOut(
                                            targetScale = 0.96f,
                                            animationSpec = tween(160, easing = FastOutSlowInEasing),
                                        ),
                                )
                                .using(SizeTransform(clip = true))
                        },
                        label = "destinationPanelContent",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) { destination ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (destination) {
                                LauncherDestination.INSTANCES -> InstancesPanelContent(
                                    instances = instances,
                                    selectedInstanceId = selectedInstanceId,
                                    onInstanceSelect = onInstanceSelect,
                                    onAddInstanceClick = onAddInstanceClick,
                                    onRemoveInstance = onRemoveInstance,
                                    onReorderInstances = onReorderInstances,
                                )
                                LauncherDestination.ACCOUNT -> AccountPanelContent(
                                    accounts = accounts,
                                    selectedAccountId = selectedAccountId,
                                    onAccountSelect = onAccountSelect,
                                    onAddAccountClick = onAddAccountClick,
                                    onRequestRenameAccount = onRequestRenameAccount,
                                    onRefreshAccount = onRefreshAccount,
                                    onRemoveAccount = onRemoveAccount,
                                    skinRefreshKey = skinRefreshKey,
                                    isSuspended = isSuspended,
                                    onReorderAccounts = onReorderAccounts,
                                )
                                LauncherDestination.SETTINGS -> SettingsPanelContent(
                                    backgroundConfig = backgroundConfig,
                                    accentColor = accentColor,
                                    isImportingBackground = isImportingBackground,
                                    backgroundImportError = backgroundImportError,
                                    onPickBackgroundMedia = onPickBackgroundMedia,
                                    onResetBackground = onResetBackground,
                                    onAccentColorChange = onAccentColorChange,
                                    onOpenAccentColorPicker = onOpenAccentColorPicker,
                                    selectedRendererDescriptor = selectedRendererDescriptor,
                                    availableRenderers = availableRenderers,
                                    onRendererSelect = onRendererSelect,
                                    resolutionScale = resolutionScale,
                                    onResolutionScaleChange = onResolutionScaleChange,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelHeaderDivider(
    modifier: Modifier = Modifier,
) {
    val colors = LauncherTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    *colors.railDividerStops.toTypedArray(),
                ),
            ),
    )
}

@Composable
private fun InstancesPanelContent(
    instances: List<LauncherInstance>,
    selectedInstanceId: String,
    onInstanceSelect: (LauncherInstance) -> Unit,
    onAddInstanceClick: () -> Unit,
    onRemoveInstance: (LauncherInstance) -> Unit,
    onReorderInstances: (List<LauncherInstance>) -> Unit = {},
) {
    val colors = LauncherTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Top-Left "Add Instance" Button
        val addBtnShape = RoundedCornerShape(14.dp)
        val addInstanceText = stringResource(R.string.add_instance)
        Row(
            modifier = Modifier
                .smoothPressClickable(
                    pressScale = 0.95f,
                    onClick = onAddInstanceClick,
                )
                .clip(addBtnShape)
                .background(colors.surfaceButton)
                .border(1.dp, colors.borderButtonStrong, addBtnShape)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_add),
                contentDescription = addInstanceText,
                tint = colors.iconPrimary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = addInstanceText,
                color = colors.textPrimary,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }

        PanelHeaderDivider()

        // Instances List below "Add Instance" button
        if (instances.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_instances),
                    contentDescription = null,
                    tint = colors.iconMuted,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.no_instances_installed),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        } else {
            val lazyListState = rememberLazyListState()
            val haptic = LocalHapticFeedback.current
            val soundPlayer = LocalLauncherSoundPlayer.current
            // Instance ID whose {•••} options menu is currently expanded
            var openOptionsInstanceId by remember { mutableStateOf<String?>(null) }
            val reorderState = rememberReorderableState(
                listState = lazyListState,
                totalItemsCount = { instances.size },
                onMove = { from, to -> onReorderInstances(instances.reorder(from, to)) },
            )

            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
            ) {
                items(
                    count = instances.size,
                    key = { index -> instances[index].id },
                ) { index ->
                    val instance = instances[index]
                    val isDragging = reorderState.isItemDragging(index, instance.id)
                    val itemModifier = if (isDragging) Modifier else Modifier.animateItem()

                    val isSelected = instance.id == selectedInstanceId
                    val isMenuOpen = openOptionsInstanceId == instance.id
                    val instanceName = instance.name
                    val subtitle = if (instance.loaderType == InstanceLoaderType.FABRIC && !instance.loaderVersion.isNullOrBlank()) {
                        stringResource(R.string.instance_subtitle_fabric, instance.mcVersion, instance.loaderVersion)
                    } else {
                        stringResource(R.string.instance_subtitle_vanilla, instance.mcVersion)
                    }
                    val itemShape = RoundedCornerShape(14.dp)
                    val itemBg by animateColorAsState(
                        targetValue = if (isSelected) colors.surfaceCardSelected else colors.surfaceCard,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        label = "panelInstanceItemBg",
                    )
                    val itemBorder by animateColorAsState(
                        targetValue = if (isSelected) colors.borderSelectedCard else colors.borderSubtle,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        label = "panelInstanceItemBorder",
                    )
                    val titleColor by animateColorAsState(
                        targetValue = if (isSelected) colors.accent else colors.textPrimary,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        label = "panelInstanceTitleColor",
                    )
                    val moreBtnBg by animateColorAsState(
                        targetValue = if (isMenuOpen) colors.surfaceCardSelected else colors.transparent,
                        animationSpec = tween(durationMillis = 180),
                        label = "instanceMoreBtnBg",
                    )
                    val moreBtnRotation by animateFloatAsState(
                        targetValue = if (isMenuOpen) 90f else 0f,
                        animationSpec = spring(
                            dampingRatio = 0.72f,
                            stiffness = 420f,
                        ),
                        label = "instanceMoreBtnRotation",
                    )

                    Box(
                        modifier = itemModifier
                            .fillMaxWidth()
                            .reorderableItem(
                                state = reorderState,
                                key = instance.id,
                                index = index,
                                enabled = instances.size > 1,
                                haptic = haptic,
                                soundPlayer = soundPlayer,
                            ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .smoothPressClickable(
                                    pressScale = 0.98f,
                                    onClick = {
                                        if (!reorderState.isDragging && !reorderState.justFinishedDrag) {
                                            openOptionsInstanceId = null
                                            onInstanceSelect(instance)
                                        }
                                    },
                                )
                                .clip(itemShape)
                                .background(if (isDragging) colors.surfaceCardSelected else itemBg)
                                .border(
                                    width = if (isDragging) 1.5.dp else 1.dp,
                                    color = if (isDragging) colors.accent else itemBorder,
                                    shape = itemShape,
                                )
                                .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                InstanceLoaderIcon(
                                    loaderType = instance.loaderType,
                                    contentDescription = instanceName,
                                    defaultTint = titleColor,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = instanceName,
                                        color = titleColor,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = subtitle,
                                        color = colors.white.copy(alpha = 0.60f),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            // Right: More options {•••} (plus smoothly animated inline Remove)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                AnimatedVisibility(
                                    visible = isMenuOpen,
                                    enter = fadeIn(
                                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                                    ) + expandHorizontally(
                                        animationSpec = spring(
                                            dampingRatio = 0.82f,
                                            stiffness = 440f,
                                        ),
                                        expandFrom = Alignment.End,
                                    ),
                                    exit = fadeOut(
                                        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
                                    ) + shrinkHorizontally(
                                        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
                                        shrinkTowards = Alignment.End,
                                    ),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        val actionShape = RoundedCornerShape(10.dp)

                                        // Remove option
                                        Box(
                                            modifier = Modifier
                                                .smoothPressClickable(
                                                    pressScale = 0.92f,
                                                    onClick = {
                                                        openOptionsInstanceId = null
                                                        onRemoveInstance(instance)
                                                    },
                                                )
                                                .clip(actionShape)
                                                .background(colors.errorCancelContainer)
                                                .border(1.dp, colors.errorBorder, actionShape)
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = stringResource(R.string.action_remove),
                                                color = colors.errorText,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                ),
                                            )
                                        }
                                    }
                                }

                                // {•••} More options button
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .smoothPressClickable(
                                            pressScale = 0.90f,
                                            onClick = {
                                                openOptionsInstanceId =
                                                    if (isMenuOpen) null else instance.id
                                            },
                                        )
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(moreBtnBg),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_more_horiz),
                                        contentDescription = stringResource(R.string.action_more_options),
                                        tint = colors.textSecondary,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .graphicsLayer { rotationZ = moreBtnRotation },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Default no-account skin: the bundled account skin desaturated to gray and converted
 * to the classic (4px arm) model. Falls back to the procedural gray classic skin.
 */
@Composable
private fun rememberNoAccountSkin(): Bitmap {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            val base = SkinUtils.normalizeSkin(SkinUtils.fromAsset(context, SKIN_ASSET))
            SkinUtils.toGrayscale(SkinUtils.toClassicModel(base))
        }
            .onFailure { Log.w("LauncherScreen", "No usable $SKIN_ASSET, using gray placeholder", it) }
            .getOrElse { DefaultSkin.createPlaceholder() }
    }
}

/**
 * Extracts the 8x8 front head face (8, 8) + 8x8 hat overlay (40, 8) from a 64x64 Minecraft skin bitmap.
 */
private fun extractSkinHeadBitmap(skinBitmap: Bitmap): ImageBitmap {
    val scale = (skinBitmap.width / 64).coerceAtLeast(1)
    val headSize = 8 * scale
    val output = Bitmap.createBitmap(headSize, headSize, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(output)
    val paint = AndroidPaint().apply {
        isAntiAlias = false
        isFilterBitmap = false
    }
    val dst = AndroidRect(0, 0, headSize, headSize)
    val faceSrc = AndroidRect(8 * scale, 8 * scale, 16 * scale, 16 * scale)
    canvas.drawBitmap(skinBitmap, faceSrc, dst, paint)

    if (skinBitmap.width >= 48 * scale && skinBitmap.height >= 16 * scale) {
        val hatSrc = AndroidRect(40 * scale, 8 * scale, 48 * scale, 16 * scale)
        canvas.drawBitmap(skinBitmap, hatSrc, dst, paint)
    }
    return output.asImageBitmap()
}

@Composable
private fun AccountPanelContent(
    accounts: List<LauncherAccount>,
    selectedAccountId: String,
    onAccountSelect: (LauncherAccount) -> Unit,
    onAddAccountClick: () -> Unit,
    onRequestRenameAccount: (LauncherAccount) -> Unit,
    onRefreshAccount: (LauncherAccount) -> Unit = {},
    onRemoveAccount: (LauncherAccount) -> Unit,
    skinRefreshKey: Int = 0,
    isSuspended: Boolean = false,
    onReorderAccounts: (List<LauncherAccount>) -> Unit = {},
) {
    val colors = LauncherTheme.colors
    val context = LocalContext.current
    val selectedAccount = remember(accounts, selectedAccountId) {
        accounts.firstOrNull { it.id == selectedAccountId } ?: accounts.firstOrNull()
    }
    val hasAccount = selectedAccount != null
    val accountSkin = remember(selectedAccount?.id, skinRefreshKey) {
        AccountSkinManager.loadSkin(context, selectedAccount)
    }
    // No account -> the account skin, grayed out and converted to the classic model. No capes.
    val skin = if (hasAccount) accountSkin else rememberNoAccountSkin()
    val skinHeadBitmap = remember(skin) {
        extractSkinHeadBitmap(skin)
    }
    val cape = remember(selectedAccount?.id, skinRefreshKey) {
        AccountSkinManager.loadCape(context, selectedAccount)
    }
    val previewUsername = selectedAccount?.username ?: ""

    // Account ID whose {•••} options menu is currently expanded
    var openOptionsAccountId by remember { mutableStateOf<String?>(null) }

    val isMicrosoft = selectedAccount != null && selectedAccount.typeRes == R.string.account_type_microsoft

    // Idle animation, rotatable by dragging, NOT auto-rotating
    val accountSkinConfig = remember(previewUsername, isMicrosoft) {
        SkinViewerConfig(
            model = if (isMicrosoft) SkinModel.AUTO else SkinModel.CLASSIC,
            animation = SkinAnimations.Idle,
            autoRotate = false,
            dragToRotate = true,
            allowPitch = true,
            pinchToZoom = false,
            doubleTapToReset = true,
            initialYaw = -20f,
            initialPitch = 6f,
            initialZoom = 1.15f,
            backgroundColor = LauncherColorTokens.TransparentArgb,
            transparentBackground = true,
            nameTag = previewUsername,
            nameTagScale = 1.15f,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Dismiss {•••} menu when tapping outside inside the panel
        if (openOptionsAccountId != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { openOptionsAccountId = null },
                    ),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Left Side: Top-Left "Add Account" Button + Account List below it
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Top-Left "Add Account" Button
                val addBtnShape = RoundedCornerShape(14.dp)
                val addAccountText = stringResource(R.string.add_account)
                Row(
                    modifier = Modifier
                        .smoothPressClickable(
                            pressScale = 0.95f,
                            onClick = {
                                openOptionsAccountId = null
                                onAddAccountClick()
                            },
                        )
                        .clip(addBtnShape)
                        .background(colors.surfaceButton)
                        .border(1.dp, colors.borderButtonStrong, addBtnShape)
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_add),
                        contentDescription = addAccountText,
                        tint = colors.iconPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = addAccountText,
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }

                PanelHeaderDivider()

                // Account List: [{profileskinhead} {username}     {•••}]
                val lazyListState = rememberLazyListState()
                val haptic = LocalHapticFeedback.current
                val soundPlayer = LocalLauncherSoundPlayer.current
                val reorderState = rememberReorderableState(
                    listState = lazyListState,
                    totalItemsCount = { accounts.size },
                    onMove = { from, to -> onReorderAccounts(accounts.reorder(from, to)) },
                    onDragStart = { openOptionsAccountId = null },
                )

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
                ) {
                    items(
                        count = accounts.size,
                        key = { index -> accounts[index].id },
                    ) { index ->
                        val account = accounts[index]
                        val isDragging = reorderState.isItemDragging(index, account.id)
                        val itemModifier = if (isDragging) Modifier else Modifier.animateItem()

                        val isSelected = account.id == selectedAccount?.id
                        val isMenuOpen = openOptionsAccountId == account.id
                        val itemShape = RoundedCornerShape(14.dp)
                        val itemBg by animateColorAsState(
                            targetValue = if (isSelected) colors.surfaceCardSelected else colors.surfaceCard,
                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                            label = "accountItemBg",
                        )
                        val itemBorder by animateColorAsState(
                            targetValue = if (isSelected) colors.borderSelectedCard else colors.borderSubtle,
                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                            label = "accountItemBorder",
                        )
                        val titleColor by animateColorAsState(
                            targetValue = if (isSelected) colors.accent else colors.textPrimary,
                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                            label = "accountTitleColor",
                        )
                        val moreBtnBg by animateColorAsState(
                            targetValue = if (isMenuOpen) colors.surfaceCardSelected else colors.transparent,
                            animationSpec = tween(durationMillis = 180),
                            label = "moreBtnBg",
                        )
                        val moreBtnRotation by animateFloatAsState(
                            targetValue = if (isMenuOpen) 90f else 0f,
                            animationSpec = spring(
                                dampingRatio = 0.72f,
                                stiffness = 420f,
                            ),
                            label = "moreBtnRotation",
                        )

                        Box(
                            modifier = itemModifier
                                .fillMaxWidth()
                                .reorderableItem(
                                    state = reorderState,
                                    key = account.id,
                                    index = index,
                                    enabled = accounts.size > 1,
                                    haptic = haptic,
                                    soundPlayer = soundPlayer,
                                ),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .smoothPressClickable(
                                            pressScale = 0.98f,
                                            onClick = {
                                                if (!reorderState.isDragging && !reorderState.justFinishedDrag) {
                                                    openOptionsAccountId = null
                                                    onAccountSelect(account)
                                                }
                                            },
                                        )
                                        .clip(itemShape)
                                        .background(if (isDragging) colors.surfaceCardSelected else itemBg)
                                        .border(
                                            width = if (isDragging) 1.5.dp else 1.dp,
                                            color = if (isDragging) colors.accent else itemBorder,
                                            shape = itemShape,
                                        )
                                        .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    // Left: {profileskinhead} {username}
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        val itemSkinHead = remember(account.id, skinRefreshKey) {
                                            extractSkinHeadBitmap(AccountSkinManager.loadSkin(context, account))
                                        }
                                        Image(
                                            bitmap = itemSkinHead,
                                            contentDescription = account.username,
                                            filterQuality = FilterQuality.None,
                                            contentScale = ContentScale.FillBounds,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .border(
                                                    width = 1.dp,
                                                    color = colors.borderModal,
                                                    shape = RoundedCornerShape(6.dp),
                                                ),
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            Text(
                                                text = account.username,
                                                color = titleColor,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontSize = 14.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                Icon(
                                                    painter = painterResource(
                                                        id = if (account.typeRes == R.string.account_type_microsoft) R.drawable.ic_microsoft else R.drawable.ic_offline,
                                                    ),
                                                    contentDescription = null,
                                                    tint = if (account.typeRes == R.string.account_type_microsoft) Color.Unspecified else colors.textMuted,
                                                    modifier = Modifier.size(11.dp),
                                                )
                                                Text(
                                                    text = stringResource(account.typeRes),
                                                    color = colors.textMuted,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Normal,
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }

                                    // Right: More options {•••} (plus smoothly animated inline Rename / Remove) + Drag Handle
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        AnimatedVisibility(
                                            visible = isMenuOpen,
                                            enter = fadeIn(
                                                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                                            ) + expandHorizontally(
                                                animationSpec = spring(
                                                    dampingRatio = 0.82f,
                                                    stiffness = 440f,
                                                ),
                                                expandFrom = Alignment.End,
                                            ),
                                            exit = fadeOut(
                                                animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
                                            ) + shrinkHorizontally(
                                                animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
                                                shrinkTowards = Alignment.End,
                                            ),
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                val actionShape = RoundedCornerShape(10.dp)
                                                if (account.typeRes == R.string.account_type_microsoft) {
                                                    // Refresh option for Microsoft accounts
                                                    Box(
                                                        modifier = Modifier
                                                            .smoothPressClickable(
                                                                pressScale = 0.92f,
                                                                onClick = {
                                                                    openOptionsAccountId = null
                                                                    onRefreshAccount(account)
                                                                },
                                                            )
                                                            .clip(actionShape)
                                                            .background(colors.surfaceBadge)
                                                            .border(1.dp, colors.white.copy(alpha = 0.30f), actionShape)
                                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Text(
                                                            text = stringResource(R.string.action_refresh),
                                                            color = colors.textPrimary,
                                                            style = MaterialTheme.typography.labelMedium.copy(
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                            ),
                                                        )
                                                    }
                                                } else {
                                                    // Rename option for Offline accounts
                                                    Box(
                                                        modifier = Modifier
                                                            .smoothPressClickable(
                                                                pressScale = 0.92f,
                                                                onClick = {
                                                                    openOptionsAccountId = null
                                                                    onRequestRenameAccount(account)
                                                                },
                                                            )
                                                            .clip(actionShape)
                                                            .background(colors.surfaceBadge)
                                                            .border(1.dp, colors.white.copy(alpha = 0.30f), actionShape)
                                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Text(
                                                            text = stringResource(R.string.action_rename),
                                                            color = colors.textPrimary,
                                                            style = MaterialTheme.typography.labelMedium.copy(
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                            ),
                                                        )
                                                    }
                                                }

                                                // Remove option
                                                Box(
                                                    modifier = Modifier
                                                        .smoothPressClickable(
                                                            pressScale = 0.92f,
                                                            onClick = {
                                                                openOptionsAccountId = null
                                                                onRemoveAccount(account)
                                                            },
                                                        )
                                                        .clip(actionShape)
                                                        .background(colors.errorCancelContainer)
                                                        .border(1.dp, colors.errorBorder, actionShape)
                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.action_remove),
                                                        color = colors.errorText,
                                                        style = MaterialTheme.typography.labelMedium.copy(
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                        ),
                                                    )
                                                }
                                            }
                                        }

                                        // {•••} More options button
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .smoothPressClickable(
                                                pressScale = 0.90f,
                                                onClick = {
                                                    openOptionsAccountId =
                                                        if (isMenuOpen) null else account.id
                                                },
                                            )
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(moreBtnBg),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_more_horiz),
                                            contentDescription = stringResource(R.string.action_more_options),
                                            tint = colors.textSecondary,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .graphicsLayer { rotationZ = moreBtnRotation },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

            // Right Side: Interactive 3D Skin View above (Idle animation, rotatable by drag, NOT auto-rotating)
            Column(
                modifier = Modifier
                    .width(230.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                val skinBoxShape = RoundedCornerShape(16.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(skinBoxShape)
                        .background(colors.surfaceCard),
                    contentAlignment = Alignment.Center,
                ) {
                    SkinViewer(
                        modifier = Modifier.fillMaxSize(),
                        skin = skin,
                        cape = cape,
                        config = accountSkinConfig,
                        paused = isSuspended,
                    )

                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .border(
                                width = 1.dp,
                                color = colors.borderDefault,
                                shape = skinBoxShape,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
fun RightPlayAndSkinPanel(
    instances: List<LauncherInstance>,
    selectedInstanceId: String,
    onInstanceSelect: (LauncherInstance) -> Unit,
    onAddInstanceClick: () -> Unit,
    activeUsername: String,
    isChangePanelOpen: Boolean,
    onChangeToggle: () -> Unit,
    onPlayClick: () -> Unit,
    screenWidth: Dp,
    screenHeight: Dp,
    backgroundConfig: BackgroundConfig,
    sharedTimeSeconds: State<Float>,
    modifier: Modifier = Modifier,
    blurredBackgroundLayer: GraphicsLayer? = null,
    backgroundRootOffset: Offset = Offset.Zero,
    backgroundFrameTick: () -> Int = { 0 },
    isSuspended: Boolean = false,
    activeAccount: LauncherAccount? = null,
    skinRefreshKey: Int = 0,
    onReorderInstances: (List<LauncherInstance>) -> Unit = {},
) {
    val colors = LauncherTheme.colors
    val context = LocalContext.current
    // No account -> the account skin grayed out in the classic model; capes are never shown.
    val hasAccount = activeUsername.isNotBlank()
    val accountSkin = remember(activeAccount?.id, skinRefreshKey) {
        AccountSkinManager.loadSkin(context, activeAccount)
    }
    val skin = if (hasAccount) accountSkin else rememberNoAccountSkin()
    val cape = remember(activeAccount?.id, skinRefreshKey) {
        AccountSkinManager.loadCape(context, activeAccount)
    }

    val isMicrosoft = activeAccount != null && activeAccount.typeRes == R.string.account_type_microsoft

    val skinConfig = remember(activeUsername, isMicrosoft) {
        SkinViewerConfig(
            model = if (isMicrosoft) SkinModel.AUTO else SkinModel.CLASSIC,
            animation = SkinAnimations.CoolDisplay,
            autoRotate = false,
            dragToRotate = false,
            allowPitch = false,
            pinchToZoom = false,
            doubleTapToReset = false,
            initialYaw = -22f,
            initialPitch = 5f,
            initialZoom = 1.28f,
            backgroundColor = LauncherColorTokens.TransparentArgb,
            transparentBackground = true,
            nameTag = activeUsername,
            nameTagScale = 1.15f,
        )
    }

    val panelEndPadding = 56.dp
    val playColumnWidth = 200.dp
    val playButtonHeight = 56.dp
    val playBottomPadding = 38.dp
    val rowSpacing = 10.dp
    val buttonShape = RoundedCornerShape(18.dp)
    val selectedInstance = remember(instances, selectedInstanceId) {
        instances.firstOrNull { it.id == selectedInstanceId } ?: instances.firstOrNull()
    }
    val selectedInstanceName = selectedInstance?.name.orEmpty()

    // Same container smoothly springs between 56dp x 56dp Change button and 300dp x full-height Instance List panel
    val resizeSpec = spring<Dp>(
        dampingRatio = 0.84f,
        stiffness = 360f,
    )
    val changeWidth by animateDpAsState(
        targetValue = if (isChangePanelOpen) 300.dp else 56.dp,
        animationSpec = resizeSpec,
        label = "changeWidth",
    )
    val changeHeight by animateDpAsState(
        targetValue = if (isChangePanelOpen) (screenHeight - 10.dp).coerceAtLeast(56.dp) else 56.dp,
        animationSpec = resizeSpec,
        label = "changeHeight",
    )
    val changeBottomPadding by animateDpAsState(
        targetValue = if (isChangePanelOpen) 5.dp else playBottomPadding,
        animationSpec = resizeSpec,
        label = "changeBottomPadding",
    )

    Row(
        modifier = modifier
            .fillMaxHeight()
            .padding(end = panelEndPadding),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(rowSpacing),
    ) {
        // Unified Change Button / Panel:
        // Closed -> 56dp x 56dp button showing only "change" icon.
        // Open   -> Resizes to 300dp width & full height showing [{icon} {instance-name}    ] list.
        Box(
            modifier = Modifier
                .padding(bottom = changeBottomPadding)
                .width(changeWidth)
                .height(changeHeight)
                .smoothPressClickable(
                    enabled = !isChangePanelOpen,
                    pressScale = 0.93f,
                    onClick = onChangeToggle,
                )
                .clip(buttonShape),
            contentAlignment = Alignment.Center,
        ) {
            val density = LocalDensity.current
            val screenWidthPx = with(density) { screenWidth.roundToPx() }
            val screenHeightPx = with(density) { screenHeight.roundToPx() }
            val rightInsetPx = with(density) { (panelEndPadding + playColumnWidth + rowSpacing).roundToPx() }
            val changeBottomInsetPx = with(density) { changeBottomPadding.roundToPx() }

            // 1. Shared Frosted Glass Backdrop Layer (samples the single blurred fullscreen background)
            FrostedGlassBackdrop(
                blurredBackgroundLayer = blurredBackgroundLayer,
                backgroundRootOffset = backgroundRootOffset,
                backgroundFrameTick = backgroundFrameTick,
                sharedTimeSeconds = sharedTimeSeconds,
                backgroundConfig = backgroundConfig,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                fallbackOffsetX = { maxWidth ->
                    -(screenWidthPx.coerceAtLeast(maxWidth) - rightInsetPx - maxWidth)
                },
                fallbackOffsetY = { maxHeight ->
                    -(screenHeightPx.coerceAtLeast(maxHeight) - changeBottomInsetPx - maxHeight)
                },
            )

            // 2. Frosted glass outer border
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            *colors.panelBorderStops.toTypedArray(),
                        ),
                        shape = buttonShape,
                    ),
            )

            // 3. Smooth Crossfade inside the resizing container (no SizeTransform so blur never glitches)
            Crossfade(
                targetState = isChangePanelOpen,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                label = "changePanelCrossfade",
            ) { panelOpen ->
                if (!panelOpen) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_change),
                            contentDescription = stringResource(R.string.action_change_instance),
                            tint = colors.iconPrimary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                } else if (instances.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_instances),
                            contentDescription = null,
                            tint = colors.iconMuted,
                            modifier = Modifier.size(30.dp),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.no_instances_installed),
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val addShape = RoundedCornerShape(12.dp)
                        Row(
                            modifier = Modifier
                                .smoothPressClickable(
                                    pressScale = 0.95f,
                                    onClick = onAddInstanceClick,
                                )
                                .clip(addShape)
                                .background(colors.surfaceBadge)
                                .border(1.dp, colors.borderHighlightTop, addShape)
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_add),
                                contentDescription = stringResource(R.string.add_instance),
                                tint = colors.accent,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.add_instance),
                                color = colors.accent,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                    }
                } else {
                    val lazyListState = rememberLazyListState()
                    val haptic = LocalHapticFeedback.current
                    val soundPlayer = LocalLauncherSoundPlayer.current
                    val reorderState = rememberReorderableState(
                        listState = lazyListState,
                        totalItemsCount = { instances.size },
                        onMove = { from, to -> onReorderInstances(instances.reorder(from, to)) },
                    )

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
                    ) {
                        items(
                            count = instances.size,
                            key = { index -> instances[index].id },
                        ) { index ->
                            val instance = instances[index]
                            val isDragging = reorderState.isItemDragging(index, instance.id)
                            val itemModifier = if (isDragging) Modifier else Modifier.animateItem()

                            val isSelected = instance.id == selectedInstanceId
                            val instanceName = instance.name
                            val instanceSubtitle = if (
                                instance.loaderType == InstanceLoaderType.FABRIC &&
                                !instance.loaderVersion.isNullOrBlank()
                            ) {
                                stringResource(R.string.instance_subtitle_fabric, instance.mcVersion, instance.loaderVersion)
                            } else {
                                stringResource(R.string.instance_subtitle_vanilla, instance.mcVersion)
                            }
                            val itemShape = RoundedCornerShape(14.dp)
                            val itemBg by animateColorAsState(
                                targetValue = if (isSelected) colors.surfaceCardSelected else colors.surfaceCard,
                                animationSpec = tween(durationMillis = 200),
                                label = "instanceItemBg",
                            )
                            val itemBorder by animateColorAsState(
                                targetValue = if (isSelected) colors.borderSelectedCard else colors.borderSubtle,
                                animationSpec = tween(durationMillis = 200),
                                label = "instanceItemBorder",
                            )
                            val itemContentColor by animateColorAsState(
                                targetValue = if (isSelected) colors.accent else colors.textStrong,
                                animationSpec = tween(durationMillis = 200),
                                label = "instanceItemContent",
                            )

                            Box(
                                modifier = itemModifier
                                    .fillMaxWidth()
                                    .reorderableItem(
                                        state = reorderState,
                                        key = instance.id,
                                        index = index,
                                        enabled = instances.size > 1,
                                        haptic = haptic,
                                        soundPlayer = soundPlayer,
                                    ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .smoothPressClickable(
                                            pressScale = 0.96f,
                                            onClick = {
                                                if (!reorderState.isDragging && !reorderState.justFinishedDrag) {
                                                    onInstanceSelect(instance)
                                                }
                                            },
                                        )
                                        .clip(itemShape)
                                        .background(if (isDragging) colors.surfaceCardSelected else itemBg)
                                        .border(
                                            width = if (isDragging) 1.5.dp else 1.dp,
                                            color = if (isDragging) colors.accent else itemBorder,
                                            shape = itemShape,
                                        )
                                        .padding(horizontal = 12.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Start,
                                ) {
                                    InstanceLoaderIcon(
                                        loaderType = instance.loaderType,
                                        contentDescription = instanceName,
                                        defaultTint = itemContentColor,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(
                                            text = instanceName,
                                            color = itemContentColor,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = instanceSubtitle,
                                            color = colors.white.copy(alpha = 0.52f),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Right Column: 3D Skin View standing right above the Play Button
        Column(
            modifier = Modifier
                .width(playColumnWidth)
                .fillMaxHeight()
                .padding(bottom = playBottomPadding, top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .height(250.dp)
                    .aspectRatio(0.82f),
                contentAlignment = Alignment.BottomCenter,
            ) {
                SkinViewer(
                    modifier = Modifier.fillMaxSize(),
                    skin = skin,
                    cape = cape,
                    config = skinConfig,
                    paused = isSuspended,
                )
            }

            // Play Button with Real-Time Gaussian Blur + Smooth Press Bounce
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(playButtonHeight)
                    .smoothPressClickable(
                        pressScale = 0.95f,
                        onClick = onPlayClick,
                    )
                    .clip(buttonShape),
                contentAlignment = Alignment.Center,
            ) {
                val playDensity = LocalDensity.current
                val playScreenWidthPx = with(playDensity) { screenWidth.roundToPx() }
                val playScreenHeightPx = with(playDensity) { screenHeight.roundToPx() }
                val playEndInsetPx = with(playDensity) { panelEndPadding.roundToPx() }
                val playBottomInsetPx = with(playDensity) { playBottomPadding.roundToPx() }

                // 1. Shared Frosted Glass Backdrop Layer (samples the single blurred fullscreen background)
                FrostedGlassBackdrop(
                    blurredBackgroundLayer = blurredBackgroundLayer,
                    backgroundRootOffset = backgroundRootOffset,
                    backgroundFrameTick = backgroundFrameTick,
                    sharedTimeSeconds = sharedTimeSeconds,
                    backgroundConfig = backgroundConfig,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    fallbackOffsetX = { maxWidth ->
                        -(playScreenWidthPx.coerceAtLeast(maxWidth) - playEndInsetPx - maxWidth)
                    },
                    fallbackOffsetY = { maxHeight ->
                        -(playScreenHeightPx.coerceAtLeast(maxHeight) - playBottomInsetPx - maxHeight)
                    },
                )

                // 2. Frosted glass outer border
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                *colors.panelBorderStops.toTypedArray(),
                            ),
                            shape = buttonShape,
                        ),
                )

                // 3. Sharp Foreground "PLAY" Text + Fancy AnimatedContent Instance Name below "PLAY"
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.action_play),
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.4.sp,
                        ),
                    )
                    AnimatedContent(
                        targetState = selectedInstanceName.ifEmpty { stringResource(R.string.no_instance_selected) },
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                                slideInVertically(
                                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 400f),
                                    initialOffsetY = { fullHeight -> fullHeight },
                                ) +
                                scaleIn(initialScale = 0.90f, animationSpec = tween(200)))
                                .togetherWith(
                                    fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing)) +
                                        slideOutVertically(
                                            animationSpec = tween(160, easing = FastOutSlowInEasing),
                                            targetOffsetY = { fullHeight -> -fullHeight },
                                        ) +
                                        scaleOut(targetScale = 0.90f, animationSpec = tween(150)),
                                )
                                .using(SizeTransform(clip = false))
                        },
                        label = "instanceNameAnimatedContent",
                    ) { name ->
                        Text(
                            text = name,
                            color = colors.textSubtle,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                lineHeight = 12.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationRailButton(
    destination: LauncherDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LauncherTheme.colors
    val itemShape = RoundedCornerShape(16.dp)
    val bgColor by animateColorAsState(
        targetValue = if (selected) colors.surfaceCardSelected else colors.transparent,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "navItemBg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) colors.borderSelectedCard else colors.transparent,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "navItemBorder",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.textButton,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "navItemContent",
    )
    val selectedScale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 380f,
        ),
        label = "navItemSelectedScale",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = selectedScale
                scaleY = selectedScale
            }
            .smoothPressClickable(
                pressScale = 0.92f,
                onClick = onClick,
            )
            .clip(itemShape)
            .background(bgColor)
            .border(1.dp, borderColor, itemShape)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Glowing active indicator bar on the left edge when selected
        androidx.compose.animation.AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(180)) + scaleIn(
                initialScale = 0.3f,
                animationSpec = spring(dampingRatio = 0.68f, stiffness = 420f),
            ),
            exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.3f, animationSpec = tween(140)),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.accent),
            )
        }

        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                (fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                    slideInVertically(
                        animationSpec = spring(dampingRatio = 0.76f, stiffness = 400f),
                        initialOffsetY = { fullHeight -> if (targetState) fullHeight / 2 else -fullHeight / 2 },
                    ) +
                    scaleIn(
                        initialScale = 0.85f,
                        animationSpec = spring(dampingRatio = 0.76f, stiffness = 400f),
                    ))
                    .togetherWith(
                        fadeOut(animationSpec = tween(140, easing = FastOutSlowInEasing)) +
                            slideOutVertically(
                                animationSpec = tween(150, easing = FastOutSlowInEasing),
                                targetOffsetY = { fullHeight -> if (targetState) -fullHeight / 2 else fullHeight / 2 },
                            ) +
                            scaleOut(
                                targetScale = 0.85f,
                                animationSpec = tween(140, easing = FastOutSlowInEasing),
                            ),
                    )
                    .using(SizeTransform(clip = false))
            },
            label = "navButtonAnimatedContent",
        ) { isSelectedState ->
            val displayIconRes = if (isSelectedState) R.drawable.ic_close_chevron else destination.iconRes
            val displayText = stringResource(if (isSelectedState) R.string.nav_close else destination.titleRes)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(id = displayIconRes),
                    contentDescription = displayText,
                    tint = contentColor,
                    modifier = Modifier.size(26.dp),
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = displayText,
                    color = contentColor,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = if (isSelectedState) FontWeight.Bold else FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

private enum class SettingsCategory(
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
) {
    DISPLAY_AND_RENDERER(
        titleRes = R.string.settings_category_display_renderer,
        iconRes = R.drawable.ic_display_renderer,
    ),
    JAVA_TWEAK(
        titleRes = R.string.settings_category_java_tweak,
        iconRes = R.drawable.ic_java_tweak,
    ),
    CONTROLS(
        titleRes = R.string.settings_category_controls,
        iconRes = R.drawable.ic_controls,
    ),
    APPEARANCE(
        titleRes = R.string.settings_category_appearance,
        iconRes = R.drawable.ic_appearance,
    ),
}

@Composable
private fun SettingsPanelContent(
    backgroundConfig: BackgroundConfig,
    accentColor: Color,
    isImportingBackground: Boolean,
    backgroundImportError: String?,
    onPickBackgroundMedia: () -> Unit,
    onResetBackground: () -> Unit,
    onAccentColorChange: (Color) -> Unit,
    onOpenAccentColorPicker: () -> Unit,
    selectedRendererDescriptor: RendererDescriptor,
    availableRenderers: List<RendererDescriptor>,
    onRendererSelect: (RendererDescriptor) -> Unit,
    resolutionScale: Int,
    onResolutionScaleChange: (Int) -> Unit,
) {
    val colors = LauncherTheme.colors
    var selectedCategory by remember { mutableStateOf(SettingsCategory.DISPLAY_AND_RENDERER) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Top Category Tab Bar ("Display and Renderer", "Java Tweak", "Controls", "Appearance") styled like rail bar buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsCategory.entries.forEach { category ->
                val isSelected = selectedCategory == category
                val tabShape = RoundedCornerShape(16.dp)
                val tabBg by animateColorAsState(
                    targetValue = if (isSelected) colors.surfaceCardSelected else colors.transparent,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    label = "settingsCategoryBg",
                )
                val tabBorder by animateColorAsState(
                    targetValue = if (isSelected) colors.borderSelectedCard else colors.transparent,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    label = "settingsCategoryBorder",
                )
                val tabContentColor by animateColorAsState(
                    targetValue = if (isSelected) colors.accent else colors.textButton,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    label = "settingsCategoryContent",
                )

                Box(
                    modifier = Modifier
                        .smoothPressClickable(
                            pressScale = 0.96f,
                            onClick = { selectedCategory = category },
                        )
                        .clip(tabShape)
                        .background(tabBg)
                        .border(1.dp, tabBorder, tabShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier.padding(
                            start = 14.dp,
                            end = 14.dp,
                            top = 9.dp,
                            bottom = 11.dp,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = category.iconRes),
                            contentDescription = null,
                            tint = tabContentColor,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(category.titleRes),
                            color = tabContentColor,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Glowing active indicator bar along the bottom edge, matching rail bar buttons
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isSelected,
                        enter = fadeIn(tween(180)) + scaleIn(
                            initialScale = 0.3f,
                            animationSpec = spring(dampingRatio = 0.68f, stiffness = 420f),
                        ),
                        exit = fadeOut(tween(140)) + scaleOut(
                            targetScale = 0.3f,
                            animationSpec = tween(140),
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 3.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(colors.accent),
                        )
                    }
                }
            }
        }

        PanelHeaderDivider()

        // Category content area
        when (selectedCategory) {
            SettingsCategory.DISPLAY_AND_RENDERER -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DisplayAndRendererSettingsContent(
                        selectedRendererDescriptor = selectedRendererDescriptor,
                        availableRenderers = availableRenderers,
                        onRendererSelect = onRendererSelect,
                        resolutionScale = resolutionScale,
                        onResolutionScaleChange = onResolutionScaleChange,
                    )
                }
            }
            SettingsCategory.JAVA_TWEAK,
            SettingsCategory.CONTROLS -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            SettingsCategory.APPEARANCE -> {
                val isDefaultBg = backgroundConfig.source is BackgroundSource.MinecraftSunset ||
                    backgroundConfig.source is BackgroundSource.SolidColor
                val accentHex = remember(accentColor) { formatColorHex(accentColor) }
                val isDefaultAccent = remember(accentColor) {
                    accentHex == formatColorHex(LauncherColorTokens.DefaultAccent)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Appearance Card: Background + Accent Color separated by a divider line
                    AppearanceSettingsCard(
                        title = stringResource(R.string.settings_bg_title),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AppearanceSelectorResetRow(
                            label = stringResource(R.string.settings_bg_pick_media),
                            selectorEnabled = !isImportingBackground,
                            resetEnabled = !isDefaultBg && !isImportingBackground,
                            onSelectClick = onPickBackgroundMedia,
                            onResetClick = onResetBackground,
                        )

                        if (isImportingBackground) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(
                                    color = colors.accent,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = stringResource(R.string.settings_bg_importing),
                                    color = colors.textSubtle,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                )
                            }
                        } else if (!backgroundImportError.isNullOrBlank()) {
                            Text(
                                text = backgroundImportError,
                                color = colors.errorText,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        // Divider line between the Background and Accent Color sections
                        PanelHeaderDivider()

                        // Accent Color section -> [{current color}            {reset}]
                        Text(
                            text = stringResource(R.string.settings_accent_title),
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        AppearanceSelectorResetRow(
                            label = accentHex,
                            resetEnabled = !isDefaultAccent,
                            onSelectClick = onOpenAccentColorPicker,
                            onResetClick = { onAccentColorChange(LauncherColorTokens.DefaultAccent) },
                            leadingSwatch = {
                                val swatchShape = RoundedCornerShape(6.dp)
                                Box(
                                    modifier = Modifier
                                        .width(28.dp)
                                        .height(18.dp)
                                        .clip(swatchShape)
                                        .background(accentColor)
                                        .border(1.dp, colors.borderModal, swatchShape),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceSelectorResetRow(
    label: String,
    resetEnabled: Boolean,
    onSelectClick: () -> Unit,
    onResetClick: () -> Unit,
    selectorEnabled: Boolean = true,
    leadingSwatch: (@Composable () -> Unit)? = null,
) {
    val colors = LauncherTheme.colors
    val selectorShape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 12.dp)
                .smoothPressClickable(
                    enabled = selectorEnabled,
                    pressScale = 0.96f,
                    onClick = onSelectClick,
                )
                .clip(selectorShape)
                .background(
                    if (selectorEnabled) colors.surfaceButton else colors.surfaceButtonDisabled,
                )
                .border(
                    1.dp,
                    if (selectorEnabled) colors.borderButton else colors.borderSubtle,
                    selectorShape,
                )
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (leadingSwatch != null) {
                leadingSwatch()
            }

            Text(
                text = label,
                color = if (selectorEnabled) colors.textPrimary else colors.textActionDisabled,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        AppearanceOptionButton(
            label = stringResource(R.string.action_reset),
            selected = false,
            enabled = resetEnabled,
            onClick = onResetClick,
        )
    }
}

@Composable
private fun DisplayAndRendererSettingsContent(
    selectedRendererDescriptor: RendererDescriptor,
    availableRenderers: List<RendererDescriptor>,
    onRendererSelect: (RendererDescriptor) -> Unit,
    resolutionScale: Int,
    onResolutionScaleChange: (Int) -> Unit,
) {
    val colors = LauncherTheme.colors
    var isRendererDropdownOpen by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(16.dp)

    // 1. Renderer Selection Card
    val chevronRotation by animateFloatAsState(
        targetValue = if (isRendererDropdownOpen) 180f else 0f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 420f,
        ),
        label = "rendererChevronRotation",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(colors.surfaceCard)
            .border(1.dp, colors.borderDefault, cardShape)
            .padding(14.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = 420f,
                ),
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Clickable header Column: "Renderer" on top, current renderer and chevron below
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .smoothPressClickable(
                    pressScale = 0.98f,
                    onClick = { isRendererDropdownOpen = !isRendererDropdownOpen },
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_renderer_title),
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Medium,
                            ),
                        ) {
                            append(stringResource(R.string.settings_current_renderer_prefix))
                            append(" ")
                        }
                        withStyle(
                            SpanStyle(
                                color = colors.accent,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        ) {
                            append(selectedRendererDescriptor.displayName)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_down),
                    contentDescription = stringResource(R.string.action_more_options),
                    tint = colors.textSecondary,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = chevronRotation },
                )
            }
        }

        // Dropdown Menu Body: Renderer options list with animated expansion
        AnimatedVisibility(
            visible = isRendererDropdownOpen,
            enter = fadeIn(tween(180, easing = FastOutSlowInEasing)) + expandVertically(
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
            ),
            exit = fadeOut(tween(140, easing = FastOutSlowInEasing)) + shrinkVertically(
                animationSpec = tween(180, easing = FastOutSlowInEasing),
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.borderSubtle),
                )

                Spacer(modifier = Modifier.height(2.dp))

                availableRenderers.forEach { descriptor ->
                    val isSelected = descriptor.id == selectedRendererDescriptor.id
                    val optionShape = RoundedCornerShape(8.dp)
                    val optionBg by animateColorAsState(
                        targetValue = if (isSelected) colors.surfaceCardSelected else colors.transparent,
                        animationSpec = tween(durationMillis = 180),
                        label = "rendererOptionBg_${descriptor.id}",
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .smoothPressClickable(
                                pressScale = 0.98f,
                                onClick = {
                                    onRendererSelect(descriptor)
                                    isRendererDropdownOpen = false
                                },
                            )
                            .clip(optionShape)
                            .background(optionBg)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = descriptor.displayName,
                                color = if (isSelected) colors.accent else colors.textPrimary,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            if (descriptor.isPlugin) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(colors.accent.copy(alpha = 0.18f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_plugin_badge),
                                        color = colors.accent,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 2. Display Resolution Scaling (Resize) Card
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(colors.surfaceCard)
            .border(1.dp, colors.borderDefault, cardShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.settings_res_scale_title),
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = "$resolutionScale%",
                color = colors.accent,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }

        // Slider for fine resolution resize: continuous 25-100% without steps
        Slider(
            value = resolutionScale.toFloat(),
            onValueChange = { onResolutionScaleChange(it.roundToInt().coerceIn(25, 100)) },
            valueRange = 25f..100f,
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.surfaceButton,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AppearanceSettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LauncherTheme.colors
    val cardShape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(cardShape)
            .background(colors.surfaceCard)
            .border(1.dp, colors.borderDefault, cardShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            color = colors.textPrimary,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        content()
    }
}

@Composable
private fun AppearanceOptionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LauncherTheme.colors
    val btnShape = RoundedCornerShape(10.dp)
    val bgColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.surfaceButtonDisabled
            selected -> colors.accentContainer
            else -> colors.surfaceButton
        },
        animationSpec = tween(durationMillis = 160),
        label = "appearanceOptionBg",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.borderSubtle
            selected -> colors.accentBorder
            else -> colors.borderButton
        },
        animationSpec = tween(durationMillis = 160),
        label = "appearanceOptionBorder",
    )
    val textColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.textActionDisabled
            selected -> colors.accent
            else -> colors.textSecondary
        },
        animationSpec = tween(durationMillis = 160),
        label = "appearanceOptionText",
    )

    Box(
        modifier = modifier
            .smoothPressClickable(
                enabled = enabled,
                pressScale = 0.95f,
                onClick = onClick,
            )
            .clip(btnShape)
            .background(bgColor)
            .border(1.dp, borderColor, btnShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(
    showBackground = true,
    device = "spec:width=891dp,height=411dp,dpi=420,isRound=false,chinSize=0dp,orientation=landscape"
)
@Composable
fun LauncherScreenPreview() {
    DomreiLauncherTheme {
        LauncherScreen()
    }
}
