/*
 * Minosoft
 * Copyright (C) 2021 Moritz Zwerger, Lukas Eisenhauer
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * This software is not affiliated with Mojang AB, the original developer of Minecraft.
 */

package de.bixilon.minosoft.gui.input.camera

import de.bixilon.minosoft.Minosoft
import de.bixilon.minosoft.config.config.game.controls.KeyBindingsNames
import de.bixilon.minosoft.data.entities.EntityRotation
import de.bixilon.minosoft.data.entities.entities.player.PlayerEntity
import de.bixilon.minosoft.data.mappings.biomes.Biome
import de.bixilon.minosoft.gui.rendering.RenderConstants
import de.bixilon.minosoft.gui.rendering.RenderWindow
import de.bixilon.minosoft.gui.rendering.shader.Shader
import de.bixilon.minosoft.gui.rendering.util.VecUtil
import de.bixilon.minosoft.gui.rendering.util.VecUtil.blockPosition
import de.bixilon.minosoft.gui.rendering.util.VecUtil.chunkPosition
import de.bixilon.minosoft.gui.rendering.util.VecUtil.inChunkSectionPosition
import de.bixilon.minosoft.gui.rendering.util.VecUtil.sectionHeight
import de.bixilon.minosoft.gui.rendering.util.abstractions.ScreenResizeCallback
import de.bixilon.minosoft.protocol.network.connection.PlayConnection
import de.bixilon.minosoft.protocol.packets.c2s.play.PositionAndRotationC2SP
import de.bixilon.minosoft.protocol.packets.c2s.play.PositionC2SP
import de.bixilon.minosoft.protocol.packets.c2s.play.RotationC2SP
import de.bixilon.minosoft.protocol.protocol.ProtocolDefinition
import glm_.func.cos
import glm_.func.rad
import glm_.func.sin
import glm_.glm
import glm_.mat4x4.Mat4
import glm_.vec2.Vec2
import glm_.vec2.Vec2i
import glm_.vec3.Vec3
import glm_.vec3.Vec3i

