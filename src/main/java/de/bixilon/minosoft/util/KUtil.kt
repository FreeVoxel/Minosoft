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

package de.bixilon.minosoft.util

import de.bixilon.jiibles.Table
import de.bixilon.jiibles.TableStyles
import de.bixilon.kotlinglm.vec2.Vec2t
import de.bixilon.kotlinglm.vec3.Vec3t
import de.bixilon.kotlinglm.vec4.Vec4t
import de.bixilon.kutil.cast.CastUtil.unsafeCast
import de.bixilon.kutil.collections.CollectionUtil.synchronizedListOf
import de.bixilon.kutil.collections.CollectionUtil.synchronizedMapOf
import de.bixilon.kutil.collections.CollectionUtil.synchronizedSetOf
import de.bixilon.kutil.concurrent.pool.DefaultThreadPool
import de.bixilon.kutil.concurrent.time.TimeWorker
import de.bixilon.kutil.primitive.BooleanUtil.decide
import de.bixilon.kutil.reflection.ReflectionUtil.forceInit
import de.bixilon.kutil.reflection.ReflectionUtil.realName
import de.bixilon.minosoft.data.container.stack.ItemStack
import de.bixilon.minosoft.data.entities.entities.Entity
import de.bixilon.minosoft.data.registries.ResourceLocation
import de.bixilon.minosoft.data.registries.ResourceLocationAble
import de.bixilon.minosoft.data.text.ChatColors
import de.bixilon.minosoft.data.text.ChatComponent
import de.bixilon.minosoft.data.text.TextComponent
import de.bixilon.minosoft.data.text.TextFormattable
import de.bixilon.minosoft.modding.event.master.GlobalEventMaster
import de.bixilon.minosoft.protocol.network.connection.play.PlayConnection
import de.bixilon.minosoft.protocol.network.connection.status.StatusConnection
import de.bixilon.minosoft.protocol.network.network.client.NettyClient
import de.bixilon.minosoft.protocol.protocol.OutByteBuffer
import de.bixilon.minosoft.protocol.protocol.ProtocolDefinition
import de.bixilon.minosoft.util.account.microsoft.MicrosoftOAuthUtils
import de.bixilon.minosoft.util.json.Jackson
import de.bixilon.minosoft.util.url.URLProtocolStreamHandlers
import io.netty.channel.SimpleChannelInboundHandler
import org.kamranzafar.jtar.TarHeader
import java.util.*


object KUtil {
    val RANDOM = Random()

    init {
        Table.DEFAULT_STYLE = TableStyles.FANCY
    }

    fun bitSetOf(long: Long): BitSet {
        return BitSet.valueOf(longArrayOf(long))
    }

    fun Any?.toResourceLocation(): ResourceLocation {
        return when (this) {
            is String -> ResourceLocation(this)
            is ResourceLocation -> this
            else -> TODO("Don't know how to turn $this into a resource location!")
        }
    }

    fun <T> T.synchronizedDeepCopy(): T {
        return when (this) {
            is Map<*, *> -> {
                val map: MutableMap<Any?, Any?> = synchronizedMapOf()

                for ((key, value) in this) {
                    map[key.synchronizedDeepCopy()] = value.synchronizedDeepCopy()
                }
                map.unsafeCast()
            }
            is List<*> -> {
                val list: MutableList<Any?> = synchronizedListOf()

                for (key in this) {
                    list += key.synchronizedDeepCopy()
                }

                list.unsafeCast()
            }
            is Set<*> -> {
                val set: MutableSet<Any?> = synchronizedSetOf()

                for (key in this) {
                    set += key.synchronizedDeepCopy()
                }

                set.unsafeCast()
            }
            is ItemStack -> this.copy().unsafeCast()
            is ChatComponent -> this
            is String -> this
            is Number -> this
            is Boolean -> this
            null -> null.unsafeCast()
            else -> TODO("Don't know how to copy ${(this as T)!!::class.java.name}")
        }
    }

    fun Set<String>.toResourceLocationList(): Set<ResourceLocation> {
        val ret: MutableSet<ResourceLocation> = mutableSetOf()

        for (resourceLocation in this) {
            ret += resourceLocation.toResourceLocation()
        }

        return ret.toSet()
    }

    fun pause() {
        var setBreakPointHere = 1
    }

    /**
     * Converts millis to ticks
     */
    val Number.ticks: Int
        get() = this.toInt() / ProtocolDefinition.TICK_TIME

    /**
     * Converts ticks to millis
     */
    val Number.millis: Int
        get() = this.toInt() * ProtocolDefinition.TICK_TIME

