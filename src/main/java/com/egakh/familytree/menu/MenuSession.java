package com.egakh.familytree.menu;

import com.egakh.familytree.command.FamilyTreeCommand;
import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.FamilyTreeState;
import com.egakh.familytree.data.HorseStats;
import com.egakh.familytree.event.PetLifecycleListeners;
import com.egakh.familytree.naming.PetRenaming;
import com.egakh.familytree.network.FamilyTreePackets;
import com.egakh.familytree.network.SnapshotPages;
import com.egakh.familytree.permissions.ParentLinks;
import com.egakh.familytree.permissions.PetAccess;
import com.egakh.familytree.permissions.ServerPetAccess;
import com.egakh.familytree.settings.FamilyTreeServerSettings;
import com.egakh.familytree.text.ServerText;
import com.egakh.familytree.util.DeathMessages;
import com.egakh.familytree.util.HorseStatUnits;
import com.egakh.familytree.util.PetRelations;
import com.egakh.familytree.util.TimeUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One player's walk through the menus. Records are read live from the save on every view. */
final class MenuSession {
    private static final int PAGE_SIZE = 45;
    private static final int CHILD_START = 27;
    private static final int CHILD_SLOTS = 18;
    private static final int PREVIOUS = 45, LEFT = 46, LEFT_INNER = 47, LEFT_CENTER = 48, INFO = 49,
            RIGHT_CENTER = 50, RIGHT_INNER = 51, CLOSE = 52, NEXT = 53;
    private static final int FOCUS = 22, PARENT_A = 10, PARENT_B = 16, CONFIRM = 38, CANCEL = 42;
    private static final int[][] GRANDPARENTS = {{0, 2}, {6, 8}};
    private static final int TOOLTIP_COLUMNS = 40;
    private static final int MAX_QUERY = 64;
    private static final long ACTION_INTERVAL_NANOS = 50_000_000L;
    private static final Comparator<AnimalRecord> BY_NAME = Comparator
            .comparing(AnimalRecord::name, String.CASE_INSENSITIVE_ORDER).thenComparing(AnimalRecord::id);

    private enum Filter {
        ALL("familytree.screen.filter.all"), ALIVE("familytree.screen.filter.alive"), DECEASED("familytree.screen.filter.deceased");
        final String key;
        Filter(String key) { this.key = key; }
        boolean accepts(AnimalRecord pet) { return this == ALL || pet.deceased() == (this == DECEASED); }
        Filter next() { return values()[(ordinal() + 1) % values().length]; }
    }

    private final ServerPlayer player;
    private boolean viewingAll;
    private boolean canViewAll;
    private boolean operator;
    private Filter filter = Filter.ALL;
    private String species;
    private String query = "";
    private int page;
    private int childPage;
    private int pickerPage;
    private List<UUID> visible = List.of();
    private List<String> speciesOrder = List.of();
    private Map<UUID, List<UUID>> children = Map.of();
    private List<UUID> filtered;
    private final Deque<UUID> history = new ArrayDeque<>();
    private String notice;
    private long lastAction;

    MenuSession(ServerPlayer player) {
        this.player = player;
        refreshAccess();
        this.viewingAll = canViewAll;
    }

    /** Some buttons rescan the visible records, so a modified client may not press them faster than 20 times a second. */
    private boolean allowAction() {
        long now = System.nanoTime();
        if (now - lastAction < ACTION_INTERVAL_NANOS) return false;
        lastAction = now;
        return true;
    }

    private FamilyTreeState state() {
        return FamilyTreeState.get(player.level().getServer());
    }

    private String language() {
        return ServerText.language(player);
    }

    private void refreshAccess() {
        canViewAll = FamilyTreeServerSettings.mayViewAll(player);
        operator = ServerPetAccess.operator(player.createCommandSourceStack());
    }

    private boolean viewAll() {
        return viewingAll && canViewAll;
    }

