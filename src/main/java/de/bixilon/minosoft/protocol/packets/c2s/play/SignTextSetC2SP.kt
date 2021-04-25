/*
 * Minosoft
 * Copyright (C) 2020 Moritz Zwerger
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * This software is not affiliated with Mojang AB, the original developer of Minecraft.
 */
package de.bixilon.minosoft.protocol.packets.c2s.play

import de.bixilon.minosoft.data.text.ChatComponent
import de.bixilon.minosoft.protocol.packets.c2s.PlayC2SPacket
import de.bixilon.minosoft.protocol.protocol.PlayOutByteBuffer
import de.bixilon.minosoft.protocol.protocol.ProtocolDefinition
import de.bixilon.minosoft.protocol.protocol.ProtocolVersions
import de.bixilon.minosoft.util.logging.Log
import de.bixilon.minosoft.util.logging.LogMessageType
import glm_.vec3.Vec3i

class SignTextSetC2SP(
    val position: Vec3i,
    val lines: Array<ChatComponent>,
) : PlayC2SPacket {

    override fun write(buffer: PlayOutByteBuffer) {
        if (buffer.versionId < ProtocolVersions.V_14W04A) {
            buffer.writeByteBlockPosition(position)
        } else {
            buffer.writePosition(position)
        }
        if (buffer.versionId < ProtocolVersions.V_14W25A || buffer.versionId >= ProtocolVersions.V_15W35A) {
            for (i in 0 until ProtocolDefinition.SIGN_LINES) {
                buffer.writeString(lines[i].message)
            }
        } else {
            for (i in 0 until ProtocolDefinition.SIGN_LINES) {
                buffer.writeChatComponent(lines[i])
            }
        }
    }

    override fun log() {
        Log.log(LogMessageType.NETWORK_PACKETS_OUT) { "Set sign text (position=$position)" }
    }
}