    fun Collection<Int>.entities(connection: PlayConnection): Set<Entity> {
        val entities: MutableList<Entity> = mutableListOf()
        for (id in this) {
            entities += connection.world.entities[id] ?: continue
        }
        return entities.toSet()
    }

    fun Any?.format(): ChatComponent {
        return ChatComponent.of(
            when (this) {
                is ChatComponent -> return this
                null -> TextComponent("null").color(ChatColors.DARK_RED)
                is TextFormattable -> this.toText()
                is Boolean -> TextComponent(this.toString()).color(this.decide(ChatColors.GREEN, ChatColors.RED))
                is Enum<*> -> {
                    val name = this.name
                    TextComponent(
                        if (name.length == 1) {
                            name
                        } else {
                            name.lowercase()
                        }
                    ).color(ChatColors.YELLOW)
                }
                is Float -> "§d%.3f".format(this)
                is Double -> "§d%.4f".format(this)
                is Number -> TextComponent(this).color(ChatColors.LIGHT_PURPLE)
                is ResourceLocation -> TextComponent(this.toString()).color(ChatColors.GOLD)
                is ResourceLocationAble -> resourceLocation.format()
                is Vec4t<*> -> "(${this.x.format()} ${this.y.format()} ${this.z.format()} ${this.w.format()})"
                is Vec3t<*> -> "(${this.x.format()} ${this.y.format()} ${this.z.format()})"
                is Vec2t<*> -> "(${this.x.format()} ${this.y.format()})"
                else -> this.toString()
            }
        )
    }

    fun Any.toJson(beautiful: Boolean = false): String {
        return if (beautiful) {
            Jackson.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(this)
        } else {
            Jackson.MAPPER.writeValueAsString(this)
        }
    }

    fun String.fromJson(): Any {
        return Jackson.MAPPER.readValue(this, Jackson.JSON_MAP_TYPE)
    }

    val Throwable.text: TextComponent
        get() = TextComponent(this::class.java.realName + ": " + this.message).color(ChatColors.DARK_RED)


    fun <T : ResourceLocationAble> List<T>.asResourceLocationMap(): Map<ResourceLocation, T> {
        val ret: MutableMap<ResourceLocation, T> = mutableMapOf()

        for (value in this) {
            ret[value.resourceLocation] = value
        }

        return ret.toMap()
    }

    fun String?.nullCompare(other: String?): Int? {
        if (this == null || other == null) {
            return null
        }
        val compared = this.compareTo(other)
        if (compared != 0) {
            return compared
        }
        return null
    }

    fun Any?.autoType(): Any? {
        if (this == null) {
            return null
        }
        if (this is Number) {
            return this
        }
        val string = this.toString()

        if (string == "true") {
            return true
        }
        if (string == "false") {
            return false
        }

        // ToDo: Optimize
        if (string.matches("\\d+".toRegex())) {
            return string.toInt()
        }

        return string
    }


    val BooleanArray.isTrue: Boolean
        get() {
            for (boolean in this) {
                if (!boolean) {
                    return false
                }
            }
            return true
        }

    fun TarHeader.generalize() {
        userId = 0
        groupId = 0
        modTime = 0L
        userName = StringBuffer("nobody")
        groupName = StringBuffer("nobody")
    }


    fun initUtilClasses() {
        DefaultThreadPool += { GlobalEventMaster::class.java.forceInit() }
        DefaultThreadPool += { ShutdownManager::class.java.forceInit() }
        DefaultThreadPool += { Jackson::class.java.forceInit() }
        DefaultThreadPool += { URLProtocolStreamHandlers::class.java.forceInit() }
        DefaultThreadPool += { MicrosoftOAuthUtils::class.java.forceInit() }
        DefaultThreadPool += { TimeWorker::class.java.forceInit() }
        DefaultThreadPool += { SystemInformation::class.java.forceInit() }
        DefaultThreadPool += { StatusConnection::class.java.forceInit() }
        DefaultThreadPool += { PlayConnection::class.java.forceInit() }
        DefaultThreadPool += { NettyClient::class.java.forceInit() }
        DefaultThreadPool += { SimpleChannelInboundHandler::class.java.forceInit() }
    }

    fun ByteArray.withLengthPrefix(): ByteArray {
        val prefixed = OutByteBuffer()
        prefixed.writeByteArray(this)
        return prefixed.toArray()
    }

    fun String.codePointAtOrNull(index: Int): Int? {
        if (index < 0 || index >= this.length) {
            return null
        }
        return this.codePointAt(index)
    }

    fun String.toFavicon(): ByteArray {
        return Base64.getDecoder().decode(this.replace("data:image/png;base64,", "").replace("\n", ""))
    }
}
