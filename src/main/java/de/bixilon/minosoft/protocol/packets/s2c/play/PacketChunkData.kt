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
package de.bixilon.minosoft.protocol.packets.s2c.play

import de.bixilon.minosoft.data.entities.block.BlockEntity
import de.bixilon.minosoft.data.mappings.ResourceLocation
import de.bixilon.minosoft.data.mappings.tweaker.VersionTweaker
import de.bixilon.minosoft.data.world.ChunkData
import de.bixilon.minosoft.data.world.biome.source.SpatialBiomeArray
import de.bixilon.minosoft.modding.event.events.ChunkDataChangeEvent
import de.bixilon.minosoft.modding.event.events.ChunkUnloadEvent
import de.bixilon.minosoft.protocol.network.connection.PlayConnection
import de.bixilon.minosoft.protocol.packets.s2c.PlayS2CPacket
import de.bixilon.minosoft.protocol.protocol.PlayInByteBuffer
import de.bixilon.minosoft.protocol.protocol.ProtocolVersions
import de.bixilon.minosoft.util.KUtil.nullCast
import de.bixilon.minosoft.util.Util
import de.bixilon.minosoft.util.chunk.ChunkUtil
import de.bixilon.minosoft.util.logging.Log
import de.bixilon.minosoft.util.logging.Log.log
import de.bixilon.minosoft.util.logging.LogMessageType
import de.bixilon.minosoft.util.nbt.tag.NBTUtil.compoundCast
import glm_.vec2.Vec2i
import glm_.vec3.Vec3i
import java.util.*

class PacketChunkData() : PlayS2CPacket() {
    private val blockEntities: MutableMap<Vec3i, BlockEntity> = mutableMapOf()
    lateinit var chunkPosition: Vec2i
    var chunkData: ChunkData? = ChunkData()
        private set
    var heightMap: Map<String, Any>? = null
    private var isFullChunk = false

    constructor(buffer: PlayInByteBuffer) : this() {
        val dimension = buffer.connection.world.dimension!!
        chunkPosition = Vec2i(buffer.readInt(), buffer.readInt())
        if (buffer.versionId < ProtocolVersions.V_20W45A) {
            isFullChunk = !buffer.readBoolean()
        }
        if (buffer.versionId < ProtocolVersions.V_14W26A) {
            val sectionBitMask = BitSet.valueOf(buffer.readByteArray(2))
            val addBitMask = BitSet.valueOf(buffer.readByteArray(2))

            // decompress chunk data
            val decompressed: PlayInByteBuffer = if (buffer.versionId < ProtocolVersions.V_14W28A) {
                Util.decompress(buffer.readByteArray(buffer.readInt()), buffer.connection)
            } else {
                buffer
            }
            ChunkUtil.readChunkPacket(decompressed, dimension, sectionBitMask, addBitMask, !isFullChunk, dimension.hasSkyLight)?.let {
                chunkData!!.replace(it)
            } ?: let {
                // unload chunk
                chunkData = null
            }
            return
        }

        if (buffer.versionId >= ProtocolVersions.V_1_16_PRE7 && buffer.versionId < ProtocolVersions.V_1_16_2_PRE2) {
            buffer.readBoolean() // ToDo: ignore old data???
        }
        val sectionBitMask: BitSet = when {
            buffer.versionId < ProtocolVersions.V_15W34C -> {
                BitSet.valueOf(buffer.readByteArray(2))
            }
            buffer.versionId < ProtocolVersions.V_15W36D -> {
                BitSet.valueOf(buffer.readByteArray(4))
            }
            buffer.versionId < ProtocolVersions.V_21W03A -> {
                BitSet.valueOf(longArrayOf(buffer.readVarInt().toLong()))
            }
            else -> {
                BitSet.valueOf(buffer.readLongArray())
            }
        }
        if (buffer.versionId >= ProtocolVersions.V_18W44A) {
            heightMap = buffer.readNBT()?.compoundCast()
        }
        if (!isFullChunk) {
            chunkData!!.biomeSource = SpatialBiomeArray(buffer.readBiomeArray())
        }
        val size = buffer.readVarInt()
        val lastPos = buffer.pointer
        if (size > 0) {
            ChunkUtil.readChunkPacket(buffer, dimension, sectionBitMask, null, !isFullChunk, dimension.hasSkyLight)?.let {
                chunkData!!.replace(it)
            } ?: let {
                chunkData = null
            }
            // set position of the byte buffer, because of some reasons HyPixel makes some weird stuff and sends way to much 0 bytes. (~ 190k), thanks @pokechu22
            buffer.pointer = size + lastPos
        }
        if (buffer.versionId >= ProtocolVersions.V_1_9_4) {
            val blockEntitiesCount = buffer.readVarInt()
            for (i in 0 until blockEntitiesCount) {
                val nbt = buffer.readNBT()?.compoundCast()!!
                val position = Vec3i(nbt["x"]?.nullCast<Int>()!!, nbt["y"]?.nullCast<Int>()!!, nbt["z"]?.nullCast<Int>()!!)
                val resourceLocation = ResourceLocation(nbt["id"]?.nullCast<String>()!!)
                val type = buffer.connection.mapping.blockEntityRegistry.get(resourceLocation) ?: let {
                    log(LogMessageType.OTHER_ERROR, message = "Unknown block entity $resourceLocation", formatting = arrayOf())
                    null
                } ?: continue
                val entity = type.build(buffer.connection) ?: continue
                entity.updateNBT(nbt)
                blockEntities[position] = entity
            }
        }
        return
    }

    override fun handle(connection: PlayConnection) {

        chunkData?.blocks?.let {
            VersionTweaker.transformSections(it, connection.version.versionId)
        }
        chunkData?.let {
            val chunk = connection.world.getOrCreateChunk(chunkPosition)
            chunk.setData(chunkData!!)
            connection.world.setBlockEntity(blockEntities)
            connection.fireEvent(ChunkDataChangeEvent(connection, this))
        } ?: let {
            connection.world.unloadChunk(chunkPosition)
            connection.fireEvent(ChunkUnloadEvent(connection, chunkPosition))
        }

        for ((blockPosition, blockEntity) in blockEntities) {
            connection.world.setBlockEntity(blockPosition, blockEntity)
        }
    }

    override fun log() {
        Log.log(LogMessageType.NETWORK_PACKETS_IN) { "Chunk data (chunkPosition=$chunkPosition)" }
    }
}
