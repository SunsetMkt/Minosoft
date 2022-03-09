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
import de.bixilon.minosoft.data.registries.entities.EntityFactory
import de.bixilon.minosoft.data.registries.entities.EntityType
import de.bixilon.minosoft.protocol.network.connection.play.PlayConnection

class Zoglin(connection: PlayConnection, entityType: EntityType) : Monster(connection, entityType) {

    @get:EntityMetaDataFunction(name = "Is baby")
    val isBaby: Boolean
        get() = data.sets.getBoolean(EntityDataFields.ZOGLIN_IS_BABY)


    companion object : EntityFactory<Zoglin> {
        override val RESOURCE_LOCATION: ResourceLocation = ResourceLocation("zoglin")

        override fun build(connection: PlayConnection, entityType: EntityType): Zoglin {
            return Zoglin(connection, entityType)
        }
    }
}
