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

package de.bixilon.minosoft.protocol.packets.clientbound.play;

import de.bixilon.minosoft.game.datatypes.blocks.Block;
import de.bixilon.minosoft.game.datatypes.world.Chunk;
import de.bixilon.minosoft.game.datatypes.world.ChunkLocation;
import de.bixilon.minosoft.game.datatypes.world.ChunkNibble;
import de.bixilon.minosoft.game.datatypes.world.ChunkNibbleLocation;
import de.bixilon.minosoft.logging.Log;
import de.bixilon.minosoft.protocol.packets.ClientboundPacket;
import de.bixilon.minosoft.protocol.protocol.InByteBuffer;
import de.bixilon.minosoft.protocol.protocol.InPacketBuffer;
import de.bixilon.minosoft.protocol.protocol.PacketHandler;
import de.bixilon.minosoft.protocol.protocol.ProtocolVersion;
import de.bixilon.minosoft.util.BitByte;
import de.bixilon.minosoft.util.Util;

import java.util.HashMap;

public class PacketChunkBulk implements ClientboundPacket {
    final HashMap<ChunkLocation, Chunk> chunkMap = new HashMap<>();


    @Override
    public void read(InPacketBuffer buffer, ProtocolVersion v) {
        switch (v) {
            case VERSION_1_7_10:
                // ToDo only implement once, not twice (chunk data and chunk bulk)
                short chunkColumnCount = buffer.readShort();
                int dataLen = buffer.readInteger();
                boolean containsSkyLight = buffer.readBoolean();

                // decompress chunk data
                InByteBuffer decompressed = Util.decompress(buffer.readBytes(dataLen));

                // chunk meta data
                int read = 0;
                for (int i = 0; i < chunkColumnCount; i++) {
                    int x = buffer.readInteger();
                    int z = buffer.readInteger();
                    short sectionBitMask = buffer.readShort();
                    short addBitMask = buffer.readShort();


                    //chunk
                    byte sections = BitByte.getBitCount(sectionBitMask);
                    int totalBytes = 4096 * sections; // 16 * 16 * 16 * sections; Section Width * Section Height * Section Width * sections
                    int halfBytes = totalBytes / 2; // half bytes

                    byte[] blockTypes = decompressed.readBytes(totalBytes);
                    byte[] meta = decompressed.readBytes(halfBytes);
                    byte[] light = decompressed.readBytes(halfBytes);
                    byte[] skyLight = null;
                    if (containsSkyLight) {
                        skyLight = decompressed.readBytes(halfBytes);
                    }
                    byte[] addBlockTypes = decompressed.readBytes(Integer.bitCount(addBitMask) * 2048); // 16 * 16 * 16 * addBlocks / 2
                    //ToDo test add Block Types
                    byte[] biomes = decompressed.readBytes(256);

                    //parse data
                    int arrayPos = 0;
                    HashMap<Byte, ChunkNibble> nibbleMap = new HashMap<>();
                    for (byte c = 0; c < 16; c++) { // max sections per chunks in chunk column
                        if (BitByte.isBitSet(sectionBitMask, c)) {

                            HashMap<ChunkNibbleLocation, Block> blockMap = new HashMap<>();

                            for (int nibbleY = 0; nibbleY < 16; nibbleY++) {
                                for (int nibbleZ = 0; nibbleZ < 16; nibbleZ++) {
                                    for (int nibbleX = 0; nibbleX < 16; nibbleX++) {

                                        short singeBlockId = blockTypes[arrayPos];
                                        byte singleMeta;
                                        // get block meta and shift and add (merge) id if needed
                                        if (arrayPos % 2 == 0) {
                                            // high bits
                                            singleMeta = BitByte.getLow4Bits(meta[arrayPos / 2]);
                                            if (BitByte.isBitSet(addBitMask, c)) {
                                                singeBlockId = (short) ((singeBlockId << 4) | BitByte.getHigh4Bits(addBlockTypes[arrayPos / 2]));
                                            }
                                        } else {
                                            // low 4 bits
                                            singleMeta = BitByte.getHigh4Bits(meta[arrayPos / 2]);

                                            if (BitByte.isBitSet(addBitMask, c)) {
                                                singeBlockId = (short) ((singeBlockId << 4) | BitByte.getLow4Bits(addBlockTypes[arrayPos / 2]));
                                            }
                                        }


                                        // ToDo light, biome
                                        Block block = Block.byLegacy(singeBlockId, singleMeta);
                                        if (block == Block.AIR) {
                                            continue;
                                        }
                                        blockMap.put(new ChunkNibbleLocation(nibbleX, nibbleY, nibbleZ), block);
                                        arrayPos++;
                                    }
                                }
                            }
                            nibbleMap.put(c, new ChunkNibble(blockMap));

                        }

                    }
                    chunkMap.put(new ChunkLocation(x, z), new Chunk(nibbleMap));
                }
                break;
        }
    }

    @Override
    public void log() {
        Log.protocol(String.format("Chunk bulk packet received (chunks: %s)", chunkMap.size()));
    }

    public HashMap<ChunkLocation, Chunk> getChunkMap() {
        return chunkMap;
    }

    @Override
    public void handle(PacketHandler h) {
        h.handle(this);
    }
}
