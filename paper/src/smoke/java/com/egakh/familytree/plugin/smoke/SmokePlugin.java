package com.egakh.familytree.plugin.smoke;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.plugin.FamilyTreePlugin;
import com.egakh.familytree.plugin.storage.FamilyTreeStore;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Checks tracking on a real server with vanilla breeding AI and a real suffocation. Configure with system properties:
 * {@code familytree.smoke.history} requires that many records at startup, as after a restart or a mod world import.
 */
public final class SmokePlugin extends JavaPlugin implements Listener {
    private static final UUID OWNER = UUID.fromString("00000000-0000-4000-8000-000000000123");
    private final List<String> lines = new ArrayList<>();
    private final List<String> breedEvents = new ArrayList<>();
    private boolean failed;
    private FamilyTreePlugin familyTree;
    private World world;
    private Location base;
    private AbstractHorse horseA, horseB, camel, trapHorse;
    private Sheep wildA, wildB, keptC, wildD;
    private Animals frogA, frogB;

    @Override
    public void onEnable() {
        familyTree = (FamilyTreePlugin) Bukkit.getPluginManager().getPlugin("FamilyTree");
        getServer().getPluginManager().registerEvents(this, this);
        log("server " + Bukkit.getName() + " " + Bukkit.getVersion() + " / " + Bukkit.getBukkitVersion());
        world = Bukkit.getWorlds().get(0);
        log("world folder " + world.getWorldFolder() + ", history file " + familyTree.files().file());
        int history = Integer.getInteger("familytree.smoke.history", -1);
        if (history >= 0) {
            check("history kept at startup: " + store().all().size() + " records, expected at least " + history,
                    store().all().size() >= history && !store().readOnly());
            if (Boolean.getBoolean("familytree.smoke.historyOnly")) {
                finish();
                return;
            }
        }
        getServer().getScheduler().runTaskLater(this, this::setUp, 40L);
    }

    private FamilyTreeStore store() {
        return familyTree.store();
    }

    /** Spigot loads a newly forced chunk's entities a few ticks later, so animals are spawned once that has happened. */
    private void setUp() {
        for (int x = -1; x <= 0; x++) for (int z = 0; z <= 1; z++) world.setChunkForceLoaded(x, z, true);
        getServer().getScheduler().runTaskLater(this, this::spawnAnimals, 40L);
    }

    private void spawnAnimals() {
        base = new Location(world, 4.5, world.getHighestBlockYAt(4, 4) + 1, 4.5);
        int before = store().all().size();
        horseA = horse(base.clone().add(0, 0, 0), true);
        horseB = horse(base.clone().add(1.5, 0, 0), true);
        camel = (AbstractHorse) spawn(EntityType.valueOf("CAMEL"), base.clone().add(-4, 0, 3));
        trapHorse = (AbstractHorse) spawn(EntityType.SKELETON_HORSE, base.clone().add(-4, 0, -3));
        trapHorse.setTamed(true);
        wildA = sheep(base.clone().add(6, 0, 0), DyeColor.WHITE);
        wildB = sheep(base.clone().add(7, 0, 0), DyeColor.RED);
        keptC = sheep(base.clone().add(6, 0, 24), DyeColor.BLUE);
        wildD = sheep(base.clone().add(7, 0, 24), DyeColor.RED);
        frogA = (Animals) spawn(EntityType.valueOf("FROG"), base.clone().add(-16, 0, 0));
        frogB = (Animals) spawn(EntityType.valueOf("FROG"), base.clone().add(-16, 0, 1));
        getServer().getScheduler().runTaskLater(this, () -> scanAndBreed(before), 20L);
    }

