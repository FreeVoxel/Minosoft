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

package de.bixilon.minosoft.gui.rendering.block

import de.bixilon.minosoft.data.world.ChunkSection
import de.bixilon.minosoft.gui.rendering.RenderWindow
import de.bixilon.minosoft.gui.rendering.block.mesh.ChunkSectionMesh
import de.bixilon.minosoft.protocol.protocol.ProtocolDefinition
import glm_.vec3.Vec3i
import java.util.*

class SectionPreparer(
    val renderWindow: RenderWindow,
) {


    fun prepare(section: ChunkSection): ChunkSectionMesh {
        val mesh = ChunkSectionMesh(renderWindow)

        for (x in 0 until ProtocolDefinition.SECTION_MAX_X) {
            for (y in 0 until ProtocolDefinition.SECTION_MAX_Y) {
                for (z in 0 until ProtocolDefinition.SECTION_MAX_Z) {
                    val block = section.blocks[ChunkSection.getIndex(x, y, z)]

                    block?.model?.singleRender(Vec3i(x, y, z), mesh, Random(0L), 0xFF, intArrayOf(0xF, 0xF, 0xF, 0xF))
                }
            }
        }



        return mesh
    }
}
