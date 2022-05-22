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
package de.bixilon.minosoft.data.registries.registries

import de.bixilon.kutil.cast.CastUtil.nullCast
import de.bixilon.kutil.cast.CastUtil.unsafeCast
import de.bixilon.kutil.json.JsonObject
import de.bixilon.kutil.json.JsonUtil.asJsonObject
import de.bixilon.kutil.json.JsonUtil.toJsonObject
import de.bixilon.minosoft.data.container.InventorySlots
import de.bixilon.minosoft.data.entities.block.BlockDataDataType
import de.bixilon.minosoft.data.entities.data.EntityDataField
import de.bixilon.minosoft.data.entities.data.types.EntityDataDataTypes
import de.bixilon.minosoft.data.registries.*
import de.bixilon.minosoft.data.registries.biomes.Biome
import de.bixilon.minosoft.data.registries.biomes.BiomeCategory
import de.bixilon.minosoft.data.registries.biomes.BiomePrecipitation
import de.bixilon.minosoft.data.registries.blocks.entites.BlockEntityTypeRegistry
import de.bixilon.minosoft.data.registries.blocks.types.Block
import de.bixilon.minosoft.data.registries.dimension.Dimension
import de.bixilon.minosoft.data.registries.effects.StatusEffect
import de.bixilon.minosoft.data.registries.enchantment.Enchantment
import de.bixilon.minosoft.data.registries.entities.EntityType
import de.bixilon.minosoft.data.registries.entities.variants.CatVariant
import de.bixilon.minosoft.data.registries.entities.variants.FrogVariant
import de.bixilon.minosoft.data.registries.entities.villagers.VillagerProfession
import de.bixilon.minosoft.data.registries.fluid.Fluid
import de.bixilon.minosoft.data.registries.items.Item
import de.bixilon.minosoft.data.registries.items.ItemRegistry
import de.bixilon.minosoft.data.registries.materials.Material
import de.bixilon.minosoft.data.registries.other.containers.ContainerType
import de.bixilon.minosoft.data.registries.particle.ParticleType
import de.bixilon.minosoft.data.registries.registries.registry.*
import de.bixilon.minosoft.data.registries.statistics.Statistic
import de.bixilon.minosoft.data.registries.versions.Version
import de.bixilon.minosoft.datafixer.RegistryFixer.fix
import de.bixilon.minosoft.protocol.packets.c2s.play.entity.EntityActionC2SP
import de.bixilon.minosoft.protocol.packets.s2c.play.entity.EntityAnimationS2CP
import de.bixilon.minosoft.protocol.packets.s2c.play.title.TitleS2CF
import de.bixilon.minosoft.recipes.RecipeRegistry
import de.bixilon.minosoft.util.KUtil.toResourceLocation
import de.bixilon.minosoft.util.collections.Clearable
import de.bixilon.minosoft.util.logging.Log
import de.bixilon.minosoft.util.logging.LogLevels
import de.bixilon.minosoft.util.logging.LogMessageType
import de.bixilon.minosoft.util.nbt.tag.NBTUtil.listCast
import java.lang.reflect.Field


class Registries {
    val registries: MutableMap<ResourceLocation, AbstractRegistry<*>> = mutableMapOf()
    var shapes: MutableList<VoxelShape> = mutableListOf()
    val motiveRegistry: Registry<Motive> = register("motive", Registry(codec = Motive))
    val blockRegistry: Registry<Block> = register("block", Registry(flattened = true, codec = Block, metaType = MetaTypes.BLOCKS))
    val itemRegistry: ItemRegistry = register("item", ItemRegistry())
    val enchantmentRegistry: Registry<Enchantment> = register("enchantment", Registry(codec = Enchantment))
    val particleTypeRegistry: Registry<ParticleType> = register("particle_type", Registry(codec = ParticleType))
    val statusEffectRegistry: Registry<StatusEffect> = register("mob_effect", Registry(codec = StatusEffect))
    val statisticRegistry: Registry<Statistic> = register("custom_stat", Registry())
    val biomeRegistry: Registry<Biome> = register("biome", Registry(codec = Biome))
    val dimensionRegistry: Registry<Dimension> = register("dimension_type", Registry(codec = Dimension))
    val materialRegistry: Registry<Material> = register("material", Registry(codec = Material))
    val fluidRegistry: Registry<Fluid> = register("fluid", Registry(codec = Fluid))
    val soundEventRegistry: ResourceLocationRegistry = register("sound_event", ResourceLocationRegistry())
    val recipes = RecipeRegistry()

    val villagerProfessionRegistry: Registry<VillagerProfession> = register("villager_profession", Registry(codec = VillagerProfession))
    val villagerTypeRegistry: ResourceLocationRegistry = register("villager_type", ResourceLocationRegistry())

    val catVariants: Registry<CatVariant> = register("cat_variant", Registry(codec = CatVariant))
    val frogVariants: Registry<FrogVariant> = register("frog_variant", Registry(codec = FrogVariant))

