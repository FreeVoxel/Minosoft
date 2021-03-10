/*
 * Minosoft
 * Copyright (C) 2021 Moritz Zwerger
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program.If not, see <https://www.gnu.org/licenses/>.
 *
 * This software is not affiliated with Mojang AB, the original developer of Minecraft.
 */

package de.bixilon.minosoft.gui.rendering.hud.elements.debug

import de.bixilon.minosoft.data.text.ChatComponent
import de.bixilon.minosoft.gui.rendering.RenderConstants
import de.bixilon.minosoft.gui.rendering.hud.HUDRenderer
import de.bixilon.minosoft.gui.rendering.hud.elements.HUDElement
import de.bixilon.minosoft.gui.rendering.hud.elements.primitive.TextElement
import de.bixilon.minosoft.modding.loading.ModLoader
import de.bixilon.minosoft.util.GitInfo
import glm_.vec2.Vec2
import org.lwjgl.opengl.GL11.*
import oshi.SystemInfo


class HUDSystemDebugElement(hudRenderer: HUDRenderer) : HUDElement(hudRenderer) {

    private lateinit var gpuText: String
    private lateinit var gpuVersionText: String
    private val maxMemoryText: String = getFormattedMaxMemory()


    override fun init() {
        gpuText = glGetString(GL_RENDERER) ?: "unknown"
        gpuVersionText = glGetString(GL_VERSION) ?: "unknown"
    }

    override fun draw() {
        elementList.clear()

        for (text in listOf(
            "Java: ${Runtime.version()} ${System.getProperty("sun.arch.data.model")}bit",
            "Memory: ${getUsedMemoryPercent()}% ${getFormattedUsedMemory()}/$maxMemoryText",
            "Allocated: ${getAllocatedMemoryPercent()}% ${getFormattedAllocatedMemory()}",
            "System: $systemMemoryText",
            "",
            "OS: $osText",
            "CPU: $processorText",
            "",
            "Display: ${getScreenDimensions()}",
            "GPU: $gpuText",
            "Version: $gpuVersionText",
            "",
            if (GitInfo.IS_INITIALIZED) {
                "Commit: ${GitInfo.GIT_COMMIT_ID_DESCRIBE}: ${GitInfo.GIT_COMMIT_MESSAGE_SHORT}"
            } else {
                "GitInfo uninitialized :("
            },
            "",
            "Mods: ${ModLoader.MOD_MAP.size} active, ${hudRenderer.connection.eventListenerSize} listeners",
        )) {
            val textElement = TextElement(ChatComponent.valueOf(text), hudRenderer.renderWindow.font, Vec2(2, elementList.size.y + RenderConstants.TEXT_LINE_PADDING))
            elementList.addChild(textElement)
        }
        elementList.pushChildrenToRight(1.0f)
    }

    private fun getUsedMemory(): Long {
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun getFormattedUsedMemory(): String {
        return formatBytes(getUsedMemory())
    }

    private fun getAllocatedMemory(): Long {
        return runtime.totalMemory()
    }

    private fun getFormattedAllocatedMemory(): String {
        return formatBytes(getAllocatedMemory())
    }

    private fun getMaxMemory(): Long {
        return runtime.maxMemory()
    }

    private fun getFormattedMaxMemory(): String {
        return formatBytes(getMaxMemory())
    }

    private fun getUsedMemoryPercent(): Long {
        return getUsedMemory() * 100 / runtime.maxMemory()
    }

    private fun getAllocatedMemoryPercent(): Long {
        return getAllocatedMemory() * 100 / runtime.maxMemory()
    }

    private fun getScreenDimensions(): String {
        return "${hudRenderer.renderWindow.screenWidth}x${hudRenderer.renderWindow.screenHeight}"
    }

    companion object {
        private val UNITS = listOf("B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB", "ZiB", "YiB")

        fun formatBytes(bytes: Long): String {
            var lastFactor = 1L
            var currentFactor = 1024L
            for (unit in UNITS) {
                if (bytes < currentFactor) {
                    if (bytes < (lastFactor * 10)) {
                        return "${"%.1f".format(bytes / lastFactor.toFloat())}${unit}"
                    }
                    return "${bytes / lastFactor}${unit}"
                }
                lastFactor = currentFactor
                currentFactor *= 1024L
            }
            throw IllegalArgumentException()
        }

        private val runtime = Runtime.getRuntime()
        private val systemInfo = SystemInfo()
        private val systemInfoHardwareAbstractionLayer = systemInfo.hardware

        private val systemMemoryText: String = formatBytes(systemInfoHardwareAbstractionLayer.memory.total)
        private val osText: String = "${System.getProperty("os.name")}: ${systemInfo.operatingSystem.family} ${systemInfo.operatingSystem.bitness}bit"

        private val processorText = " ${runtime.availableProcessors()}x ${systemInfoHardwareAbstractionLayer.processor.processorIdentifier.name.replace("\\s{2,}".toRegex(), "")}"


    }
}
