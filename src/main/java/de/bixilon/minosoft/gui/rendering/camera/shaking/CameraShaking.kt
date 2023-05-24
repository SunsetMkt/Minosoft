/*
 * Minosoft
 * Copyright (C) 2020-2023 Moritz Zwerger
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * This software is not affiliated with Mojang AB, the original developer of Minecraft.
 */

package de.bixilon.minosoft.gui.rendering.camera.shaking

import de.bixilon.kotlinglm.mat4x4.Mat4
import de.bixilon.kotlinglm.vec3.Vec3
import de.bixilon.kotlinglm.vec3.swizzle.xz
import de.bixilon.kutil.avg.FloatAverage
import de.bixilon.kutil.time.TimeUtil.millis
import de.bixilon.minosoft.config.profile.profiles.rendering.camera.shaking.ShakingC
import de.bixilon.minosoft.gui.rendering.camera.Camera
import de.bixilon.minosoft.protocol.protocol.ProtocolDefinition
import kotlin.math.PI
import kotlin.math.sin

class CameraShaking(
    private val camera: Camera,
    private val profile: ShakingC,
) {
    private var rotation = 0.0f
    private var strength = FloatAverage(5 * ProtocolDefinition.TICK_TIME * 1_000_000L, 1.0f)
    private val speed = FloatAverage(10 * ProtocolDefinition.TICK_TIME * 1_000_000L, 0.0f)

    val isEmpty: Boolean get() = rotation == 0.0f

    fun update(): Boolean {
        this.strength += 1.0f
        val time = millis()
        val walking = bobbing(time)
        val damage = damage(time)

        val previous = this.rotation
        this.rotation = walking + damage

        return this.rotation != previous
    }

    private fun bobbing(time: Long): Float {
        val physics = camera.context.connection.camera.entity.physics
        val velocity = physics.velocity.xz.length2().toFloat() // velocity affects how quick it goes
        if (velocity > 0.003 && physics.onGround) {
            this.speed += velocity
        } else {
            this.speed += 0.0f // TODO: remove this, kutil 1.21
        }

        val speed = minOf(this.speed.avg, 0.5f)

        if (speed == 0.0f) return 0.0f

        val intensity = speed * 100
        val time = sin(millis().toDouble() / 1000.0)
        val timeIntensity = sin(time / intensity)

        println(timeIntensity)
        return sin(timeIntensity * PI * 2).toFloat() / 10
    }

    private fun damage(time: Long): Float {
        val strength = this.strength.avg * profile.amplifier // strength affects how far it goes


        //  this.rotation = sin(time * minOf(this.speed.avg, 0.5f) / 3.0f) * strength * 0.03f

        // TODO
        return 0.0f
    }

    fun onDamage() {
        strength += 10.0f
    }

    fun transform(): Mat4? {
        if (rotation == 0.0f) return null
        return Mat4()
            .rotateAssign(rotation, Vec3(0, 0, 1))
    }
}
