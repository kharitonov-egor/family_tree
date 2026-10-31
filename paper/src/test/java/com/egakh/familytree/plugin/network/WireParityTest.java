package com.egakh.familytree.plugin.network;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.HorseStats;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The plugin must send the bytes the mod's codecs send, or the client mod can't read them. The expected values are
 * the ones in the mod's {@code WireFormatTest}, which every mod target checks against Minecraft's own codecs.
 */
class WireParityTest {
    private static final String PAGE = "0000000000000007000000000000000800010202000000000000000100000000000000020e6d696e6563726166743a776f6c6605526f76657200010000000000000003000000000000000400000000000000000700000000000004d20101000000000000"
            + "000f00010000000000000005000000000000000601054f776e6572010e6d696e6563726166743a70616c65013ff80000000000004050000000000000c020000000000000136d696e6563726166743a6f766572776f726c64000000000000000e01010743"
            + "726565706572000108536e6f7762616c6c0001036e65770000000000000009000000000000000a0f6d696e6563726166743a686f72736506436c6f766572010000000000000000000300000000000000630000000000010e63686573746e75742f776869"
            + "746500000001013fd599999999999a013fe666666666666601403e000000000000000000000000000000050000000000000064010001";
    private static final Map<String, String> REQUESTS = Map.ofEntries(
            Map.entry("open", "0000000000000009000000000000000a01"),
            Map.entry("discover", "000000000000000b000000000000000c00"),
            Map.entry("locate", "000000000000000d000000000000000e"),
            Map.entry("rename", "000000000000000f000000000000001000000000000000110000000000000012084665726e20e78cab"),
            Map.entry("link", "0000000000000013000000000000001400000000000000150000000000000016000000000000001700000000000000180000000000000000000000000000000001"),
            Map.entry("linkResult", "0000000000000019000000000000001aac02"),
            Map.entry("renameResult", "000000000000001b000000000000001c0200"),
            Map.entry("waypoint", "000000000000001d000000000000001e000000000000001f0000000000000020"),
            Map.entry("waypointResult", "000000000000002100000000000000220005456d626572136d696e6563726166743a6f766572776f726c640102030405060708"),
            Map.entry("relatives", "0000000000000023000000000000002400000000000000250000000000000026"),
            Map.entry("relativesResult", "00000000000000270000000000000028000000000000002900000000000000" + "2a0006436c6f7665720302"
                    + "000000000000002b000000000000002c0105456d626572" + "000000000000002d000000000000002e03044d6f7373"));

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    @Test void snapshotPagesMatchTheMod() {
        AnimalRecord pet = new AnimalRecord(new UUID(1, 2), "minecraft:wolf", "Rover", false, new UUID(3, 4), null,
                7, 1234, true, 15L, null, new UUID(5, 6), "Owner", "minecraft:pale",
                new AnimalRecord.LastSeen(1.5, 64, -8, "minecraft:overworld", 14), new AnimalRecord.DeathCause("Creeper", null));
        pet.setTreeName("Snowball");
        pet.setVariantOrigin("new");
        AnimalRecord horse = new AnimalRecord(new UUID(9, 10), "minecraft:horse", "Clover", true, null, null,
                3, 99, false, null, null, null, null, "chestnut/white", null, null);
        horse.setHorseStats(new HorseStats(0.3375, 0.7, 30.0, null));
        var page = new Payloads.SnapshotPage(new UUID(7, 8), 0, 1, 2, List.of(pet, horse), 5, 100, true, false, true);
        byte[] bytes = Payloads.encodePage(page);
        assertEquals(PAGE, hex(bytes));
        assertEquals(PAGE, hex(Payloads.encodePage(Payloads.decodePage(bytes))));
    }

    @Test void requestsAndResultsMatchTheMod() {
        assertEquals(REQUESTS.get("open"), roundTrip(Payloads.encodeOpen(new Payloads.OpenRequest(new UUID(9, 10), true)),
                bytes -> Payloads.encodeOpen(Payloads.decodeOpen(bytes))));
        assertEquals(REQUESTS.get("discover"), roundTrip(Payloads.encodeOpen(new Payloads.OpenRequest(new UUID(11, 12), false)),
                bytes -> Payloads.encodeOpen(Payloads.decodeOpen(bytes))));
        assertEquals(REQUESTS.get("locate"), roundTrip(Payloads.encodeLocate(new Payloads.LocateRequest(new UUID(13, 14))),
                bytes -> Payloads.encodeLocate(Payloads.decodeLocate(bytes))));
        assertEquals(REQUESTS.get("rename"), roundTrip(Payloads.encodeRename(new Payloads.RenameRequest(new UUID(15, 16), new UUID(17, 18), "Fern 猫")),
                bytes -> Payloads.encodeRename(Payloads.decodeRename(bytes))));
        assertEquals(REQUESTS.get("link"), roundTrip(Payloads.encodeLink(new Payloads.LinkRequest(new UUID(19, 20), new UUID(21, 22),
                new UUID(23, 24), new UUID(0, 0), true)), bytes -> Payloads.encodeLink(Payloads.decodeLink(bytes))));
        assertEquals(REQUESTS.get("linkResult"), hex(Payloads.encodeLinkResult(new UUID(25, 26), 300)));
        assertEquals(REQUESTS.get("renameResult"), hex(Payloads.encodeRenameResult(new UUID(27, 28), 2, null)));
        assertEquals(REQUESTS.get("waypoint"), roundTrip(Payloads.encodeWaypoint(new Payloads.WaypointRequest(new UUID(29, 30), new UUID(31, 32))),
                bytes -> Payloads.encodeWaypoint(Payloads.decodeWaypoint(bytes))));
        assertEquals(REQUESTS.get("waypointResult"), hex(Payloads.encodeWaypointResult(
                new Payloads.WaypointResult(new UUID(33, 34), 0, "Ember", "minecraft:overworld", 0x0102030405060708L))));
        assertEquals(REQUESTS.get("relatives"), roundTrip(Payloads.encodeRelatives(new Payloads.RelativesRequest(new UUID(35, 36), new UUID(37, 38))),
                bytes -> Payloads.encodeRelatives(Payloads.decodeRelatives(bytes))));
        assertEquals(REQUESTS.get("relativesResult"), roundTrip(Payloads.encodeRelativesResult(new Payloads.RelativesResult(
                new UUID(39, 40), new UUID(41, 42), 0, "Clover", 3, List.of(
                        new Payloads.RelativesResult.Relative(new UUID(43, 44), 1, "Ember"),
                        new Payloads.RelativesResult.Relative(new UUID(45, 46), 3, "Moss")))),
                bytes -> Payloads.encodeRelativesResult(Payloads.decodeRelativesResult(bytes))));
    }

