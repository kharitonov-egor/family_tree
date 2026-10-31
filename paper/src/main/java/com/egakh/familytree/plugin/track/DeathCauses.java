package com.egakh.familytree.plugin.track;

import com.egakh.familytree.data.AnimalRecord;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Map;

/**
 * The death cause as the mod saves it: Minecraft's death message id, such as {@code inWall}, and the attacker's name.
 * Bukkit 1.20.4 and newer expose the damage type directly. Older servers only give a cause, which maps to the id.
 */
public final class DeathCauses {
    private static final Map<String, String> LEGACY = Map.ofEntries(
            Map.entry("CONTACT", "cactus"), Map.entry("SUFFOCATION", "inWall"), Map.entry("FALL", "fall"),
            Map.entry("FIRE", "inFire"), Map.entry("FIRE_TICK", "onFire"), Map.entry("MELTING", "onFire"),
            Map.entry("LAVA", "lava"), Map.entry("DROWNING", "drown"), Map.entry("BLOCK_EXPLOSION", "explosion"),
            Map.entry("VOID", "outOfWorld"), Map.entry("LIGHTNING", "lightningBolt"), Map.entry("SUICIDE", "generic"),
            Map.entry("STARVATION", "starve"), Map.entry("POISON", "magic"), Map.entry("MAGIC", "magic"),
            Map.entry("WITHER", "wither"), Map.entry("FALLING_BLOCK", "fallingBlock"), Map.entry("THORNS", "thorns"),
            Map.entry("DRAGON_BREATH", "dragonBreath"), Map.entry("CUSTOM", "generic"), Map.entry("FLY_INTO_WALL", "flyIntoWall"),
            Map.entry("HOT_FLOOR", "hotFloor"), Map.entry("CRAMMING", "cramming"), Map.entry("DRYOUT", "dryout"),
            Map.entry("FREEZE", "freeze"), Map.entry("SONIC_BOOM", "sonic_boom"), Map.entry("KILL", "genericKill"),
            Map.entry("WORLD_BORDER", "outsideBorder"));

    private DeathCauses() {}

    public static AnimalRecord.DeathCause of(LivingEntity entity) {
        EntityDamageEvent last = entity.getLastDamageCause();
        if (last == null) return new AnimalRecord.DeathCause(null, "generic");
        Object source = Compat.call(last, "getDamageSource");
        if (source != null) {
            Object msgId = Compat.call(Compat.call(source, "getDamageType"), "getTranslationKey");
            Object causing = Compat.call(source, "getCausingEntity");
            if (msgId instanceof String id) return new AnimalRecord.DeathCause(name(causing), id);
        }
        Entity attacker = null;
        if (last instanceof EntityDamageByEntityEvent byEntity) {
            attacker = byEntity.getDamager();
            if (attacker instanceof Projectile projectile) {
                ProjectileSource shooter = projectile.getShooter();
                attacker = shooter instanceof Entity entityShooter ? entityShooter : null;
            }
        }
        String cause = last.getCause().name();
        String msgId = switch (cause) {
            case "ENTITY_ATTACK", "ENTITY_SWEEP_ATTACK" -> attacker instanceof Player ? "player" : "mob";
            case "PROJECTILE" -> last instanceof EntityDamageByEntityEvent byEntity ? projectileId(byEntity.getDamager()) : "arrow";
            case "ENTITY_EXPLOSION" -> attacker != null ? "explosion.player" : "explosion";
            case "MAGIC" -> attacker != null ? "indirectMagic" : "magic";
            default -> LEGACY.getOrDefault(cause, "generic");
        };
        return new AnimalRecord.DeathCause(name(attacker), msgId);
    }

    private static String projectileId(Entity projectile) {
        String type = Entities.species(projectile);
        return switch (type) {
            case "minecraft:trident" -> "trident";
            case "minecraft:fireball", "minecraft:small_fireball", "minecraft:dragon_fireball" -> "fireball";
            case "minecraft:arrow", "minecraft:spectral_arrow" -> "arrow";
            default -> "thrown";
        };
    }

    private static String name(Object entity) {
        return entity instanceof Entity living ? living.getName() : null;
    }
}
