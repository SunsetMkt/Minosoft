/*
 * Minosoft
 * Copyright (C) 2021 Moritz Zwerger
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * This software is not affiliated with Mojang AB, the original developer of Minecraft.
 */

package de.bixilon.minosoft.data.mappings

import de.bixilon.minosoft.Minosoft
import de.bixilon.minosoft.data.entities.EntityObjectType
import de.bixilon.minosoft.data.entities.block.BlockEntityMetaType
import de.bixilon.minosoft.data.entities.meta.EntityMetaData
import de.bixilon.minosoft.data.inventory.InventorySlots
import de.bixilon.minosoft.data.mappings.registry.PerVersionEnumRegistry
import de.bixilon.minosoft.data.mappings.registry.PerVersionRegistry
import de.bixilon.minosoft.data.mappings.registry.Registry
import de.bixilon.minosoft.protocol.packets.c2s.play.EntityActionC2SP
import de.bixilon.minosoft.protocol.packets.s2c.play.EntityAnimationS2CP
import de.bixilon.minosoft.protocol.packets.s2c.play.title.TitleS2CF
import de.bixilon.minosoft.util.json.ResourceLocationJsonMap.toResourceLocationMap

object DefaultRegistries {
    private val ENUM_RESOURCE_LOCATION = ResourceLocation("minosoft:mapping/enums.json")
    private val REGISTRIES_RESOURCE_LOCATION = ResourceLocation("minosoft:mapping/default_registries.json")
    private var initialized = false

    val EQUIPMENT_SLOTS_REGISTRY = PerVersionEnumRegistry(InventorySlots.EquipmentSlots)
    val HAND_EQUIPMENT_SLOTS_REGISTRY = PerVersionEnumRegistry(InventorySlots.EquipmentSlots)
    val ARMOR_EQUIPMENT_SLOTS_REGISTRY = PerVersionEnumRegistry(InventorySlots.EquipmentSlots)
    val ARMOR_STAND_EQUIPMENT_SLOTS_REGISTRY = PerVersionEnumRegistry(InventorySlots.EquipmentSlots)

    val ENTITY_META_DATA_DATA_TYPES_REGISTRY = PerVersionEnumRegistry(EntityMetaData.EntityMetaDataDataTypes)

    val TITLE_ACTIONS_REGISTRY = PerVersionEnumRegistry(TitleS2CF.TitleActions)

    val ENTITY_ANIMATION_REGISTRY = PerVersionEnumRegistry(EntityAnimationS2CP.EntityAnimations)
    val ENTITY_ACTIONS_REGISTRY = PerVersionEnumRegistry(EntityActionC2SP.EntityActions)

    val ENTITY_OBJECT_REGISTRY: Registry<EntityObjectType> = Registry()

    val BLOCK_ENTITY_META_TYPE_REGISTRY: PerVersionRegistry<BlockEntityMetaType> = PerVersionRegistry()

    val DEFAULT_PLUGIN_CHANNELS_REGISTRY: PerVersionRegistry<PluginChannel> = PerVersionRegistry()


    fun load() {
        check(!initialized) { "Already initialized!" }

        val enumJson = Minosoft.MINOSOFT_ASSETS_MANAGER.readJsonAsset(ENUM_RESOURCE_LOCATION).asJsonObject.toResourceLocationMap()

        EQUIPMENT_SLOTS_REGISTRY.initialize(enumJson[ResourceLocation("equipment_slots")]!!)
        HAND_EQUIPMENT_SLOTS_REGISTRY.initialize(enumJson[ResourceLocation("hand_equipment_slots")]!!)
        ARMOR_EQUIPMENT_SLOTS_REGISTRY.initialize(enumJson[ResourceLocation("armor_equipment_slots")]!!)
        ARMOR_STAND_EQUIPMENT_SLOTS_REGISTRY.initialize(enumJson[ResourceLocation("armor_stand_equipment_slots")]!!)

        ENTITY_META_DATA_DATA_TYPES_REGISTRY.initialize(enumJson[ResourceLocation("entity_meta_data_data_types")]!!)

        TITLE_ACTIONS_REGISTRY.initialize(enumJson[ResourceLocation("title_actions")]!!)

        ENTITY_ANIMATION_REGISTRY.initialize(enumJson[ResourceLocation("entity_animations")]!!)
        ENTITY_ACTIONS_REGISTRY.initialize(enumJson[ResourceLocation("entity_actions")]!!)


        val registriesJson = Minosoft.MINOSOFT_ASSETS_MANAGER.readJsonAsset(REGISTRIES_RESOURCE_LOCATION).asJsonObject.toResourceLocationMap()

        DEFAULT_PLUGIN_CHANNELS_REGISTRY.initialize(registriesJson[ResourceLocation("default_plugin_channels")]!!, PluginChannel)

        ENTITY_OBJECT_REGISTRY.initialize(registriesJson[ResourceLocation("entity_objects")]!!, null, EntityObjectType)

        BLOCK_ENTITY_META_TYPE_REGISTRY.initialize(registriesJson[ResourceLocation("block_entity_meta_data_types")]!!, BlockEntityMetaType)

        initialized = true
    }

}