    val equipmentSlotRegistry: EnumRegistry<InventorySlots.EquipmentSlots> = EnumRegistry(values = InventorySlots.EquipmentSlots)
    val handEquipmentSlotRegistry: EnumRegistry<InventorySlots.EquipmentSlots> = EnumRegistry(values = InventorySlots.EquipmentSlots)
    val armorEquipmentSlotRegistry: EnumRegistry<InventorySlots.EquipmentSlots> = EnumRegistry(values = InventorySlots.EquipmentSlots)
    val armorStandEquipmentSlotRegistry: EnumRegistry<InventorySlots.EquipmentSlots> = EnumRegistry(values = InventorySlots.EquipmentSlots)

    val entityDataDataDataTypesRegistry: EnumRegistry<EntityDataDataTypes> = EnumRegistry(values = EntityDataDataTypes)

    val titleActionsRegistry: EnumRegistry<TitleS2CF.TitleActions> = EnumRegistry(values = TitleS2CF.TitleActions)

    val entityAnimationRegistry: EnumRegistry<EntityAnimationS2CP.EntityAnimations> = EnumRegistry(values = EntityAnimationS2CP.EntityAnimations)
    val entityActionsRegistry: EnumRegistry<EntityActionC2SP.EntityActions> = EnumRegistry(values = EntityActionC2SP.EntityActions)

    val biomePrecipitationRegistry: FakeEnumRegistry<BiomePrecipitation> = FakeEnumRegistry(codec = BiomePrecipitation)
    val biomeCategoryRegistry: FakeEnumRegistry<BiomeCategory> = FakeEnumRegistry(codec = BiomeCategory)

    val blockStateRegistry = BlockStateRegistry(false)

    val entityDataIndexMap: MutableMap<EntityDataField, Int> = mutableMapOf()
    val entityTypeRegistry: Registry<EntityType> = register("entity_type", Registry(codec = EntityType))

    val blockEntityTypeRegistry = BlockEntityTypeRegistry()
    val blockDataDataDataTypeRegistry: Registry<BlockDataDataType> = Registry(codec = BlockDataDataType)

    val containerTypeRegistry: Registry<ContainerType> = Registry(codec = ContainerType)
    val gameEventRegistry: ResourceLocationRegistry = ResourceLocationRegistry()
    val worldEventRegistry: ResourceLocationRegistry = ResourceLocationRegistry()

    var isFullyLoaded = false
        private set

    private var isFlattened = false


    var parentRegistries: Registries? = null
        set(value) {
            field = value

            for (parentableField in PARENTABLE_FIELDS) {
                PARENTABLE_SET_PARENT_METHOD(parentableField.get(this), value?.let { parentableField.get(it) })
            }
        }

    fun getEntityDataIndex(field: EntityDataField): Int? {
        return entityDataIndexMap[field] ?: parentRegistries?.getEntityDataIndex(field)
    }

    fun load(version: Version, pixlyzerData: Map<String, Any>) {
        isFlattened = version.flattened
        blockRegistry.flattened = isFlattened
        blockStateRegistry.flattened = isFlattened
        itemRegistry.flattened = isFlattened
        // pre init stuff
        loadShapes(pixlyzerData["shapes"]?.toJsonObject())

        // enums
        equipmentSlotRegistry.initialize(pixlyzerData["equipment_slots"])
        handEquipmentSlotRegistry.initialize(pixlyzerData["hand_equipment_slots"])
        armorEquipmentSlotRegistry.initialize(pixlyzerData["armor_equipment_slots"])
        armorStandEquipmentSlotRegistry.initialize(pixlyzerData["armor_stand_equipment_slots"])

        entityDataDataDataTypesRegistry.initialize(pixlyzerData["entity_data_data_types"])

        titleActionsRegistry.initialize(pixlyzerData["title_actions"])
        entityAnimationRegistry.initialize(pixlyzerData["entity_animations"])
        entityActionsRegistry.initialize(pixlyzerData["entity_actions"])

        // id stuff
        biomeCategoryRegistry.update(pixlyzerData["biome_categories"]?.unsafeCast(), this)
        biomePrecipitationRegistry.update(pixlyzerData["biome_precipitations"]?.unsafeCast(), this)

        // id resource location stuff
        containerTypeRegistry.rawUpdate(pixlyzerData["container_types"]?.toJsonObject(), this)
        gameEventRegistry.rawUpdate(pixlyzerData["game_events"]?.toJsonObject(), this)
        worldEventRegistry.rawUpdate(pixlyzerData["world_events"]?.toJsonObject(), this)


        entityTypeRegistry.rawUpdate(pixlyzerData["entities"]?.toJsonObject(), this)

        motiveRegistry.rawUpdate(pixlyzerData["motives"]?.toJsonObject(), this)
        soundEventRegistry.rawUpdate(pixlyzerData["sound_events"]?.toJsonObject(), null)
        particleTypeRegistry.rawUpdate(pixlyzerData["particles"]?.toJsonObject(), this)
        materialRegistry.rawUpdate(pixlyzerData["materials"]?.toJsonObject(), this)
        enchantmentRegistry.rawUpdate(pixlyzerData["enchantments"]?.toJsonObject(), this)
        statusEffectRegistry.rawUpdate(pixlyzerData["status_effects"]?.toJsonObject(), this)
        biomeRegistry.rawUpdate(pixlyzerData["biomes"]?.toJsonObject(), this)
        dimensionRegistry.rawUpdate(pixlyzerData["dimensions"]?.toJsonObject(), this)
        fluidRegistry.rawUpdate(pixlyzerData["fluids"]?.toJsonObject(), this)
        blockRegistry.rawUpdate(pixlyzerData["blocks"]?.toJsonObject(), this)
        itemRegistry.rawUpdate(pixlyzerData["items"]?.toJsonObject(), this)

        blockEntityTypeRegistry.rawUpdate(pixlyzerData["block_entities"]?.toJsonObject(), this)

        villagerProfessionRegistry.rawUpdate(pixlyzerData["villager_professions"]?.toJsonObject(), this)
        villagerTypeRegistry.rawUpdate(pixlyzerData["villager_types"]?.toJsonObject(), null)


        blockDataDataDataTypeRegistry.rawUpdate(pixlyzerData["block_data_data_types"]?.toJsonObject(), this)

        catVariants.rawUpdate(pixlyzerData["variant/cat"]?.toJsonObject(), this)
        frogVariants.rawUpdate(pixlyzerData["variant/frog"]?.toJsonObject(), this)


        // post init
        for (field in TYPE_MAP.values) {
            field.get(this).unsafeCast<Registry<*>>().postInit(this)
        }
        isFullyLoaded = true
        shapes.clear()
    }

