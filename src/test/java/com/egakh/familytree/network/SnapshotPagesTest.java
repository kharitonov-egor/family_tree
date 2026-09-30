package com.egakh.familytree.network;

import com.egakh.familytree.client.SnapshotAssembler;
import com.egakh.familytree.data.AnimalRecord;
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
        var assembler = new SnapshotAssembler();
        assembler.begin(request);
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
            if (i == pages.size() - 1) assertEquals(10_000, result.orElseThrow().records().size());
            else assertTrue(result.isEmpty());
        }
    }

    @Test void ignoresOldRequestsAndRejectsOutOfOrderPages() {
        UUID old = UUID.randomUUID(), current = UUID.randomUUID();
        var pages = SnapshotPages.split(old, List.of(), 5, 100, false, false, false);
        var assembler = new SnapshotAssembler(); assembler.begin(current);
        assertTrue(assembler.accept(pages.getFirst()).isEmpty());
        assembler.begin(old);
        var page = pages.getFirst();
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
