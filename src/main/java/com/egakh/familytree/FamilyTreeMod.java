package com.egakh.familytree;

import com.egakh.familytree.command.FamilyTreeCommand;
import com.egakh.familytree.event.PetLifecycleListeners;
import com.egakh.familytree.network.FamilyTreePackets;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FamilyTreeMod implements ModInitializer {

    public static final String MOD_ID = "familytree";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        FamilyTreePackets.registerCommon();
        FamilyTreePackets.registerServer();
        PetLifecycleListeners.register();

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> FamilyTreeCommand.register(dispatcher));

        LOGGER.info("Family Tree mod initialized");
    }
}
