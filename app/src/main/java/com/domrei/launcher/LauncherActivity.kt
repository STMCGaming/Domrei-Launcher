package com.domrei.launcher

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Rect as AndroidRect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.domrei.gameengine.DomreiGameEngine
import com.domrei.gameengine.GameEngineState
import com.domrei.gameengine.GameEngineStatus
import com.domrei.gameengine.GameLaunchRequest
import com.domrei.gameengine.renderer.RendererType
import com.domrei.gameengine.runtime.InstalledJavaRuntime
import com.domrei.gameengine.runtime.JavaRuntimeVersion
import com.domrei.launcher.minecraft.DownloadFileItem
import com.domrei.launcher.minecraft.DownloadStageStatus
import com.domrei.launcher.minecraft.FabricLoaderVersionEntry
import com.domrei.launcher.minecraft.InstallProgressSnapshot
import com.domrei.launcher.minecraft.MinecraftInstanceManager
import com.domrei.launcher.minecraft.MinecraftVersionFilter
import com.domrei.launcher.minecraft.MinecraftVersionManifest
import com.domrei.launcher.minecraft.OfficialMinecraftApi
import com.domrei.launcher.ui.theme.DomreiLauncherTheme
import com.domrei.skinview.DefaultSkin
import com.domrei.skinview.SkinAnimations
import com.domrei.skinview.SkinUtils
import com.domrei.skinview.SkinViewer
import com.domrei.skinview.SkinViewerConfig
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SKIN_ASSET = "skin.png"
private const val CAPE_ASSET = "cape.png"

class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureFullScreenWithCutout()

        setContent {
            DomreiLauncherTheme {
                LauncherScreen()
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
) {
    VANILLA(R.string.instance_type_vanilla, R.drawable.ic_instances, true),
    FABRIC(R.string.instance_type_fabric, R.drawable.ic_fabric, true),
    OTHER(R.string.instance_type_other_not_yet, R.drawable.ic_settings, false),
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
)

data class LauncherAccount(
    val id: String,
    val username: String,
    @StringRes val typeRes: Int = R.string.account_type_offline,
)

sealed interface LauncherDialog {
    data object AddAccount : LauncherDialog
    data class RenameAccount(val account: LauncherAccount) : LauncherDialog
    data object AddInstance : LauncherDialog
}

/**
 * Reusable interactive press-bounce modifier that scales down smoothly on press and springs back on release.
 */
private fun Modifier.smoothPressClickable(
    enabled: Boolean = true,
    pressScale: Float = 0.95f,
    onClick: () -> Unit,
): Modifier = composed {
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
            onClick = onClick,
        )
}

