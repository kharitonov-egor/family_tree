package com.egakh.familytree.permissions;

import com.egakh.familytree.platform.PermissionBackend;
import com.egakh.familytree.settings.ServerSettings;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import static com.egakh.familytree.permissions.FamilyTreePermission.*;
import static org.junit.jupiter.api.Assertions.*;

class FamilyTreePermissionTest {
    private static ServerSettings settings(String text) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(text));
        return ServerSettings.parse(properties).settings();
    }

    /** Without a permission mod, players get exactly what Family Tree 1.3.0 gave them by op level. */
    @Test void defaultsMatchTheOperatorLevelsOf130() {
        Set<FamilyTreePermission> everyone = EnumSet.of(USE, MENU, SCAN, LOCATE, RENAME, PARENTS, AGE, LINKING_STICK, BREED);
        for (FamilyTreePermission permission : values()) {
            assertTrue(permission.fallback(ServerSettings.DEFAULT, true), permission + " must allow operators");
            assertEquals(everyone.contains(permission), permission.fallback(ServerSettings.DEFAULT, false), permission.node());
        }
    }

    @Test void viewPolicySetsTheDefaultForViewingOthersPets() throws IOException {
        ServerSettings everyone = settings("viewPolicy=EVERYONE_ALL"), own = settings("viewPolicy=OWN_ONLY");
        assertTrue(VIEW_OTHERS.fallback(everyone, false));
        assertFalse(VIEW_OTHERS.fallback(own, true), "OWN_ONLY hides other players' pets from operators too");
        assertFalse(MANAGE_OTHERS.fallback(everyone, false), "Browsing never grants editing");
        assertTrue(MANAGE_OTHERS.fallback(own, true));
    }

    @Test void ownerActionSettingsLeaveActionsToOperators() throws IOException {
        ServerSettings limited = settings("ownersCanLocate=false\nownersCanRename=false\nownersCanEditParents=false\nownersCanSetAge=false");
        for (FamilyTreePermission permission : EnumSet.of(LOCATE, RENAME, PARENTS, AGE)) {
            assertFalse(permission.fallback(limited, false), permission.node());
            assertTrue(permission.fallback(limited, true), permission.node());
        }
        assertTrue(USE.fallback(limited, false));
        assertTrue(LINKING_STICK.fallback(limited, false));
    }

    @Test void nodesAreUniqueLowercaseAndPrefixed() {
        Set<String> nodes = new HashSet<>();
        for (FamilyTreePermission permission : values()) {
            assertTrue(nodes.add(permission.node()), permission.node());
            assertEquals("familytree." + permission.path(), permission.node());
            assertTrue(permission.path().matches("[a-z0-9_.]+"), permission.path());
        }
    }

    @Test void withoutAPermissionModTheBackendReturnsTheDefault() {
        assertTrue(PermissionBackend.NONE.check(null, null, PRUNE, true));
        assertFalse(PermissionBackend.NONE.check(null, null, USE, false));
    }
}
