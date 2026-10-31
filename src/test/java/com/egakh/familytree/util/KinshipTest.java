package com.egakh.familytree.util;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.util.Kinship.Half;
import com.egakh.familytree.util.Kinship.Kind;
import com.egakh.familytree.util.Kinship.Relation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class KinshipTest {
    private final Map<UUID, AnimalRecord> records = new HashMap<>();

    private AnimalRecord horse(String name, AnimalRecord a, AnimalRecord b) {
        return horse(name, a == null ? null : a.id(), b == null ? null : b.id());
    }

    private AnimalRecord horse(String name, UUID a, UUID b) {
        AnimalRecord record = new AnimalRecord(UUID.randomUUID(), "minecraft:horse", name, false,
                a, b, 0, 0, false, null, null, null, null, null, null, null);
        records.put(record.id(), record);
        return record;
    }

    private Relation relation(AnimalRecord a, AnimalRecord b) {
        return new Kinship(records).relation(a.id(), b.id());
    }

    private void assertRelation(Kind kind, int up, int down, Half half, AnimalRecord a, AnimalRecord b) {
        Relation relation = relation(a, b);
        String label = a.name() + " to " + b.name();
        assertEquals(kind, relation.kind(), label);
        assertEquals(up, relation.up(), label + " up");
        assertEquals(down, relation.down(), label + " down");
        if (half != null) assertEquals(half, relation.half(), label + " half");
    }

    private String key(AnimalRecord a, AnimalRecord b) {
        Component text = KinshipText.describe(relation(a, b), a.name(), b.name());
        return ((TranslatableContents) text.getContents()).getKey();
    }

    private String cousinPhrase(AnimalRecord a, AnimalRecord b) {
        Component text = KinshipText.describe(relation(a, b), a.name(), b.name());
        Object phrase = ((TranslatableContents) text.getContents()).getArgs()[2];
        return describeKeys((Component) phrase);
    }

    private static String describeKeys(Component component) {
        TranslatableContents contents = (TranslatableContents) component.getContents();
        StringBuilder out = new StringBuilder(contents.getKey());
        for (Object arg : contents.getArgs()) if (arg instanceof Component inner && inner.getContents() instanceof TranslatableContents)
            out.append("(").append(describeKeys(inner)).append(")");
        return out.toString();
    }

    @Test
    void namesRelativesAcrossAGeneratedPedigree() {
        AnimalRecord g1 = horse("G1", (UUID) null, null), g2 = horse("G2", (UUID) null, null);
        AnimalRecord g3 = horse("G3", (UUID) null, null), g4 = horse("G4", (UUID) null, null);
        AnimalRecord outsider = horse("Outsider", (UUID) null, null), stranger = horse("Stranger", (UUID) null, null);
        AnimalRecord p1 = horse("P1", g1, g2), p2 = horse("P2", g1, g2), p3 = horse("P3", g1, g3), p4 = horse("P4", g4, null);
        AnimalRecord c1 = horse("C1", p1, outsider), c2 = horse("C2", p2, stranger), c3 = horse("C3", p3, p4);
        AnimalRecord c1b = horse("C1b", p1, outsider), c1h = horse("C1h", p1, stranger), c1u = horse("C1u", p1, null);
        AnimalRecord d1 = horse("D1", c1, null), d2 = horse("D2", c2, null), e1 = horse("E1", d1, null), f1 = horse("F1", e1, null);
        AnimalRecord lone = horse("Lone", (UUID) null, null);

        assertRelation(Kind.SAME, 0, 0, null, c1, c1);
        assertRelation(Kind.ANCESTOR, 0, 1, null, p1, c1);
        assertRelation(Kind.ANCESTOR, 0, 2, null, g1, c1);
        assertRelation(Kind.ANCESTOR, 0, 3, null, g1, d1);
        assertRelation(Kind.ANCESTOR, 0, 5, null, g1, f1);
        assertRelation(Kind.DESCENDANT, 1, 0, null, c1, p1);
        assertRelation(Kind.DESCENDANT, 4, 0, null, f1, p1);
        assertRelation(Kind.SIBLING, 1, 1, Half.FULL, p1, p2);
        assertRelation(Kind.SIBLING, 1, 1, Half.HALF, p1, p3);
        assertRelation(Kind.SIBLING, 1, 1, Half.FULL, c1, c1b);
        assertRelation(Kind.SIBLING, 1, 1, Half.HALF, c1, c1h);
        assertRelation(Kind.SIBLING, 1, 1, Half.UNKNOWN, c1, c1u);
        assertRelation(Kind.AUNT_OR_UNCLE, 1, 2, Half.FULL, p2, c1);
        assertRelation(Kind.AUNT_OR_UNCLE, 1, 2, Half.HALF, p3, c1);
        assertRelation(Kind.AUNT_OR_UNCLE, 1, 3, null, p2, d1);
        assertRelation(Kind.NIECE_OR_NEPHEW, 2, 1, Half.FULL, c1, p2);
        assertRelation(Kind.COUSIN, 2, 2, Half.FULL, c1, c2);
        assertRelation(Kind.COUSIN, 2, 2, Half.HALF, c1, c3);
        assertRelation(Kind.COUSIN, 3, 2, null, d1, c2);
        assertRelation(Kind.COUSIN, 3, 3, null, d1, d2);
        assertRelation(Kind.COUSIN, 4, 3, null, e1, d2);
        assertRelation(Kind.UNRELATED, -1, -1, null, c1, lone);
        assertRelation(Kind.UNRELATED, -1, -1, null, outsider, stranger);

        assertEquals("familytree.kin.parent", key(p1, c1));
        assertEquals("familytree.kin.grandparent", key(g1, c1));
        assertEquals("familytree.kin.great_grandparent", key(g1, d1));
        assertEquals("familytree.kin.ancestor", key(g1, f1));
        assertEquals("familytree.kin.child", key(c1, p1));
        assertEquals("familytree.kin.grandchild", key(d1, p1));
        assertEquals("familytree.kin.descendant", key(f1, p1));
        assertEquals("familytree.kin.siblings.full", key(p1, p2));
        assertEquals("familytree.kin.siblings.half", key(p1, p3));
        assertEquals("familytree.kin.siblings", key(c1, c1u));
        assertEquals("familytree.kin.aunt", key(p2, c1));
        assertEquals("familytree.kin.aunt.half", key(p3, c1));
        assertEquals("familytree.kin.great_aunt", key(p2, d1));
        assertEquals("familytree.kin.distant_aunt", key(p2, e1));
        assertEquals("familytree.kin.niece", key(c1, p2));
        assertEquals("familytree.kin.great_niece", key(d1, p2));
        assertEquals("familytree.kin.unrelated", key(c1, lone));
        assertEquals("familytree.kin.same", key(c1, c1));
        assertEquals("familytree.kin.cousin.1", cousinPhrase(c1, c2));
        assertEquals("familytree.kin.half(familytree.kin.cousin.1)", cousinPhrase(c1, c3));
        assertEquals("familytree.kin.removed.1(familytree.kin.cousin.1)", cousinPhrase(d1, c2));
        assertEquals("familytree.kin.cousin.2", cousinPhrase(d1, d2));
        assertEquals("familytree.kin.removed.1(familytree.kin.cousin.2)", cousinPhrase(e1, d2));
    }

    @Test
    void textbookInbreedingCoefficients() {
        AnimalRecord a = horse("A", (UUID) null, null), b = horse("B", (UUID) null, null), c = horse("C", (UUID) null, null);
        AnimalRecord d = horse("D", (UUID) null, null), e = horse("E", (UUID) null, null), f = horse("F", (UUID) null, null);
        AnimalRecord fullOne = horse("Full1", a, b), fullTwo = horse("Full2", a, b), halfOne = horse("Half", a, c);
        Kinship kinship = new Kinship(records);
        assertEquals(0.25, kinship.foalInbreeding(fullOne.id(), fullTwo.id()), 1e-12, "full siblings");
        assertEquals(0.125, kinship.foalInbreeding(fullOne.id(), halfOne.id()), 1e-12, "half siblings");
        assertEquals(0.25, kinship.foalInbreeding(a.id(), fullOne.id()), 1e-12, "parent and offspring");
        assertEquals(0, kinship.foalInbreeding(a.id(), b.id()), 0, "unrelated founders");
        assertEquals(0.5, kinship.coancestry(a.id(), a.id()), 0, "a founder with itself");

        AnimalRecord cousinOne = horse("Cousin1", fullOne, d), cousinTwo = horse("Cousin2", fullTwo, e);
        AnimalRecord niece = horse("Niece", fullTwo, f);
        kinship = new Kinship(records);
        assertEquals(1.0 / 16, kinship.foalInbreeding(cousinOne.id(), cousinTwo.id()), 1e-12, "first cousins");
        assertEquals(1.0 / 8, kinship.foalInbreeding(fullOne.id(), niece.id()), 1e-12, "aunt and nephew");
        AnimalRecord halfCousin = horse("HalfCousin", halfOne, f);
        kinship = new Kinship(records);
        assertEquals(1.0 / 32, kinship.foalInbreeding(cousinOne.id(), halfCousin.id()), 1e-12, "half first cousins");

        AnimalRecord x1 = horse("X1", a, b), x2 = horse("X2", a, b), y1 = horse("Y1", c, d), y2 = horse("Y2", c, d);
        AnimalRecord doubleOne = horse("Double1", x1, y1), doubleTwo = horse("Double2", x2, y2);
        kinship = new Kinship(records);
        assertEquals(1.0 / 8, kinship.foalInbreeding(doubleOne.id(), doubleTwo.id()), 1e-12, "double first cousins");

        // Half siblings whose shared parent is itself the foal of full siblings: (1/8) * (1 + 1/4).
        AnimalRecord inbred = horse("Inbred", fullOne, fullTwo);
        AnimalRecord h1 = horse("H1", inbred, e), h2 = horse("H2", inbred, f);
        kinship = new Kinship(records);
        assertEquals(0.25, kinship.inbreeding(inbred.id()), 1e-12);
        assertEquals(0.125 * 1.25, kinship.foalInbreeding(h1.id(), h2.id()), 1e-12, "inbred common ancestor");
    }

    @Test
    void repeatedFullSiblingMatingFollowsTheKnownSeries() {
        AnimalRecord first = horse("S0a", (UUID) null, null), second = horse("S0b", (UUID) null, null);
        double[] expected = {0, 0.25, 0.375, 0.5, 0.59375, 0.671875};
        for (int generation = 1; generation < expected.length; generation++) {
            AnimalRecord a = horse("S" + generation + "a", first, second), b = horse("S" + generation + "b", first, second);
            assertEquals(expected[generation - 1], new Kinship(records).inbreeding(a.id()), 1e-12, "generation " + generation);
            first = a;
            second = b;
        }
        assertEquals(expected[expected.length - 1], new Kinship(records).foalInbreeding(first.id(), second.id()), 1e-12);
    }

    @Test
    void sharedParentWithoutARecordStillLinksSiblings() {
        UUID missing = UUID.randomUUID();
        AnimalRecord otherA = horse("OtherA", (UUID) null, null), otherB = horse("OtherB", (UUID) null, null);
        AnimalRecord a = horse("A", missing, otherA.id()), b = horse("B", missing, otherB.id());
        assertRelation(Kind.SIBLING, 1, 1, Half.HALF, a, b);
        assertEquals(0.125, new Kinship(records).foalInbreeding(a.id(), b.id()), 1e-12);
    }

    @Test
    void pathRunsThroughTheNearestSharedAncestors() {
        AnimalRecord g1 = horse("G1", (UUID) null, null), g2 = horse("G2", (UUID) null, null);
        AnimalRecord p1 = horse("P1", g1, g2), p2 = horse("P2", g1, g2);
        AnimalRecord other = horse("Other", (UUID) null, null);
        AnimalRecord c1 = horse("C1", p1, other), c2 = horse("C2", p2, null);
        Set<UUID> path = new Kinship(records).path(c1.id(), c2.id());
        assertEquals(Set.of(c1.id(), c2.id(), p1.id(), p2.id(), g1.id(), g2.id()), path);
        assertEquals(Set.of(p1.id(), c1.id()), new Kinship(records).path(p1.id(), c1.id()));
        assertTrue(new Kinship(records).path(c1.id(), horse("Lone", (UUID) null, null).id()).isEmpty());
    }

    @Test
    void damagedCyclesFinishWithoutLooping() {
        AnimalRecord a = horse("A", (UUID) null, null), b = horse("B", a, null);
        a.setParents(b.id(), null);
        AnimalRecord c = horse("C", a, b);
        Kinship kinship = new Kinship(records);
        assertTrue(Double.isFinite(kinship.foalInbreeding(a.id(), c.id())));
        assertNotNull(kinship.relation(a.id(), c.id()));
        assertNotNull(kinship.path(c.id(), b.id()));
    }

    @Test
    void deepLinesUseNoJavaRecursion() {
        AnimalRecord founder = horse("F", (UUID) null, null), line = founder;
        for (int i = 0; i < 5000; i++) line = horse("L" + i, line, null);
        Kinship kinship = new Kinship(records);
        assertEquals(Kind.ANCESTOR, kinship.relation(founder.id(), line.id()).kind());
        assertEquals(5000, kinship.relation(founder.id(), line.id()).down());
        assertTrue(kinship.coancestry(founder.id(), line.id()) >= 0);
        assertEquals(0.25, kinship.coancestry(line.id(), records.get(line.parentA()).id()), 1e-12);
        assertEquals(List.of(founder.id()), kinship.relation(founder.id(), line.id()).commonAncestors());
    }

    @Test
    void percentagesKeepThreeSignificantDigits() {
        assertEquals("12.5%", KinshipText.percent(0.125));
        assertEquals("25%", KinshipText.percent(0.25));
        assertEquals("6.25%", KinshipText.percent(0.0625));
        assertEquals("3.13%", KinshipText.percent(1.0 / 32));
        assertEquals("0.0833%", KinshipText.percent(1.0 / 1200));
        assertEquals("0%", KinshipText.percent(0));
        assertEquals("100%", KinshipText.percent(1));
        assertEquals("99.9%", KinshipText.percent(0.99999));
    }
}
