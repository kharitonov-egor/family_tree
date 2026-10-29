package com.egakh.familytree.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LangTablesTest {
    @Test
    void picksTheClosestShippedLanguage() {
        assertEquals("de_de", LangTables.resolve("de_de"));
        assertEquals("de_de", LangTables.resolve("DE_AT"));
        assertEquals("pt_br", LangTables.resolve("pt_pt"));
        assertEquals("es_mx", LangTables.resolve("es_ar"));
        assertEquals("es_es", LangTables.resolve("es_es"));
        assertEquals("zh_tw", LangTables.resolve("zh_hk"));
        assertEquals("zh_cn", LangTables.resolve("zh_sg"));
        assertEquals("en_us", LangTables.resolve("en_gb"));
        assertEquals("en_us", LangTables.resolve("lol_us"));
        assertEquals("en_us", LangTables.resolve("../../en_us"));
        assertEquals("en_us", LangTables.resolve(null));
    }

    @Test
    void missingTranslationsFallBackToEnglish() {
        assertEquals("Family Tree", LangTables.template("en_us", "familytree.menu.title"));
        assertNotNull(LangTables.template("xx_xx", "familytree.menu.close"));
        assertNull(LangTables.template("de_de", "familytree.no_such_key"));
        assertNotEquals(LangTables.template("en_us", "familytree.menu.close"), LangTables.template("de_de", "familytree.menu.close"));
    }

    @Test
    void serverTextFormatsResolvedTemplates() {
        assertEquals("Page 2 of 5", ServerText.plain("en_us", "familytree.menu.page", 2, 5));
        assertEquals("familytree.no_such_key", ServerText.plain("en_us", "familytree.no_such_key"));
    }
}
