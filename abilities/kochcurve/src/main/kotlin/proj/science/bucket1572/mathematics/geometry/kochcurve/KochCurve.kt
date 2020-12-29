package proj.science.bucket1572.mathematics.geometry.kochcurve

import com.github.noonmaru.psychics.AbilityConcept
import com.github.noonmaru.psychics.AbilityType
import com.github.noonmaru.psychics.ActiveAbility
import com.github.noonmaru.psychics.PsychicProjectile
import com.github.noonmaru.psychics.attribute.EsperAttribute
import com.github.noonmaru.psychics.attribute.EsperStatistic
import com.github.noonmaru.psychics.damage.Damage
import com.github.noonmaru.psychics.damage.DamageType
import com.github.noonmaru.psychics.item.isPsychicbound
import com.github.noonmaru.psychics.util.TargetFilter
import com.github.noonmaru.tap.config.Config
import com.github.noonmaru.tap.config.Name
import com.github.noonmaru.tap.fake.Trail
import com.github.noonmaru.tap.math.normalizeAndLength
import net.md_5.bungee.api.ChatColor
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector

@Name("concept")
class KochCurveConcept : AbilityConcept() {

    @Config
    var slownessTime: Int = 10

    @Config
    var slownessAmplifier: Int = 1

    @Config
    var split: Int = 2

    @Config
    var maxTicks: Int = 200

    @Config
    var projectileSpeed: Double = 1.0

    @Config
    var delay: Long = 20

    init {
        type = AbilityType.ACTIVE
        displayName = "코흐눈송이"
        levelRequirement = 0
        cooldownTicks = 0
        cost = 0.0
        castingTicks = 0
        range = 20.0
        damage = Damage(
            DamageType.RANGED, EsperStatistic.of(
                EsperAttribute.ATTACK_DAMAGE to 5.0
            )
        )

        val kochSnow = ItemStack(Material.PACKED_ICE, 1)
        kochSnow.apply {
            val meta = itemMeta
            meta.setDisplayName("${ChatColor.DARK_RED}${ChatColor.BOLD}코흐 눈송이")
            meta.addEnchant(Enchantment.DAMAGE_ALL, 1, false)
            meta.lore = listOf(
                "원거리 공격에 최적인 기하의 공격기"
            )
            meta.isPsychicbound = true
            itemMeta = meta
        }
        wand = kochSnow
        supplyItems = listOf(
            kochSnow
        )
        description = listOf(
            "자신이 바라보는 방향으로 눈송이를 발사합니다.",
            "살아있는 엔티티가 눈송이에 맞으면, ${slownessTime}만큼의 시간 동안",
            "${slownessAmplifier}의 구속 효과에 걸립니다.",
            "또한, ${split}만큼의 추가 눈송이가 ${delay / 20.0}초 후 피격 대상으로부터 날아갑니다. ",
            "효과는 원래 눈송이와 같습니다.",
            "",
            "${ChatColor.ITALIC}코흐 눈송이는 프랙탈의 일종입니다."
        )
    }
}

class KochCurve : ActiveAbility<KochCurveConcept>(), Listener {
    init {
        targeter = {
            esper.player.eyeLocation
        }
    }

    override fun onEnable() {
        psychic.registerEvents(this)
    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        val eyeLocation = esper.player.eyeLocation
        val projectile = KochSnowflake()
        psychic.launchProjectile(eyeLocation, projectile)
        projectile.velocity = eyeLocation.direction.multiply(concept.projectileSpeed)
    }

    private inner class KochSnowflake : PsychicProjectile(concept.maxTicks, concept.range) {
        var hitLivingEntity: LivingEntity? = null

        override fun onRemove() {
            val targetEntity: LivingEntity = hitLivingEntity ?: return
            if (targetEntity.type == EntityType.ARMOR_STAND) return
            targetEntity.addPotionEffect(
                PotionEffect(
                    PotionEffectType.SLOW, concept.slownessTime, concept.slownessAmplifier
                )
            )
            targetEntity.psychicDamage(concept.damage!!)

            for (i in 0 until concept.split) {
                val radians: Double = Math.toRadians(360.0 / concept.split) * (i + 1)
                val direction: Vector = velocity.clone().rotateAroundY(radians)
                val padding: Vector = direction.clone().multiply(2.5)
                val launchLocation: Location =
                    targetEntity.eyeLocation.clone().add(padding)
                if (launchLocation.block.isEmpty) {
                    val fractal = KochGenerator()
                    fractal.direction = direction.clone()
                    fractal.launchLocation = launchLocation.clone()
                    psychic.runTask(fractal, concept.delay)
                }
            }
        }

        override fun onTrail(trail: Trail) {
            trail.velocity?.let { velocity ->
                val from = trail.from
                val length = velocity.normalizeAndLength()

                from.world.rayTrace(
                    from,
                    velocity,
                    length,
                    FluidCollisionMode.NEVER,
                    true,
                    1.0,
                    TargetFilter(esper.player)
                )?.let { rayTraceResult ->
                    val hitEntity: Entity? = rayTraceResult.hitEntity
                    if (hitEntity is LivingEntity) {
                        hitLivingEntity = hitEntity
                    }

                    remove()
                }

                val to = trail.to
                to.world.spawnParticle(Particle.SNOWBALL, to, 5)
            }
        }
    }

    private inner class KochGenerator : Runnable {
        var launchLocation: Location? = null
        var direction: Vector? = null

        override fun run() {
            val projectile = KochSnowflake()
            psychic.launchProjectile(launchLocation!!, projectile)
            projectile.velocity = direction!!.clone().multiply(concept.projectileSpeed)
        }
    }

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        var placingItem: ItemStack = event.player.inventory.itemInMainHand
        if (placingItem.type.isAir) {
            placingItem = event.player.inventory.itemInOffHand
            if (placingItem.type.isAir) {
                return
            }
        }

        if ((placingItem.itemMeta == concept.wand!!.itemMeta) and
            (placingItem.type == concept.wand!!.type)
        ) {
            event.isCancelled = true
        }
    }
}