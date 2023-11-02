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

package de.bixilon.minosoft.gui.rendering.entities.feature.register

import de.bixilon.minosoft.gui.rendering.entities.EntitiesRenderer
import de.bixilon.minosoft.gui.rendering.entities.hitbox.HitboxManager
import de.bixilon.minosoft.gui.rendering.entities.renderer.living.player.PlayerRegister

class EntityRenderFeatures(renderer: EntitiesRenderer) {
    val features: MutableList<FeatureRegister> = mutableListOf()

    val hitbox = HitboxManager(renderer).register()
    val player = PlayerRegister(renderer).register()


    fun init() {
        for (feature in features) {
            feature.init()
        }
    }

    operator fun plusAssign(register: FeatureRegister) {
        this.features += register
    }

    private fun <T : FeatureRegister> T.register(): T {
        this@EntityRenderFeatures += this
        return this
    }
}
