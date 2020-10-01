/*
 * Codename Minosoft
 * Copyright (C) 2020 Moritz Zwerger
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *  This software is not affiliated with Mojang AB, the original developer of Minecraft.
 */

package de.bixilon.minosoft.game.datatypes.entities.mob;

import de.bixilon.minosoft.game.datatypes.entities.Location;
import de.bixilon.minosoft.game.datatypes.entities.Mob;
import de.bixilon.minosoft.game.datatypes.entities.MobInterface;
import de.bixilon.minosoft.game.datatypes.entities.meta.EntityMetaData;
import de.bixilon.minosoft.game.datatypes.entities.meta.PufferfishMetaData;

import java.util.UUID;

public class Pufferfish extends Mob implements MobInterface {
    PufferfishMetaData metaData;

    public Pufferfish(int entityId, UUID uuid, Location location, short yaw, short pitch, short headYaw, EntityMetaData.MetaDataHashMap sets, int protocolId) {
        super(entityId, uuid, location, yaw, pitch, headYaw);
        this.metaData = new PufferfishMetaData(sets, protocolId);
    }

    @Override
    public EntityMetaData getMetaData() {
        return metaData;
    }

    @Override
    public void setMetaData(EntityMetaData metaData) {
        this.metaData = (PufferfishMetaData) metaData;
    }

    @Override
    public float getWidth() {
        return switch (metaData.getPufferState()) {
            case UN_PUFFED -> 0.35F;
            case SEMI_PUFFED -> 0.5F;
            case FULLY_PUFFED -> 0.7F;
        };
    }

    @Override
    public float getHeight() {
        return switch (metaData.getPufferState()) {
            case UN_PUFFED -> 0.35F;
            case SEMI_PUFFED -> 0.5F;
            case FULLY_PUFFED -> 0.7F;
        };
    }

    @Override
    public int getMaxHealth() {
        return 3;
    }

    @Override
    public Class<? extends EntityMetaData> getMetaDataClass() {
        return PufferfishMetaData.class;
    }
}
