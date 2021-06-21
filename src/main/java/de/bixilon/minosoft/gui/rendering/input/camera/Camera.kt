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

package de.bixilon.minosoft.gui.rendering.input.camera

import de.bixilon.minosoft.Minosoft
import de.bixilon.minosoft.config.config.game.controls.KeyBindingsNames
import de.bixilon.minosoft.data.entities.EntityRotation
import de.bixilon.minosoft.data.player.LocalPlayerEntity
import de.bixilon.minosoft.data.registries.blocks.types.FluidBlock
import de.bixilon.minosoft.gui.rendering.RenderConstants
import de.bixilon.minosoft.gui.rendering.RenderWindow
import de.bixilon.minosoft.gui.rendering.input.camera.hit.BlockRaycastHit
import de.bixilon.minosoft.gui.rendering.input.camera.hit.EntityRaycastHit
import de.bixilon.minosoft.gui.rendering.input.camera.hit.FluidRaycastHit
import de.bixilon.minosoft.gui.rendering.input.camera.hit.RaycastHit
import de.bixilon.minosoft.gui.rendering.modding.events.CameraMatrixChangeEvent
import de.bixilon.minosoft.gui.rendering.modding.events.CameraPositionChangeEvent
import de.bixilon.minosoft.gui.rendering.modding.events.FrustumChangeEvent
import de.bixilon.minosoft.gui.rendering.modding.events.ResizeWindowEvent
import de.bixilon.minosoft.gui.rendering.sky.SkyRenderer
import de.bixilon.minosoft.gui.rendering.util.VecUtil
import de.bixilon.minosoft.gui.rendering.util.VecUtil.floor
import de.bixilon.minosoft.gui.rendering.util.VecUtil.getWorldOffset
import de.bixilon.minosoft.modding.event.CallbackEventInvoker
import de.bixilon.minosoft.protocol.network.connection.PlayConnection
import glm_.func.cos
import glm_.func.rad
import glm_.func.sin
import glm_.glm
import glm_.mat4x4.Mat4d
import glm_.vec2.Vec2
import glm_.vec2.Vec2d
import glm_.vec3.Vec3d

