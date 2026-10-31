package com.egakh.familytree.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PruneRequestTest {
    @Test void confirmIsReadFromTheEndOfTheGreedySpeciesArgument() {
        assertEquals(new PruneRequest("minecraft:cow", false), PruneRequest.species(" minecraft:cow "));
        assertEquals(new PruneRequest("minecraft:cow", true), PruneRequest.species("minecraft:cow confirm"));
        assertEquals(new PruneRequest("minecraft:cow", true), PruneRequest.species("minecraft:cow  CONFIRM"));
        assertEquals(new PruneRequest("minecraft:confirm", false), PruneRequest.species("minecraft:confirm"));
    }
}
