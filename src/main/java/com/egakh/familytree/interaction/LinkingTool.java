package com.egakh.familytree.interaction;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.FamilyTreeState;
import com.egakh.familytree.event.PetLifecycleListeners;
import com.egakh.familytree.util.PetFilter;
import com.egakh.familytree.util.Genealogy;
import com.egakh.familytree.util.PetRelations;
import com.egakh.familytree.permissions.ServerPetAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class LinkingTool {

    private static final String TOOL_NAME = "familytree";
    private static final long EXPIRE_MILLIS = 2 * 60 * 1000L;
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private LinkingTool() {}

    public static InteractionResult onUseEntity(Player player, Level world, InteractionHand hand,
                                                 Entity entity, EntityHitResult hit) {
        if (player.isSpectator()) return InteractionResult.PASS;
        if (!isLinkingTool(player.getItemInHand(hand))) return InteractionResult.PASS;
        if (!PetFilter.isTameableSpecies(entity)) return InteractionResult.PASS;
        if (world.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || !(world instanceof ServerLevel level)) {
            return InteractionResult.PASS;
        }

        if (!PetFilter.isTrackable(entity)) {
            sp.sendSystemMessage(Component.translatable("familytree.tool.not_tamed"));
            return InteractionResult.SUCCESS_SERVER;
        }

        FamilyTreeState state = FamilyTreeState.get(level);
        if (state.readOnly()) return InteractionResult.SUCCESS_SERVER;
        PetLifecycleListeners.ensureRecord(level, state, entity);
        AnimalRecord record = state.get(entity.getUUID());
        if (record == null) {
            sp.sendSystemMessage(Component.translatable("familytree.tool.not_tamed"));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!ServerPetAccess.canManage(sp.createCommandSourceStack(), record)) {
            sp.sendSystemMessage(Component.translatable("familytree.access.denied"));
            return InteractionResult.SUCCESS_SERVER;
        }

        if (entity instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, false, false));
        }

        if (sp.isShiftKeyDown()) {
            startUnlink(sp, record);
        } else {
            advanceSelection(sp, state, record);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static boolean isLinkingTool(ItemStack stack) {
        if (!stack.is(Items.STICK)) return false;
        Component customName = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        return customName != null && customName.getString().trim().equalsIgnoreCase(TOOL_NAME);
    }

    private static void advanceSelection(ServerPlayer player, FamilyTreeState state, AnimalRecord record) {
        Session session = SESSIONS.get(player.getUUID());
        long now = System.currentTimeMillis();
        if (session == null || session.expired(now) || session.pending != Mode.NONE) {
            if (session != null && !session.expired(now)) {
                player.sendSystemMessage(Component.translatable("familytree.tool.restarted")
                        .withStyle(ChatFormatting.GRAY));
            }
            session = new Session();
            SESSIONS.put(player.getUUID(), session);
        }
        session.lastTouchedMillis = now;

        if (session.pets.contains(record.id())) {
            player.sendSystemMessage(Component.translatable("familytree.tool.already_selected", record.name()));
            return;
        }
        if (session.pets.size() == 1) {
            AnimalRecord first = state.get(session.pets.getFirst());
            if (first != null && !first.speciesId().equals(record.speciesId())
                    && !java.util.Set.of(first.speciesId(), record.speciesId()).equals(java.util.Set.of("minecraft:horse", "minecraft:donkey"))) {
                player.sendSystemMessage(Component.translatable("familytree.command.pair.species_mismatch"));
                return;
            }
        }

        if (session.pets.size() == 2 && Genealogy.wouldCreateCycle(record.id(),
                session.pets.get(0), session.pets.get(1), state::get)) {
            player.sendSystemMessage(Component.translatable("familytree.command.pair.cycle"));
            return;
        }
        if (session.pets.size() == 2 && !PetRelations.speciesMatch(state.get(session.pets.get(0)),
                state.get(session.pets.get(1)), record)) {
            player.sendSystemMessage(Component.translatable("familytree.command.pair.species_mismatch"));
            return;
        }

        session.pets.add(record.id());
        String key = switch (session.pets.size()) {
            case 1 -> "familytree.tool.selected_parent1";
            case 2 -> "familytree.tool.selected_parent2";
            default -> "familytree.tool.selected_child";
        };
        player.sendSystemMessage(Component.translatable(key, record.name()));
        sendKnownRelations(player, state, record);

        if (session.pets.size() == 3) {
            session.pending = Mode.LINK;
            AnimalRecord p1 = state.get(session.pets.get(0));
            AnimalRecord p2 = state.get(session.pets.get(1));
            sendConfirmPrompt(player, Component.translatable("familytree.tool.confirm_link",
                    p1 != null ? p1.name() : "?", p2 != null ? p2.name() : "?", record.name()));
        }
    }

    private static void startUnlink(ServerPlayer player, AnimalRecord record) {
        if (record.parentA() == null && record.parentB() == null) {
            player.sendSystemMessage(Component.translatable("familytree.tool.no_parents", record.name()));
            SESSIONS.remove(player.getUUID());
            return;
        }
        Session session = new Session();
        session.pending = Mode.UNLINK;
        session.unlinkTarget = record.id();
        SESSIONS.put(player.getUUID(), session);
        sendConfirmPrompt(player, Component.translatable("familytree.tool.confirm_unlink", record.name()));
    }

    private static void sendKnownRelations(ServerPlayer player, FamilyTreeState state, AnimalRecord record) {
        AnimalRecord pa = record.parentA() != null ? state.get(record.parentA()) : null;
        AnimalRecord pb = record.parentB() != null ? state.get(record.parentB()) : null;
        if (!ServerPetAccess.canView(player.createCommandSourceStack(), pa)) pa = null;
        if (!ServerPetAccess.canView(player.createCommandSourceStack(), pb)) pb = null;
        MutableComponent parents = pa != null || pb != null
                ? Component.translatable("familytree.tool.known_parents",
                        pa != null ? pa.name() : "?", pb != null ? pb.name() : "?")
                : Component.translatable("familytree.tool.known_parents_none");
        player.sendSystemMessage(parents.withStyle(ChatFormatting.GRAY));

        List<UUID> childIds = state.buildChildIndex().get(record.id());
        MutableComponent children;
        if (childIds == null || childIds.isEmpty()) {
            children = Component.translatable("familytree.tool.known_children_none");
        } else {
            String names = childIds.stream()
                    .map(state::get)
                    .filter(r -> ServerPetAccess.canView(player.createCommandSourceStack(), r))
                    .map(AnimalRecord::name)
                    .collect(Collectors.joining(", "));
            children = Component.translatable("familytree.tool.known_children", names);
        }
        player.sendSystemMessage(children.withStyle(ChatFormatting.GRAY));
    }

    private static void sendConfirmPrompt(ServerPlayer player, Component question) {
        Component confirm = Component.translatable("familytree.tool.confirm_button").withStyle(s -> s
                .withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand("/familytree confirmlink")));
        Component cancel = Component.translatable("familytree.tool.cancel_button").withStyle(s -> s
                .withColor(ChatFormatting.RED).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand("/familytree cancellink")));
        player.sendSystemMessage(question);
        player.sendSystemMessage(Component.empty().append(confirm).append(" ").append(cancel));
    }

    public static int confirm(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        Session session = player != null ? SESSIONS.remove(player.getUUID()) : null;
        if (session == null || session.expired(System.currentTimeMillis()) || session.pending == Mode.NONE) {
            source.sendFailure(Component.translatable("familytree.tool.nothing_pending"));
            return 0;
        }

        FamilyTreeState state = FamilyTreeState.get(source.getServer());
        if (state.readOnly()) return 0;
        if (session.pending == Mode.UNLINK) {
            AnimalRecord target = state.get(session.unlinkTarget);
            if (!ServerPetAccess.canManage(source, target)) {
                source.sendFailure(Component.translatable("familytree.tool.nothing_pending"));
                return 0;
            }
            state.update(target.id(), r -> r.setParents(null, null));
            source.sendSuccess(() -> Component.translatable("familytree.command.unpair.result", target.name()), false);
            return 1;
        }

        AnimalRecord parentA = state.get(session.pets.get(0));
        AnimalRecord parentB = state.get(session.pets.get(1));
        AnimalRecord child = state.get(session.pets.get(2));
        if (parentA == null || parentB == null || child == null) {
            source.sendFailure(Component.translatable("familytree.tool.nothing_pending"));
            return 0;
        }
        if (!ServerPetAccess.canManage(source, parentA) || !ServerPetAccess.canManage(source, parentB)
                || !ServerPetAccess.canManage(source, child)) {
            source.sendFailure(Component.translatable("familytree.access.denied"));
            return 0;
        }
        if (parentA.id().equals(parentB.id())) {
            source.sendFailure(Component.translatable("familytree.command.pair.same_parent"));
            return 0;
        }
        if (child.id().equals(parentA.id()) || child.id().equals(parentB.id())) {
            source.sendFailure(Component.translatable("familytree.command.pair.child_matches_parent"));
            return 0;
        }
        if (!PetRelations.speciesMatch(parentA, parentB, child)) {
            source.sendFailure(Component.translatable("familytree.command.pair.species_mismatch"));
            return 0;
        }

        // Recheck after the confirmation delay, since another player may have edited the tree.
        if (Genealogy.wouldCreateCycle(child.id(), parentA.id(), parentB.id(), state::get)) {
            source.sendFailure(Component.translatable("familytree.command.pair.cycle"));
            return 0;
        }

        state.update(child.id(), r -> r.setParents(parentA.id(), parentB.id()));
        source.sendSuccess(() -> Component.translatable("familytree.command.pair.result",
                parentA.name(), parentB.name(), child.name()), false);
        return 1;
    }

    public static int cancel(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        Session session = player != null ? SESSIONS.remove(player.getUUID()) : null;
        if (session == null) {
            source.sendFailure(Component.translatable("familytree.tool.nothing_pending"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("familytree.tool.cancelled"), false);
        return 1;
    }

    private enum Mode { NONE, LINK, UNLINK }
    public static void disconnect(UUID player) { SESSIONS.remove(player); }
    public static void stop() { SESSIONS.clear(); }

    private static final class Session {
        final List<UUID> pets = new ArrayList<>();
        Mode pending = Mode.NONE;
        UUID unlinkTarget;
        long lastTouchedMillis = System.currentTimeMillis();

        boolean expired(long now) {
            return now - lastTouchedMillis > EXPIRE_MILLIS;
        }
    }
}
