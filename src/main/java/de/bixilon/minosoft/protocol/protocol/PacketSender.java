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

package de.bixilon.minosoft.protocol.protocol;

import de.bixilon.minosoft.data.ChatTextPositions;
import de.bixilon.minosoft.data.entities.EntityRotation;
import de.bixilon.minosoft.data.mappings.ResourceLocation;
import de.bixilon.minosoft.data.player.Hands;
import de.bixilon.minosoft.data.text.ChatComponent;
import de.bixilon.minosoft.modding.event.events.ChatMessageReceivingEvent;
import de.bixilon.minosoft.modding.event.events.ChatMessageSendingEvent;
import de.bixilon.minosoft.modding.event.events.CloseWindowEvent;
import de.bixilon.minosoft.modding.event.events.HeldItemChangeEvent;
import de.bixilon.minosoft.protocol.network.connection.PlayConnection;
import de.bixilon.minosoft.protocol.packets.c2s.login.LoginPluginResponseC2SPacket;
import de.bixilon.minosoft.protocol.packets.c2s.play.*;
import de.bixilon.minosoft.util.Util;
import de.bixilon.minosoft.util.logging.Log;
import glm_.vec3.Vec3;
import org.checkerframework.common.value.qual.IntRange;

import java.util.UUID;

public class PacketSender {
    public static final char[] ILLEGAL_CHAT_CHARS = {'§', '\n', '\r'};
    private final PlayConnection connection;

    public PacketSender(PlayConnection connection) {
        this.connection = connection;
    }

    public void setFlyStatus(boolean flying) {
        this.connection.sendPacket(new PlayerAbilitiesC2SPacket(flying));
    }

    public void sendChatMessage(String message) {
        if (message.isBlank()) {
            // throw new IllegalArgumentException(("Chat message is blank!"));
            return;
        }
        for (char illegalChar : ILLEGAL_CHAT_CHARS) {
            if (message.indexOf(illegalChar) != -1) {
                // throw new IllegalArgumentException(String.format("%s is not allowed in chat", illegalChar));
                return;
            }
        }
        ChatMessageSendingEvent event = new ChatMessageSendingEvent(this.connection, message);
        if (this.connection.fireEvent(event)) {
            return;
        }
        Log.game("Sending chat message: %s", message);
        this.connection.sendPacket(new ChatMessageC2SPacket(event.getMessage()));
    }

    public void spectateEntity(UUID entityUUID) {
        this.connection.sendPacket(new SpectateEntityC2SPacket(entityUUID));
    }

    public void swingArm(Hands hand) {
        this.connection.sendPacket(new HandAnimationC2SPacket(hand));
    }

    public void swingArm() {
        this.connection.sendPacket(new HandAnimationC2SPacket(Hands.MAIN_HAND));
    }

    public void dropItem() {
        this.connection.sendPacket(new PlayerDiggingC2SPacket(PlayerDiggingC2SPacket.DiggingStatus.DROP_ITEM, null, PlayerDiggingC2SPacket.DiggingFaces.BOTTOM));
    }

    public void dropItemStack() {
        this.connection.sendPacket(new PlayerDiggingC2SPacket(PlayerDiggingC2SPacket.DiggingStatus.DROP_ITEM_STACK, null, PlayerDiggingC2SPacket.DiggingFaces.BOTTOM));
    }

    public void swapItemInHand() {
        this.connection.sendPacket(new PlayerDiggingC2SPacket(PlayerDiggingC2SPacket.DiggingStatus.SWAP_ITEMS_IN_HAND, null, PlayerDiggingC2SPacket.DiggingFaces.BOTTOM));
    }

    public void closeWindow(byte windowId) {
        CloseWindowEvent event = new CloseWindowEvent(this.connection, windowId, CloseWindowEvent.Initiators.CLIENT);
        if (this.connection.fireEvent(event)) {
            return;
        }
        this.connection.sendPacket(new CloseWindowC2SPacket(windowId));
    }

    public void respawn() {
        sendClientStatus(ClientActionC2SPacket.ClientStates.PERFORM_RESPAWN);
    }

    public void sendClientStatus(ClientActionC2SPacket.ClientStates status) {
        this.connection.sendPacket(new ClientActionC2SPacket(status));
    }

    public void sendPluginMessageData(String channel, OutByteBuffer toSend) {
        this.connection.sendPacket(new PluginMessageC2SPacket(channel, toSend.toByteArray()));
    }

    public void sendPluginMessageData(ResourceLocation channel, OutByteBuffer toSend) {
        String channelName = channel.getFull();
        if (Util.doesStringContainsUppercaseLetters(channelName)) {
            channelName = channel.getPath();
        }
        this.connection.sendPacket(new PluginMessageC2SPacket(channelName, toSend.toByteArray()));
    }

    public void sendLoginPluginMessageResponse(int messageId, OutByteBuffer toSend) {
        this.connection.sendPacket(new LoginPluginResponseC2SPacket(messageId, toSend.toByteArray()));
    }

    public void setLocation(Vec3 position, EntityRotation rotation, boolean onGround) {
        this.connection.sendPacket(new PlayerPositionAndRotationC2SPacket(position, rotation, onGround));
        this.connection.getPlayer().getEntity().setPosition(position);
        this.connection.getPlayer().getEntity().setRotation(rotation);
    }

    public void sendFakeChatMessage(ChatComponent message, ChatTextPositions position) {
        this.connection.fireEvent(new ChatMessageReceivingEvent(this.connection, message, position, null));
    }

    public void sendFakeChatMessage(String message) {
        sendFakeChatMessage(ChatComponent.Companion.valueOf(message), ChatTextPositions.CHAT_BOX);
    }

    public void selectSlot(@IntRange(from = 0, to = 8) int slot) {
        this.connection.fireEvent(new HeldItemChangeEvent(this.connection, slot));
        this.connection.getPlayer().getInventoryManager().setSelectedHotbarSlot(slot);
        this.connection.sendPacket(new HeldItemChangeC2SPacket(slot));
    }
}
