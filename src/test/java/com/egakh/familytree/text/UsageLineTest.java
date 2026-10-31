package com.egakh.familytree.text;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

class UsageLineTest {
    private static final Set<String> SUBCOMMANDS = Set.of("menu", "list", "scan", "locate", "pair", "unpair", "breed",
            "setage", "setbirth", "prune", "info");

    @Test
    void dropsTheChoicesAPlayerCannotRun() {
        String usage = LangTables.template(LangTables.DEFAULT, "familytree.command.usage");
        assertEquals("/familytree <list|scan|locate <name>|breed <a> <b>|info [name]>",
                UsageLine.filter(usage, sub -> !Set.of("menu", "pair", "unpair", "setage", "setbirth", "prune").contains(sub)));
        assertEquals(usage, UsageLine.filter(usage, sub -> true));
    }

    @Test
    void everyLanguageListsEachSubcommandOnceUnderItsEnglishName() {
        for (String language : LangTables.SHIPPED) {
            String usage = LangTables.template(language, "familytree.command.usage");
            int open = usage.indexOf('<');
            List<String> choices = UsageLine.choices(usage.substring(open + 1, usage.lastIndexOf('>')));
            Set<String> names = new TreeSet<>();
            for (String choice : choices) {
                assertNotNull(choice, language + ": brackets don't balance");
                names.add(choice.split(" ", 2)[0]);
            }
            assertEquals(new TreeSet<>(SUBCOMMANDS), names, language);
            assertEquals(SUBCOMMANDS.size(), choices.size(), language);
            assertFalse(UsageLine.filter(usage, sub -> !sub.equals("prune")).contains("prune"), language);
        }
    }

    @Test
    void anUnexpectedLineStaysWhole() {
        assertEquals("/familytree", UsageLine.filter("/familytree", sub -> false));
        assertEquals("/familytree <a|b <c>", UsageLine.filter("/familytree <a|b <c>", sub -> false));
    }
}