    void reload() {
        refreshAccess();
        UUID me = player.getUUID();
        List<AnimalRecord> records = state().all().stream()
                .filter(record -> PetAccess.canView(record, me, viewAll()))
                .sorted(BY_NAME).limit(SnapshotPages.MAX_RECORDS).toList();
        Set<UUID> ids = new HashSet<>();
        for (AnimalRecord record : records) ids.add(record.id());
        List<UUID> sorted = new ArrayList<>(records.size());
        Map<UUID, List<UUID>> index = new HashMap<>();
        Set<String> kinds = new LinkedHashSet<>();
        for (AnimalRecord record : records) {
            sorted.add(record.id());
            kinds.add(record.speciesId());
            for (UUID parent : new LinkedHashSet<>(java.util.Arrays.asList(record.parentA(), record.parentB()))) {
                if (parent != null && ids.contains(parent)) index.computeIfAbsent(parent, key -> new ArrayList<>()).add(record.id());
            }
        }
        visible = sorted;
        children = index;
        speciesOrder = kinds.stream().sorted(Comparator.comparing(MenuSession::speciesSortKey)).toList();
        if (species != null && !speciesOrder.contains(species)) species = null;
        filtered = null;
    }

    private static String speciesSortKey(String speciesId) {
        return switch (speciesId) {
            case "minecraft:wolf" -> "0";
            case "minecraft:cat" -> "1";
            case "minecraft:parrot" -> "2";
            default -> "3" + speciesId;
        };
    }

    private AnimalRecord view(UUID id) {
        if (id == null) return null;
        AnimalRecord record = state().get(id);
        return PetAccess.canView(record, player.getUUID(), viewAll()) ? record : null;
    }

    private boolean manageable(AnimalRecord pet) {
        return PetAccess.canManage(pet, player.getUUID(), operator);
    }

    private List<UUID> filtered() {
        if (filtered != null) return filtered;
        String needle = query.toLowerCase(Locale.ROOT);
        List<UUID> result = new ArrayList<>();
        for (UUID id : visible) {
            AnimalRecord pet = view(id);
            if (pet == null || !filter.accepts(pet) || species != null && !species.equals(pet.speciesId())) continue;
            if (!needle.isEmpty() && !matches(pet, needle)) continue;
            result.add(id);
        }
        return filtered = result;
    }

    private static boolean matches(AnimalRecord pet, String needle) {
        String englishSpecies = MenuItems.speciesName(pet.speciesId()).getString();
        return pet.name().toLowerCase(Locale.ROOT).contains(needle)
                || pet.originalName().toLowerCase(Locale.ROOT).contains(needle)
                || pet.speciesId().toLowerCase(Locale.ROOT).contains(needle)
                || englishSpecies.toLowerCase(Locale.ROOT).contains(needle);
    }

    private void notice(String key, Object... args) {
        notice = ServerText.plain(language(), key, args);
        ServerText.send(player, Component.translatable(key, args));
    }

    // Browser