@Composable
fun LauncherScreen(
    modifier: Modifier = Modifier,
    backgroundConfig: BackgroundConfig = BackgroundConfig(
        source = BackgroundSource.MinecraftSunset(),
    ),
) {
    val context = LocalContext.current
    val currentBackground by remember(backgroundConfig) { mutableStateOf(backgroundConfig) }

    // null = Default Home Screen.
    // Tapping a button selects it; tapping the currently active button returns to the Home Screen (null).
    var currentDestination by remember { mutableStateOf<LauncherDestination?>(null) }

    // Controls the 300dp-wide Change panel opened by the "change" button left of Play
    var isChangePanelOpen by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val instanceManager = remember(context) { MinecraftInstanceManager(context) }
    val gameEngine = remember(context) { DomreiGameEngine(context) }
    val engineStatus by gameEngine.status.collectAsState()
    val engineLogs by gameEngine.logs.collectAsState()
    var selectedRenderer by remember { mutableStateOf(gameEngine.rendererManager.getSelectedRenderer()) }
    var installedRuntimes by remember { mutableStateOf(gameEngine.jdkManager.listInstalledRuntimes()) }
    var rendererInstallStates by remember {
        mutableStateOf(
            RendererType.entries.associateWith { gameEngine.rendererManager.isRendererInstalled(it) }
        )
    }
    var instances by remember { mutableStateOf(instanceManager.loadInstances()) }
    var selectedInstanceId by remember { mutableStateOf(instances.firstOrNull()?.id.orEmpty()) }

    // Dynamic Island-style Install ProgressBar state (strictly for AddInstance)
    var activeInstallTask by remember { mutableStateOf<InstanceInstallTask?>(null) }
    var installSnapshot by remember { mutableStateOf<InstallProgressSnapshot?>(null) }

    // Dynamic Island-style Game Engine ProgressBar state (PREPARING_RUNTIME / PREPARING_RENDERER / BUILDING_SPEC)
    var activeEngineTask by remember { mutableStateOf<InstanceInstallTask?>(null) }
    var engineSnapshot by remember { mutableStateOf<InstallProgressSnapshot?>(null) }

    val isEnginePreparing = engineStatus.state == GameEngineState.PREPARING_RUNTIME ||
        engineStatus.state == GameEngineState.PREPARING_RENDERER ||
        engineStatus.state == GameEngineState.BUILDING_SPEC

    val effectiveProgressTask: InstanceInstallTask? = activeInstallTask ?: activeEngineTask ?: if (isEnginePreparing) {
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
    } else {
        engineSnapshot ?: if (isEnginePreparing) {
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
                        fileName = "PREPARING_RENDERER (${engineStatus.activeRenderer?.displayName ?: selectedRenderer.displayName})",
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
            Log.e("LauncherScreen", "Instance install failed for ${task.mcVersion}", err)
            delay(2600L)
        }
        activeInstallTask = null
        isProgressBarExpanded = false
    }

    val initialAccounts = remember(context) {
        listOf(
            LauncherAccount("acc_domrei", context.getString(R.string.default_account_domrei), R.string.account_type_microsoft),
            LauncherAccount("acc_steve", context.getString(R.string.default_account_steve), R.string.account_type_offline),
            LauncherAccount("acc_alex", context.getString(R.string.default_account_alex), R.string.account_type_offline),
        )
    }
    var accounts by remember { mutableStateOf(initialAccounts) }
    var nextAccountCounter by remember { mutableIntStateOf(initialAccounts.size + 1) }
    var selectedAccountId by remember { mutableStateOf(initialAccounts.first().id) }
    val selectedAccount = remember(accounts, selectedAccountId) {
        accounts.firstOrNull { it.id == selectedAccountId } ?: accounts.firstOrNull()
    }
    val defaultPlayerName = stringResource(R.string.default_player_name)
    val activeUsername = selectedAccount?.username ?: defaultPlayerName

    // Full-screen dialog state (Rename Account, Add Account, Add Instance, or any dialog like them)
    var activeDialog by remember { mutableStateOf<LauncherDialog?>(null) }
    // Remember last non-null dialog so exit animation can play smoothly without disappearing on frame 1
    var displayedDialog by remember { mutableStateOf<LauncherDialog?>(null) }
    if (activeDialog != null) {
        displayedDialog = activeDialog
    }
    var dialogInputText by remember { mutableStateOf("") }

    // Smoothly animate full-screen blur radius when any dialog opens or closes
    val dialogBlurRadius by animateDpAsState(
        targetValue = if (activeDialog != null) 20.dp else 0.dp,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "dialogBlurRadius",
    )

    // Shared animation clock so the real-time Gaussian-blurred rail backdrop matches the live background
    val animSpeed = (currentBackground.source as? BackgroundSource.MinecraftSunset)?.animationSpeed ?: 1f
    val sharedTimeSeconds = rememberBackgroundTimeSeconds(animSpeed)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val sceneGraphicsLayer = rememberGraphicsLayer()

        // Main screen content container: smoothly blurred across the full screen when any dialog is open
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (dialogBlurRadius > 0.5.dp) {
                        Modifier.blur(
                            radius = dialogBlurRadius,
                            edgeTreatment = BlurredEdgeTreatment.Rectangle,
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            // Scene container recorded into sceneGraphicsLayer so ProgressBar blurs the live panels
            // (Instances, Account, Settings, RightPlayAndSkinPanel) and background underneath it.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        sceneGraphicsLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        drawLayer(sceneGraphicsLayer)
                    },
            ) {
                // 1. Full-screen animated background
                LauncherBackground(
                    modifier = Modifier.fillMaxSize(),
                    config = currentBackground,
                    sharedTimeSeconds = sharedTimeSeconds,
                )

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
                    instanceManager.removeInstance(instance.id)
                    val remaining = instanceManager.loadInstances()
                    instances = remaining
                    if (selectedInstanceId == instance.id) {
                        selectedInstanceId = remaining.firstOrNull()?.id.orEmpty()
                    }
                },
                accounts = accounts,
                selectedAccountId = selectedAccountId,
                onAccountSelect = { account -> selectedAccountId = account.id },
                onAddAccountClick = {
                    dialogInputText = context.getString(R.string.default_new_player_format, nextAccountCounter)
                    activeDialog = LauncherDialog.AddAccount
                },
                onRequestRenameAccount = { account ->
                    dialogInputText = account.username
                    activeDialog = LauncherDialog.RenameAccount(account)
                },
                onRemoveAccount = { account ->
                    val remaining = accounts.filterNot { it.id == account.id }
                    accounts = remaining
                    if (selectedAccountId == account.id) {
                        selectedAccountId = remaining.firstOrNull()?.id.orEmpty()
                    }
                },
                selectedRenderer = selectedRenderer,
                rendererInstallStates = rendererInstallStates,
                installedRuntimes = installedRuntimes,
                engineStatus = engineStatus,
                engineLogs = engineLogs,
                onRendererSelect = { renderer ->
                    selectedRenderer = renderer
                    gameEngine.rendererManager.setSelectedRenderer(renderer)
                },
                onDownloadRenderer = { renderer ->
                    coroutineScope.launch {
                        activeEngineTask = InstanceInstallTask(
                            id = "renderer_${renderer.id}_${System.currentTimeMillis()}",
                            instanceName = renderer.displayName,
                            loaderType = InstanceLoaderType.VANILLA,
                            mcVersion = renderer.glVersionLabel,
                            loaderVersion = null,
                            isInstanceInstall = false,
                            customStatusTitle = "PREPARING_RENDERER: ${renderer.displayName}",
                        )
                        gameEngine.rendererManager.prepareRenderer(
                            type = renderer,
                            forceReinstall = true,
                        ) { stage, fraction ->
                            activeEngineTask = activeEngineTask?.copy(
                                customStatusTitle = "PREPARING_RENDERER: $stage",
                            )
                            engineSnapshot = InstallProgressSnapshot(
                                progress = fraction.coerceIn(0f, 1f),
                                currentFileName = stage,
                                downloadedMb = fraction * 8.8f,
                                totalMb = 8.8f,
                                speedMbPerSec = 4.2f,
                                files = listOf(
                                    DownloadFileItem(
                                        fileName = renderer.nativeLibFileName,
                                        sizeMb = 8.8f,
                                        progress = fraction,
                                        status = if (fraction >= 1f) DownloadStageStatus.DONE else DownloadStageStatus.DOWNLOADING,
                                    ),
                                ),
                                isComplete = fraction >= 1f,
                            )
                        }
                        rendererInstallStates = RendererType.entries.associateWith {
                            gameEngine.rendererManager.isRendererInstalled(it)
                        }
                        delay(800L)
                        activeEngineTask = null
                        engineSnapshot = null
                        isProgressBarExpanded = false
                    }
                },
                onDownloadRuntime = { runtimeVersion ->
                    coroutineScope.launch {
                        activeEngineTask = InstanceInstallTask(
                            id = "jre_${runtimeVersion.dirName}_${System.currentTimeMillis()}",
                            instanceName = runtimeVersion.displayName,
                            loaderType = InstanceLoaderType.VANILLA,
                            mcVersion = runtimeVersion.supportedMinecraftRange,
                            loaderVersion = null,
                            isInstanceInstall = false,
                            customStatusTitle = "PREPARING_RUNTIME: ${runtimeVersion.displayName}",
                        )
                        gameEngine.jdkManager.ensureRuntimeInstalled(
                            version = runtimeVersion,
                            forceReinstall = true,
                        ) { rtProgress ->
                            activeEngineTask = activeEngineTask?.copy(
                                customStatusTitle = "PREPARING_RUNTIME: ${rtProgress.stage}",
                            )
                            engineSnapshot = InstallProgressSnapshot(
                                progress = rtProgress.progressFraction.coerceIn(0f, 1f),
                                currentFileName = rtProgress.stage,
                                downloadedMb = rtProgress.progressFraction * 24f,
                                totalMb = 24f,
                                speedMbPerSec = 5.5f,
                                files = listOf(
                                    DownloadFileItem(
                                        fileName = "${runtimeVersion.dirName}/universal.tar.xz",
                                        sizeMb = 18f,
                                        progress = (rtProgress.progressFraction * 1.4f).coerceIn(0f, 1f),
                                        status = if (rtProgress.progressFraction >= 0.7f) DownloadStageStatus.DONE else DownloadStageStatus.DOWNLOADING,
                                    ),
                                    DownloadFileItem(
                                        fileName = "${runtimeVersion.dirName}/bin-arch.tar.xz",
                                        sizeMb = 6f,
                                        progress = ((rtProgress.progressFraction - 0.45f) * 1.8f).coerceIn(0f, 1f),
                                        status = if (rtProgress.progressFraction >= 1f) DownloadStageStatus.DONE else DownloadStageStatus.DOWNLOADING,
                                    ),
                                ),
                                isComplete = rtProgress.progressFraction >= 1f,
                            )
                        }
                        installedRuntimes = gameEngine.jdkManager.listInstalledRuntimes()
                        delay(900L)
                        activeEngineTask = null
                        engineSnapshot = null
                        isProgressBarExpanded = false
                    }
                },
                onStopGame = {
                    gameEngine.stopActiveGame()
                },
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                backgroundConfig = currentBackground,
                sharedTimeSeconds = sharedTimeSeconds,
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
                    isChangePanelOpen = isChangePanelOpen,
                    onChangeToggle = { isChangePanelOpen = !isChangePanelOpen },
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
                            coroutineScope.launch {
                                activeEngineTask = InstanceInstallTask(
                                    id = "launch_${targetInstance.id}_${System.currentTimeMillis()}",
                                    instanceName = targetInstance.name,
                                    loaderType = targetInstance.loaderType,
                                    mcVersion = targetInstance.mcVersion,
                                    loaderVersion = targetInstance.loaderVersion,
                                    isInstanceInstall = false,
                                    customStatusTitle = "PREPARING_RUNTIME: ${targetInstance.name}",
                                )
                                val request = GameLaunchRequest(
                                    instanceName = targetInstance.name,
                                    minecraftVersion = targetInstance.mcVersion,
                                    loaderType = if (targetInstance.loaderType == InstanceLoaderType.FABRIC) "Fabric" else "Vanilla",
                                    loaderVersion = targetInstance.loaderVersion,
                                    minecraftRootDir = instanceManager.rootDir,
                                    instanceGameDir = instanceManager.gameDirFor(targetInstance.id),
                                    username = activeUsername,
                                    preferredRenderer = selectedRenderer,
                                )
                                gameEngine.launchGame(
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
                                                fileName = "PREPARING_RENDERER (${selectedRenderer.displayName})",
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
                                installedRuntimes = gameEngine.jdkManager.listInstalledRuntimes()
                                rendererInstallStates = RendererType.entries.associateWith {
                                    gameEngine.rendererManager.isRendererInstalled(it)
                                }
                                delay(900L)
                                activeEngineTask = null
                                engineSnapshot = null
                                isProgressBarExpanded = false
                            }
                        }
                    },
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    backgroundConfig = currentBackground,
                    sharedTimeSeconds = sharedTimeSeconds,
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
            AnimatedVisibility(
                visible = effectiveProgressTask != null,
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
                        screenWidth = screenWidth,
                        screenHeight = screenHeight,
                        backgroundConfig = currentBackground,
                        sharedTimeSeconds = sharedTimeSeconds,
                        sceneGraphicsLayer = sceneGraphicsLayer,
                        topInset = 6.dp,
                    )
                }
            }
        }

        // 5. Full-screen 20% opacity + full-screen blur Dialog Overlay (Rename Account, Add Account, Add Instance, etc.)
        var isDialogInputFocused by remember(activeDialog) { mutableStateOf(false) }
        FullScreenBlurDialog(
            visible = activeDialog != null,
            isKeyboardActive = isDialogInputFocused,
            onDismiss = { activeDialog = null },
        ) {
            when (val dialog = displayedDialog) {
                is LauncherDialog.RenameAccount -> {
                    LauncherTextInputDialogContent(
                        title = stringResource(R.string.rename_account),
                        value = dialogInputText,
                        onValueChange = { dialogInputText = it },
                        confirmText = stringResource(R.string.action_save),
                        onFocusChange = { isDialogInputFocused = it },
                        onCancel = { activeDialog = null },
                        onConfirm = {
                            val trimmed = dialogInputText.trim()
                            if (trimmed.isNotEmpty()) {
                                accounts = accounts.map {
                                    if (it.id == dialog.account.id) it.copy(username = trimmed) else it
                                }
                            }
                            activeDialog = null
                        },
                    )
                }
                is LauncherDialog.AddAccount -> {
                    LauncherTextInputDialogContent(
                        title = stringResource(R.string.add_account),
                        value = dialogInputText,
                        onValueChange = { dialogInputText = it },
                        confirmText = stringResource(R.string.action_add),
                        onFocusChange = { isDialogInputFocused = it },
                        onCancel = { activeDialog = null },
                        onConfirm = {
                            val num = nextAccountCounter++
                            val trimmed = dialogInputText.trim().ifEmpty {
                                context.getString(R.string.default_new_player_format, num)
                            }
                            val newAcc = LauncherAccount(
                                id = "acc_$num",
                                username = trimmed,
                                typeRes = R.string.account_type_offline,
                            )
                            accounts = accounts + newAcc
                            selectedAccountId = newAcc.id
                            activeDialog = null
                        },
                    )
                }
                is LauncherDialog.AddInstance -> {
                    AddInstanceDialogContent(
                        onFocusChange = { isDialogInputFocused = it },
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
                null -> Unit
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
                .background(Color.Black.copy(alpha = 0.20f))
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

@Composable
private fun LauncherTextInputDialogContent(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    confirmText: String,
    onFocusChange: (Boolean) -> Unit = {},
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var isFocused by remember { mutableStateOf(false) }

    val inputBorderColor by animateColorAsState(
        targetValue = if (isFocused) Color(0xFFFFDF8E).copy(alpha = 0.75f) else Color.White.copy(alpha = 0.28f),
        animationSpec = tween(durationMillis = 200),
        label = "inputBorderColor",
    )
    val inputBgColor by animateColorAsState(
        targetValue = if (isFocused) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.12f),
        animationSpec = tween(durationMillis = 200),
        label = "inputBgColor",
    )

    val modalShape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .width(320.dp)
            .clip(modalShape)
            .background(Color(0x33101A2E))
            .border(1.dp, Color.White.copy(alpha = 0.28f), modalShape)
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
            color = Color.White,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
        )

        val inputShape = RoundedCornerShape(12.dp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                },
            ),
            cursorBrush = SolidColor(Color(0xFFFFDF8E)),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    isFocused = it.isFocused
                    onFocusChange(it.isFocused)
                }
                .clip(inputShape)
                .background(inputBgColor)
                .border(1.dp, inputBorderColor, inputShape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )

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
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), btnShape)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = Color.White.copy(alpha = 0.85f),
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
                        onClick = {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                            onConfirm()
                        },
                    )
                    .clip(btnShape)
                    .background(Color.White.copy(alpha = 0.24f))
                    .border(1.dp, Color.White.copy(alpha = 0.42f), btnShape)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = confirmText,
                    color = Color(0xFFFFDF8E),
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
    onInstall: (loaderType: InstanceLoaderType, mcVersion: String, loaderVersion: String?, instanceName: String) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var currentStep by remember { mutableStateOf<AddInstanceStep>(AddInstanceStep.SelectType) }
    var selectedFilter by remember { mutableStateOf(MinecraftVersionFilter.RELEASE) }
    var selectedMcVersion by remember { mutableStateOf("") }
    var selectedLoaderVersion by remember { mutableStateOf("") }
    var instanceNameInput by remember { mutableStateOf("") }
    var isNameFieldFocused by remember { mutableStateOf(false) }

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
            .background(Color(0x33101A2E))
            .border(1.dp, Color.White.copy(alpha = 0.28f), modalShape)
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
                            color = Color.White,
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
                                            if (enabled) Color.White.copy(alpha = 0.14f)
                                            else Color.White.copy(alpha = 0.05f),
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (enabled) Color.White.copy(alpha = 0.30f)
                                            else Color.White.copy(alpha = 0.12f),
                                            shape = cardShape,
                                        )
                                        .padding(vertical = 18.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(id = loaderType.iconRes),
                                        contentDescription = title,
                                        tint = if (enabled) Color.White else Color.White.copy(alpha = 0.38f),
                                        modifier = Modifier.size(30.dp),
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = title,
                                        color = if (enabled) Color.White else Color.White.copy(alpha = 0.42f),
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
                                    .background(Color.White.copy(alpha = 0.10f))
                                    .border(1.dp, Color.White.copy(alpha = 0.22f), btnShape)
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.action_cancel),
                                    color = Color.White.copy(alpha = 0.85f),
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
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.14f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Icon(
                                    painter = painterResource(id = step.loaderType.iconRes),
                                    contentDescription = loaderTitle,
                                    tint = Color(0xFFFFDF8E),
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = loaderTitle,
                                    color = Color(0xFFFFDF8E),
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
                                val chipBg by animateColorAsState(
                                    targetValue = if (isFilterSelected) Color(0xFFFFDF8E).copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
                                    animationSpec = tween(180),
                                    label = "filterChipBg",
                                )
                                val chipBorder by animateColorAsState(
                                    targetValue = if (isFilterSelected) Color(0xFFFFDF8E).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.16f),
                                    animationSpec = tween(180),
                                    label = "filterChipBorder",
                                )
                                val chipText by animateColorAsState(
                                    targetValue = if (isFilterSelected) Color(0xFFFFDF8E) else Color.White.copy(alpha = 0.78f),
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
                                        .background(chipBg)
                                        .border(1.dp, chipBorder, chipShape)
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
                                            color = Color(0xFFFFDF8E),
                                            strokeWidth = 2.5.dp,
                                            modifier = Modifier.size(28.dp),
                                        )
                                        Text(
                                            text = stringResource(R.string.loading_versions),
                                            color = Color.White.copy(alpha = 0.80f),
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
                                            color = Color(0xFFFFB2B4),
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
                                                .background(Color.White.copy(alpha = 0.16f))
                                                .border(1.dp, Color.White.copy(alpha = 0.34f), retryShape)
                                                .padding(horizontal = 14.dp, vertical = 7.dp),
                                        ) {
                                            Text(
                                                text = stringResource(R.string.action_retry),
                                                color = Color(0xFFFFDF8E),
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
                                        color = Color.White.copy(alpha = 0.70f),
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
                                            val rowShape = RoundedCornerShape(14.dp)
                                            val rowBg by animateColorAsState(
                                                targetValue = if (isSelected) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
                                                animationSpec = tween(180),
                                                label = "mcVersionRowBg",
                                            )
                                            val rowBorder by animateColorAsState(
                                                targetValue = if (isSelected) Color.White.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.14f),
                                                animationSpec = tween(180),
                                                label = "mcVersionRowBorder",
                                            )
                                            val rowTextColor by animateColorAsState(
                                                targetValue = if (isSelected) Color(0xFFFFDF8E) else Color.White,
                                                animationSpec = tween(180),
                                                label = "mcVersionRowText",
                                            )
                                            val badgeLabel = when {
                                                version == versionManifest?.latestRelease ||
                                                    version == versionManifest?.latestSnapshot -> stringResource(R.string.version_badge_latest)
                                                entry.type == "old_beta" -> stringResource(R.string.version_badge_beta)
                                                entry.type == "old_alpha" -> stringResource(R.string.version_badge_alpha)
                                                else -> null
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .smoothPressClickable(
                                                        pressScale = 0.97f,
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
                                                    Icon(
                                                        painter = painterResource(id = R.drawable.ic_instances),
                                                        contentDescription = version,
                                                        tint = rowTextColor,
                                                        modifier = Modifier.size(20.dp),
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Text(
                                                        text = version,
                                                        color = rowTextColor,
                                                        style = MaterialTheme.typography.bodyLarge.copy(
                                                            fontSize = 14.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        ),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }

                                                if (badgeLabel != null) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(Color(0xFFFFDF8E).copy(alpha = 0.18f))
                                                            .border(1.dp, Color(0xFFFFDF8E).copy(alpha = 0.40f), RoundedCornerShape(6.dp))
                                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                                    ) {
                                                        Text(
                                                            text = badgeLabel,
                                                            color = Color(0xFFFFDF8E),
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 11.sp,
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
                                    .background(Color.White.copy(alpha = 0.10f))
                                    .border(1.dp, Color.White.copy(alpha = 0.22f), btnShape)
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.action_back),
                                    color = Color.White.copy(alpha = 0.85f),
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
                                    .background(if (canProceed) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.08f))
                                    .border(
                                        1.dp,
                                        if (canProceed) Color.White.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.14f),
                                        btnShape,
                                    )
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.action_next),
                                    color = if (canProceed) Color(0xFFFFDF8E) else Color.White.copy(alpha = 0.35f),
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
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.14f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_fabric),
                                    contentDescription = step.mcVersion,
                                    tint = Color(0xFFFFDF8E),
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = step.mcVersion,
                                    color = Color(0xFFFFDF8E),
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
                                            color = Color(0xFFFFDF8E),
                                            strokeWidth = 2.5.dp,
                                            modifier = Modifier.size(28.dp),
                                        )
                                        Text(
                                            text = stringResource(R.string.loading_fabric_loaders),
                                            color = Color.White.copy(alpha = 0.80f),
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
                                            color = Color(0xFFFFB2B4),
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
                                                .background(Color.White.copy(alpha = 0.16f))
                                                .border(1.dp, Color.White.copy(alpha = 0.34f), retryShape)
                                                .padding(horizontal = 14.dp, vertical = 7.dp),
                                        ) {
                                            Text(
                                                text = stringResource(R.string.action_retry),
                                                color = Color(0xFFFFDF8E),
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
                                        color = Color.White.copy(alpha = 0.70f),
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
                                            val rowShape = RoundedCornerShape(14.dp)
                                            val rowBg by animateColorAsState(
                                                targetValue = if (isSelected) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
                                                animationSpec = tween(180),
                                                label = "loaderRowBg",
                                            )
                                            val rowBorder by animateColorAsState(
                                                targetValue = if (isSelected) Color.White.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.14f),
                                                animationSpec = tween(180),
                                                label = "loaderRowBorder",
                                            )
                                            val rowTextColor by animateColorAsState(
                                                targetValue = if (isSelected) Color(0xFFFFDF8E) else Color.White,
                                                animationSpec = tween(180),
                                                label = "loaderRowText",
                                            )

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .smoothPressClickable(
                                                        pressScale = 0.97f,
                                                        onClick = {
                                                            selectedLoaderVersion = loaderVer
                                                            instanceNameInput = context.getString(R.string.instance_fabric_short_format, step.mcVersion)
                                                            currentStep = AddInstanceStep.EnterInstanceName(
                                                                loaderType = step.loaderType,
                                                                mcVersion = step.mcVersion,
                                                                loaderVersion = loaderVer,
                                                            )
                                                        },
                                                    )
                                                    .clip(rowShape)
                                                    .background(rowBg)
                                                    .border(1.dp, rowBorder, rowShape)
                                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        painter = painterResource(id = R.drawable.ic_fabric),
                                                        contentDescription = loaderVer,
                                                        tint = rowTextColor,
                                                        modifier = Modifier.size(20.dp),
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Text(
                                                        text = loaderVer,
                                                        color = rowTextColor,
                                                        style = MaterialTheme.typography.bodyLarge.copy(
                                                            fontSize = 14.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        ),
                                                    )
                                                }

                                                if (loaderEntry.stable) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(Color(0xFFFFDF8E).copy(alpha = 0.18f))
                                                            .border(1.dp, Color(0xFFFFDF8E).copy(alpha = 0.40f), RoundedCornerShape(6.dp))
                                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                                    ) {
                                                        Text(
                                                            text = stringResource(R.string.loader_stable_badge),
                                                            color = Color(0xFFFFDF8E),
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 11.sp,
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
                                    .background(Color.White.copy(alpha = 0.10f))
                                    .border(1.dp, Color.White.copy(alpha = 0.22f), btnShape)
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.action_back),
                                    color = Color.White.copy(alpha = 0.85f),
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
                                    .background(if (canProceedLoader) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.08f))
                                    .border(
                                        1.dp,
                                        if (canProceedLoader) Color.White.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.14f),
                                        btnShape,
                                    )
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.action_next),
                                    color = if (canProceedLoader) Color(0xFFFFDF8E) else Color.White.copy(alpha = 0.35f),
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
                    val inputBorderColor by animateColorAsState(
                        targetValue = if (isNameFieldFocused) Color(0xFFFFDF8E).copy(alpha = 0.75f) else Color.White.copy(alpha = 0.28f),
                        animationSpec = tween(durationMillis = 200),
                        label = "instanceNameBorderColor",
                    )
                    val inputBgColor by animateColorAsState(
                        targetValue = if (isNameFieldFocused) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.12f),
                        animationSpec = tween(durationMillis = 200),
                        label = "instanceNameBgColor",
                    )
                    val badgeText = if (step.loaderType == InstanceLoaderType.FABRIC && step.loaderVersion != null) {
                        context.getString(R.string.instance_fabric_format, step.mcVersion, step.loaderVersion)
                    } else {
                        step.mcVersion
                    }

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
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.14f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Icon(
                                    painter = painterResource(id = step.loaderType.iconRes),
                                    contentDescription = badgeText,
                                    tint = Color(0xFFFFDF8E),
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = badgeText,
                                    color = Color(0xFFFFDF8E),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }
                        }

                        val inputShape = RoundedCornerShape(12.dp)
                        BasicTextField(
                            value = instanceNameInput,
                            onValueChange = { instanceNameInput = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus(force = true)
                                    keyboardController?.hide()
                                },
                            ),
                            cursorBrush = SolidColor(Color(0xFFFFDF8E)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged {
                                    isNameFieldFocused = it.isFocused
                                    onFocusChange(it.isFocused)
                                }
                                .clip(inputShape)
                                .background(inputBgColor)
                                .border(1.dp, inputBorderColor, inputShape)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        )

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
                                    .background(Color.White.copy(alpha = 0.10f))
                                    .border(1.dp, Color.White.copy(alpha = 0.22f), btnShape)
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.action_back),
                                    color = Color.White.copy(alpha = 0.85f),
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
                                    .background(Color.White.copy(alpha = 0.24f))
                                    .border(1.dp, Color.White.copy(alpha = 0.42f), btnShape)
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.action_install),
                                    color = Color(0xFFFFDF8E),
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
    sharedTimeSeconds: Float,
    modifier: Modifier = Modifier,
    sceneGraphicsLayer: GraphicsLayer? = null,
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
        targetValue = if (expanded) 224.dp else 38.dp,
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

    val accentColor = if (hasError) Color(0xFFFF7A7E) else Color(0xFFFFDF8E)

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
        // 1. Real-time Gaussian Blurred Backdrop Layer (blurs the live underlying panels:
        // Instances, Account, Settings, RightPlayAndSkinPanel, and LauncherBackground)
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
                    radius = 20.dp,
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

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color(0x460A1224),
                            0.5f to Color(0x3A0F1B33),
                            1.0f to Color(0x52070D1A),
                        ),
                    ),
            )
        }

        // 2. Frosted glass outer border
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        0.0f to accentColor.copy(alpha = 0.48f),
                        0.5f to Color.White.copy(alpha = 0.22f),
                        1.0f to Color.White.copy(alpha = 0.30f),
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
                        Icon(
                            painter = painterResource(id = task.loaderType.iconRes),
                            contentDescription = statusTitle,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusTitle,
                            color = if (hasError) Color(0xFFFFB2B4) else Color.White,
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
                                .background(Color.White.copy(alpha = 0.16f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(animatedProgress)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            if (hasError) {
                                                listOf(Color(0xFFE5484D), Color(0xFFFF7A7E))
                                            } else {
                                                listOf(Color(0xFFFFB84D), Color(0xFFFFDF8E))
                                            },
                                        ),
                                    ),
                            )
                        }
                        Text(
                            text = stringResource(R.string.percent_format, percentInt),
                            color = accentColor,
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
                            Icon(
                                painter = painterResource(id = task.loaderType.iconRes),
                                contentDescription = statusTitle,
                                tint = accentColor,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = statusTitle,
                                color = if (hasError) Color(0xFFFFB2B4) else Color.White,
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
                            color = accentColor,
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
                            .background(Color.White.copy(alpha = 0.15f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedProgress)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        if (hasError) {
                                            listOf(Color(0xFFE5484D), Color(0xFFFF7A7E))
                                        } else {
                                            listOf(Color(0xFFFFB84D), Color(0xFFFFDF8E))
                                        },
                                    ),
                                ),
                        )
                    }

                    // Current active file & transfer rate row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.downloading_item_format, currentFileName),
                            color = Color.White.copy(alpha = 0.86f),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(
                                R.string.download_speed_format,
                                String.format(Locale.US, "%.1f", downloadedMb),
                                String.format(Locale.US, "%.1f", totalMb),
                                String.format(Locale.US, "%.1f", speedMbPerSec),
                            ),
                            color = accentColor.copy(alpha = 0.90f),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
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
                                            itemError -> Color(0xFFE5484D).copy(alpha = 0.16f)
                                            itemActive -> Color.White.copy(alpha = 0.14f)
                                            else -> Color.White.copy(alpha = 0.05f)
                                        },
                                    )
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = item.fileName,
                                    color = when {
                                        itemError -> Color(0xFFFFB2B4)
                                        itemActive -> Color(0xFFFFDF8E)
                                        itemDone -> Color.White.copy(alpha = 0.85f)
                                        else -> Color.White.copy(alpha = 0.45f)
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
                                        itemError -> Color(0xFFFF7A7E)
                                        itemDone -> Color(0xFF7EE2A8)
                                        itemActive -> Color(0xFFFFDF8E)
                                        else -> Color.White.copy(alpha = 0.42f)
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
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
    accounts: List<LauncherAccount>,
    selectedAccountId: String,
    onAccountSelect: (LauncherAccount) -> Unit,
    onAddAccountClick: () -> Unit,
    onRequestRenameAccount: (LauncherAccount) -> Unit,
    onRemoveAccount: (LauncherAccount) -> Unit,
    selectedRenderer: RendererType,
    rendererInstallStates: Map<RendererType, Boolean>,
    installedRuntimes: Map<JavaRuntimeVersion, InstalledJavaRuntime?>,
    engineStatus: GameEngineStatus,
    engineLogs: List<String>,
    onRendererSelect: (RendererType) -> Unit,
    onDownloadRenderer: (RendererType) -> Unit,
    onDownloadRuntime: (JavaRuntimeVersion) -> Unit,
    onStopGame: () -> Unit,
    screenWidth: Dp,
    screenHeight: Dp,
    backgroundConfig: BackgroundConfig,
    sharedTimeSeconds: Float,
    modifier: Modifier = Modifier,
) {
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
        // 1. Real-time Gaussian Blurred Backdrop Layer shared across the entire unified Box.
        // Measured at constant full-screen size (wPx x hPx) and placed at (-outerMargin, -outerMargin)
        // so the GPU blur buffer never reallocates or shifts while AnimatedVisibility expands/shrinks!
        Box(
            modifier = Modifier
                .matchParentSize()
                .layout { measurable, constraints ->
                    val wPx = screenWidth.roundToPx().coerceAtLeast(constraints.maxWidth)
                    val hPx = screenHeight.roundToPx().coerceAtLeast(constraints.maxHeight)
                    val offsetX = -outerMargin.roundToPx()
                    val offsetY = -outerMargin.roundToPx()
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

            // Subtle frosted glass translucent tint overlay inside the blurred layer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color(0x380A1224),
                            0.5f to Color(0x2E0F1B33),
                            1.0f to Color(0x44070D1A),
                        ),
                    ),
            )
        }

        // 2. Unified frosted glass outer border around the combined Rail + Panel box
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        0.0f to Color.White.copy(alpha = 0.28f),
                        0.5f to Color.White.copy(alpha = 0.14f),
                        1.0f to Color.White.copy(alpha = 0.22f),
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
                                    0.0f to Color.White.copy(alpha = 0.18f),
                                    0.5f to Color.White.copy(alpha = 0.34f),
                                    1.0f to Color.White.copy(alpha = 0.18f),
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
                                )
                                LauncherDestination.ACCOUNT -> AccountPanelContent(
                                    accounts = accounts,
                                    selectedAccountId = selectedAccountId,
                                    onAccountSelect = onAccountSelect,
                                    onAddAccountClick = onAddAccountClick,
                                    onRequestRenameAccount = onRequestRenameAccount,
                                    onRemoveAccount = onRemoveAccount,
                                )
                                LauncherDestination.SETTINGS -> SettingsPanelContent(
                                    selectedRenderer = selectedRenderer,
                                    rendererInstallStates = rendererInstallStates,
                                    installedRuntimes = installedRuntimes,
                                    engineStatus = engineStatus,
                                    engineLogs = engineLogs,
                                    onRendererSelect = onRendererSelect,
                                    onDownloadRenderer = onDownloadRenderer,
                                    onDownloadRuntime = onDownloadRuntime,
                                    onStopGame = onStopGame,
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
private fun InstancesPanelContent(
    instances: List<LauncherInstance>,
    selectedInstanceId: String,
    onInstanceSelect: (LauncherInstance) -> Unit,
    onAddInstanceClick: () -> Unit,
    onRemoveInstance: (LauncherInstance) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                .background(Color.White.copy(alpha = 0.14f))
                .border(1.dp, Color.White.copy(alpha = 0.32f), addBtnShape)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_add),
                contentDescription = addInstanceText,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = addInstanceText,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }

        // Instances List below "Add Instance" button
        if (instances.isEmpty()) {
            val emptyShape = RoundedCornerShape(14.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(emptyShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), emptyShape)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_instances),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(32.dp),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.no_instances_installed),
                    color = Color.White.copy(alpha = 0.90f),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.no_instances_hint),
                    color = Color.White.copy(alpha = 0.60f),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = 0.85f,
                            stiffness = 400f,
                        ),
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
            ) {
                instances.forEach { instance ->
                    androidx.compose.runtime.key(instance.id) {
                        val isSelected = instance.id == selectedInstanceId
                        val instanceName = instance.name
                        val subtitle = if (instance.loaderType == InstanceLoaderType.FABRIC && !instance.loaderVersion.isNullOrBlank()) {
                            stringResource(R.string.instance_subtitle_fabric, instance.mcVersion, instance.loaderVersion)
                        } else {
                            stringResource(R.string.instance_subtitle_vanilla, instance.mcVersion)
                        }
                        val itemShape = RoundedCornerShape(14.dp)
                        val itemBg by animateColorAsState(
                            targetValue = if (isSelected) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                            label = "panelInstanceItemBg",
                        )
                        val itemBorder by animateColorAsState(
                            targetValue = if (isSelected) Color.White.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.14f),
                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                            label = "panelInstanceItemBorder",
                        )
                        val titleColor by animateColorAsState(
                            targetValue = if (isSelected) Color(0xFFFFDF8E) else Color.White,
                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                            label = "panelInstanceTitleColor",
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .smoothPressClickable(
                                    pressScale = 0.98f,
                                    onClick = { onInstanceSelect(instance) },
                                )
                                .clip(itemShape)
                                .background(itemBg)
                                .border(1.dp, itemBorder, itemShape)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    painter = painterResource(id = instance.iconRes),
                                    contentDescription = instanceName,
                                    tint = titleColor,
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
                                        color = Color.White.copy(alpha = 0.60f),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            val removeShape = RoundedCornerShape(10.dp)
                            Box(
                                modifier = Modifier
                                    .smoothPressClickable(
                                        pressScale = 0.92f,
                                        onClick = { onRemoveInstance(instance) },
                                    )
                                    .clip(removeShape)
                                    .background(Color(0xFFE5484D).copy(alpha = 0.22f))
                                    .border(1.dp, Color(0xFFFF7A7E).copy(alpha = 0.40f), removeShape)
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.action_remove),
                                    color = Color(0xFFFFB2B4),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 11.sp,
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
    onRemoveAccount: (LauncherAccount) -> Unit,
) {
    val context = LocalContext.current
    val skin = remember {
        runCatching { SkinUtils.fromAsset(context, SKIN_ASSET) }
            .mapCatching { SkinUtils.normalizeSkin(it) }
            .onFailure { Log.w("AccountPanel", "No usable $SKIN_ASSET, using default skin", it) }
            .getOrElse { DefaultSkin.create() }
    }
    val skinHeadBitmap = remember(skin) {
        extractSkinHeadBitmap(skin)
    }
    val cape = remember {
        runCatching { SkinUtils.fromAsset(context, CAPE_ASSET) }
            .mapCatching { SkinUtils.normalizeCape(it) }
            .onFailure { Log.w("AccountPanel", "No usable $CAPE_ASSET, using default cape", it) }
            .getOrElse { DefaultSkin.createCape() }
    }

    val selectedAccount = remember(accounts, selectedAccountId) {
        accounts.firstOrNull { it.id == selectedAccountId } ?: accounts.firstOrNull()
    }
    val defaultPlayerName = stringResource(R.string.default_player_name)
    val previewUsername = selectedAccount?.username ?: defaultPlayerName

    // Account ID whose {•••} options menu is currently expanded
    var openOptionsAccountId by remember { mutableStateOf<String?>(null) }

    // Idle animation, rotatable by dragging, NOT auto-rotating
    val accountSkinConfig = remember(previewUsername) {
        SkinViewerConfig(
            animation = SkinAnimations.Idle,
            autoRotate = false,
            dragToRotate = true,
            allowPitch = true,
            pinchToZoom = false,
            doubleTapToReset = true,
            initialYaw = -20f,
            initialPitch = 6f,
            initialZoom = 1.15f,
            backgroundColor = 0x00000000,
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
                        .background(Color.White.copy(alpha = 0.14f))
                        .border(1.dp, Color.White.copy(alpha = 0.32f), addBtnShape)
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_add),
                        contentDescription = addAccountText,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = addAccountText,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }

                // Account List: [{profileskinhead} {username}     {•••}]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = 0.85f,
                                stiffness = 400f,
                            ),
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
                ) {
                    accounts.forEach { account ->
                        androidx.compose.runtime.key(account.id) {
                            val isSelected = account.id == selectedAccount?.id
                            val isMenuOpen = openOptionsAccountId == account.id
                            val itemShape = RoundedCornerShape(14.dp)
                            val itemBg by animateColorAsState(
                                targetValue = if (isSelected) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
                                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                                label = "accountItemBg",
                            )
                            val itemBorder by animateColorAsState(
                                targetValue = if (isSelected) Color.White.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.14f),
                                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                                label = "accountItemBorder",
                            )
                            val titleColor by animateColorAsState(
                                targetValue = if (isSelected) Color(0xFFFFDF8E) else Color.White,
                                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                                label = "accountTitleColor",
                            )
                            val moreBtnBg by animateColorAsState(
                                targetValue = if (isMenuOpen) Color.White.copy(alpha = 0.22f) else Color.Transparent,
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
                                                openOptionsAccountId = null
                                                onAccountSelect(account)
                                            },
                                        )
                                        .clip(itemShape)
                                        .background(itemBg)
                                        .border(1.dp, itemBorder, itemShape)
                                        .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    // Left: {profileskinhead} {username}
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Image(
                                            bitmap = skinHeadBitmap,
                                            contentDescription = account.username,
                                            filterQuality = FilterQuality.None,
                                            contentScale = ContentScale.FillBounds,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .border(
                                                    width = 1.dp,
                                                    color = Color.White.copy(alpha = 0.28f),
                                                    shape = RoundedCornerShape(6.dp),
                                                ),
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
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
                                    }

                                    // Right: More options {•••} (plus smoothly animated inline Rename / Remove)
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
                                                // Rename option
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
                                                        .background(Color.White.copy(alpha = 0.16f))
                                                        .border(1.dp, Color.White.copy(alpha = 0.30f), actionShape)
                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.action_rename),
                                                        color = Color.White,
                                                        style = MaterialTheme.typography.labelMedium.copy(
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                        ),
                                                    )
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
                                                        .background(Color(0xFFE5484D).copy(alpha = 0.25f))
                                                        .border(1.dp, Color(0xFFFF7A7E).copy(alpha = 0.45f), actionShape)
                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.action_remove),
                                                        color = Color(0xFFFFB2B4),
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
                                                tint = Color.White.copy(alpha = 0.9f),
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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    SkinViewer(
                        modifier = Modifier.fillMaxSize(),
                        skin = skin,
                        cape = cape,
                        config = accountSkinConfig,
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
    sharedTimeSeconds: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val skin = remember {
        runCatching { SkinUtils.fromAsset(context, SKIN_ASSET) }
            .mapCatching { SkinUtils.normalizeSkin(it) }
            .onFailure { Log.w("LauncherScreen", "No usable $SKIN_ASSET, using default skin", it) }
            .getOrNull()
    }
    val cape = remember {
        runCatching { SkinUtils.fromAsset(context, CAPE_ASSET) }
            .mapCatching { SkinUtils.normalizeCape(it) }
            .onFailure { Log.w("LauncherScreen", "No usable $CAPE_ASSET, using default cape", it) }
            .getOrElse { DefaultSkin.createCape() }
    }

    val skinConfig = remember(activeUsername) {
        SkinViewerConfig(
            animation = SkinAnimations.CoolDisplay,
            autoRotate = false,
            dragToRotate = false,
            allowPitch = false,
            pinchToZoom = false,
            doubleTapToReset = false,
            initialYaw = -22f,
            initialPitch = 5f,
            initialZoom = 1.28f,
            backgroundColor = 0x00000000,
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
            // 1. Real-time Gaussian Blurred Backdrop Layer:
            // Blurred at constant full-screen size (wPx x hPx) and positioned mathematically
            // so the GPU blur buffer never reallocates or lags during resize!
            Box(
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val wPx = screenWidth.roundToPx().coerceAtLeast(constraints.maxWidth)
                        val hPx = screenHeight.roundToPx().coerceAtLeast(constraints.maxHeight)
                        val rightInsetPx = (panelEndPadding + playColumnWidth + rowSpacing).roundToPx()
                        val bottomInsetPx = changeBottomPadding.roundToPx()
                        val offsetX = -(wPx - rightInsetPx - constraints.maxWidth)
                        val offsetY = -(hPx - bottomInsetPx - constraints.maxHeight)
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

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color(0x380A1224),
                                0.5f to Color(0x2E0F1B33),
                                1.0f to Color(0x44070D1A),
                            ),
                        ),
                )
            }

            // 2. Frosted glass outer border
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            0.0f to Color.White.copy(alpha = 0.34f),
                            0.5f to Color.White.copy(alpha = 0.16f),
                            1.0f to Color.White.copy(alpha = 0.26f),
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
                            tint = Color.White,
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
                            tint = Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.size(30.dp),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.no_instances_installed),
                            color = Color.White.copy(alpha = 0.90f),
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
                                .background(Color.White.copy(alpha = 0.16f))
                                .border(1.dp, Color.White.copy(alpha = 0.34f), addShape)
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_add),
                                contentDescription = stringResource(R.string.add_instance),
                                tint = Color(0xFFFFDF8E),
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.add_instance),
                                color = Color(0xFFFFDF8E),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
                    ) {
                        instances.forEach { instance ->
                            val isSelected = instance.id == selectedInstanceId
                            val instanceName = instance.name
                            val itemShape = RoundedCornerShape(14.dp)
                            val itemBg by animateColorAsState(
                                targetValue = if (isSelected) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
                                animationSpec = tween(durationMillis = 200),
                                label = "instanceItemBg",
                            )
                            val itemBorder by animateColorAsState(
                                targetValue = if (isSelected) Color.White.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.14f),
                                animationSpec = tween(durationMillis = 200),
                                label = "instanceItemBorder",
                            )
                            val itemContentColor by animateColorAsState(
                                targetValue = if (isSelected) Color(0xFFFFDF8E) else Color.White.copy(alpha = 0.92f),
                                animationSpec = tween(durationMillis = 200),
                                label = "instanceItemContent",
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .smoothPressClickable(
                                        pressScale = 0.96f,
                                        onClick = { onInstanceSelect(instance) },
                                    )
                                    .clip(itemShape)
                                    .background(itemBg)
                                    .border(1.dp, itemBorder, itemShape)
                                    .padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start,
                            ) {
                                Icon(
                                    painter = painterResource(id = instance.iconRes),
                                    contentDescription = instanceName,
                                    tint = itemContentColor,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(modifier = Modifier.width(10.dp))
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
                // 1. Real-time Gaussian Blurred Backdrop Layer (blurred at constant full-screen size)
                Box(
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            val wPx = screenWidth.roundToPx().coerceAtLeast(constraints.maxWidth)
                            val hPx = screenHeight.roundToPx().coerceAtLeast(constraints.maxHeight)
                            val offsetX = -(wPx - panelEndPadding.roundToPx() - constraints.maxWidth)
                            val offsetY = -(hPx - playBottomPadding.roundToPx() - constraints.maxHeight)
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

                    // Frosted glass translucent tint overlay inside the blurred layer
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.0f to Color(0x380A1224),
                                    0.5f to Color(0x2E0F1B33),
                                    1.0f to Color(0x44070D1A),
                                ),
                            ),
                    )
                }

                // 2. Frosted glass outer border
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                0.0f to Color.White.copy(alpha = 0.34f),
                                0.5f to Color.White.copy(alpha = 0.16f),
                                1.0f to Color.White.copy(alpha = 0.26f),
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
                        color = Color.White,
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
                            color = Color.White.copy(alpha = 0.78f),
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
    val itemShape = RoundedCornerShape(16.dp)
    val bgColor by animateColorAsState(
        targetValue = if (selected) Color.White.copy(alpha = 0.22f) else Color.Transparent,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "navItemBg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) Color.White.copy(alpha = 0.42f) else Color.Transparent,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "navItemBorder",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) Color(0xFFFFDF8E) else Color.White.copy(alpha = 0.88f),
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
                    .background(Color(0xFFFFDF8E)),
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

@Composable
private fun SettingsPanelContent(
    selectedRenderer: RendererType,
    rendererInstallStates: Map<RendererType, Boolean>,
    installedRuntimes: Map<JavaRuntimeVersion, InstalledJavaRuntime?>,
    engineStatus: GameEngineStatus,
    engineLogs: List<String>,
    onRendererSelect: (RendererType) -> Unit,
    onDownloadRenderer: (RendererType) -> Unit,
    onDownloadRuntime: (JavaRuntimeVersion) -> Unit,
    onStopGame: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Left Column: Renderer Selection (MobileGlues & Krypton Wrapper) + Engine Console
        Column(
            modifier = Modifier
                .weight(1.08f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_renderer_title),
                color = Color.White,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )

            RendererType.entries.forEach { renderer ->
                val isSelected = renderer == selectedRenderer
                val isInstalled = rendererInstallStates[renderer] == true
                val cardShape = RoundedCornerShape(14.dp)
                val cardBg by animateColorAsState(
                    targetValue = if (isSelected) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f),
                    animationSpec = tween(durationMillis = 200),
                    label = "rendererCardBg",
                )
                val cardBorder by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFFFFDF8E).copy(alpha = 0.65f) else Color.White.copy(alpha = 0.16f),
                    animationSpec = tween(durationMillis = 200),
                    label = "rendererCardBorder",
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .smoothPressClickable(
                            pressScale = 0.98f,
                            onClick = { onRendererSelect(renderer) },
                        )
                        .clip(cardShape)
                        .background(cardBg)
                        .border(1.dp, cardBorder, cardShape)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = renderer.displayName,
                                color = if (isSelected) Color(0xFFFFDF8E) else Color.White,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            Text(
                                text = renderer.glVersionLabel,
                                color = Color.White.copy(alpha = 0.72f),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (isSelected) {
                                Text(
                                    text = stringResource(R.string.renderer_badge_active),
                                    color = Color(0xFF7EE2A8),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }
                            val btnShape = RoundedCornerShape(8.dp)
                            Box(
                                modifier = Modifier
                                    .smoothPressClickable(
                                        pressScale = 0.94f,
                                        onClick = { onDownloadRenderer(renderer) },
                                    )
                                    .clip(btnShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .border(1.dp, Color.White.copy(alpha = 0.28f), btnShape)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (isInstalled) {
                                        stringResource(R.string.action_reinstall)
                                    } else {
                                        stringResource(R.string.action_download)
                                    },
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                )
                            }
                        }
                    }

                    Text(
                        text = renderer.subtitle,
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.settings_engine_console_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                if (engineStatus.state == GameEngineState.RUNNING) {
                    val stopShape = RoundedCornerShape(8.dp)
                    Box(
                        modifier = Modifier
                            .smoothPressClickable(
                                pressScale = 0.94f,
                                onClick = onStopGame,
                            )
                            .clip(stopShape)
                            .background(Color(0xFFE5484D).copy(alpha = 0.28f))
                            .border(1.dp, Color(0xFFFF7A7E).copy(alpha = 0.50f), stopShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.action_stop_game),
                            color = Color(0xFFFFB2B4),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
            }

            val consoleShape = RoundedCornerShape(12.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(consoleShape)
                    .background(Color.Black.copy(alpha = 0.28f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), consoleShape)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "${engineStatus.state.name}: ${engineStatus.stageMessage}",
                    color = Color(0xFFFFDF8E),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                if (engineLogs.isEmpty()) {
                    Text(
                        text = stringResource(R.string.engine_idle_hint),
                        color = Color.White.copy(alpha = 0.58f),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    )
                } else {
                    engineLogs.takeLast(6).forEach { logLine ->
                        Text(
                            text = logLine,
                            color = Color.White.copy(alpha = 0.80f),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Right Column: OpenJDK Runtime Manager (ZalithLauncher2 JRE 8, 17, 21, 25)
        Column(
            modifier = Modifier
                .weight(0.92f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_openjdk_title),
                color = Color.White,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )

            JavaRuntimeVersion.entries.forEach { runtimeVersion ->
                val installed = installedRuntimes[runtimeVersion]
                val isInstalled = installed != null
                val itemShape = RoundedCornerShape(14.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(itemShape)
                        .background(Color.White.copy(alpha = if (isInstalled) 0.14f else 0.08f))
                        .border(
                            width = 1.dp,
                            color = if (isInstalled) {
                                Color(0xFF7EE2A8).copy(alpha = 0.45f)
                            } else {
                                Color.White.copy(alpha = 0.16f)
                            },
                            shape = itemShape,
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = runtimeVersion.displayName,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            Text(
                                text = if (isInstalled) {
                                    stringResource(R.string.runtime_status_installed)
                                } else {
                                    stringResource(R.string.runtime_status_not_installed)
                                },
                                color = if (isInstalled) Color(0xFF7EE2A8) else Color.White.copy(alpha = 0.55f),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                        }
                        Text(
                            text = runtimeVersion.supportedMinecraftRange,
                            color = Color.White.copy(alpha = 0.68f),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        )
                    }

                    val actionShape = RoundedCornerShape(10.dp)
                    Box(
                        modifier = Modifier
                            .smoothPressClickable(
                                pressScale = 0.94f,
                                onClick = { onDownloadRuntime(runtimeVersion) },
                            )
                            .clip(actionShape)
                            .background(Color.White.copy(alpha = 0.16f))
                            .border(1.dp, Color.White.copy(alpha = 0.30f), actionShape)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (isInstalled) {
                                stringResource(R.string.action_reinstall)
                            } else {
                                stringResource(R.string.action_download)
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
            }
        }
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
