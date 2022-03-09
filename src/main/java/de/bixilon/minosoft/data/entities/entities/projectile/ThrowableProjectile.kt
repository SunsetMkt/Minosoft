/*
 * Minosoft
 * Copyright (C) 2020-2022 Moritz Zwerger
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * This software is not affiliated with Mojang AB, the original developer of Minecraft.
 */
package de.bixilon.minosoft.data.entities.entities.projectile

import de.bixilon.minosoft.data.registries.entities.EntityType
import de.bixilon.minosoft.data.registries.fluid.DefaultFluids
import de.bixilon.minosoft.protocol.network.connection.play.PlayConnection

abstract class ThrowableProjectile(connection: PlayConnection, entityType: EntityType) : Projectile(connection, entityType) {
    open val gravity: Float = 0.03f


    override fun realTick() {
        super.realTick()


        val velocity = this.velocity


        val velocityMultiplier = if (fluidHeights[DefaultFluids.WATER] != null) {
            // ToDo: Spawn bubble particles
            0.8
        } else {
            0.99
        }


        this.velocity = (this.velocity * velocityMultiplier)


        if (hasGravity) {
            this.velocity.y -= gravity
        }

        position = position + connection.collisionDetector.collide(this, velocity, aabb)
    }
}
