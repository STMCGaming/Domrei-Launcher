package com.domrei.skinview

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * An animation that poses the player for a given time.
 *
 * Implement this to create custom animations. [animate] is called on the render thread once per frame
 * with a freshly reset [pose]; mutate it to move parts.
 */
fun interface SkinAnimation {
    /**
     * @param pose pose to modify (already reset to the default standing pose).
     * @param time animation time in seconds (already multiplied by the configured speed).
     */
    fun animate(pose: PlayerPose, time: Float)
}

/** Built-in animations. */
object SkinAnimations {

    /** Gentle breathing and arm sway. */
    val Idle = SkinAnimation { pose, t ->
        val sway = sin(t * 1.6f) * 0.05f
        pose.rightArm.rotZ = -0.05f - sway
        pose.leftArm.rotZ = 0.05f + sway
        pose.head.rotX = sin(t * 0.8f) * 0.04f
        pose.head.rotY = sin(t * 0.5f) * 0.08f
        pose.cape.rotX = sin(t * 1.2f) * 0.03f
    }

    /** Walk cycle. */
    val Walk = SkinAnimation { pose, t ->
        val phase = t * 5f
        val swing = sin(phase) * 0.6f
        pose.rightLeg.rotX = swing
        pose.leftLeg.rotX = -swing
        pose.rightArm.rotX = -swing
        pose.leftArm.rotX = swing
        pose.root.y = abs(sin(phase)) * 0.4f
        pose.head.rotY = sin(phase * 0.5f) * 0.05f
        pose.cape.rotX = 0.15f + abs(sin(phase)) * 0.1f
    }

    /** Run cycle with bent posture. */
    val Run = SkinAnimation { pose, t ->
        val phase = t * 9f
        val swing = sin(phase) * 1.1f
        pose.rightLeg.rotX = swing
        pose.leftLeg.rotX = -swing
        pose.rightArm.rotX = -swing
        pose.leftArm.rotX = swing
        pose.rightArm.rotZ = -0.1f
        pose.leftArm.rotZ = 0.1f
        pose.root.y = abs(sin(phase)) * 1f
        pose.cape.rotX = 0.6f + sin(phase * 2f) * 0.08f
    }

    /** Waving with the right arm. */
    val Wave = SkinAnimation { pose, t ->
        pose.rightArm.rotZ = (-PI * 0.8).toFloat() + sin(t * 8f) * 0.35f
        pose.leftArm.rotZ = 0.05f + sin(t * 1.6f) * 0.04f
        pose.head.rotZ = sin(t * 2f) * 0.06f
    }

    /** Arms out, slowly flying/floating. */
    val Fly = SkinAnimation { pose, t ->
        pose.root.y = sin(t * 2f) * 1.5f
        pose.rightArm.rotZ = -1.2f + sin(t * 2f) * 0.1f
        pose.leftArm.rotZ = 1.2f - sin(t * 2f) * 0.1f
        pose.rightLeg.rotX = 0.15f
        pose.leftLeg.rotX = -0.15f
        pose.cape.rotX = 0.8f + sin(t * 3f) * 0.15f
    }

    /** Head nodding "yes". */
    val Nod = SkinAnimation { pose, t ->
        pose.head.rotX = sin(t * 6f) * 0.3f
    }

    /**
     * Clean Lunar / NameMC style launcher showcase pose:
     * Grounded feet with a subtle natural stance, relaxed arms with gentle breathing,
     * and the head looking smoothly toward the viewer.
     */
    val CoolDisplay = SkinAnimation { pose, t ->
        val sway = sin(t * 1.5f) * 0.03f

        // Anchor feet near the bottom of the viewport above the Play button while leaving room for nametag
        pose.root.y = -2.4f

        // Head looking naturally toward the viewer (no awkward sideways neck tilt)
        pose.head.rotY = 0.14f + sin(t * 0.6f) * 0.04f
        pose.head.rotX = sin(t * 0.9f) * 0.025f

        // Relaxed, natural arms with subtle opposite swing and breathing
        pose.rightArm.rotX = 0.10f + sin(t * 0.9f) * 0.03f
        pose.rightArm.rotZ = -0.07f - sway

        pose.leftArm.rotX = -0.10f - sin(t * 0.9f) * 0.03f
        pose.leftArm.rotZ = 0.07f + sway

        // Clean grounded legs (flush with torso, subtle natural stance)
        pose.rightLeg.rotX = -0.06f
        pose.rightLeg.rotZ = -0.02f

        pose.leftLeg.rotX = 0.06f
        pose.leftLeg.rotZ = 0.02f

        // Gentle cape movement
        pose.cape.rotX = 0.12f + sin(t * 1.4f) * 0.03f
    }

    /** All built-in animations, handy for pickers. */
    val all: List<Pair<String, SkinAnimation>> = listOf(
        "Idle" to Idle,
        "CoolDisplay" to CoolDisplay,
        "Walk" to Walk,
        "Run" to Run,
        "Wave" to Wave,
        "Fly" to Fly,
        "Nod" to Nod,
    )
}
