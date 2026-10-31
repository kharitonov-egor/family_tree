package com.egakh.familytree.plugin.network;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.HorseStats;
import com.egakh.familytree.naming.PetRenaming;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The mod's payloads, written field by field in its codec order. {@code WireParityTest} checks these bytes against
 * the expected bytes in the mod's {@code WireFormatTest}.
 */
public final class Payloads {
    public static final String NAMESPACE = "familytree";
    public static final String OPEN = "familytree:open_request_v4";
    public static final String DISCOVER = "familytree:discover_v3";
    public static final String LOCATE = "familytree:locate";
    public static final String RENAME = "familytree:rename_request_v2";
    public static final String LINK = "familytree:link_parents";
    public static final String WAYPOINT = "familytree:waypoint_request_v1";
    public static final String RELATIVES = "familytree:relatives_request_v1";
    public static final String SNAPSHOT = "familytree:snapshot_pages_v5";
    public static final String RENAME_RESULT = "familytree:rename_result_v3";
    public static final String LINK_RESULT = "familytree:link_parents_result";
    public static final String WAYPOINT_RESULT = "familytree:waypoint_result_v1";
    public static final String RELATIVES_RESULT = "familytree:relatives_result_v1";
    public static final List<String> INCOMING = List.of(OPEN, DISCOVER, LOCATE, RENAME, LINK, WAYPOINT, RELATIVES);
    public static final List<String> OUTGOING = List.of(SNAPSHOT, RENAME_RESULT, LINK_RESULT, WAYPOINT_RESULT, RELATIVES_RESULT);
    static final int MAX_PAGE_RECORDS = 8192;
    private static final int WAYPOINT_TEXT = 256;
    private static final int RELATIVES_TEXT = 256;

    private Payloads() {}

    public record OpenRequest(UUID requestId, boolean requestAll) {}
    public record LocateRequest(UUID petId) {}
    public record RenameRequest(UUID requestId, UUID petId, String name) {}
    public record LinkRequest(UUID requestId, UUID child, UUID parentA, UUID parentB, boolean clear) {}
    public record WaypointRequest(UUID requestId, UUID petId) {}
    public record SnapshotPage(UUID requestId, int index, int pageCount, int totalRecords, List<AnimalRecord> records,
                               long currentWorldDay, long currentEpochMillis, boolean mayViewAll, boolean viewingAll, boolean mayManageAll) {}
    public record WaypointResult(UUID requestId, int status, String name, String dimension, long position) {
        public enum Status { FOUND, NOT_ALLOWED, NO_POSITION, BUSY }

        public static WaypointResult failed(UUID requestId, Status status) {
            return new WaypointResult(requestId, status.ordinal(), "", "", 0);
        }
    }
    public record RelativesRequest(UUID requestId, UUID target) {}
    /** {@code relation} is a {@code CloseRelation} ordinal. {@code total} counts every nearby relative before the cap. */
    public record RelativesResult(UUID requestId, UUID target, int status, String targetName, int total, List<Relative> relatives) {
        public static final int MAX_RELATIVES = 32;

        /** The network sends the ordinal, so the order matches the mod's. */
        public enum Status { FOUND, NOT_TRACKED, BUSY, UNAVAILABLE, NOT_ALLOWED }

        public record Relative(UUID id, int relation, String name) {}

        public static RelativesResult empty(UUID requestId, UUID target, Status status) {
            return new RelativesResult(requestId, target, status.ordinal(), "", 0, List.of());
        }
    }

    public static OpenRequest decodeOpen(byte[] bytes) {
        Wire.Reader in = new Wire.Reader(bytes);
        return new OpenRequest(in.uuid(), in.bool());
    }

    public static LocateRequest decodeLocate(byte[] bytes) {
        return new LocateRequest(new Wire.Reader(bytes).uuid());
    }

    public static RenameRequest decodeRename(byte[] bytes) {
        Wire.Reader in = new Wire.Reader(bytes);
        return new RenameRequest(in.uuid(), in.uuid(), in.string(PetRenaming.MAX_NAME_LENGTH));
    }

    public static LinkRequest decodeLink(byte[] bytes) {
        Wire.Reader in = new Wire.Reader(bytes);
        return new LinkRequest(in.uuid(), in.uuid(), in.uuid(), in.uuid(), in.bool());
    }

    public static WaypointRequest decodeWaypoint(byte[] bytes) {
        Wire.Reader in = new Wire.Reader(bytes);
        return new WaypointRequest(in.uuid(), in.uuid());
    }