    void showBrowser() {
        refreshAccess();
        List<UUID> list = filtered();
        int pages = Math.max(1, (list.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));
        int shownPage = page;
        Menus.show(player, title("familytree.menu.title"), (id, inventory, ignored) -> {
            LockedChestMenu menu = new LockedChestMenu(id, player, this::allowAction);
            for (int i = 0; i < PAGE_SIZE && shownPage * PAGE_SIZE + i < list.size(); i++) {
                UUID petId = list.get(shownPage * PAGE_SIZE + i);
                AnimalRecord pet = view(petId);
                if (pet == null) continue;
                menu.put(i, petIcon(pet, null, "familytree.menu.pet.open", false), () -> {
                    history.clear();
                    childPage = 0;
                    showFamily(petId);
                });
            }
            if (list.isEmpty()) menu.put(FOCUS, wrapped(Items.PAPER,
                    visible.isEmpty() ? "familytree.screen.empty" : "familytree.screen.no_matches"), null);
            if (shownPage > 0) menu.put(PREVIOUS, button(Items.ARROW, text("familytree.menu.previous"),
                    pageLine(shownPage, pages)), () -> { page--; showBrowser(); });
            if (shownPage < pages - 1) menu.put(NEXT, button(Items.ARROW, text("familytree.menu.next"),
                    pageLine(shownPage + 2, pages)), () -> { page++; showBrowser(); });
            menu.put(LEFT, filterButton(), () -> { filter = filter.next(); page = 0; filtered = null; showBrowser(); });
            menu.put(LEFT_INNER, speciesButton(), () -> { cycleSpecies(); showBrowser(); });
            if (canViewAll) menu.put(LEFT_CENTER, button(Items.SPYGLASS, text(viewingAll
                    ? "familytree.screen.scope.all" : "familytree.screen.scope.mine"), hint("familytree.menu.click_to_change")),
                    this::toggleScope);
            menu.put(INFO, infoButton(text("familytree.menu.count", list.size()), pageLine(shownPage + 1, pages),
                    "familytree.menu.help"), null);
            menu.put(RIGHT_CENTER, button(Items.OAK_SIGN, query.isEmpty() ? text("familytree.screen.search")
                    : text("familytree.menu.search.current", query), hint("familytree.menu.search.hint")), this::openSearch);
            menu.put(RIGHT_INNER, button(Items.COMPASS, text("familytree.discover.button"),
                    hint("familytree.menu.discover.hint")), this::discover);
            menu.put(CLOSE, closeButton(), player::closeContainer);
            menu.fill(MenuItems.filler(), PAGE_SIZE, LockedChestMenu.SIZE);
            return menu;
        });
        notice = null;
    }

    private void cycleSpecies() {
        if (species == null) species = speciesOrder.isEmpty() ? null : speciesOrder.get(0);
        else {
            int next = speciesOrder.indexOf(species) + 1;
            species = next >= speciesOrder.size() ? null : speciesOrder.get(next);
        }
        page = 0;
        filtered = null;
    }

    private void toggleScope() {
        if (!FamilyTreePackets.allowOpen(player)) notice("familytree.menu.wait");
        else {
            viewingAll = !viewingAll;
            page = 0;
            reload();
        }
        showBrowser();
    }

    private void discover() {
        var server = player.level().getServer();
        if (!FamilyTreePackets.allowScan(server, player.getUUID())) notice("familytree.command.cooldown");
        else {
            UUID me = player.getUUID();
            boolean all = canViewAll;
            var result = PetLifecycleListeners.scanLoadedPets(server, record -> PetAccess.canView(record, me, all));
            notice("familytree.command.scan.result", result.imported(), result.refreshed());
            reload();
        }
        showBrowser();
    }

