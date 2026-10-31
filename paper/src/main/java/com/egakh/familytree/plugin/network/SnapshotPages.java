package com.egakh.familytree.plugin.network;

import com.egakh.familytree.data.AnimalRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Splits a family list into pages, as the mod's {@code SnapshotPages} does, within the server's plugin message limit. */
final class SnapshotPages {
    static final int MAX_PAGE_BYTES = 256 * 1024;
    static final int MAX_RECORDS = 100_000;
    private static final int HEADER_BYTES = 64;

    private SnapshotPages() {}

    static List<Payloads.SnapshotPage> split(UUID requestId, List<AnimalRecord> records, long day, long epoch,
                                             boolean all, boolean viewing, boolean operator, int pageBytes) {
        if (records.size() > MAX_RECORDS) throw new IllegalArgumentException("Too many records in one browser");
        int budget = Math.min(MAX_PAGE_BYTES, pageBytes);
        List<List<AnimalRecord>> parts = new ArrayList<>();
        List<AnimalRecord> current = new ArrayList<>();
        int bytes = HEADER_BYTES;
        for (AnimalRecord record : records) {
            int size = Payloads.recordSize(record);
            if (size + HEADER_BYTES > budget) throw new IllegalArgumentException("Pet record exceeds page budget");
            if (bytes + size > budget || current.size() == Payloads.MAX_PAGE_RECORDS) {
                parts.add(List.copyOf(current));
                current.clear();
                bytes = HEADER_BYTES;
            }
            current.add(record);
            bytes += size;
        }
        if (!current.isEmpty() || parts.isEmpty()) parts.add(List.copyOf(current));
        List<Payloads.SnapshotPage> result = new ArrayList<>(parts.size());
        for (int i = 0; i < parts.size(); i++) result.add(new Payloads.SnapshotPage(requestId, i, parts.size(),
                records.size(), parts.get(i), day, epoch, all, viewing, operator));
        return result;
    }
}
