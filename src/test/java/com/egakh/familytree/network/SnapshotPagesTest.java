package com.egakh.familytree.network;

import com.egakh.familytree.client.SnapshotAssembler;
import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.HorseStats;
import io.netty.buffer.Unpooled;
import com.egakh.familytree.network.payloads.SnapshotPagePayload;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotPagesTest {
    @Test void sendsMoreThanTheOldCapWithinTheByteBudgetAndAssemblesEveryRecord() {
        List<AnimalRecord> records = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) records.add(new AnimalRecord(new UUID(0, i), "minecraft:wolf",
                "Moss " + i + " \u732b".repeat(20), false, new UUID(1, i), new UUID(2, i), 0, 0,
                false, null, null, new UUID(3, i), "Owner", "minecraft:pale",
                new AnimalRecord.LastSeen(1, 64, 2, "minecraft:overworld", 5), null));
        UUID request = UUID.randomUUID();
        var pages = SnapshotPages.split(request, records, 5, 100, true, true, false);
        assertTrue(pages.size() > 1);
        assertEquals(10_000, sendAndAssemble(request, pages).size());
    }

    @Test void horseStatsArriveWithEveryRecordAndPagesStayWithinTheByteBudget() {
        List<AnimalRecord> records = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            var horse = new AnimalRecord(new UUID(0, i), i % 2 == 0 ? "minecraft:horse" : "minecraft:llama",
                    "Comet " + i + " \u99AC".repeat(20), false, new UUID(1, i), new UUID(2, i), 0, 0,
                    false, null, null, new UUID(3, i), "Owner", "dark_brown/white_dots",
                    new AnimalRecord.LastSeen(1, 64, 2, "minecraft:overworld", 5),
                    new AnimalRecord.DeathCause("Zombie", "mob"));
            horse.setTreeName("Comet the " + i + " \u99AC".repeat(20));
            horse.setHorseStats(new HorseStats(0.1125 + i * 1e-6, 0.4 + i * 1e-6, 15.0 + i * 1e-3, i % 2 == 0 ? null : 1 + i % 5));
            records.add(horse);
        }
        UUID request = UUID.randomUUID();
        var pages = SnapshotPages.split(request, records, 5, 100, true, true, true);
        assertTrue(pages.size() > 1);
        var received = sendAndAssemble(request, pages);
        assertEquals(10_000, received.size());
        for (int i = 0; i < received.size(); i++) {
            assertEquals(records.get(i).id(), received.get(i).id());
            assertEquals(records.get(i).horseStats(), received.get(i).horseStats());
        }
    }

    private static List<AnimalRecord> sendAndAssemble(UUID request, List<SnapshotPagePayload> pages) {
        var assembler = new SnapshotAssembler();
        assembler.begin(request);
        List<AnimalRecord> received = null;
        for (int i = 0; i < pages.size(); i++) {
            var page = pages.get(i);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                SnapshotPagePayload.STREAM_CODEC.encode(buf, page);
                assertTrue(buf.readableBytes() <= SnapshotPages.MAX_PAGE_BYTES);
                page = SnapshotPagePayload.STREAM_CODEC.decode(buf);
                assertEquals(0, buf.readableBytes());
            } finally { buf.release(); }
            var result = assembler.accept(page);
            if (i == pages.size() - 1) received = result.orElseThrow().records();
            else assertTrue(result.isEmpty());
        }
        return received;
    }

    @Test void ignoresOldRequestsAndRejectsOutOfOrderPages() {
        UUID old = UUID.randomUUID(), current = UUID.randomUUID();
        var pages = SnapshotPages.split(old, List.of(), 5, 100, false, false, false);
        var assembler = new SnapshotAssembler(); assembler.begin(current);
        assertTrue(assembler.accept(pages.get(0)).isEmpty());
        assembler.begin(old);
        var page = pages.get(0);
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(new com.egakh.familytree.network.payloads.SnapshotPagePayload(
                old, 1, 2, 0, List.of(), 5, 100, false, false, false)));
    }

    @Test void limiterRejectsBurstsWithoutExtendingTheCooldown() {
        long[] now = {0};
        var limiter = new RequestLimiter<String>(100, () -> now[0]);
        assertTrue(limiter.allow("player"));
        now[0] = 99; assertFalse(limiter.allow("player"));
        now[0] = 100; assertTrue(limiter.allow("player"));
        limiter.remove("player"); assertTrue(limiter.allow("player"));
    }
}
