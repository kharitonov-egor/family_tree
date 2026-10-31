package com.egakh.familytree.plugin.track;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.permissions.FamilyTreePermission;
import com.egakh.familytree.plugin.Chat;
import com.egakh.familytree.plugin.FamilyTreePlugin;
import com.egakh.familytree.plugin.storage.FamilyTreeStore;
import com.egakh.familytree.plugin.text.Text;
import com.egakh.familytree.util.Genealogy;
import com.egakh.familytree.util.PetRelations;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** The mod's linking stick: a stick named {@code linkingStickName} links parents and adds animals other than pets. */
public final class LinkingStick implements Listener {
    private final FamilyTreePlugin plugin;
    private final Tracker tracker;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public LinkingStick(FamilyTreePlugin plugin, Tracker tracker) {
        this.plugin = plugin;
        this.tracker = tracker;
    }

    private Chat chat() {
        return plugin.chat();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUseEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        ItemStack held = event.getHand() == EquipmentSlot.OFF_HAND ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (!isLinkingTool(held) || !(event.getRightClicked() instanceof Animals entity)) return;
        event.setCancelled(true);
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        FamilyTreeStore state = plugin.store();
        if (state.readOnly()) return;
        if (!plugin.access().allowed(player, FamilyTreePermission.LINKING_STICK)) {
            chat().send(player, "familytree.permission.denied", FamilyTreePermission.LINKING_STICK.node());
            return;
        }
        PetRules rules = tracker.rules();
        if (!rules.isTrackable(entity) && !state.contains(entity.getUniqueId())) {
            if (PetRules.isTameableSpecies(entity)) {
                chat().send(player, PetRules.isUnclaimedMount(entity) ? "familytree.tool.ride_first" : "familytree.tool.not_tamed");
                return;
            }
            Tracker.AddResult added = tracker.addAnimal(state, entity, player.getUniqueId(), player.getName());
            if (added != Tracker.AddResult.ADDED) {
                if (added == Tracker.AddResult.FULL) chat().send(player, "familytree.tool.farm_full", plugin.settings().tracking().farmRecordLimit());
                else chat().send(player, "familytree.tool.not_recorded", Text.vanilla(entityKey(entity)));
                return;
            }
            chat().send(player, "familytree.tool.added", state.get(entity.getUniqueId()).name());
        }
        tracker.ensureRecord(state, entity);
        AnimalRecord record = state.get(entity.getUniqueId());
        if (record == null) {
            chat().send(player, "familytree.tool.not_tamed");
            return;
        }
        if (!plugin.access().canManage(player, record)) {
            chat().send(player, "familytree.access.denied");
            return;
        }
        if (!plugin.access().allowed(player, FamilyTreePermission.PARENTS)) {
            chat().send(player, "familytree.permission.denied", FamilyTreePermission.PARENTS.node());
            return;
        }
        entity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, false));
        if (player.isSneaking()) startUnlink(player, record);
        else advanceSelection(player, state, record);
    }

    private static String entityKey(Entity entity) {
        String species = Entities.species(entity);
        int colon = species.indexOf(':');
        return "entity." + species.substring(0, colon) + "." + species.substring(colon + 1);
    }

    private boolean isLinkingTool(ItemStack stack) {
        if (!plugin.settings().linkingStick() || stack == null || stack.getType() != Material.STICK) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        String name = ChatColor.stripColor(meta.getDisplayName());
        return name != null && name.trim().equalsIgnoreCase(plugin.settings().linkingStickName().trim());
    }

    private void advanceSelection(Player player, FamilyTreeStore state, AnimalRecord record) {
        Session session = sessions.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (session == null || expired(session, now) || session.pending != Mode.NONE) {
            if (session != null && !expired(session, now))
                chat().send(player, chat().tr(player, "familytree.tool.restarted").color(Text.Color.GRAY));
            session = new Session();
            sessions.put(player.getUniqueId(), session);
        }
        session.lastTouchedMillis = now;
        if (session.pets.contains(record.id())) {
            chat().send(player, "familytree.tool.already_selected", record.name());
            return;
        }
        if (session.pets.size() == 1) {
            AnimalRecord first = state.get(session.pets.get(0));
            if (first != null && !first.speciesId().equals(record.speciesId())
                    && !Set.of(first.speciesId(), record.speciesId()).equals(Set.of("minecraft:horse", "minecraft:donkey"))) {
                chat().send(player, "familytree.command.pair.species_mismatch");
                return;
            }
        }
        if (session.pets.size() == 2 && Genealogy.wouldCreateCycle(record.id(), session.pets.get(0), session.pets.get(1), state::get)) {
            chat().send(player, "familytree.command.pair.cycle");
            return;
        }
        if (session.pets.size() == 2 && !PetRelations.speciesMatch(state.get(session.pets.get(0)), state.get(session.pets.get(1)), record)) {
            chat().send(player, "familytree.command.pair.species_mismatch");
            return;
        }
        session.pets.add(record.id());
        String key = switch (session.pets.size()) {
            case 1 -> "familytree.tool.selected_parent1";
            case 2 -> "familytree.tool.selected_parent2";
            default -> "familytree.tool.selected_child";
        };
        chat().send(player, key, record.name());
        sendKnownRelations(player, state, record);
        if (session.pets.size() == 3) {
            session.pending = Mode.LINK;
            AnimalRecord first = state.get(session.pets.get(0));
            AnimalRecord second = state.get(session.pets.get(1));
            sendConfirmPrompt(player, chat().tr(player, "familytree.tool.confirm_link",
                    first != null ? first.name() : "?", second != null ? second.name() : "?", record.name()));
        }
    }

    private void startUnlink(Player player, AnimalRecord record) {
        if (record.parentA() == null && record.parentB() == null) {
            chat().send(player, "familytree.tool.no_parents", record.name());
            sessions.remove(player.getUniqueId());
            return;
        }
        Session session = new Session();
        session.pending = Mode.UNLINK;
        session.unlinkTarget = record.id();
        sessions.put(player.getUniqueId(), session);
        sendConfirmPrompt(player, chat().tr(player, "familytree.tool.confirm_unlink", record.name()));
    }

    private void sendKnownRelations(Player player, FamilyTreeStore state, AnimalRecord record) {
        AnimalRecord a = state.get(record.parentA()), b = state.get(record.parentB());
        if (!plugin.access().canView(player, a)) a = null;
        if (!plugin.access().canView(player, b)) b = null;
        Text parents = a != null || b != null
                ? chat().tr(player, "familytree.tool.known_parents", a != null ? a.name() : "?", b != null ? b.name() : "?")
                : chat().tr(player, "familytree.tool.known_parents_none");
        chat().send(player, parents.color(Text.Color.GRAY));
        List<UUID> childIds = state.buildChildIndex().get(record.id());
        Text children;
        if (childIds == null || childIds.isEmpty()) children = chat().tr(player, "familytree.tool.known_children_none");
        else children = chat().tr(player, "familytree.tool.known_children", childIds.stream().map(state::get)
                .filter(child -> plugin.access().canView(player, child)).map(AnimalRecord::name).collect(Collectors.joining(", ")));
        chat().send(player, children.color(Text.Color.GRAY));
    }

    private void sendConfirmPrompt(Player player, Text question) {
        Text confirm = chat().tr(player, "familytree.tool.confirm_button").color(Text.Color.GREEN).bold(true).run("/familytree confirmlink");
        Text cancel = chat().tr(player, "familytree.tool.cancel_button").color(Text.Color.RED).bold(true).run("/familytree cancellink");
        chat().send(player, question);
        chat().send(player, Text.empty().append(confirm).append(" ").append(cancel));
    }

    public boolean confirm(CommandSender sender) {
        Player player = sender instanceof Player p ? p : null;
        Session session = player != null ? sessions.remove(player.getUniqueId()) : null;
        if (session == null || expired(session, System.currentTimeMillis()) || session.pending == Mode.NONE) {
            chat().fail(sender, "familytree.tool.nothing_pending");
            return false;
        }
        FamilyTreeStore state = plugin.store();
        if (state.readOnly()) return false;
        if (!plugin.access().allowed(sender, FamilyTreePermission.PARENTS)) {
            chat().fail(sender, "familytree.permission.denied", FamilyTreePermission.PARENTS.node());
            return false;
        }
        if (session.pending == Mode.UNLINK) {
            AnimalRecord target = state.get(session.unlinkTarget);
            if (!plugin.access().canManage(sender, target)) {
                chat().fail(sender, "familytree.tool.nothing_pending");
                return false;
            }
            state.update(target.id(), record -> record.setParents(null, null));
            chat().send(sender, "familytree.command.unpair.result", target.name());
            return true;
        }
        AnimalRecord parentA = state.get(session.pets.get(0)), parentB = state.get(session.pets.get(1)), child = state.get(session.pets.get(2));
        if (parentA == null || parentB == null || child == null) {
            chat().fail(sender, "familytree.tool.nothing_pending");
            return false;
        }
        if (!plugin.access().canManage(sender, parentA) || !plugin.access().canManage(sender, parentB) || !plugin.access().canManage(sender, child)) {
            chat().fail(sender, "familytree.access.denied");
            return false;
        }
        if (parentA.id().equals(parentB.id())) {
            chat().fail(sender, "familytree.command.pair.same_parent");
            return false;
        }
        if (child.id().equals(parentA.id()) || child.id().equals(parentB.id())) {
            chat().fail(sender, "familytree.command.pair.child_matches_parent");
            return false;
        }
        if (!PetRelations.speciesMatch(parentA, parentB, child)) {
            chat().fail(sender, "familytree.command.pair.species_mismatch");
            return false;
        }
        // Recheck after the confirmation delay, since another player may have edited the tree.
        if (Genealogy.wouldCreateCycle(child.id(), parentA.id(), parentB.id(), state::get)) {
            chat().fail(sender, "familytree.command.pair.cycle");
            return false;
        }
        state.update(child.id(), record -> record.setParents(parentA.id(), parentB.id()));
        chat().send(sender, "familytree.command.pair.result", parentA.name(), parentB.name(), child.name());
        return true;
    }

    public boolean cancel(CommandSender sender) {
        Session session = sender instanceof Player player ? sessions.remove(player.getUniqueId()) : null;
        if (session == null) {
            chat().fail(sender, "familytree.tool.nothing_pending");
            return false;
        }
        chat().send(sender, "familytree.tool.cancelled");
        return true;
    }

    public void forget(UUID player) {
        sessions.remove(player);
    }

    public void clear() {
        sessions.clear();
    }

    private boolean expired(Session session, long now) {
        return now - session.lastTouchedMillis > plugin.settings().linkingSelectionSeconds() * 1000L;
    }

    private enum Mode { NONE, LINK, UNLINK }

    private static final class Session {
        final List<UUID> pets = new ArrayList<>();
        Mode pending = Mode.NONE;
        UUID unlinkTarget;
        long lastTouchedMillis = System.currentTimeMillis();
    }
}
