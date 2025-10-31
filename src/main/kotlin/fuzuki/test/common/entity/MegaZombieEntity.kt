package fuzuki.test.common.entity

import com.mojang.logging.LogUtils
import net.minecraft.entity.EntityType
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.mob.ZombieEntity
import net.minecraft.item.ArmorItem
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.Difficulty
import net.minecraft.world.LocalDifficulty
import net.minecraft.world.World
import net.minecraft.entity.LivingEntity
import net.minecraft.world.GameRules

class MegaZombieEntity(entityType: EntityType<out ZombieEntity>, world: World) : ZombieEntity(entityType, world) {
    private var spawnCooldown = 0
    private var totalHelpersSpawned = 0

    override fun tick() {
        super.tick()
        if (world.isClient) return

        if (spawnCooldown > 0) spawnCooldown--
    }

    override fun damage(source: DamageSource, amount: Float): Boolean {
        val result = super.damage(source, amount)
        if (!result || world.isClient) {
            return result
        }

        if (spawnCooldown > 0 || totalHelpersSpawned >= MAX_HELPERS_PER_LIFE) {
            return result
        }

        val serverWorld = world as? ServerWorld ?: return result
        if (serverWorld.difficulty == Difficulty.PEACEFUL) return result

        val attacker = (source.attacker ?: source.source) as? LivingEntity ?: return result

        val nearbyCount = serverWorld.getEntitiesByType(EntityType.ZOMBIE) {
            it !== this && it.squaredDistanceTo(this) <= (HELPER_SCAN_RADIUS_SQ)
        }.size

        if (nearbyCount >= MAX_NEARBY_ZOMBIES) {
            LOGGER.debug("Skipping helper spawn: nearby zombie cap reached ({})", nearbyCount)
            return result
        }

        val helpersToSpawn = when (serverWorld.difficulty) {
            Difficulty.EASY -> 2
            Difficulty.NORMAL -> 3
            Difficulty.HARD -> 4
            else -> 0
        }.coerceAtMost(MAX_HELPERS_PER_LIFE - totalHelpersSpawned)

        if (helpersToSpawn <= 0) return result

        if (!serverWorld.gameRules.getBoolean(GameRules.DO_MOB_GRIEFING)) {
            LOGGER.debug("Skipping helper spawn: mob griefing disabled")
            return result
        }

        val spawned = spawnHelpers(serverWorld, attacker, helpersToSpawn)
        if (spawned > 0) {
            spawnCooldown = COOLDOWN_TICKS
            totalHelpersSpawned += spawned
        }

        return result
    }

    private fun spawnHelpers(serverWorld: ServerWorld, attacker: LivingEntity, count: Int): Int {
        val angles = IntArray(count) { index ->
            val fraction = index.toDouble() / count.toDouble()
            (fraction * 360.0).toInt()
        }

        var spawned = 0
        for (angleDeg in angles) {
            val rad = Math.toRadians(angleDeg.toDouble())
            val offset = Vec3d(Math.cos(rad) * SPAWN_RADIUS, 0.0, Math.sin(rad) * SPAWN_RADIUS)
            val tentative = BlockPos.ofFloored(attacker.pos.add(offset))
            val spawnPos = findSafeSpawnPosition(serverWorld, tentative) ?: continue

            val zombie = EntityType.ZOMBIE.create(serverWorld) ?: continue
            zombie.refreshPositionAndAngles(spawnPos, random.nextFloat() * 360f, 0f)
            zombie.initialize(
                serverWorld,
                serverWorld.getLocalDifficulty(spawnPos),
                net.minecraft.entity.SpawnReason.EVENT,
                null
            )
            zombie.target = attacker
            serverWorld.spawnEntity(zombie)
            spawned++
        }
        return spawned
    }

    private fun findSafeSpawnPosition(serverWorld: ServerWorld, around: BlockPos): BlockPos? {
        var pos = around
        for (dy in 0..MAX_VERTICAL_SEARCH) {
            val candidate = pos.down(dy)
            val state = serverWorld.getBlockState(candidate)
            if (!state.isAir && !state.getCollisionShape(serverWorld, candidate).isEmpty) {
                val above = candidate.up()
                val aboveTwo = above.up()
                if (serverWorld.getBlockState(above).isAir && serverWorld.getBlockState(aboveTwo).isAir) {
                    return above
                }
            }
        }
        return null
    }

