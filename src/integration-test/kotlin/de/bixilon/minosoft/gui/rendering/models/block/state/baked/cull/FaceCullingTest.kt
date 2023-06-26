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

package de.bixilon.minosoft.gui.rendering.models.block.state.baked.cull

import de.bixilon.kotlinglm.vec2.Vec2
import de.bixilon.kotlinglm.vec2.Vec2i
import de.bixilon.kutil.exception.Broken
import de.bixilon.minosoft.data.direction.Directions
import de.bixilon.minosoft.data.registries.blocks.settings.BlockSettings
import de.bixilon.minosoft.data.registries.blocks.state.BlockState
import de.bixilon.minosoft.data.registries.blocks.types.Block
import de.bixilon.minosoft.data.registries.identified.Namespaces.minosoft
import de.bixilon.minosoft.gui.rendering.models.block.state.baked.BakedFace
import de.bixilon.minosoft.gui.rendering.models.block.state.baked.BakedModel
import de.bixilon.minosoft.gui.rendering.models.block.state.baked.cull.side.FaceProperties
import de.bixilon.minosoft.gui.rendering.models.block.state.baked.cull.side.SideProperties
import de.bixilon.minosoft.gui.rendering.system.base.texture.TextureTransparencies
import de.bixilon.minosoft.gui.rendering.system.base.texture.texture.MemoryTexture
import de.bixilon.minosoft.gui.rendering.util.vec.vec2.Vec2iUtil.EMPTY
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

@Test(groups = ["models", "culling"])
class FaceCullingTest {

    private fun createFace(transparency: TextureTransparencies = TextureTransparencies.OPAQUE, properties: FaceProperties? = FaceProperties(Vec2(0), Vec2(1), transparency)): BakedFace {
        return BakedFace(floatArrayOf(), floatArrayOf(), 1.0f, -1, null, MemoryTexture(minosoft("test"), Vec2i.EMPTY), properties)
    }

    private fun createNeighbour(transparency: TextureTransparencies = TextureTransparencies.OPAQUE, properties: SideProperties? = SideProperties(arrayOf(FaceProperties(Vec2(0), Vec2(1), transparency)), transparency), type: Int = 0): BlockState {
        val block = object : Block(minosoft("dummy$type"), BlockSettings()) {
            override val hardness: Float get() = Broken()
        }

        return createNeighbour(block, transparency, properties)
    }

    private fun createNeighbour(block: Block, transparency: TextureTransparencies = TextureTransparencies.OPAQUE, properties: SideProperties? = SideProperties(arrayOf(FaceProperties(Vec2(0), Vec2(1), transparency)), transparency)): BlockState {
        val state = BlockState(block, 0)

        state.model = BakedModel(emptyArray(), arrayOf(properties, properties, properties, properties, properties, properties), null)

        return state
    }

    private fun createState(type: Int = 0) = createNeighbour(type = type)

    fun noNeighbour() {
        val face = createFace()
        assertFalse(FaceCulling.canCull(createState(), face, Directions.DOWN, null))
    }

    @Test
    fun selfNotTouching() {
        val face = createFace(properties = null)
        val neighbour = createNeighbour()
        assertFalse(FaceCulling.canCull(createState(), face, Directions.DOWN, neighbour))
    }

    @Test
    fun neighbourNotTouching() {
        val face = createFace()
        val neighbour = createNeighbour(properties = null)
        assertFalse(FaceCulling.canCull(createState(), face, Directions.DOWN, neighbour))
    }

    @Test
    fun fullNeighbour() {
        val face = createFace()
        val neighbour = createNeighbour()
        assertTrue(FaceCulling.canCull(createState(), face, Directions.DOWN, neighbour))
    }

    @Test
    fun sizeMatch() {
        val face = createFace(properties = FaceProperties(Vec2(0.0f, 0.5f), Vec2(1.0f, 0.5f), TextureTransparencies.OPAQUE))
        val neighbour = createNeighbour(properties = side(FaceProperties(Vec2(0.0f, 0.5f), Vec2(1.0f, 0.5f), TextureTransparencies.OPAQUE)))
        assertTrue(FaceCulling.canCull(createState(), face, Directions.EAST, neighbour))
    }

