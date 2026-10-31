package com.egakh.familytree.plugin.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArgsTest {
    @Test void readsWordsQuotedNamesAndTheRest() {
        Args args = new Args(new String[]{"pair", "\"Big", "Rex\"", "Luna", "猫"});
        assertEquals("pair", args.next());
        assertEquals("Big Rex", args.next());
        assertEquals("Luna", args.next());
        assertEquals("猫", args.next());
        assertTrue(args.done());
        Args greedy = new Args(new String[]{"locate", "Big", "Rex"});
        greedy.next();
        assertEquals("Big Rex", greedy.rest());
        assertEquals("\"a \\\"b\\\"\"", Args.quoteIfNeeded("a \"b\""));
        assertEquals("Rex", Args.quoteIfNeeded("Rex"));
        Args escaped = new Args(new String[]{"\"a", "\\\"b\\\"\""});
        assertEquals("a \"b\"", escaped.next());
    }
}