class Camera(
    val connection: PlayConnection,
    val renderWindow: RenderWindow,
) {
    private var mouseSensitivity = Minosoft.getConfig().config.game.camera.moseSensitivity
    val entity: LocalPlayerEntity
        get() = connection.player
    private var lastMousePosition: Vec2d = Vec2d(0.0, 0.0)
    private var zoom = 0.0f

    var cameraFront = Vec3d(0.0, 0.0, -1.0)
    var cameraRight = Vec3d(0.0, 0.0, -1.0)
    private var cameraUp = Vec3d(0.0, 1.0, 0.0)

    var target: RaycastHit? = null
        private set
    var blockTarget: BlockRaycastHit? = null
        private set
    var fluidTarget: FluidRaycastHit? = null
        private set
    var entityTarget: EntityRaycastHit? = null
        private set

    val fov: Double
        get() {
            val fov = Minosoft.config.config.game.camera.fov / (zoom + 1.0)

            if (!Minosoft.config.config.game.camera.dynamicFov) {
                return fov
            }
            return fov * entity.fovMultiplier
        }


    var viewMatrix = calculateViewMatrix()
        private set
    var projectionMatrix = calculateProjectionMatrix(renderWindow.window.sizef)
        private set
    var viewProjectionMatrix = projectionMatrix * viewMatrix
        private set


    val frustum: Frustum = Frustum(this)


    fun mouseCallback(position: Vec2d) {
        val delta = position - lastMousePosition
        lastMousePosition = position
        if (renderWindow.inputHandler.currentKeyConsumer != null) {
            return
        }
        delta *= mouseSensitivity
        var yaw = delta.x + entity.rotation.headYaw
        if (yaw > 180) {
            yaw -= 360
        } else if (yaw < -180) {
            yaw += 360
        }
        yaw %= 180
        val pitch = glm.clamp(delta.y + entity.rotation.pitch, -89.9, 89.9)
        entity.rotation = EntityRotation(yaw, pitch)
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
            KeyBindingsNames.MOVE_SNEAK,
            KeyBindingsNames.MOVE_TOGGLE_FLY,
        )

        connection.registerEvent(CallbackEventInvoker.of<ResizeWindowEvent> { recalculateViewProjectionMatrix() })
        frustum.recalculate()
        connection.fireEvent(FrustumChangeEvent(renderWindow, frustum))
    }

    private fun recalculateViewProjectionMatrix() {
        viewMatrix = calculateViewMatrix()
        projectionMatrix = calculateProjectionMatrix(renderWindow.window.sizef)
        viewProjectionMatrix = projectionMatrix * viewMatrix
        connection.fireEvent(CameraMatrixChangeEvent(
            renderWindow = renderWindow,
            viewMatrix = viewMatrix,
            projectionMatrix = projectionMatrix,
            viewProjectionMatrix = viewProjectionMatrix,
        ))
        for (shader in renderWindow.shaders) {
            if (shader.uniforms.contains("uViewProjectionMatrix")) {
                shader.use().setMat4("uViewProjectionMatrix", viewProjectionMatrix)
            }
        }
    }

    private fun onPositionChange() {
        recalculateViewProjectionMatrix()
        frustum.recalculate()
        connection.fireEvent(FrustumChangeEvent(renderWindow, frustum))
        connection.fireEvent(CameraPositionChangeEvent(renderWindow, entity.eyePosition))

        // recalculate sky color for current biome
        renderWindow[SkyRenderer.Companion]?.let { skyRenderer ->
            skyRenderer.baseColor = connection.world.getBiome(entity.positionInfo.blockPosition)?.skyColor ?: RenderConstants.DEFAULT_SKY_COLOR


            connection.world.dimension?.hasSkyLight?.let {
                if (it) {
                    skyRenderer.baseColor = entity.positionInfo.biome?.skyColor ?: RenderConstants.DEFAULT_SKY_COLOR
                } else {
                    skyRenderer.baseColor = RenderConstants.BLACK_COLOR
                }
            } ?: let { skyRenderer.baseColor = RenderConstants.DEFAULT_SKY_COLOR }
        }
    }

    private fun calculateProjectionMatrix(screenDimensions: Vec2): Mat4d {
        return glm.perspective(fov.rad, screenDimensions.x.toDouble() / screenDimensions.y, 0.1, 1000.0)
    }

    private fun calculateViewMatrix(): Mat4d {
        val cameraPosition = entity.eyePosition
        return glm.lookAt(cameraPosition, cameraPosition + cameraFront, CAMERA_UP_VEC3)
    }

    private fun setRotation(yaw: Double, pitch: Double) {
        cameraFront = Vec3d(
            (yaw + 90).rad.cos * (-pitch).rad.cos,
            (-pitch).rad.sin,
            (yaw + 90).rad.sin * (-pitch).rad.cos
        ).normalize()

        cameraRight = (cameraFront cross CAMERA_UP_VEC3).normalize()
        cameraUp = (cameraRight cross cameraFront).normalize()
        recalculateViewProjectionMatrix()
    }

    fun draw() {
        val input = if (renderWindow.inputHandler.currentKeyConsumer == null) {
            MovementInput(
                pressingForward = renderWindow.inputHandler.isKeyBindingDown(KeyBindingsNames.MOVE_FORWARD),
                pressingBack = renderWindow.inputHandler.isKeyBindingDown(KeyBindingsNames.MOVE_BACKWARDS),
                pressingLeft = renderWindow.inputHandler.isKeyBindingDown(KeyBindingsNames.MOVE_LEFT),
                pressingRight = renderWindow.inputHandler.isKeyBindingDown(KeyBindingsNames.MOVE_RIGHT),
                jumping = renderWindow.inputHandler.isKeyBindingDown(KeyBindingsNames.MOVE_JUMP),
                sneaking = renderWindow.inputHandler.isKeyBindingDown(KeyBindingsNames.MOVE_SNEAK),
                sprinting = renderWindow.inputHandler.isKeyBindingDown(KeyBindingsNames.MOVE_SPRINT),
                flyDown = renderWindow.inputHandler.isKeyBindingDown(KeyBindingsNames.MOVE_FLY_DOWN),
                flyUp = renderWindow.inputHandler.isKeyBindingDown(KeyBindingsNames.MOVE_FLY_UP),
                toggleFlyDown = renderWindow.inputHandler.isKeyBindingDown(KeyBindingsNames.MOVE_TOGGLE_FLY),
            )
        } else {
            MovementInput()
        }
        entity.input = input
        entity.tick() // The thread pool might be busy, we force a tick here to avoid lagging

        zoom = if (renderWindow.inputHandler.isKeyBindingDown(KeyBindingsNames.ZOOM)) {
            2f
        } else {
            0.0f
        }
        // ToDo: Only update if changed
        onPositionChange()

        val eyePosition = entity.eyePosition
        val cameraFront = cameraFront

        target = raycast(eyePosition, cameraFront, blocks = true, fluids = true, entities = true)
        blockTarget = raycast(eyePosition, cameraFront, blocks = true, fluids = false, entities = false) as BlockRaycastHit?
        fluidTarget = raycast(eyePosition, cameraFront, blocks = false, fluids = true, entities = false) as FluidRaycastHit?
        entityTarget = raycast(eyePosition, cameraFront, blocks = false, fluids = false, entities = true) as EntityRaycastHit?
    }

    private fun raycast(origin: Vec3d, direction: Vec3d, blocks: Boolean, fluids: Boolean, entities: Boolean): RaycastHit? {
        if (!blocks && !fluids && entities) {
            return null // ToDo: Raycast entities
        }
        val currentPosition = Vec3d(origin)

        fun getTotalDistance(): Double {
            return (origin - currentPosition).length()
        }

        for (i in 0..RAYCAST_MAX_STEPS) {
            val blockPosition = currentPosition.floor
            val blockState = connection.world[blockPosition]

            if (blockState == null) {
                currentPosition += direction * (VecUtil.getDistanceToNextIntegerAxisInDirection(currentPosition, direction) + 0.001)
                continue
            }
            val voxelShapeRaycastResult = (blockState.block.getOutlineShape(connection, blockState, blockPosition) + blockPosition + blockPosition.getWorldOffset(blockState.block)).raycast(currentPosition, direction)
            if (voxelShapeRaycastResult.hit) {
                val distance = getTotalDistance()
                currentPosition += direction * voxelShapeRaycastResult.distance
                currentPosition += direction * (VecUtil.getDistanceToNextIntegerAxisInDirection(currentPosition, direction) + 0.001)

                if (blockState.block is FluidBlock) {
                    if (!fluids) {
                        continue
                    }
                    return FluidRaycastHit(
                        currentPosition,
                        distance,
                        voxelShapeRaycastResult.direction,
                        blockState,
                        blockPosition,
                        blockState.block.fluid,
                    )
                }

                if (!blocks) {
                    continue
                }
                return BlockRaycastHit(
                    currentPosition,
                    distance,
                    voxelShapeRaycastResult.direction,
                    blockState,
                    blockPosition,
                )
            } else {
                currentPosition += direction * (VecUtil.getDistanceToNextIntegerAxisInDirection(currentPosition, direction) + 0.001)
            }
        }
        return null
    }

    companion object {
        val CAMERA_UP_VEC3 = Vec3d(0.0, 1.0, 0.0)

        private const val RAYCAST_MAX_STEPS = 100
    }
}