    private void scanAndBreed(int before) {
        log("before scan: entities " + world.getEntities().size() + ", horse valid " + horseA.isValid() + ", tamed " + horseA.isTamed()
                + ", owner " + com.egakh.familytree.plugin.track.Entities.owner(horseA)
                + ", trackable " + familyTree.tracker().rules().isTrackable(horseA));
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "familytree scan");
        check("scan records the two owned horses", store().contains(horseA.getUniqueId()) && store().contains(horseB.getUniqueId()));
        check("a camel and an ownerless skeleton horse stay out", !store().contains(camel.getUniqueId()) && !store().contains(trapHorse.getUniqueId()));
        check("wild sheep stay out", !store().contains(wildA.getUniqueId()));
        AnimalRecord recordA = store().get(horseA.getUniqueId());
        check("owned horse has its owner and stats", recordA != null && OWNER.equals(recordA.ownerId()) && recordA.horseStats() != null
                && recordA.horseStats().speed() != null && recordA.variantId() != null && recordA.variantId().contains("/"));
        log("horse variant " + (recordA == null ? null : recordA.variantId()) + ", records before " + before + " after scan " + store().all().size());
        familyTree.tracker().addAnimal(store(), keptC, OWNER, "Owner");
        check("an added sheep keeps its owner and wool color", store().contains(keptC.getUniqueId())
                && "blue".equals(store().get(keptC.getUniqueId()).variantId()) && OWNER.equals(store().get(keptC.getUniqueId()).ownerId()));
        for (Animals animal : List.of(horseA, horseB, wildA, wildB, keptC, wildD, frogA, frogB)) animal.setLoveModeTicks(600);
        waitFor("vanilla AI breeds the horses", 1200, () -> child(horseA) != null, this::afterHorseFoal);
    }

    private void afterHorseFoal() {
        AnimalRecord foal = child(horseA);
        check("the foal has both parents, the parents' owner, stats, and a variant", foal != null
                && (horseB.getUniqueId().equals(foal.parentA()) || horseB.getUniqueId().equals(foal.parentB()))
                && OWNER.equals(foal.ownerId()) && foal.horseStats() != null && foal.variantId() != null);
        waitFor("vanilla AI breeds the recorded sheep", 1200, () -> child(keptC) != null, () -> {
            AnimalRecord lamb = child(keptC);
            check("a recorded family keeps growing without a player: the lamb, its owner, and the wild mate",
                    lamb != null && OWNER.equals(lamb.ownerId()) && store().contains(wildD.getUniqueId()) && lamb.variantOrigin() != null);
            log("lamb variant " + lamb.variantId() + " origin " + lamb.variantOrigin());
            waitFor("the wild sheep breed too", 600, () -> breedEvents.stream().anyMatch(event -> event.contains(wildA.getUniqueId().toString())), () -> {
                check("a lamb of wild sheep without a player stays out",
                        store().all().stream().noneMatch(record -> wildA.getUniqueId().equals(record.parentA()) || wildA.getUniqueId().equals(record.parentB())));
                suffocate();
            });
        });
    }

    private void suffocate() {
        horseB.setHealth(1.0);
        Location inGround = horseB.getLocation().clone();
        inGround.setY(world.getHighestBlockYAt(inGround) - 2);
        horseB.teleport(inGround);
        waitFor("a recorded horse suffocates", 400, () -> store().get(horseB.getUniqueId()).deceased(), () -> {
            AnimalRecord dead = store().get(horseB.getUniqueId());
            check("the death keeps its day and cause", dead.deathWorldDay() != null && dead.deathCause() != null
                    && "inWall".equals(dead.deathCause().msgId()));
            log("death cause " + dead.deathCause());
            horseA.setCustomName("Comet");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "familytree scan");
            getServer().getScheduler().runTaskLater(this, () -> {
                check("a name change reaches the record on the next scan", "Comet".equals(store().get(horseA.getUniqueId()).originalName()));
                log("frog breed events: " + breedEvents.stream().filter(event -> event.contains("frog")).toList());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "save-all");
                getServer().getScheduler().runTaskLater(this, this::checkSaved, 60L);
            }, 45L);
        });
    }

    private void checkSaved() {
        Path file = familyTree.files().file();
        check("save-all writes " + file, Files.exists(file) && !store().dirty());
        log("records saved: " + store().all().size());
        finish();
    }

    private AnimalRecord child(Entity parent) {
        for (AnimalRecord record : store().all())
            if (parent.getUniqueId().equals(record.parentA()) || parent.getUniqueId().equals(record.parentB())) return record;
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreed(EntityBreedEvent event) {
        String line = "breed " + event.getEntity().getType() + " " + event.getEntity().getUniqueId() + " mother "
                + event.getMother().getType() + " " + event.getMother().getUniqueId() + " father " + event.getFather().getUniqueId()
                + " breeder " + event.getBreeder() + " cancelled " + event.isCancelled() + " " + event.getEntity().getType().name().toLowerCase();
        breedEvents.add(line);
        log(line);
    }

    private AbstractHorse horse(Location at, boolean owned) {
        AbstractHorse horse = (AbstractHorse) spawn(EntityType.HORSE, at);
        horse.setAdult();
        if (owned) {
            horse.setTamed(true);
            horse.setOwner(Bukkit.getOfflinePlayer(OWNER));
        }
        return horse;
    }

    private Sheep sheep(Location at, DyeColor color) {
        Sheep sheep = (Sheep) spawn(EntityType.SHEEP, at);
        sheep.setAdult();
        sheep.setColor(color);
        return sheep;
    }

    private Entity spawn(EntityType type, Location at) {
        return world.spawnEntity(at, type);
    }

    private void waitFor(String what, int ticks, BooleanSupplier done, Runnable then) {
        BukkitTask[] task = new BukkitTask[1];
        int[] left = {ticks / 10};
        task[0] = getServer().getScheduler().runTaskTimer(this, () -> {
            if (done.getAsBoolean()) {
                task[0].cancel();
                check(what, true);
                then.run();
            } else if (--left[0] <= 0) {
                task[0].cancel();
                check(what + " (timed out)", false);
                finish();
            }
        }, 10L, 10L);
    }

    private void check(String what, boolean passed) {
        if (!passed) failed = true;
        log((passed ? "PASS " : "FAIL ") + what);
    }

    private void log(String line) {
        lines.add(line);
        getLogger().info(line);
    }

    private void finish() {
        lines.add(failed ? "RESULT FAIL" : "RESULT PASS");
        try {
            Files.createDirectories(getDataFolder().toPath());
            Files.write(getDataFolder().toPath().resolve("smoke-result.txt"), lines);
        } catch (IOException failure) {
            getLogger().severe("Could not write the smoke result: " + failure);
        }
        if (!Boolean.getBoolean("familytree.smoke.keepRunning")) Bukkit.shutdown();
    }
}
