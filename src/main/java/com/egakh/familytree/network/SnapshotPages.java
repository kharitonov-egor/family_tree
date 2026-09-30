package com.egakh.familytree.network;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.network.payloads.SnapshotPagePayload;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SnapshotPages {
    public static final int MAX_PAGE_BYTES = 256 * 1024;
    public static final int MAX_RECORDS = 100_000;
    private static final int HEADER_BYTES = 64;
    private SnapshotPages() {}

    public static List<SnapshotPagePayload> split(UUID requestId, List<AnimalRecord> records,
                                                 long day, long epoch, boolean all, boolean viewing, boolean operator) {
        if (records.size() > MAX_RECORDS) throw new IllegalArgumentException("Too many records in one browser");
        List<List<AnimalRecord>> parts = new ArrayList<>();
        List<AnimalRecord> current = new ArrayList<>();
        int bytes = HEADER_BYTES;
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (AnimalRecord record : records) {
                buffer.clear();
                AnimalRecord.STREAM_CODEC.encode(buffer, record);
                int size = buffer.readableBytes();
                if (size + HEADER_BYTES > MAX_PAGE_BYTES) throw new IllegalArgumentException("Pet record exceeds page budget");
                if (bytes + size > MAX_PAGE_BYTES || current.size() == 8192) {
                    parts.add(List.copyOf(current));
                    current.clear();
                    bytes = HEADER_BYTES;
                }
                current.add(record);
                bytes += size;
            }
            if (!current.isEmpty() || parts.isEmpty()) parts.add(List.copyOf(current));
        } finally { buffer.release(); }
        List<SnapshotPagePayload> result = new ArrayList<>(parts.size());
        for (int i = 0; i < parts.size(); i++) result.add(new SnapshotPagePayload(requestId, i, parts.size(),
                records.size(), parts.get(i), day, epoch, all, viewing, operator));
        return result;
    }
}