    @Test
    fun greaterNeighbour() {
        val face = createFace(properties = FaceProperties(Vec2(0.0f, 0.5f), Vec2(1.0f, 0.5f), TextureTransparencies.OPAQUE))
        val neighbour = createNeighbour(properties = side(FaceProperties(Vec2(0.0f, 0.5f), Vec2(1.0f, 0.6f), TextureTransparencies.OPAQUE)))
        assertTrue(FaceCulling.canCull(createState(), face, Directions.EAST, neighbour))
    }

    @Test
    fun smallerNeighbour() {
        val face = createFace(properties = FaceProperties(Vec2(0.0f, 0.5f), Vec2(1.0f, 0.5f), TextureTransparencies.OPAQUE))
        val neighbour = createNeighbour(properties = side(FaceProperties(Vec2(0.0f, 0.5f), Vec2(1.0f, 0.4f), TextureTransparencies.OPAQUE)))
        assertFalse(FaceCulling.canCull(createState(), face, Directions.EAST, neighbour))
    }

    @Test
    fun noSize() {
        val face = createFace(properties = FaceProperties(Vec2(0.0f, 0.5f), Vec2(1.0f, 0.5f), TextureTransparencies.OPAQUE))
        val neighbour = createNeighbour(properties = side(FaceProperties(Vec2(0.1f, 0.5f), Vec2(1.0f, 0.5f), TextureTransparencies.OPAQUE)))
        assertTrue(FaceCulling.canCull(createState(), face, Directions.EAST, neighbour))
    }

    @Test
    fun shiftedNeighbour1() {
        val face = createFace(properties = FaceProperties(Vec2(0.0f, 0.4f), Vec2(1.0f, 0.5f), TextureTransparencies.OPAQUE))
        val neighbour = createNeighbour(properties = side(FaceProperties(Vec2(0.1f, 0.4f), Vec2(1.0f, 0.5f), TextureTransparencies.OPAQUE)))
        assertFalse(FaceCulling.canCull(createState(), face, Directions.EAST, neighbour))
    }

    @Test
    fun shiftedNeighbour2() {
        val face = createFace(properties = FaceProperties(Vec2(0.0f, 0.4f), Vec2(1.0f, 0.5f), TextureTransparencies.OPAQUE))
        val neighbour = createNeighbour(properties = side(FaceProperties(Vec2(0.1f, 0.4f), Vec2(1.0f, 0.6f), TextureTransparencies.OPAQUE)))
        assertFalse(FaceCulling.canCull(createState(), face, Directions.EAST, neighbour))
    }

    @Test
    fun shiftedNeighbour3() {
        val face = createFace(properties = FaceProperties(Vec2(0.1f, 0.8f), Vec2(0.9f, 0.9f), TextureTransparencies.OPAQUE))
        val neighbour = createNeighbour(properties = side(FaceProperties(Vec2(0.1f, 0.5f), Vec2(0.95f, 0.95f), TextureTransparencies.OPAQUE)))
        assertTrue(FaceCulling.canCull(createState(), face, Directions.EAST, neighbour))
    }

    @Test
    fun multipleNeighbourFaces() {
        val face = createFace(properties = FaceProperties(Vec2(0.1f, 0.3f), Vec2(0.9f, 0.9f), TextureTransparencies.OPAQUE))
        val neighbour = createNeighbour(properties = side(FaceProperties(Vec2(0.1f, 0.2f), Vec2(0.95f, 0.4f), TextureTransparencies.OPAQUE), FaceProperties(Vec2(0.1f, 0.4f), Vec2(0.95f, 0.6f), TextureTransparencies.OPAQUE), FaceProperties(Vec2(0.1f, 0.6f), Vec2(0.95f, 0.95f), TextureTransparencies.OPAQUE)))
        assertTrue(FaceCulling.canCull(createState(), face, Directions.EAST, neighbour))
    }

    @Test
    fun `transparent side on opaque neighbour`() {
        val face = createFace(transparency = TextureTransparencies.TRANSPARENT)
        val neighbour = createNeighbour(transparency = TextureTransparencies.OPAQUE)
        assertTrue(FaceCulling.canCull(createState(), face, Directions.EAST, neighbour))
    }

