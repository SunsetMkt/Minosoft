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

package de.bixilon.minosoft.data.registries.blocks

import de.bixilon.kutil.cast.CastUtil.unsafeCast
import de.bixilon.kutil.cast.CastUtil.unsafeNull
import de.bixilon.minosoft.IT
import de.bixilon.minosoft.data.direction.Directions
import de.bixilon.minosoft.data.registries.ResourceLocation
import de.bixilon.minosoft.data.registries.blocks.types.Block
import org.testng.Assert
import org.testng.Assert.assertEquals

abstract class BlockTest<T : Block> {
    var block: T = unsafeNull()
    var state: BlockState = unsafeNull()


    fun retrieveBlock(name: ResourceLocation) {
        val block = IT.V_1_18_2.registries!!.blockRegistry[name]
        Assert.assertNotNull(block)
        block!!
        assertEquals(block.resourceLocation, name)
        this.block = block.unsafeCast()
        this.state = block.defaultState
    }

    fun testLightProperties(
        luminance: Int,
        propagatesLight: Boolean,
        skylightEnters: Boolean,
        filtersSkylight: Boolean,
        propagates: BooleanArray,
    ) {
        assertEquals(state.luminance, luminance)
        val light = state.lightProperties
        assertEquals(light.propagatesLight, propagatesLight)
        assertEquals(light.skylightEnters, skylightEnters)
        assertEquals(light.filtersSkylight, filtersSkylight)

        for (direction in Directions.VALUES) {
            assertEquals(light.propagatesLight(direction), propagates[direction.ordinal], "$direction failed")
        }
    }
}