    private fun loadShapes(pixlyzerData: Map<String, Any>?) {
        pixlyzerData ?: return
        val aabbs = loadAABBs(pixlyzerData["aabbs"].nullCast()!!)
        loadVoxelShapes(pixlyzerData["shapes"].unsafeCast(), aabbs)
    }

    private fun loadVoxelShapes(pixlyzerData: Collection<Any>, aabbs: List<AABB>) {
        for (shape in pixlyzerData) {
            shapes.add(VoxelShape(shape, aabbs))
        }
    }

    private fun loadAABBs(pixlyzerData: Collection<Map<String, Any>>): List<AABB> {
        val aabbs = mutableListOf<AABB>()
        for (data in pixlyzerData) {
            aabbs.add(AABB(data))
        }
        return aabbs
    }

    fun clear() {
        for (field in this::class.java.fields) {
            if (!field.type.isAssignableFrom(Clearable::class.java)) {
                continue
            }
            field.javaClass.getMethod("clear")(this)
        }
    }

    operator fun <T : RegistryItem> get(type: Class<T>): Registry<T>? {
        var currentField: Field?
        var currentClass: Class<*> = type
        do {
            currentField = TYPE_MAP[currentClass]
            currentClass = currentClass.superclass
        } while (currentField == null && currentClass != Object::class.java)
        return currentField?.get(this) as Registry<T>?
    }


    private fun <T, R : AbstractRegistry<T>> register(name: String, registry: R): R {
        registries[name.toResourceLocation()] = registry

        return registry
    }

    operator fun get(name: ResourceLocation): AbstractRegistry<*>? {
        return registries[name]
    }

    fun update(registries: JsonObject) {
        for ((key, value) in registries) {
            val fixedKey = key.toResourceLocation().fix()
            if (fixedKey in IGNORED_REGISTRIES) {
                continue
            }
            val registry = this[fixedKey]
            if (registry == null) {
                Log.log(LogMessageType.VERSION_LOADING, LogLevels.WARN) { "Can not find registry: $fixedKey" }
                continue
            }
            val values: List<JsonObject> = if (value is List<*>) {
                value.unsafeCast()
            } else {
                value.asJsonObject()["value"].listCast()!!
            }

            registry.update(values, this)
        }
    }

    companion object {
        val IGNORED_REGISTRIES = setOf("minecraft:worldgen/biome".toResourceLocation())
        private val PARENTABLE_FIELDS: List<Field>
        private val PARENTABLE_SET_PARENT_METHOD = Parentable::class.java.getDeclaredMethod("setParent", Any::class.java)
        private val TYPE_MAP: Map<Class<*>, Field>

        init {
            val fields: MutableList<Field> = mutableListOf()

            for (field in Registries::class.java.declaredFields) {
                if (!Parentable::class.java.isAssignableFrom(field.type)) {
                    continue
                }
                fields.add(field)
            }

            PARENTABLE_FIELDS = fields.toList()
        }

        init {
            val types: MutableMap<Class<*>, Field> = mutableMapOf()


            for (field in Registries::class.java.declaredFields) {
                if (!Registry::class.java.isAssignableFrom(field.type)) {
                    continue
                }
                field.isAccessible = true

                var generic = field.genericType

                if (field.type != Registry::class.java) {
                    var type = field.type
                    while (type != Object::class.java) {
                        if (type.superclass == Registry::class.java) {
                            generic = type.genericSuperclass
                            break
                        }
                        type = type.superclass
                    }
                }

                types[RegistryUtil.getClassOfFactory(generic)] = field
            }

            types[Item::class.java] = Registries::class.java.getDeclaredField("itemRegistry")

            TYPE_MAP = types.toMap()
        }
    }
}