    @Test
    fun `translucent side on opaque neighbour`() {
        val face = createFace(transparency = TextureTransparencies.TRANSLUCENT)
        val neighbour = createNeighbour(transparency = TextureTransparencies.OPAQUE)
        assertTrue(FaceCulling.canCull(createState(), face, Directions.EAST, neighbour))
    }

    @Test
    fun `opaque side on transparent neighbour`() {
        val face = createFace(transparency = TextureTransparencies.OPAQUE)
        val neighbour = createNeighbour(transparency = TextureTransparencies.TRANSPARENT)
        assertFalse(FaceCulling.canCull(createState(), face, Directions.EAST, neighbour))
    }

    @Test
    fun `opaque side on translucent neighbour`() {
        val face = createFace(transparency = TextureTransparencies.OPAQUE)
        val neighbour = createNeighbour(transparency = TextureTransparencies.TRANSLUCENT)
        assertFalse(FaceCulling.canCull(createState(), face, Directions.EAST, neighbour))
    }

    @Test
    fun `same block, both sides transparent`() {
        val face = createFace(transparency = TextureTransparencies.TRANSPARENT)
        val neighbour = createNeighbour(transparency = TextureTransparencies.TRANSPARENT)
        assertTrue(FaceCulling.canCull(createState(), face, Directions.EAST, neighbour))
    }

    @Test
    fun `same block, both sides translucent`() {
        val face = createFace(transparency = TextureTransparencies.TRANSLUCENT)
        val neighbour = createNeighbour(transparency = TextureTransparencies.TRANSLUCENT)
        assertTrue(FaceCulling.canCull(createState(), face, Directions.EAST, neighbour))
    }

    @Test
    fun `different block, both sides transparent`() {
        val face = createFace(transparency = TextureTransparencies.TRANSPARENT)
        val neighbour = createNeighbour(transparency = TextureTransparencies.TRANSPARENT)
        assertFalse(FaceCulling.canCull(createState(1), face, Directions.EAST, neighbour))
    }

    @Test
    fun `different block, both sides translucent`() {
        val face = createFace(transparency = TextureTransparencies.TRANSLUCENT)
        val neighbour = createNeighbour(transparency = TextureTransparencies.TRANSLUCENT)
        assertFalse(FaceCulling.canCull(createState(1), face, Directions.EAST, neighbour))
    }

    @Test
    fun `same block, transparent sides, force no cull`() {
        val face = createFace(transparency = TextureTransparencies.TRANSPARENT)
        val neighbour = createNeighbour(transparency = TextureTransparencies.TRANSPARENT)
        assertFalse(FaceCulling.canCull(createNeighbour(forceNoCull()), face, Directions.EAST, neighbour))
    }

    @Test
    fun `same block, translucent sides, force no cull`() {
        val face = createFace(transparency = TextureTransparencies.TRANSLUCENT)
        val neighbour = createNeighbour(transparency = TextureTransparencies.TRANSLUCENT)
        assertFalse(FaceCulling.canCull(createNeighbour(forceNoCull()), face, Directions.EAST, neighbour))
    }

    @Test
    fun `opaque but no invoked custom cull`() {
        val block = object : Block(minosoft("dummy"), BlockSettings()), CustomBlockCulling {
            override val hardness get() = Broken()

            override fun shouldCull(state: BlockState, face: BakedFace, directions: Directions, neighbour: BlockState): Boolean {
                throw AssertionError("shouldCall invoked!")
            }
        }
        val face = createFace(transparency = TextureTransparencies.OPAQUE)
        val neighbour = createNeighbour(transparency = TextureTransparencies.OPAQUE)
        assertTrue(FaceCulling.canCull(createNeighbour(block), face, Directions.EAST, neighbour))
    }

    private fun side(vararg properties: FaceProperties): SideProperties {
        return SideProperties(arrayOf(*properties), properties.first().transparency)
    }

    private fun forceNoCull() = object : Block(minosoft("dummy"), BlockSettings()), CustomBlockCulling {
        override val hardness get() = Broken()

        override fun shouldCull(state: BlockState, face: BakedFace, directions: Directions, neighbour: BlockState): Boolean {
            return false
        }
    }
}
