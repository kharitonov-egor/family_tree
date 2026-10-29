package com.egakh.familytree.integration;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.HorseStats;
import com.egakh.familytree.permissions.PetAccess;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class PetInsightTest {
    private static final UUID ALEX = new UUID(1, 1);
    private static final UUID SAM = new UUID(2, 2);
    private final Map<UUID, AnimalRecord> records = new HashMap<>();

    private AnimalRecord horse(long id, String name, UUID owner, AnimalRecord a, AnimalRecord b, HorseStats stats) {
        AnimalRecord record = new AnimalRecord(new UUID(9, id), "minecraft:horse", name, false,
                a == null ? null : a.id(), b == null ? null : b.id(), 1, 1, false, null, null, owner, "Owner", null,
                new AnimalRecord.LastSeen(10, 64, 10, "minecraft:overworld", 1), null);
        record.setHorseStats(stats);
        records.put(record.id(), record);
        return record;
    }

    private Function<UUID, AnimalRecord> visibleTo(UUID player, boolean viewAll) {
        return id -> {
            AnimalRecord record = records.get(id);
            return PetAccess.canView(record, player, viewAll) ? record : null;
        };
    }

    @Test void ownerSeesNamesGenerationAndParentStats() {
        AnimalRecord grandparent = horse(1, "Old Comet", ALEX, null, null, null);
        AnimalRecord comet = horse(2, "Comet", ALEX, grandparent, null, new HorseStats(0.3, 0.7, 28.0, null));
        AnimalRecord dusk = horse(3, "Dusk", ALEX, null, null, new HorseStats(0.25, 0.8, 24.0, null));
        AnimalRecord ember = horse(4, "Ember", ALEX, comet, dusk, new HorseStats(0.32, 0.75, 30.0, null));
        ember.setTreeName("Little Ember");

        PetInsight insight = PetInsight.of(ember.id(), visibleTo(ALEX, false));

        assertEquals(new PetInsight("Little Ember", "Comet", "Dusk", 3, ember.horseStats(), comet.horseStats(), dusk.horseStats()), insight);
    }

    @Test void hiddenPetsAndParentsStayHidden() {
        AnimalRecord foreign = horse(1, "Shadow", SAM, null, null, new HorseStats(0.33, 0.9, 30.0, null));
        AnimalRecord comet = horse(2, "Comet", ALEX, null, null, null);
        AnimalRecord ember = horse(3, "Ember", ALEX, comet, foreign, null);

        assertNull(PetInsight.of(foreign.id(), visibleTo(ALEX, false)));
        PetInsight insight = PetInsight.of(ember.id(), visibleTo(ALEX, false));
        assertEquals("Comet", insight.parentA());
        assertNull(insight.parentB());
        assertNull(insight.parentBStats());
        assertEquals(2, insight.generation());
        assertEquals("Shadow", PetInsight.of(ember.id(), visibleTo(ALEX, true)).parentB());
    }

    @Test void survivesTheTooltipTag() {
        AnimalRecord comet = horse(1, "Comet", ALEX, null, null, new HorseStats(0.3, null, 28.0, 3));
        AnimalRecord ember = horse(2, "Ember", ALEX, comet, null, new HorseStats(0.32, 0.75, null, null));
        PetInsight insight = PetInsight.of(ember.id(), visibleTo(ALEX, false));
        CompoundTag tag = new CompoundTag();
        PetInsight.write(tag, insight);
        assertEquals(insight, PetInsight.read(tag));
        assertNull(PetInsight.read(new CompoundTag()));
    }

    @Test void parentLoopsDoNotHang() {
        AnimalRecord a = horse(1, "A", ALEX, null, null, null);
        AnimalRecord b = horse(2, "B", ALEX, a, null, null);
        a.setParents(b.id(), null);
        assertNotNull(PetInsight.of(a.id(), visibleTo(ALEX, false)));
    }
}
