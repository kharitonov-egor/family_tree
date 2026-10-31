package com.egakh.familytree.forge;

import com.egakh.familytree.FamilyTreeMod;
import com.egakh.familytree.command.FamilyTreeCommand;
import com.egakh.familytree.event.PetLifecycleListeners;
import com.egakh.familytree.interaction.LinkingTool;
import com.egakh.familytree.network.FamilyTreePackets;
import com.egakh.familytree.permissions.ServerPetAccess;
import com.egakh.familytree.platform.FamilyTreePaths;
import com.egakh.familytree.settings.FamilyTreeServerSettings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod(FamilyTreeMod.MOD_ID)
public final class ForgeEntrypoint {
    public ForgeEntrypoint() {
        FamilyTreePaths.configure(FMLPaths.CONFIGDIR.get());
        ForgeNetwork.register();
        IEventBus events = MinecraftForge.EVENT_BUS;
        ForgePermissions permissions = new ForgePermissions();
        events.addListener(permissions::register);
        events.addListener(EventPriority.LOWEST, permissions::loggedIn);
        events.addListener(permissions::loggedOut);
        ServerPetAccess.configure(permissions);
        events.addListener((RegisterCommandsEvent event) -> FamilyTreeCommand.register(event.getDispatcher()));
        events.addListener((ServerStartingEvent event) -> FamilyTreeServerSettings.load());
        events.addListener(EventPriority.LOWEST, (EntityJoinLevelEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level) PetLifecycleListeners.onLoad(event.getEntity(), level);
        });
        events.addListener((EntityLeaveLevelEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level) PetLifecycleListeners.onUnload(event.getEntity(), level);
        });
        events.addListener(EventPriority.LOWEST, (LivingDeathEvent event) ->
                PetLifecycleListeners.onDeath(event.getEntity(), event.getSource()));
        events.addListener((PlayerInteractEvent.EntityInteract event) -> {
            InteractionResult result = LinkingTool.onUseEntity(event.getEntity(), event.getLevel(),
                    event.getHand(), event.getTarget(), null);
            if (result != InteractionResult.PASS) {
                event.setCancellationResult(result);
                event.setCanceled(true);
            }
        });
        events.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) com.egakh.familytree.data.SaveNotice.joined(player);
        });
        events.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            FamilyTreePackets.disconnect(event.getEntity().getUUID());
            LinkingTool.disconnect(event.getEntity().getUUID());
        });
        events.addListener((ServerStoppedEvent event) -> { FamilyTreePackets.stop(); LinkingTool.stop(); });
        if (FMLEnvironment.dist == Dist.CLIENT) ForgeClient.initialize(FMLJavaModLoadingContext.get().getModEventBus());
        FamilyTreeMod.LOGGER.info("Family Tree initialized on Forge");
    }
}
