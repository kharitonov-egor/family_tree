package com.egakh.familytree.plugin.track;

import com.egakh.familytree.plugin.FamilyTreePlugin;
import org.bukkit.Material;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.UUID;

/** Bukkit events in place of the mod's mixins and loader events. Everything runs on the main thread. */
public final class TrackingListener implements Listener {
    private final FamilyTreePlugin plugin;
    private final Tracker tracker;

    public TrackingListener(FamilyTreePlugin plugin, Tracker tracker) {
        this.plugin = plugin;
        this.tracker = tracker;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) tracker.onLoad(entity);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) tracker.onUnload(entity);
    }

    /** Fires before the baby joins the world, with its owner, variant, and position already set. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getMother() instanceof Animals mother) || !(event.getFather() instanceof Animals father)) return;
        Player breeder = event.getBreeder() instanceof Player player ? player : null;
        if (event.getEntity() instanceof Animals baby) tracker.onBred(mother, father, baby, breeder);
    }

    /** Fires before Minecraft tames the animal, so the record waits a tick for the owner to be set. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        AnimalTamer owner = event.getOwner();
        if (!(owner instanceof Player player)) return;
        LivingEntity entity = event.getEntity();
        UUID id = player.getUniqueId();
        String name = player.getName();
        plugin.later(() -> {
            if (entity.isValid() && entity instanceof Tameable tameable && tameable.isTamed()) tracker.onTamed(entity, id, name);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNameTag(PlayerInteractEntityEvent event) {
        Entity target = event.getRightClicked();
        if (!(target instanceof Animals)) return;
        ItemStack held = event.getHand() == EquipmentSlot.OFF_HAND ? event.getPlayer().getInventory().getItemInOffHand()
                : event.getPlayer().getInventory().getItemInMainHand();
        if (held.getType() != Material.NAME_TAG || held.getItemMeta() == null || !held.getItemMeta().hasDisplayName()) return;
        Player player = event.getPlayer();
        String before = Entities.customName(target);
        plugin.later(() -> {
            String after = Entities.customName(target);
            if (!target.isValid() || after == null || Objects.equals(before, after) && plugin.store().contains(target.getUniqueId())) return;
            tracker.onNameTagged(target, player);
            tracker.onCustomNameChanged(target);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMount(VehicleEnterEvent event) {
        if (event.getVehicle() instanceof AbstractHorse horse && event.getEntered() instanceof Player rider)
            tracker.onMountRidden(horse, rider);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Animals) || !plugin.store().contains(entity.getUniqueId())) return;
        tracker.onDeath(entity, DeathCauses.of(entity));
    }
}
