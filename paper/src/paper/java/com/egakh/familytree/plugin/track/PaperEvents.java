package com.egakh.familytree.plugin.track;

import io.papermc.paper.event.entity.EntityFertilizeEggEvent;
import org.bukkit.entity.Animals;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Paper fires its own event, not EntityBreedEvent, when frogs, sniffers, and turtles breed, because they lay spawn
 * or eggs. Like the mod, Family Tree records both parents.
 */
final class PaperEvents implements Listener {
    private final Tracker tracker;

    PaperEvents(Tracker tracker) {
        this.tracker = tracker;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFertilize(EntityFertilizeEggEvent event) {
        if (!(event.getMother() instanceof Animals mother) || !(event.getFather() instanceof Animals father)) return;
        tracker.onBredWithoutBaby(mother, father, event.getBreeder());
    }
}
