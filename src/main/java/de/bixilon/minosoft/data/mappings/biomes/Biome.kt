/*
 * Minosoft
 * Copyright (C) 2021 Moritz Zwerger
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program.If not, see <https://www.gnu.org/licenses/>.
 *
 * This software is not affiliated with Mojang AB, the original developer of Minecraft.
 */
package de.bixilon.minosoft.data.mappings.biomes

import com.google.gson.JsonObject
import de.bixilon.minosoft.data.mappings.RegistryItem
import de.bixilon.minosoft.data.mappings.ResourceLocation
import de.bixilon.minosoft.data.mappings.ResourceLocationDeserializer
import de.bixilon.minosoft.data.mappings.versions.VersionMapping
import de.bixilon.minosoft.data.text.RGBColor
import de.bixilon.minosoft.gui.rendering.RenderConstants
import de.bixilon.minosoft.gui.rendering.TintColorCalculator
import de.bixilon.minosoft.util.MMath

data class Biome(
    val resourceLocation: ResourceLocation,
    val depth: Float,
    val scale: Float,
    val temperature: Float,
    val downfall: Float,
    val waterColor: RGBColor?,
    val waterFogColor: RGBColor?,
    val category: BiomeCategory,
    val precipation: BiomePrecipation,
    val skyColor: RGBColor,
    val foliageColor: RGBColor?,
    val grassColor: RGBColor?,
    val descriptionId: String?,
    val grassColorModifier: GrassColorModifiers = GrassColorModifiers.NONE,
) : RegistryItem {

    val temperatureColorMapCoordinate = ((1.0 - MMath.clamp(temperature, 0.0f, 1.0f)) * RenderConstants.COLORMAP_SIZE).toInt()
    val downfallColorMapCoordinate = ((1.0 - (MMath.clamp(downfall, 0.0f, 1.0f) * MMath.clamp(temperature, 0.0f, 1.0f))) * RenderConstants.COLORMAP_SIZE).toInt()

    override fun toString(): String {
        return resourceLocation.toString()
    }

    companion object : ResourceLocationDeserializer<Biome> {
        override fun deserialize(mappings: VersionMapping, resourceLocation: ResourceLocation, data: JsonObject): Biome {
            return Biome(
                resourceLocation = resourceLocation,
                depth = data["depth"]?.asFloat ?: 0f,
                scale = data["scale"]?.asFloat ?: 0f,
                temperature = data["temperature"]?.asFloat ?: 0f,
                downfall = data["downfall"]?.asFloat ?: 0f,
                waterColor = TintColorCalculator.getJsonColor(data["water_color"]?.asInt ?: 0),
                waterFogColor = TintColorCalculator.getJsonColor(data["water_fog_color"]?.asInt ?: 0),
                category = mappings.biomeCategoryRegistry.get(data["category"]?.asInt ?: -1) ?: DEFAULT_CATEGORY,
                precipation = mappings.biomePrecipationRegistry.get(data["precipitation"]?.asInt ?: -1) ?: DEFAULT_PRECIPATION,
                skyColor = data["sky_color"]?.asInt?.let { RGBColor.noAlpha(it) } ?: RenderConstants.GRASS_FAILOVER_COLOR,
                foliageColor = TintColorCalculator.getJsonColor(data["foliage_color_override"]?.asInt ?: data["foliage_color"]?.asInt ?: 0),
                grassColor = TintColorCalculator.getJsonColor(data["grass_color_override"]?.asInt ?: 0),
                descriptionId = data["water_fog_color"]?.asString,
                grassColorModifier = data["grass_color_modifier"]?.asString?.toUpperCase()?.let { GrassColorModifiers.valueOf(it) } ?: when (resourceLocation) {
                    ResourceLocation("minecraft:swamp"), ResourceLocation("minecraft:swamp_hills") -> GrassColorModifiers.SWAMP
                    ResourceLocation("minecraft:dark_forest"), ResourceLocation("minecraft:dark_forest_hills") -> GrassColorModifiers.DARK_FORREST

                    else -> GrassColorModifiers.NONE
                }
            )
        }

        private val DEFAULT_PRECIPATION = BiomePrecipation("NONE")
        private val DEFAULT_CATEGORY = BiomeCategory("NONE")

    }

    enum class GrassColorModifiers(val modifier: (color: RGBColor) -> RGBColor) {
        NONE({ color: RGBColor ->
            color
        }),
        DARK_FORREST({ color: RGBColor ->
            RGBColor(color.color + 2634762 shl 8)
        }),
        SWAMP({ color: RGBColor ->
            // ToDo: Minecraft uses PerlinSimplexNoise here
            color
        }),

    }
}
