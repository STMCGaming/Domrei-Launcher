package com.domrei.skinview

/**
 * Rotation (radians) and translation (skin pixels) of one model part relative to its default pivot.
 *
 * Rotation is applied in Z, Y, X order (same as Minecraft). Axes: x = model's left (viewer's right
 * when facing the camera), y = up, z = forward (towards the viewer when facing the camera).
 */
class PartPose {
    var rotX = 0f
    var rotY = 0f
    var rotZ = 0f
    var x = 0f
    var y = 0f
    var z = 0f

    /** Set to false to hide this part for the current frame. */
    var visible = true

    fun reset() {
        rotX = 0f; rotY = 0f; rotZ = 0f
        x = 0f; y = 0f; z = 0f
        visible = true
    }

    fun setRotation(x: Float, y: Float, z: Float) {
        rotX = x; rotY = y; rotZ = z
    }

    fun setOffset(x: Float, y: Float, z: Float) {
        this.x = x; this.y = y; this.z = z
    }
}

/** Pose of the full player for one frame. Animations mutate this. */
class PlayerPose {
    /** Whole model transform (rotate around model center). */
    val root = PartPose()
    val head = PartPose()
    val body = PartPose()
    val rightArm = PartPose()
    val leftArm = PartPose()
    val rightLeg = PartPose()
    val leftLeg = PartPose()

    /** Cape rotation. [DEFAULT_CAPE_ANGLE] tilt is added on top. */
    val cape = PartPose()

    fun reset() {
        root.reset(); head.reset(); body.reset()
        rightArm.reset(); leftArm.reset()
        rightLeg.reset(); leftLeg.reset()
        cape.reset()
    }

    companion object {
        /** Default backward tilt of the cape in radians. */
        const val DEFAULT_CAPE_ANGLE = 0.1f
    }
}