    override fun onDeath(source: DamageSource) {
        super.onDeath(source)
        val serverWorld = world as? ServerWorld ?: return
        if (random.nextFloat() <= DEATH_MINION_CHANCE) {
            val zombie = EntityType.ZOMBIE.create(serverWorld) ?: return
            zombie.refreshPositionAndAngles(this.pos, this.yaw, this.pitch)
            zombie.equipStack(EquipmentSlot.MAINHAND, ItemStack(Items.IRON_SWORD))
            zombie.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0f)
            serverWorld.spawnEntity(zombie)
        }
    }

    companion object {
        private val LOGGER = LogUtils.getLogger()
        private const val COOLDOWN_SECONDS = 30
        private const val TICKS_PER_SECOND = 20
        private const val COOLDOWN_TICKS = COOLDOWN_SECONDS * TICKS_PER_SECOND
        private const val SPAWN_RADIUS = 2.0
        private const val MAX_HELPERS_PER_LIFE = 12
        private const val MAX_NEARBY_ZOMBIES = 24
        private const val HELPER_SCAN_RADIUS_SQ = 24.0 * 24.0
        private const val MAX_VERTICAL_SEARCH = 6
        private const val DEATH_MINION_CHANCE = 0.35f
        fun createMegaZombieAttributes(): DefaultAttributeContainer.Builder =
            ZombieEntity.createZombieAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 60.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.23)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.5)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0)

        private fun pickArmorTier(random: net.minecraft.util.math.random.Random): ArmorTier {
            val roll = random.nextInt(100)
            return when {
                roll < 40 -> ArmorTier.LEATHER
                roll < 60 -> ArmorTier.CHAIN
                roll < 80 -> ArmorTier.GOLD
                roll < 95 -> ArmorTier.IRON
                else -> ArmorTier.DIAMOND
            }
        }

        private fun helmetFor(tier: ArmorTier): Item = when (tier) {
            ArmorTier.LEATHER -> Items.LEATHER_HELMET
            ArmorTier.CHAIN -> Items.CHAINMAIL_HELMET
            ArmorTier.GOLD -> Items.GOLDEN_HELMET
            ArmorTier.IRON -> Items.IRON_HELMET
            ArmorTier.DIAMOND -> Items.DIAMOND_HELMET
        }

        private fun chestFor(tier: ArmorTier): Item = when (tier) {
            ArmorTier.LEATHER -> Items.LEATHER_CHESTPLATE
            ArmorTier.CHAIN -> Items.CHAINMAIL_CHESTPLATE
            ArmorTier.GOLD -> Items.GOLDEN_CHESTPLATE
            ArmorTier.IRON -> Items.IRON_CHESTPLATE
            ArmorTier.DIAMOND -> Items.DIAMOND_CHESTPLATE
        }

        private fun legsFor(tier: ArmorTier): Item = when (tier) {
            ArmorTier.LEATHER -> Items.LEATHER_LEGGINGS
            ArmorTier.CHAIN -> Items.CHAINMAIL_LEGGINGS
            ArmorTier.GOLD -> Items.GOLDEN_LEGGINGS
            ArmorTier.IRON -> Items.IRON_LEGGINGS
            ArmorTier.DIAMOND -> Items.DIAMOND_LEGGINGS
        }

        private fun bootsFor(tier: ArmorTier): Item = when (tier) {
            ArmorTier.LEATHER -> Items.LEATHER_BOOTS
            ArmorTier.CHAIN -> Items.CHAINMAIL_BOOTS
            ArmorTier.GOLD -> Items.GOLDEN_BOOTS
            ArmorTier.IRON -> Items.IRON_BOOTS
            ArmorTier.DIAMOND -> Items.DIAMOND_BOOTS
        }

        private enum class ArmorTier {
            LEATHER, CHAIN, GOLD, IRON, DIAMOND
        }
    }
    override fun initEquipment(random: net.minecraft.util.math.random.Random, difficulty: LocalDifficulty) {
        super.initEquipment(random, difficulty)

        val tier = pickArmorTier(random)
        equipStack(EquipmentSlot.HEAD, ItemStack(helmetFor(tier)))
        equipStack(EquipmentSlot.CHEST, ItemStack(chestFor(tier)))
        equipStack(EquipmentSlot.LEGS, ItemStack(legsFor(tier)))
        equipStack(EquipmentSlot.FEET, ItemStack(bootsFor(tier)))

        // optional: prevent easy farming by default
        setEquipmentDropChance(EquipmentSlot.HEAD, 0f)
        setEquipmentDropChance(EquipmentSlot.CHEST, 0f)
        setEquipmentDropChance(EquipmentSlot.LEGS, 0f)
        setEquipmentDropChance(EquipmentSlot.FEET, 0f)
    }
}