    public static RelativesRequest decodeRelatives(byte[] bytes) {
        Wire.Reader in = new Wire.Reader(bytes);
        return new RelativesRequest(in.uuid(), in.uuid());
    }

    public static byte[] encodeOpen(OpenRequest request) {
        return new Wire.Writer().uuid(request.requestId()).bool(request.requestAll()).bytes();
    }

    public static byte[] encodeLocate(LocateRequest request) {
        return new Wire.Writer().uuid(request.petId()).bytes();
    }

    public static byte[] encodeRename(RenameRequest request) {
        return new Wire.Writer().uuid(request.requestId()).uuid(request.petId()).string(request.name(), PetRenaming.MAX_NAME_LENGTH).bytes();
    }

    public static byte[] encodeLink(LinkRequest request) {
        return new Wire.Writer().uuid(request.requestId()).uuid(request.child()).uuid(request.parentA())
                .uuid(request.parentB()).bool(request.clear()).bytes();
    }

    public static byte[] encodeWaypoint(WaypointRequest request) {
        return new Wire.Writer().uuid(request.requestId()).uuid(request.petId()).bytes();
    }

    public static byte[] encodeRelatives(RelativesRequest request) {
        return new Wire.Writer().uuid(request.requestId()).uuid(request.target()).bytes();
    }

    public static byte[] encodePage(SnapshotPage page) {
        if (page.records().size() > MAX_PAGE_RECORDS) throw new IllegalArgumentException("Too many records in one page");
        Wire.Writer out = new Wire.Writer().uuid(page.requestId()).varInt(page.index()).varInt(page.pageCount())
                .varInt(page.totalRecords()).varInt(page.records().size());
        for (AnimalRecord record : page.records()) record(out, record);
        return out.longValue(page.currentWorldDay()).longValue(page.currentEpochMillis())
                .bool(page.mayViewAll()).bool(page.viewingAll()).bool(page.mayManageAll()).bytes();
    }

    public static SnapshotPage decodePage(byte[] bytes) {
        Wire.Reader in = new Wire.Reader(bytes);
        UUID requestId = in.uuid();
        int index = in.varInt(), pageCount = in.varInt(), total = in.varInt(), size = in.varInt();
        if (size > MAX_PAGE_RECORDS) throw new IllegalArgumentException("Too many records in one page");
        List<AnimalRecord> records = new ArrayList<>(size);
        for (int i = 0; i < size; i++) records.add(record(in));
        return new SnapshotPage(requestId, index, pageCount, total, records, in.longValue(), in.longValue(), in.bool(), in.bool(), in.bool());
    }

    public static byte[] encodeRenameResult(UUID requestId, int status, AnimalRecord record) {
        Wire.Writer out = new Wire.Writer().uuid(requestId).varInt(status).bool(record != null);
        if (record != null) record(out, record);
        return out.bytes();
    }

    public static byte[] encodeLinkResult(UUID requestId, int status) {
        return new Wire.Writer().uuid(requestId).varInt(status).bytes();
    }

    public static byte[] encodeWaypointResult(WaypointResult result) {
        return new Wire.Writer().uuid(result.requestId()).varInt(result.status()).string(result.name(), WAYPOINT_TEXT)
                .string(result.dimension(), WAYPOINT_TEXT).longValue(result.position()).bytes();
    }

    public static byte[] encodeRelativesResult(RelativesResult result) {
        if (result.relatives().size() > RelativesResult.MAX_RELATIVES) throw new IllegalArgumentException("Too many relatives");
        Wire.Writer out = new Wire.Writer().uuid(result.requestId()).uuid(result.target()).varInt(result.status())
                .string(result.targetName(), RELATIVES_TEXT).varInt(result.total()).varInt(result.relatives().size());
        for (RelativesResult.Relative relative : result.relatives())
            out.uuid(relative.id()).varInt(relative.relation()).string(relative.name(), RELATIVES_TEXT);
        return out.bytes();
    }

    public static RelativesResult decodeRelativesResult(byte[] bytes) {
        Wire.Reader in = new Wire.Reader(bytes);
        UUID requestId = in.uuid(), target = in.uuid();
        int status = in.varInt();
        String name = in.string(RELATIVES_TEXT);
        int total = in.varInt(), size = in.varInt();
        if (size > RelativesResult.MAX_RELATIVES) throw new IllegalArgumentException("Too many relatives");
        List<RelativesResult.Relative> relatives = new ArrayList<>(size);
        for (int i = 0; i < size; i++) relatives.add(new RelativesResult.Relative(in.uuid(), in.varInt(), in.string(RELATIVES_TEXT)));
        return new RelativesResult(requestId, target, status, name, total, relatives);
    }

