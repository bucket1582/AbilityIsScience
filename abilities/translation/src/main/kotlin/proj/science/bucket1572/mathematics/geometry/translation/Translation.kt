// TODO: 2020-12-29 효과 / 연출 
package proj.science.bucket1572.mathematics.geometry.translation

import com.github.noonmaru.psychics.AbilityConcept
import com.github.noonmaru.psychics.AbilityType
import com.github.noonmaru.psychics.ActiveAbility
import com.github.noonmaru.psychics.PsychicProjectile
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
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack

@Name("concept")
class TranslationConcept : AbilityConcept() {

    @Config
    var maxTicks: Int = 200

    @Config
    var projectileSpeed: Double = 1.0

    init {
        type = AbilityType.ACTIVE
        displayName = "평행이동"
        levelRequirement = 0
        cooldownTicks = 0
        cost = 0.0
        castingTicks = 0
        range = 20.0
        val arrowBlock = ItemStack(Material.MAGENTA_GLAZED_TERRACOTTA, 1)
        arrowBlock.apply {
            val meta = itemMeta
            meta.setDisplayName("${ChatColor.LIGHT_PURPLE}${ChatColor.BOLD}평행이동")
            meta.addEnchant(Enchantment.SOUL_SPEED, 1, false)
            meta.lore = listOf(
                "순식간에 먼 거리로 이동하는 기하의 이동기"
            )
            meta.isPsychicbound = true
            itemMeta = meta
        }
        wand = arrowBlock
        supplyItems = listOf(
            arrowBlock
        )
        description = listOf(
            "자신이 바라보는 방향으로 광선을 발사합니다.",
            "광선이 $range 블록 내의 어떤 대상(블록, 엔티티 모두)을 맞추면,",
            "해당 대상 바로 앞으로 순간이동합니다.",
            "",
            "${ChatColor.ITALIC}y=f(x)를 x축 방향으로 a만큼 y축 방향으로 b만큼 평행이동하면,",
            "${ChatColor.ITALIC}y=f(x-a)+b"
        )
    }
}

class Translation : ActiveAbility<TranslationConcept>(), Listener {
    init {
        targeter = {
            esper.player.eyeLocation
        }
    }

    override fun onEnable() {
        psychic.registerEvents(this)
    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        val projectile = Dummy()
        val eyeLocation = esper.player.eyeLocation
        psychic.launchProjectile(eyeLocation, projectile)
        projectile.velocity = eyeLocation.direction.multiply(concept.projectileSpeed)
    }

    private inner class Dummy : PsychicProjectile(concept.maxTicks, concept.range) {
        var teleportPosition: Location? = null

        override fun onRemove() {
            esper.player.teleport(teleportPosition!!)
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
                    val locationVector = rayTraceResult.hitPosition.subtract(velocity)
                    teleportPosition = Location(
                        esper.player.world,
                        locationVector.x, locationVector.y, locationVector.z
                    )

                    remove()
                }

                val to = trail.to
                to.world.spawnParticle(Particle.FIREWORKS_SPARK, to, 1)
            }
        }
    }

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        var placingItem : ItemStack = event.player.inventory.itemInMainHand
        if (placingItem.type.isAir) {
            placingItem = event.player.inventory.itemInOffHand
            if (placingItem.type.isAir) {
                return
            }
        }

        if ((placingItem.itemMeta == concept.wand!!.itemMeta) and
            (placingItem.type == concept.wand!!.type)
        ){
            event.isCancelled = true
        }
    }
}