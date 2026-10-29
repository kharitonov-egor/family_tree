package com.egakh.familytree.client.fabric;

import com.egakh.familytree.client.screen.FamilyTreeSettingsScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu loads this entry point only when it is installed, and opens the settings from its Configure button. */
public final class ModMenuIntegration implements ModMenuApi {
    @Override public ConfigScreenFactory<FamilyTreeSettingsScreen> getModConfigScreenFactory() {
        return FamilyTreeSettingsScreen::new;
    }
}