    /** Minecraft's {@code BlockPos.asLong}: 26 bits each for x and z, 12 for y. */
    public static long packBlockPos(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) | ((long) z & 0x3FFFFFFL) << 12;
    }

    static int recordSize(AnimalRecord record) {
        Wire.Writer out = new Wire.Writer();
        record(out, record);
        return out.size();
    }

    static void record(Wire.Writer out, AnimalRecord record) {
        out.uuid(record.id()).string(record.speciesId()).string(record.originalName()).bool(record.nameAutogenerated());
        optionalUuid(out, record.parentA());
        optionalUuid(out, record.parentB());
        out.longValue(record.birthWorldDay()).longValue(record.birthEpochMillis()).bool(record.deceased());
        optionalLong(out, record.deathWorldDay());
        optionalLong(out, record.deathEpochMillis());
        optionalUuid(out, record.ownerId());
        optionalString(out, record.ownerName());
        optionalString(out, record.variantId());
        AnimalRecord.LastSeen seen = record.lastSeen();
        out.bool(seen != null);
        if (seen != null) out.doubleValue(seen.x()).doubleValue(seen.y()).doubleValue(seen.z()).string(seen.dimension()).longValue(seen.worldDay());
        AnimalRecord.DeathCause cause = record.deathCause();
        out.bool(cause != null);
        if (cause != null) {
            optionalString(out, cause.attacker());
            optionalString(out, cause.msgId());
        }
        optionalString(out, record.treeName());
        HorseStats stats = record.horseStats();
        out.bool(stats != null);
        if (stats != null) {
            optionalDouble(out, stats.speed());
            optionalDouble(out, stats.jumpStrength());
            optionalDouble(out, stats.maxHealth());
            out.bool(stats.strength() != null);
            if (stats.strength() != null) out.varInt(stats.strength());
        }
        optionalString(out, record.variantOrigin());
    }

    static AnimalRecord record(Wire.Reader in) {
        UUID id = in.uuid();
        String species = in.string(), name = in.string();
        boolean nameAuto = in.bool();
        UUID parentA = in.bool() ? in.uuid() : null, parentB = in.bool() ? in.uuid() : null;
        long birthDay = in.longValue(), birthEpoch = in.longValue();
        boolean deceased = in.bool();
        Long deathDay = in.bool() ? in.longValue() : null, deathEpoch = in.bool() ? in.longValue() : null;
        UUID owner = in.bool() ? in.uuid() : null;
        String ownerName = in.bool() ? in.string() : null, variant = in.bool() ? in.string() : null;
        AnimalRecord.LastSeen seen = in.bool()
                ? new AnimalRecord.LastSeen(in.doubleValue(), in.doubleValue(), in.doubleValue(), in.string(), in.longValue()) : null;
        AnimalRecord.DeathCause cause = in.bool()
                ? new AnimalRecord.DeathCause(in.bool() ? in.string() : null, in.bool() ? in.string() : null) : null;
        AnimalRecord record = new AnimalRecord(id, species, name, nameAuto, parentA, parentB, birthDay, birthEpoch,
                deceased, deathDay, deathEpoch, owner, ownerName, variant, seen, cause);
        record.setTreeName(in.bool() ? in.string() : null);
        if (in.bool()) record.setHorseStats(new HorseStats(in.bool() ? in.doubleValue() : null, in.bool() ? in.doubleValue() : null,
                in.bool() ? in.doubleValue() : null, in.bool() ? in.varInt() : null));
        record.setVariantOrigin(in.bool() ? in.string() : null);
        return record;
    }

    private static void optionalUuid(Wire.Writer out, UUID value) {
        out.bool(value != null);
        if (value != null) out.uuid(value);
    }

    private static void optionalLong(Wire.Writer out, Long value) {
        out.bool(value != null);
        if (value != null) out.longValue(value);
    }

    private static void optionalDouble(Wire.Writer out, Double value) {
        out.bool(value != null);
        if (value != null) out.doubleValue(value);
    }

    private static void optionalString(Wire.Writer out, String value) {
        out.bool(value != null);
        if (value != null) out.string(value);
    }
}
