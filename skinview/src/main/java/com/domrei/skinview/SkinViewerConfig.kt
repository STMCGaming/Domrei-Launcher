package com.domrei.skinview

import androidx.compose.runtime.Immutable

/** Arm model of a player skin. */
enum class SkinModel {
    /** Detect from the skin texture (transparent 4th arm column = slim). */
    AUTO,

    /** "Steve" style, 4px wide arms. */
    CLASSIC,

    /** "Alex" style, 3px wide arms. */
    SLIM,
}

/** Visibility of the inner (base) and outer (overlay: hat, jacket, sleeves, pants) layers of a body part. */
@Immutable
data class LayerVisibility(
    val inner: Boolean = true,
    val outer: Boolean = true,
) {
    companion object {
        val Both = LayerVisibility(inner = true, outer = true)
        val InnerOnly = LayerVisibility(inner = true, outer = false)
        val OuterOnly = LayerVisibility(inner = false, outer = true)
        val Hidden = LayerVisibility(inner = false, outer = false)
    }
}

/** Per-part visibility of the player model. */
@Immutable
data class PartVisibility(
    val head: LayerVisibility = LayerVisibility.Both,
    val body: LayerVisibility = LayerVisibility.Both,
    val rightArm: LayerVisibility = LayerVisibility.Both,
    val leftArm: LayerVisibility = LayerVisibility.Both,
    val rightLeg: LayerVisibility = LayerVisibility.Both,
    val leftLeg: LayerVisibility = LayerVisibility.Both,
    val cape: Boolean = true,
) {
    /** Returns a copy with every outer layer set to [visible]. */
    fun withOuterLayers(visible: Boolean) = copy(
        head = head.copy(outer = visible),
        body = body.copy(outer = visible),
        rightArm = rightArm.copy(outer = visible),
        leftArm = leftArm.copy(outer = visible),
        rightLeg = rightLeg.copy(outer = visible),
        leftLeg = leftLeg.copy(outer = visible),
    )
}

/** Simple directional + ambient lighting. Direction is in camera space (x right, y up, z towards viewer). */
@Immutable
data class SkinLighting(
    val ambient: Float = 0.65f,
    val diffuse: Float = 0.45f,
    val directionX: Float = 0.4f,
    val directionY: Float = 0.8f,
    val directionZ: Float = 1.0f,
) {
    companion object {
        /** Flat, unlit rendering (texture colors exactly as in the skin file). */
        val Flat = SkinLighting(ambient = 1f, diffuse = 0f)
    }
}

/**
 * Full configuration of a skin viewer. Every field can be changed at runtime.
 *
 * Angles are in degrees.
 */
@Immutable
data class SkinViewerConfig(
    /** Arm model. [SkinModel.AUTO] detects it from the skin. */
    val model: SkinModel = SkinModel.AUTO,
    /** Which parts / layers are drawn. */
    val parts: PartVisibility = PartVisibility(),

    /** Animation to play, or `null` for a static T-less standing pose. */
    val animation: SkinAnimation? = SkinAnimations.Idle,
    /** Playback speed multiplier for [animation]. */
    val animationSpeed: Float = 1f,
    /** Freezes the animation at its current frame. */
    val animationPaused: Boolean = false,

    /** Spin the model continuously around the vertical axis. */
    val autoRotate: Boolean = false,
    /** Auto-rotate speed in degrees per second (negative = other direction). */
    val autoRotateSpeed: Float = 30f,

    /** Allow the user to rotate the model by dragging. */
    val dragToRotate: Boolean = true,
    /** Drag sensitivity in degrees per pixel. */
    val dragSensitivity: Float = 0.4f,
    /** Allow vertical drag to change pitch (otherwise yaw only). */
    val allowPitch: Boolean = true,
    /** Pitch limit in degrees, applied in both directions. */
    val maxPitch: Float = 80f,
    /** Allow pinch-to-zoom. */
    val pinchToZoom: Boolean = true,
    /** Allow double tap to reset camera to [initialYaw]/[initialPitch]/[initialZoom]. */
    val doubleTapToReset: Boolean = true,

    /** Starting yaw (rotation around vertical axis). 0 = facing the camera. */
    val initialYaw: Float = -30f,
    /** Starting pitch. Positive looks at the model from above. */
    val initialPitch: Float = 10f,
    /** Starting zoom, 1 = the whole model fits the view. */
    val initialZoom: Float = 1f,
    val minZoom: Float = 0.5f,
    val maxZoom: Float = 3f,
    /** Vertical field of view of the camera. */
    val fieldOfView: Float = 45f,

    /** Background color as ARGB int. Alpha < 255 needs [transparentBackground]. */
    val backgroundColor: Int = 0xFF000000.toInt(),
    /**
     * Render with a translucent surface so content behind the view shows through.
     * Only read when the view is created. The surface is placed on top of the window,
     * so composables drawn over the viewer will be hidden behind it.
     */
    val transparentBackground: Boolean = false,

    val lighting: SkinLighting = SkinLighting(),

    /**
     * Minecraft-style floating nametag above the player's head.
     * Set to null to hide.
     */
    val nameTag: String? = null,

    /**
     * Size scale multiplier for the nametag. Default is 1.6f for prominent readability.
     */
    val nameTagScale: Float = 1.6f,
)