    @Test void relativesRepliesKeepTheModsCap() {
        List<Payloads.RelativesResult.Relative> tooMany = new java.util.ArrayList<>();
        for (int i = 0; i <= Payloads.RelativesResult.MAX_RELATIVES; i++) tooMany.add(new Payloads.RelativesResult.Relative(new UUID(0, i), 0, "Pup"));
        assertThrows(IllegalArgumentException.class, () -> Payloads.encodeRelativesResult(
                new Payloads.RelativesResult(new UUID(1, 1), new UUID(2, 2), 0, "Rex", tooMany.size(), tooMany)));
    }

    @Test void renameResultsCarryTheRecordLikeThePage() {
        AnimalRecord pet = new AnimalRecord(new UUID(1, 2), "minecraft:cat", "Tom", false, null, null,
                1, 2, false, null, null, null, null, "minecraft:tabby", null, null);
        String record = hex(Payloads.encodePage(new Payloads.SnapshotPage(new UUID(0, 0), 0, 1, 1, List.of(pet), 0, 0, false, false, false)));
        String result = hex(Payloads.encodeRenameResult(new UUID(3, 4), 0, pet));
        String encodedRecord = record.substring(32 + 8, record.length() - 32 - 6);
        assertEquals("0000000000000003000000000000000400" + "01" + encodedRecord, result);
    }

    /** Minecraft's BlockPos.asLong layout, checked against values worked out from its bit layout. */
    @Test void waypointPositionsUseTheBlockPosLayout() {
        assertEquals(0L, Payloads.packBlockPos(0, 0, 0));
        assertEquals(1L << 38 | 64L | 2L << 12, Payloads.packBlockPos(1, 64, 2));
        assertEquals(-1L, Payloads.packBlockPos(-1, -1, -1));
        long packed = Payloads.packBlockPos(-30_000_000, -64, 29_999_999);
        assertEquals(-30_000_000, (int) (packed >> 38));
        assertEquals(-64, (int) (packed << 52 >> 52));
        assertEquals(29_999_999, (int) (packed << 26 >> 38));
    }

    @Test void pagesStayInsideTheServersMessageLimit() {
        List<AnimalRecord> records = new java.util.ArrayList<>();
        for (int i = 0; i < 2000; i++) records.add(new AnimalRecord(new UUID(0, i), "minecraft:sheep", "Sheep " + i, true,
                null, null, 1, 2, false, null, null, new UUID(1, 1), "Alex", "white",
                new AnimalRecord.LastSeen(i, 64, -i, "minecraft:overworld", 3), null));
        var pages = SnapshotPages.split(new UUID(5, 5), records, 3, 4, false, false, false, 32766 - 64);
        assertTrue(pages.size() > 1);
        int total = 0;
        for (var page : pages) {
            assertTrue(Payloads.encodePage(page).length <= 32766, "page " + page.index());
            assertEquals(pages.size(), page.pageCount());
            assertEquals(records.size(), page.totalRecords());
            total += page.records().size();
        }
        assertEquals(records.size(), total);
    }

    /** Fails when the mod's expected bytes change, so the plugin is updated with them. */
    @Test void theModStillExpectsTheseBytes() throws IOException {
        Path test = Path.of(System.getProperty("familytree.repository"), "src/test/java/com/egakh/familytree/network/WireFormatTest.java");
        String source = Files.readString(test);
        String joined = source.replaceAll("\"\\s*\\+\\s*\"", "");
        assertTrue(joined.contains(PAGE), "WireFormatTest's snapshot bytes changed");
        for (Map.Entry<String, String> entry : REQUESTS.entrySet())
            assertTrue(joined.contains('"' + entry.getValue() + '"'), "WireFormatTest's " + entry.getKey() + " bytes changed");
    }

    private static String roundTrip(byte[] bytes, Function<byte[], byte[]> again) {
        assertArrayEquals(bytes, again.apply(bytes));
        return hex(bytes);
    }
}
