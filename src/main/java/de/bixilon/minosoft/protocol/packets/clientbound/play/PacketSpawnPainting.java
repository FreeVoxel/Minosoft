/*
 * Minosoft
 * Copyright (C) 2020 Moritz Zwerger
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program.If not, see <https://www.gnu.org/licenses/>.
 *
 * This software is not affiliated with Mojang AB, the original developer of Minecraft.
 */

package de.bixilon.minosoft.protocol.packets.clientbound.play;

import de.bixilon.minosoft.data.Directions;
import de.bixilon.minosoft.data.entities.entities.decoration.Painting;
import de.bixilon.minosoft.data.mappings.Motive;
import de.bixilon.minosoft.data.world.BlockPosition;
import de.bixilon.minosoft.modding.event.events.EntitySpawnEvent;
import de.bixilon.minosoft.protocol.network.Connection;
import de.bixilon.minosoft.protocol.packets.ClientboundPacket;
import de.bixilon.minosoft.protocol.protocol.InByteBuffer;
import de.bixilon.minosoft.util.logging.Log;

import java.util.UUID;

import static de.bixilon.minosoft.protocol.protocol.ProtocolVersions.*;

public class PacketSpawnPainting extends ClientboundPacket {
    Painting entity;

    @Override
    public boolean read(InByteBuffer buffer) {
        int entityId = buffer.readVarInt();
        UUID uuid = null;
        if (buffer.getVersionId() >= V_16W02A) {
            uuid = buffer.readUUID();
        }
        Motive motive;
        if (buffer.getVersionId() < V_18W02A) {
            motive = buffer.getConnection().getMapping().getMotiveRegistry().get(buffer.readResourceLocation());
        } else {
            motive = buffer.getConnection().getMapping().getMotiveRegistry().get(buffer.readVarInt());
        }
        BlockPosition position;
        Directions direction;
        if (buffer.getVersionId() < V_14W04B) {
            position = buffer.readBlockPositionInteger();
            direction = Directions.byId(buffer.readInt());
        } else {
            position = buffer.readPosition();
            direction = Directions.byId(buffer.readUnsignedByte());
        }
        this.entity = new Painting(buffer.getConnection(), entityId, uuid, position, direction, motive);
        return true;
    }

    @Override
    public void handle(Connection connection) {
        connection.fireEvent(new EntitySpawnEvent(connection, this));

        connection.getPlayer().getWorld().addEntity(getEntity());
    }

    @Override
    public void log() {
        Log.protocol(String.format("[IN] Spawning painting at %s (entityId=%d, motive=%s, direction=%s)", this.entity.getLocation(), this.entity.getEntityId(), this.entity.getMotive(), this.entity.getDirection()));
    }

    public Painting getEntity() {
        return this.entity;
    }
}

