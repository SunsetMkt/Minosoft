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
package de.bixilon.minosoft.data.entities.entities.monster

import de.bixilon.minosoft.data.entities.EntityDataFields
import de.bixilon.minosoft.data.entities.entities.EntityMetaDataFunction
import de.bixilon.minosoft.data.registries.ResourceLocation
import de.bixilon.minosoft.data.registries.blocks.BlockState
import de.bixilon.minosoft.data.registries.entities.EntityFactory
import de.bixilon.minosoft.data.registries.entities.EntityType
import de.bixilon.minosoft.protocol.network.connection.play.PlayConnection
import de.bixilon.minosoft.protocol.protocol.ProtocolVersions

class Enderman(connection: PlayConnection, entityType: EntityType) : AbstractSkeleton(connection, entityType) {

    // ToDo: No clue here
    @get:EntityMetaDataFunction(name = "Carried block")
    val carriedBlock: BlockState?
        get() = if (versionId <= ProtocolVersions.V_1_8_9) { // ToDo: No clue here
            connection.registries.blockStateRegistry[data.sets.getInt(EntityDataFields.LEGACY_ENDERMAN_CARRIED_BLOCK) shl 4 or data.sets.getInt(EntityDataFields.LEGACY_ENDERMAN_CARRIED_BLOCK_DATA)]
        } else {
            data.sets.getBlock(EntityDataFields.ENDERMAN_CARRIED_BLOCK)
        }

    @get:EntityMetaDataFunction(name = "Is screaming")
    val isScreaming: Boolean
        get() = data.sets.getBoolean(EntityDataFields.ENDERMAN_IS_SCREAMING)

    @get:EntityMetaDataFunction(name = "Is starring")
    val isStarring: Boolean
        get() = data.sets.getBoolean(EntityDataFields.ENDERMAN_IS_STARRING)


    companion object : EntityFactory<Enderman> {
        override val RESOURCE_LOCATION: ResourceLocation = ResourceLocation("enderman")

        override fun build(connection: PlayConnection, entityType: EntityType): Enderman {
            return Enderman(connection, entityType)
        }
    }
}
