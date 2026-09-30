package com.egakh.familytree.client;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.network.SnapshotPages;
import com.egakh.familytree.network.payloads.FamilyTreeSnapshotPayload;
import com.egakh.familytree.network.payloads.SnapshotPagePayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Keeps partial responses out of the browser and ignores replies to older requests. */
public final class SnapshotAssembler {
    private UUID request;
    private SnapshotPagePayload first;
    private final List<AnimalRecord> records = new ArrayList<>();
    private int next;

    public void begin(UUID id) { clear(); request = id; }
    public void clear() { request = null; first = null; records.clear(); next = 0; }
    public boolean accepts(UUID id) { return id.equals(request); }

    public Optional<FamilyTreeSnapshotPayload> accept(SnapshotPagePayload page) {
        if (!accepts(page.requestId())) return Optional.empty();
        if (page.pageCount() < 1 || page.pageCount() > SnapshotPages.MAX_RECORDS
                || page.index() != next || page.totalRecords() < 0 || page.totalRecords() > SnapshotPages.MAX_RECORDS)
            throw new IllegalArgumentException("Invalid snapshot page");
        if (first == null) first = page;
        if (first.pageCount() != page.pageCount() || first.totalRecords() != page.totalRecords()
                || first.currentWorldDay() != page.currentWorldDay() || first.currentEpochMillis() != page.currentEpochMillis()
                || first.mayViewAll() != page.mayViewAll() || first.viewingAll() != page.viewingAll()
                || first.mayManageAll() != page.mayManageAll()) throw new IllegalArgumentException("Snapshot metadata changed");
        if (records.size() + page.records().size() > page.totalRecords()) throw new IllegalArgumentException("Snapshot count exceeded");
        records.addAll(page.records());
        next++;
        if (next != page.pageCount()) return Optional.empty();
        if (records.size() != page.totalRecords()
                || records.stream().map(AnimalRecord::id).distinct().count() != records.size())
            throw new IllegalArgumentException("Incomplete or duplicate snapshot records");
        var snapshot = new FamilyTreeSnapshotPayload(List.copyOf(records), first.currentWorldDay(), first.currentEpochMillis(),
                first.mayViewAll(), first.viewingAll(), first.mayManageAll());
        clear();
        return Optional.of(snapshot);
    }
}