class Camera(
    val connection: PlayConnection,
    var fov: Float,
    val renderWindow: RenderWindow,
) : ScreenResizeCallback {
    private var mouseSensitivity = Minosoft.getConfig().config.game.camera.moseSensitivity
    private val walkingSpeed get() = connection.player.baseAbilities.walkingSpeed * ProtocolDefinition.TICKS_PER_SECOND
    private val flyingSpeed get() = connection.player.baseAbilities.flyingSpeed * ProtocolDefinition.TICKS_PER_SECOND
    var cameraPosition = Vec3(0.0f, 0.0f, 0.0f)
    private var lastMouseX = 0.0
    private var lastMouseY = 0.0
    val playerEntity: PlayerEntity
        get() = connection.player.entity
    var yaw = 0.0
    var pitch = 0.0
    private var zoom = 0.0f

    private var lastMovementPacketSent = 0L
    private var currentPositionSent = false
    private var currentRotationSent = false

    var cameraFront = Vec3(0.0f, 0.0f, -1.0f)
    var cameraRight = Vec3(0.0f, 0.0f, -1.0f)
    private var cameraUp = Vec3(0.0f, 1.0f, 0.0f)

    var blockPosition: Vec3i = Vec3i(0, 0, 0)
        private set
    var currentBiome: Biome? = null
        private set
    var chunkPosition: Vec2i = Vec2i(0, 0)
        private set
    var sectionHeight: Int = 0
        private set
    var inChunkSectionPosition: Vec3i = Vec3i(0, 0, 0)
        private set

    val frustum: Frustum = Frustum(this)

    private val shaders: MutableSet<Shader> = mutableSetOf()
    private val frustumChangeCallbacks: MutableSet<FrustumChangeCallback> = mutableSetOf()


    fun mouseCallback(xPos: Double, yPos: Double) {
        var xOffset = xPos - this.lastMouseX
        var yOffset = yPos - this.lastMouseY
        lastMouseX = xPos
        lastMouseY = yPos
        if (renderWindow.inputHandler.currentKeyConsumer != null) {
            return
        }
        xOffset *= mouseSensitivity
        yOffset *= mouseSensitivity
        var yaw = xOffset.toFloat() + playerEntity.rotation.headYaw
        var pitch = yOffset.toFloat() + playerEntity.rotation.pitch

        // make sure that when pitch is out of bounds, screen doesn't get flipped
        if (pitch > 89.9) {
            pitch = 89.9f
        } else if (pitch < -89.9) {
            pitch = -89.9f
        }
        if (yaw > 180) {
            yaw -= 360
        } else if (yaw < -180) {
            yaw += 360
        }
        yaw %= 180
        setRotation(yaw, pitch)
    }

    fun init(renderWindow: RenderWindow) {
        renderWindow.inputHandler.registerCheckCallback(
            KeyBindingsNames.MOVE_SPRINT,
            KeyBindingsNames.MOVE_FORWARD,
            KeyBindingsNames.MOVE_BACKWARDS,
            KeyBindingsNames.MOVE_LEFT,
            KeyBindingsNames.MOVE_RIGHT,
            KeyBindingsNames.MOVE_FLY_UP,
            KeyBindingsNames.MOVE_FLY_DOWN,
            KeyBindingsNames.ZOOM,
            KeyBindingsNames.MOVE_JUMP,
        )

        frustum.recalculate()
        for (frustumChangeCallback in frustumChangeCallbacks) {
            frustumChangeCallback.onFrustumChange()
        }
    }

    fun handleInput(deltaTime: Double) {
        if (renderWindow.inputHandler.currentKeyConsumer != null) { // ToDo
            return
        }
        var cameraSpeed = if (connection.player.entity.isFlying) {
            flyingSpeed
        } else {
            walkingSpeed
        } * deltaTime
        val movementFront = Vec3(cameraFront)
        if (!Minosoft.getConfig().config.game.camera.noCipMovement) {
            movementFront.y = 0.0f
            movementFront.normalizeAssign() // when moving forwards, do not move down
        }
        if (renderWindow.inputHandler.isKeyBindingDown(KeyBindingsNames.MOVE_SPRINT)) {
            cameraSpeed *= 5
        }
        var deltaMovement = Vec3()
        if (renderWindow.inputHandler.isKeyBindingDown(KeyBindingsNames.MOVE_FORWARD)) {
            deltaMovement = deltaMovement + movementFront * cameraSpeed
        }
        if (renderWindow.inputHandler.isKeyBindingDown(KeyBindingsNames.MOVE_BACKWARDS)) {
            deltaMovement = deltaMovement - movementFront * cameraSpeed
        }
        if (renderWindow.inputHandler.isKeyBindingDown(KeyBindingsNames.MOVE_LEFT)) {
            deltaMovement = deltaMovement - cameraRight * cameraSpeed
        }
        if (renderWindow.inputHandler.isKeyBindingDown(KeyBindingsNames.MOVE_RIGHT)) {
            deltaMovement = deltaMovement + cameraRight * cameraSpeed
        }
        if (playerEntity.isFlying) {
            if (renderWindow.inputHandler.isKeyBindingDown(KeyBindingsNames.MOVE_FLY_UP)) {
                deltaMovement = deltaMovement + CAMERA_UP_VEC3 * cameraSpeed
            }
            if (renderWindow.inputHandler.isKeyBindingDown(KeyBindingsNames.MOVE_FLY_DOWN)) {
                deltaMovement = deltaMovement - CAMERA_UP_VEC3 * cameraSpeed
            }
        } else {
            if (playerEntity.onGround && renderWindow.inputHandler.isKeyBindingDown(KeyBindingsNames.MOVE_JUMP)) {
                // TODO: jump delay, correct jump height, direction wrong?
                playerEntity.velocity?.let {
                    it.y += -0.75f * ProtocolDefinition.GRAVITY
                } ?: run {
                    playerEntity.velocity = Vec3(0, -0.75f * ProtocolDefinition.GRAVITY, 0)
                }
                playerEntity.onGround = false
            }
        }
        if (deltaMovement != VecUtil.EMPTY_VEC3) {
            playerEntity.move(deltaMovement, false)
            recalculateViewProjectionMatrix()
            currentPositionSent = false
            sendPositionToServer()
        }

        val lastZoom = zoom
        zoom = if (renderWindow.inputHandler.isKeyBindingDown(KeyBindingsNames.ZOOM)) {
            2f
        } else {
            0.0f
        }
        if (lastZoom != zoom) {
            recalculateViewProjectionMatrix()
        }
    }

    fun addShaders(vararg shaders: Shader) {
        this.shaders.addAll(shaders)
    }

    fun addFrustumChangeCallback(vararg shaders: FrustumChangeCallback) {
        this.frustumChangeCallbacks.addAll(shaders)
    }

    override fun onScreenResize(screenDimensions: Vec2i) {
        recalculateViewProjectionMatrix()
    }

    private fun recalculateViewProjectionMatrix() {
        val matrix = calculateProjectionMatrix(renderWindow.screenDimensionsF) * calculateViewMatrix()
        for (shader in shaders) {
            shader.use().setMat4("viewProjectionMatrix", matrix)
        }
        positionChangeCallback()
    }

    private fun positionChangeCallback() {
        blockPosition = (cameraPosition - Vec3(0, PLAYER_HEIGHT, 0)).blockPosition
        currentBiome = connection.world.getBiome(blockPosition)
        chunkPosition = blockPosition.chunkPosition
        sectionHeight = blockPosition.sectionHeight
        inChunkSectionPosition = blockPosition.inChunkSectionPosition
        // recalculate sky color for current biome
        renderWindow.setSkyColor(connection.world.getBiome(blockPosition)?.skyColor ?: RenderConstants.DEFAULT_SKY_COLOR)

        frustum.recalculate()
        for (frustumChangeCallback in frustumChangeCallbacks) {
            frustumChangeCallback.onFrustumChange()
        }

        connection.world.dimension?.hasSkyLight?.let {
            if (it) {
                renderWindow.setSkyColor(currentBiome?.skyColor ?: RenderConstants.DEFAULT_SKY_COLOR)
            } else {
                renderWindow.setSkyColor(RenderConstants.BLACK_COLOR)
            }
        } ?: renderWindow.setSkyColor(RenderConstants.DEFAULT_SKY_COLOR)
    }

    private fun calculateProjectionMatrix(screenDimensions: Vec2): Mat4 {
        return glm.perspective((fov / (zoom + 1.0f)).rad, screenDimensions.x / screenDimensions.y, 0.1f, 1000f)
    }

    private fun calculateViewMatrix(): Mat4 {
        cameraPosition = getAbsoluteCameraPosition()
        return glm.lookAt(cameraPosition, cameraPosition + cameraFront, CAMERA_UP_VEC3)
    }

    private fun getAbsoluteCameraPosition(): Vec3 {
        return playerEntity.position + Vec3(0, PLAYER_HEIGHT, 0)
    }

    fun checkPosition() {
        if (cameraPosition != getAbsoluteCameraPosition()) {
            currentPositionSent = false
        }
    }

    fun setRotation(yaw: Float, pitch: Float) {
        playerEntity.rotation = EntityRotation(yaw.toDouble(), pitch.toDouble())

        cameraFront = Vec3(
            (yaw + 90).rad.cos * (-pitch).rad.cos,
            (-pitch).rad.sin,
            (yaw + 90).rad.sin * (-pitch).rad.cos
        ).normalize()

        cameraRight = (cameraFront cross CAMERA_UP_VEC3).normalize()
        cameraUp = (cameraRight cross cameraFront).normalize()
        recalculateViewProjectionMatrix()
        currentRotationSent = false
        sendPositionToServer()
    }

    fun draw() {
        if (!currentPositionSent || !currentRotationSent) {
            recalculateViewProjectionMatrix()
            sendPositionToServer()
        }
    }

    private fun sendPositionToServer() {
        if (System.currentTimeMillis() - lastMovementPacketSent < ProtocolDefinition.TICK_TIME) {
            return
        }
        if (!currentPositionSent && !currentPositionSent) {
            connection.sendPacket(PositionAndRotationC2SP(playerEntity.position, playerEntity.rotation, playerEntity.onGround))
        } else if (!currentPositionSent) {
            connection.sendPacket(PositionC2SP(playerEntity.position, playerEntity.onGround))
        } else {
            connection.sendPacket(RotationC2SP(playerEntity.rotation, playerEntity.onGround))
        }
        lastMovementPacketSent = System.currentTimeMillis()
        currentPositionSent = true
        currentRotationSent = true
    }

    fun setPosition(position: Vec3) {
        cameraPosition = (position + Vec3(0, PLAYER_HEIGHT, 0))
        playerEntity.position = position
    }

    companion object {
        private val CAMERA_UP_VEC3 = Vec3(0.0f, 1.0f, 0.0f)
        private const val PLAYER_HEIGHT = 1.3 // player is 1.8 blocks high, the camera is normally at 0.5. 1.8 - 0.5 = 1.13
    }
}