    private ItemStack filterButton() {
        List<Component> lore = new ArrayList<>();
        for (Filter option : Filter.values()) lore.add(text(option.key).withStyle(option == filter ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        lore.addAll(hint("familytree.menu.click_to_change"));
        return icon(Items.HOPPER, text("familytree.menu.filter", text(filter.key)), lore, filter != Filter.ALL);
    }

    private ItemStack speciesButton() {
        Item item = species == null ? Items.EGG : MenuItems.speciesIcon(species);
        Component current = species == null ? text("familytree.screen.species.all") : MenuItems.speciesName(species);
        return icon(item, text("familytree.menu.species", current), hint("familytree.menu.click_to_change"), species != null);
    }

    private void openSearch() {
        ItemStack input = icon(Items.PAPER, Component.literal(query), List.of(), false);
        Menus.show(player, title("familytree.menu.search.title"), (id, inventory, ignored) -> new TextInputMenu(id, player, this::allowAction, input, query,
                typed -> {
                    String value = typed.strip();
                    if (value.isEmpty()) return icon(Items.PAPER, text("familytree.menu.search.all"), List.of(), false);
                    return icon(Items.PAPER, text("familytree.menu.search.current", value),
                            hint("familytree.menu.search.confirm"), true);
                },
                typed -> {
                    String value = typed.strip();
                    query = value.length() > MAX_QUERY ? value.substring(0, MAX_QUERY) : value;
                    page = 0;
                    filtered = null;
                    showBrowser();
                }));
    }

    // Family

    void showFamily(UUID petId) {
        refreshAccess();
        AnimalRecord pet = view(petId);
        if (pet == null) {
            notice("familytree.rename.not_found");
            showBrowser();
            return;
        }
        List<UUID> kids = children.getOrDefault(petId, List.of()).stream().filter(id -> view(id) != null).toList();
        int pages = Math.max(1, (kids.size() + CHILD_SLOTS - 1) / CHILD_SLOTS);
        childPage = Math.max(0, Math.min(childPage, pages - 1));
        int shownPage = childPage;
        boolean manage = manageable(pet);
        Menus.show(player, title("familytree.screen.view.title", pet.name()), (id, inventory, ignored) -> {
            LockedChestMenu menu = new LockedChestMenu(id, player, this::allowAction);
            menu.put(FOCUS, petIcon(pet, null, null, true), null);
            UUID[] parents = {pet.parentA(), pet.parentB()};
            int[] parentSlots = {PARENT_A, PARENT_B};
            for (int side = 0; side < 2; side++) {
                AnimalRecord parent = view(parents[side]);
                putRelative(menu, parentSlots[side], parent, petId, "familytree.menu.role.parent",
                        "familytree.menu.parent_unknown", manage);
                for (int i = 0; i < 2; i++) {
                    AnimalRecord grandparent = parent == null ? null : view(i == 0 ? parent.parentA() : parent.parentB());
                    if (parent != null) putRelative(menu, GRANDPARENTS[side][i], grandparent, petId,
                            "familytree.menu.role.grandparent", "familytree.menu.grandparent_unknown", false);
                }
            }
            for (int i = 0; i < CHILD_SLOTS && shownPage * CHILD_SLOTS + i < kids.size(); i++) {
                AnimalRecord child = view(kids.get(shownPage * CHILD_SLOTS + i));
                if (child != null) putRelative(menu, CHILD_START + i, child, petId, "familytree.menu.role.child", null, false);
            }
            if (shownPage > 0) menu.put(PREVIOUS, button(Items.ARROW, text("familytree.menu.children.previous"),
                    pageLine(shownPage, pages)), () -> { childPage--; showFamily(petId); });
            if (shownPage < pages - 1) menu.put(NEXT, button(Items.ARROW, text("familytree.menu.children.next"),
                    pageLine(shownPage + 2, pages)), () -> { childPage++; showFamily(petId); });
            menu.put(LEFT, button(Items.ARROW, text("familytree.screen.back"), List.of()), this::back);
            if (manage) {
                menu.put(LEFT_INNER, button(Items.RECOVERY_COMPASS, text("familytree.locate.button"),
                        hint("familytree.menu.locate.hint")), () -> locate(petId));
                menu.put(LEFT_CENTER, button(Items.NAME_TAG, text("familytree.rename.button"),
                        wrappedLore("familytree.rename.hint")), () -> openRename(petId));
                menu.put(RIGHT_CENTER, button(Items.LEAD, text("familytree.parents.button"),
                        wrappedLore("familytree.parents.hint")), () -> { pickerPage = 0; showPicker(petId, null); });
            }
            menu.put(INFO, infoButton(kids.isEmpty() ? text("familytree.menu.children.none")
                    : text("familytree.menu.children.count", kids.size()), kids.size() > CHILD_SLOTS
                    ? pageLine(shownPage + 1, pages) : List.of(), manage ? null : "familytree.menu.view_only"), null);
            menu.put(RIGHT_INNER, button(Items.BOOK, text("familytree.menu.book"), hint("familytree.menu.book.hint")),
                    () -> openBook(petId));
            menu.put(CLOSE, closeButton(), player::closeContainer);
            menu.fill(MenuItems.filler(), PAGE_SIZE, LockedChestMenu.SIZE);
            return menu;
        });
        notice = null;
    }

    private void putRelative(LockedChestMenu menu, int slot, AnimalRecord relative, UUID from,
                             String role, String unknownKey, boolean canAdd) {
        if (relative != null) {
            UUID target = relative.id();
            menu.put(slot, petIcon(relative, role, "familytree.menu.pet.open", false), () -> {
                history.push(from);
                childPage = 0;
                showFamily(target);
            });
        } else if (unknownKey != null) {
            menu.put(slot, icon(Items.GRAY_DYE, text(unknownKey), canAdd ? hint("familytree.menu.parent_add_hint") : List.of(), false),
                    canAdd ? () -> { pickerPage = 0; showPicker(from, null); } : null);
        }
    }

    private void back() {
        childPage = 0;
        if (history.isEmpty()) showBrowser();
        else showFamily(history.pop());
    }

    private void locate(UUID petId) {
        if (!FamilyTreePackets.allowLocate(player)) {
            notice("familytree.menu.wait");
            showFamily(petId);
            return;
        }
        player.closeContainer();
        FamilyTreeCommand.locatePet(player.createCommandSourceStack(), petId);
    }

    private void openRename(UUID petId) {
        AnimalRecord pet = view(petId);
        if (pet == null || !manageable(pet)) {
            notice("familytree.rename.not_allowed");
            showFamily(petId);
            return;
        }
        String original = pet.originalName();
        ItemStack input = icon(Items.NAME_TAG, Component.literal(pet.name()), List.of(), false);
        Menus.show(player, title("familytree.rename.button"), (id, inventory, ignored) -> new TextInputMenu(id, player, this::allowAction, input, pet.name(),
                typed -> {
                    String name = typed.strip();
                    if (name.isEmpty()) return icon(Items.NAME_TAG, Component.literal(original), hint("familytree.rename.reset"), false);
                    if (!PetRenaming.isValidName(name)) return icon(Items.BARRIER, text("familytree.menu.rename.invalid"),
                            wrappedLore("familytree.rename.invalid"), false);
                    return icon(Items.NAME_TAG, Component.literal(name), hint("familytree.menu.rename.save"), true);
                },
                typed -> rename(petId, typed.strip())));
    }

    private void rename(UUID petId, String name) {
        var outcome = FamilyTreePackets.applyRename(player, petId, name);
        switch (outcome.result()) {
            case SUCCESS -> {
                AnimalRecord pet = state().get(petId);
                String shown = pet == null ? name : pet.name();
                if (name.isEmpty()) notice("familytree.menu.rename.reset_done", shown);
                else notice("familytree.menu.rename.done", shown);
                reload();
            }
            case NOT_FOUND -> notice("familytree.rename.not_found");
            case NOT_ALLOWED -> notice("familytree.rename.not_allowed");
            case INVALID_NAME -> notice("familytree.rename.invalid");
            case READ_ONLY -> notice("familytree.command.read_only");
            case BUSY -> notice("familytree.menu.wait");
        }
        if (outcome.result() == PetRenaming.Result.INVALID_NAME) openRename(petId);
        else showFamily(petId);
    }

    private void openBook(UUID petId) {
        AnimalRecord pet = view(petId);
        if (pet == null) {
            showBrowser();
            return;
        }
        List<AnimalRecord> kids = children.getOrDefault(petId, List.of()).stream().map(this::view)
                .filter(java.util.Objects::nonNull).toList();
        FamilyBook book = FamilyBook.build(language(), pet, this::view, kids, TimeUtil.currentWorldDay(player.level()));
        Menus.show(player, Component.literal(pet.name()), (id, inventory, ignored) -> new BookMenu(id, book.stack(), book.pages()));
    }

    // Parent picker

    private void showPicker(UUID childId, UUID firstParent) {
        refreshAccess();
        AnimalRecord child = view(childId);
        AnimalRecord first = view(firstParent);
        if (child == null || !manageable(child) || firstParent != null && first == null) {
            notice("familytree.parents.failed");
            showFamily(childId);
            return;
        }
        Set<UUID> descendants = descendants(childId);
        List<AnimalRecord> candidates = new ArrayList<>();
        for (UUID id : visible) {
            AnimalRecord pet = view(id);
            if (pet == null || !manageable(pet) || id.equals(childId) || id.equals(firstParent) || descendants.contains(id)) continue;
            boolean species = first == null ? PetRelations.mayBeParent(pet.speciesId(), child.speciesId())
                    : PetRelations.speciesMatch(first.speciesId(), pet.speciesId(), child.speciesId());
            if (species) candidates.add(pet);
        }
        int pages = Math.max(1, (candidates.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        pickerPage = Math.max(0, Math.min(pickerPage, pages - 1));
        int shownPage = pickerPage;
        int step = first == null ? 1 : 2;
        Menus.show(player, title("familytree.menu.pick.title", step, child.name()), (id, inventory, ignored) -> {
            LockedChestMenu menu = new LockedChestMenu(id, player, this::allowAction);
            for (int i = 0; i < PAGE_SIZE && shownPage * PAGE_SIZE + i < candidates.size(); i++) {
                AnimalRecord candidate = candidates.get(shownPage * PAGE_SIZE + i);
                UUID chosen = candidate.id();
                menu.put(i, petIcon(candidate, null, "familytree.menu.pet.choose", false), () -> {
                    pickerPage = 0;
                    if (first == null) showPicker(childId, chosen);
                    else showConfirm(childId, firstParent, chosen);
                });
            }
            if (candidates.isEmpty()) menu.put(FOCUS, wrapped(Items.PAPER, "familytree.parents.no_candidates"), null);
            if (shownPage > 0) menu.put(PREVIOUS, button(Items.ARROW, text("familytree.menu.previous"),
                    pageLine(shownPage, pages)), () -> { pickerPage--; showPicker(childId, firstParent); });
            if (shownPage < pages - 1) menu.put(NEXT, button(Items.ARROW, text("familytree.menu.next"),
                    pageLine(shownPage + 2, pages)), () -> { pickerPage++; showPicker(childId, firstParent); });
            menu.put(LEFT, button(Items.ARROW, text("familytree.screen.back"), List.of()), () -> {
                pickerPage = 0;
                if (first == null) showFamily(childId);
                else showPicker(childId, null);
            });
            List<Component> chosenLines = new ArrayList<>();
            if (first != null) chosenLines.add(text("familytree.parents.choose", 1, first.name()).withStyle(ChatFormatting.YELLOW));
            menu.put(INFO, infoButton(text("familytree.menu.pick.title", step, child.name()), chosenLines,
                    "familytree.parents.hint"), null);
            if (first == null && (child.parentA() != null || child.parentB() != null))
                menu.put(RIGHT_CENTER, button(Items.SHEARS, text("familytree.parents.clear"),
                        wrappedLore("familytree.parents.clear_hint")), () -> showClearConfirm(childId));
            menu.put(CLOSE, closeButton(), player::closeContainer);
            menu.fill(MenuItems.filler(), PAGE_SIZE, LockedChestMenu.SIZE);
            return menu;
        });
        notice = null;
    }

    private Set<UUID> descendants(UUID root) {
        Set<UUID> found = new HashSet<>();
        ArrayDeque<UUID> pending = new ArrayDeque<>(children.getOrDefault(root, List.of()));
        while (!pending.isEmpty()) {
            UUID next = pending.pop();
            if (found.add(next)) pending.addAll(children.getOrDefault(next, List.of()));
        }
        return found;
    }

    private void showConfirm(UUID childId, UUID parentA, UUID parentB) {
        refreshAccess();
        AnimalRecord child = view(childId), a = view(parentA), b = view(parentB);
        if (child == null || a == null || b == null) {
            notice("familytree.parents.failed");
            showFamily(childId);
            return;
        }
        Menus.show(player, title("familytree.parents.title", child.name()), (id, inventory, ignored) -> {
            LockedChestMenu menu = new LockedChestMenu(id, player, this::allowAction);
            menu.put(11, petIcon(a, "familytree.menu.role.parent", null, false), null);
            menu.put(15, petIcon(b, "familytree.menu.role.parent", null, false), null);
            menu.put(FOCUS, petIcon(child, null, null, true), null);
            menu.put(CONFIRM, button(Items.LIME_CONCRETE, text("familytree.parents.confirm"),
                    List.of(text("familytree.tool.confirm_link", a.name(), b.name(), child.name()).withStyle(ChatFormatting.GRAY))),
                    () -> link(childId, parentA, parentB, false));
            menu.put(CANCEL, button(Items.RED_CONCRETE, text("familytree.rename.cancel"), List.of()), () -> showFamily(childId));
            menu.put(LEFT, button(Items.ARROW, text("familytree.screen.back"), List.of()), () -> showPicker(childId, parentA));
            menu.put(INFO, infoButton(text("familytree.parents.title", child.name()), List.of(), "familytree.parents.hint"), null);
            menu.put(CLOSE, closeButton(), player::closeContainer);
            menu.fill(MenuItems.filler(), PAGE_SIZE, LockedChestMenu.SIZE);
            return menu;
        });
        notice = null;
    }

    private void showClearConfirm(UUID childId) {
        refreshAccess();
        AnimalRecord child = view(childId);
        if (child == null) {
            showBrowser();
            return;
        }
        Menus.show(player, title("familytree.parents.title", child.name()), (id, inventory, ignored) -> {
            LockedChestMenu menu = new LockedChestMenu(id, player, this::allowAction);
            menu.put(FOCUS, petIcon(child, null, null, true), null);
            menu.put(CONFIRM, button(Items.LIME_CONCRETE, text("familytree.parents.clear"),
                    List.of(text("familytree.parents.clear_confirm", child.name()).withStyle(ChatFormatting.GRAY))),
                    () -> link(childId, null, null, true));
            menu.put(CANCEL, button(Items.RED_CONCRETE, text("familytree.rename.cancel"), List.of()), () -> showFamily(childId));
            menu.put(LEFT, button(Items.ARROW, text("familytree.screen.back"), List.of()), () -> showPicker(childId, null));
            menu.put(INFO, infoButton(text("familytree.parents.clear_confirm", child.name()), List.of(),
                    "familytree.parents.clear_hint"), null);
            menu.put(CLOSE, closeButton(), player::closeContainer);
            menu.fill(MenuItems.filler(), PAGE_SIZE, LockedChestMenu.SIZE);
            return menu;
        });
        notice = null;
    }

    private void link(UUID childId, UUID parentA, UUID parentB, boolean clear) {
        ParentLinks.Result result = FamilyTreePackets.applyLink(player, childId, parentA, parentB, clear);
        switch (result) {
            case SUCCESS -> {
                if (clear) notice("familytree.command.unpair.result", nameOf(childId));
                else notice("familytree.command.pair.result", nameOf(parentA), nameOf(parentB), nameOf(childId));
                reload();
            }
            case NOT_ALLOWED -> notice("familytree.access.denied");
            case NOT_FOUND -> notice("familytree.parents.failed");
            case DISTINCT -> notice("familytree.parents.distinct");
            case SPECIES -> notice("familytree.command.pair.species_mismatch");
            case CYCLE -> notice("familytree.command.pair.cycle");
            case READ_ONLY -> notice("familytree.command.read_only");
            case BUSY -> notice("familytree.menu.wait");
        }
        if (result == ParentLinks.Result.BUSY && !clear) showConfirm(childId, parentA, parentB);
        else showFamily(childId);
    }

    private String nameOf(UUID id) {
        AnimalRecord pet = id == null ? null : state().get(id);
        return pet == null ? "" : pet.name();
    }

    // Item building

    private ItemStack petIcon(AnimalRecord pet, String role, String hint, boolean focused) {
        List<Component> lore = new ArrayList<>();
        if (role != null) lore.add(text(role).withStyle(ChatFormatting.DARK_AQUA));
        lore.add(MenuItems.speciesName(pet.speciesId()).withStyle(ChatFormatting.GRAY).append(Component.literal(" · "))
                .append(text(pet.deceased() ? "familytree.screen.status.deceased" : "familytree.screen.status.alive")
                        .withStyle(pet.deceased() ? ChatFormatting.RED : ChatFormatting.GREEN)));
        long end = pet.deceased() && pet.deathWorldDay() != null ? pet.deathWorldDay() : TimeUtil.currentWorldDay(player.level());
        long age = Math.max(0, end - pet.birthWorldDay());
        lore.add(text(pet.deceased() ? "familytree.menu.pet.lifespan" : "familytree.menu.pet.age", pet.birthWorldDay(), age));
        if (pet.ownerName() != null && !pet.ownerName().isBlank()) lore.add(text("familytree.owner.tamed_by", pet.ownerName()));
        Component death = pet.deceased() ? DeathMessages.line(pet) : null;
        if (death != null) lore.add(death.copy().withStyle(ChatFormatting.RED));
        HorseStats stats = pet.horseStats();
        if (stats != null) {
            lore.add(text("familytree.stats.speed", HorseStatUnits.Stat.SPEED.display(stats)));
            lore.add(text("familytree.stats.jump", HorseStatUnits.Stat.JUMP.display(stats)));
            lore.add(text("familytree.stats.health", HorseStatUnits.Stat.HEALTH.display(stats)));
            if (stats.strength() != null) lore.add(text("familytree.stats.strength", stats.strength(),
                    HorseStatUnits.chestSlots(stats.strength())));
        }
        if (hint != null) lore.add(hint(hint).get(0));
        Component name = Component.literal(pet.name()).withStyle(pet.deceased() ? ChatFormatting.GRAY : ChatFormatting.WHITE);
        return icon(MenuItems.speciesIcon(pet.speciesId()), name, lore, focused);
    }

    private ItemStack button(Item item, Component name, List<Component> lore) {
        return icon(item, name, lore, false);
    }

    private ItemStack closeButton() {
        return icon(Items.BARRIER, text("familytree.menu.close"), List.of(), false);
    }

    private ItemStack infoButton(Component title, List<Component> lines, String helpKey) {
        List<Component> lore = new ArrayList<>(lines);
        if (notice != null) for (String line : Menus.wrap(notice, TOOLTIP_COLUMNS))
            lore.add(Component.literal(line).withStyle(ChatFormatting.YELLOW));
        if (helpKey != null) lore.addAll(wrappedLore(helpKey));
        return icon(Items.PAPER, title, lore, notice != null);
    }

    private ItemStack wrapped(Item item, String key) {
        List<String> lines = Menus.wrap(ServerText.plain(language(), key), TOOLTIP_COLUMNS);
        Component name = Component.literal(lines.isEmpty() ? "" : lines.get(0));
        List<Component> lore = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) lore.add(Component.literal(lines.get(i)));
        return icon(item, name, lore, false);
    }

    private List<Component> wrappedLore(String key) {
        List<Component> lore = new ArrayList<>();
        for (String line : Menus.wrap(ServerText.plain(language(), key), TOOLTIP_COLUMNS))
            lore.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        return lore;
    }

    private List<Component> hint(String key) {
        return List.of(text(key).withStyle(ChatFormatting.YELLOW));
    }

    private List<Component> pageLine(int shown, int total) {
        return List.of(text("familytree.menu.page", shown, total).withStyle(ChatFormatting.GRAY));
    }

    private MutableComponent text(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private Component title(String key, Object... args) {
        return ServerText.localize(language(), text(key, args));
    }

    private ItemStack icon(Item item, Component name, List<Component> lore, boolean glint) {
        String language = language();
        List<Component> localized = new ArrayList<>(lore.size());
        for (Component line : lore) localized.add(ServerText.localize(language, line));
        return MenuItems.icon(item, ServerText.localize(language, name), localized, glint);
    }
}
