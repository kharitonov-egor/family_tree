package com.egakh.familytree.plugin.menu;

import com.egakh.familytree.breeding.BreedingPair;
import com.egakh.familytree.breeding.BreedingPlan;
import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.HorseStats;
import com.egakh.familytree.menu.MenuText;
import com.egakh.familytree.naming.PetRenaming;
import com.egakh.familytree.permissions.FamilyTreePermission;
import com.egakh.familytree.permissions.ParentLinks;
import com.egakh.familytree.permissions.PetAccess;
import com.egakh.familytree.plugin.Edits;
import com.egakh.familytree.plugin.FamilyTreePlugin;
import com.egakh.familytree.plugin.WorldClock;
import com.egakh.familytree.plugin.network.Payloads.RelativesResult;
import com.egakh.familytree.plugin.storage.FamilyTreeStore;
import com.egakh.familytree.plugin.text.GameText;
import com.egakh.familytree.plugin.text.Messages;
import com.egakh.familytree.plugin.text.PlanLines;
import com.egakh.familytree.plugin.text.Text;
import com.egakh.familytree.plugin.text.VanillaLang;
import com.egakh.familytree.util.AnimalCategory;
import com.egakh.familytree.util.CloseRelation;
import com.egakh.familytree.util.HorseStatUnits;
import com.egakh.familytree.util.Kinship;
import com.egakh.familytree.util.PetRelations;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** One player's walk through the menus, ported from the mod. Records are read live from the save on every view. */
final class MenuSession {
    private static final int PAGE_SIZE = 45;
    private static final int CHILD_START = 27;
    private static final int CHILD_SLOTS = 18;
    private static final int PREVIOUS = 45, LEFT = 46, LEFT_INNER = 47, LEFT_CENTER = 48, INFO = 49,
            RIGHT_CENTER = 50, RIGHT_INNER = 51, CLOSE = 52, NEXT = 53;
    private static final int FOCUS = 22, PARENT_A = 10, PARENT_B = 16, CONFIRM = 38, CANCEL = 42, PLAN = 24, RELATIVES = 20;
    private static final int[][] GRANDPARENTS = {{0, 2}, {6, 8}};
    private static final int TOOLTIP_COLUMNS = 40;
    private static final int MAX_QUERY = 64;
    private static final int MAX_RECORDS = 100_000;
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

    private final MenuManager manager;
    private final FamilyTreePlugin plugin;
    private final Player player;
    ChestView current;
    private boolean viewingAll;
    private boolean canViewAll;
    private boolean operator;
    private boolean mayScan, mayLocate, mayRename, mayEditParents, mayPlan, mayShowRelatives;
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

    MenuSession(MenuManager manager, Player player) {
        this.manager = manager;
        this.plugin = manager.plugin();
        this.player = player;
        refreshAccess();
        this.viewingAll = canViewAll;
    }

    Player player() {
        return player;
    }

    /** Some buttons rescan the visible records, so a modified client may not press them faster than 20 times a second. */
    boolean allowAction() {
        long now = System.nanoTime();
        if (now - lastAction < ACTION_INTERVAL_NANOS) return false;
        lastAction = now;
        return true;
    }

    private FamilyTreeStore state() {
        return plugin.store();
    }

    private String language() {
        return plugin.chat().language(player);
    }

    private void refreshAccess() {
        canViewAll = plugin.access().viewAll(player);
        operator = plugin.access().manageAll(player);
        mayScan = plugin.access().allowed(player, FamilyTreePermission.SCAN);
        mayLocate = plugin.access().allowed(player, FamilyTreePermission.LOCATE);
        mayRename = plugin.access().allowed(player, FamilyTreePermission.RENAME);
        mayEditParents = plugin.access().allowed(player, FamilyTreePermission.PARENTS);
        mayPlan = plugin.access().allowed(player, FamilyTreePermission.BREED);
        mayShowRelatives = plugin.bridge().mayShowRelatives(player);
    }

    private boolean viewAll() {
        return viewingAll && canViewAll;
    }

    void reload() {
        refreshAccess();
        UUID me = player.getUniqueId();
        List<AnimalRecord> records = state().all().stream()
                .filter(record -> PetAccess.canView(record, me, viewAll()))
                .sorted(BY_NAME).limit(MAX_RECORDS).toList();
        Set<UUID> ids = new HashSet<>();
        for (AnimalRecord record : records) ids.add(record.id());
        List<UUID> sorted = new ArrayList<>(records.size());
        Map<UUID, List<UUID>> index = new HashMap<>();
        Set<String> kinds = new LinkedHashSet<>();
        for (AnimalRecord record : records) {
            sorted.add(record.id());
            kinds.add(record.speciesId());
            for (UUID parent : new LinkedHashSet<>(Arrays.asList(record.parentA(), record.parentB()))) {
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
        return PetAccess.canView(record, player.getUniqueId(), viewAll()) ? record : null;
    }

    private boolean manageable(AnimalRecord pet) {
        return PetAccess.canManage(pet, player.getUniqueId(), operator);
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
        String englishSpecies = GameText.species(pet.speciesId()).plain(VanillaLang::english);
        return pet.name().toLowerCase(Locale.ROOT).contains(needle)
                || pet.originalName().toLowerCase(Locale.ROOT).contains(needle)
                || pet.speciesId().toLowerCase(Locale.ROOT).contains(needle)
                || englishSpecies.toLowerCase(Locale.ROOT).contains(needle);
    }

    private void notice(String key, Object... args) {
        notice = Messages.plain(language(), key, args);
        plugin.chat().send(player, key, args);
    }

    private ChestView chest(Text title) {
        return new ChestView(title.plain(VanillaLang::english));
    }

    // Browser

    void showBrowser() {
        refreshAccess();
        List<UUID> list = filtered();
        int pages = Math.max(1, (list.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));
        int shownPage = page;
        ChestView menu = chest(text("familytree.menu.title"));
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
        if (list.isEmpty()) menu.put(FOCUS, wrapped(Material.PAPER,
                visible.isEmpty() ? "familytree.screen.empty" : "familytree.screen.no_matches"), null);
        if (shownPage > 0) menu.put(PREVIOUS, button(Material.ARROW, text("familytree.menu.previous"),
                pageLine(shownPage, pages)), () -> { page--; showBrowser(); });
        if (shownPage < pages - 1) menu.put(NEXT, button(Material.ARROW, text("familytree.menu.next"),
                pageLine(shownPage + 2, pages)), () -> { page++; showBrowser(); });
        menu.put(LEFT, filterButton(), () -> { filter = filter.next(); page = 0; filtered = null; showBrowser(); });
        menu.put(LEFT_INNER, speciesButton(), () -> { cycleSpecies(); showBrowser(); });
        if (canViewAll) menu.put(LEFT_CENTER, button(Material.SPYGLASS, text(viewingAll
                ? "familytree.screen.scope.all" : "familytree.screen.scope.mine"), hint("familytree.menu.click_to_change")),
                this::toggleScope);
        menu.put(INFO, infoButton(text("familytree.menu.count", list.size()), pageLine(shownPage + 1, pages),
                "familytree.menu.help"), null);
        menu.put(RIGHT_CENTER, button(Material.OAK_SIGN, query.isEmpty() ? text("familytree.screen.search")
                : text("familytree.menu.search.current", query), hint("familytree.menu.search.hint")), this::openSearch);
        if (mayScan) menu.put(RIGHT_INNER, button(Material.COMPASS, text("familytree.discover.button"),
                hint("familytree.menu.discover.hint")), this::discover);
        menu.put(CLOSE, closeButton(), player::closeInventory);
        menu.fill(MenuItems.filler(), PAGE_SIZE, ChestView.SIZE);
        manager.show(this, menu);
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
        if (!plugin.cooldowns().open(player.getUniqueId())) notice("familytree.menu.wait");
        else {
            viewingAll = !viewingAll;
            page = 0;
            reload();
        }
        showBrowser();
    }

    private void discover() {
        if (!mayScan) notice("familytree.permission.denied", FamilyTreePermission.SCAN.node());
        else if (!plugin.cooldowns().scan(player.getUniqueId())) notice("familytree.command.cooldown");
        else {
            UUID me = player.getUniqueId();
            boolean all = canViewAll;
            var result = plugin.tracker().scanLoadedPets(record -> PetAccess.canView(record, me, all));
            notice("familytree.command.scan.result", result.imported(), result.refreshed());
            reload();
        }
        showBrowser();
    }

    private ItemStack filterButton() {
        List<Text> lore = new ArrayList<>();
        for (Filter option : Filter.values()) lore.add(text(option.key).color(option == filter ? Text.Color.YELLOW : Text.Color.GRAY));
        lore.addAll(hint("familytree.menu.click_to_change"));
        return icon(Material.HOPPER, text("familytree.menu.filter", text(filter.key)), lore, filter != Filter.ALL);
    }

    private ItemStack speciesButton() {
        Material item = species == null ? Material.EGG : MenuItems.speciesIcon(species);
        Text current = species == null ? text("familytree.screen.species.all") : GameText.species(species);
        return icon(item, text("familytree.menu.species", current), hint("familytree.menu.click_to_change"), species != null);
    }

    private void openSearch() {
        ItemStack input = icon(Material.PAPER, Text.literal(query.isEmpty() ? " " : query), List.of(), false);
        manager.prompts.open(player, title("familytree.menu.search.title"), input, query,
                typed -> {
                    String value = typed.strip();
                    if (value.isEmpty()) return icon(Material.PAPER, text("familytree.menu.search.all"), List.of(), false);
                    return icon(Material.PAPER, text("familytree.menu.search.current", value), hint("familytree.menu.search.confirm"), true);
                },
                typed -> {
                    String value = typed.strip();
                    query = value.length() > MAX_QUERY ? value.substring(0, MAX_QUERY) : value;
                    page = 0;
                    filtered = null;
                    showBrowser();
                },
                this::showBrowser,
                "familytree.plugin.prompt.search");
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
        ChestView menu = chest(text("familytree.screen.view.title", pet.name()));
        menu.put(FOCUS, petIcon(pet, null, null, true), null);
        UUID[] parents = {pet.parentA(), pet.parentB()};
        int[] parentSlots = {PARENT_A, PARENT_B};
        for (int side = 0; side < 2; side++) {
            AnimalRecord parent = view(parents[side]);
            putRelative(menu, parentSlots[side], parent, petId, "familytree.menu.role.parent",
                    "familytree.menu.parent_unknown", manage && mayEditParents);
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
        if (shownPage > 0) menu.put(PREVIOUS, button(Material.ARROW, text("familytree.menu.children.previous"),
                pageLine(shownPage, pages)), () -> { childPage--; showFamily(petId); });
        if (shownPage < pages - 1) menu.put(NEXT, button(Material.ARROW, text("familytree.menu.children.next"),
                pageLine(shownPage + 2, pages)), () -> { childPage++; showFamily(petId); });
        menu.put(LEFT, button(Material.ARROW, text("familytree.screen.back"), List.of()), this::back);
        if (manage && mayLocate) menu.put(LEFT_INNER, button(Material.RECOVERY_COMPASS, text("familytree.locate.button"),
                hint("familytree.menu.locate.hint")), () -> locate(petId));
        if (manage && mayRename) menu.put(LEFT_CENTER, button(Material.NAME_TAG, text("familytree.rename.button"),
                wrappedLore("familytree.rename.hint")), () -> openRename(petId));
        if (manage && mayEditParents) menu.put(RIGHT_CENTER, button(Material.LEAD, text("familytree.parents.button"),
                wrappedLore("familytree.parents.hint")), () -> { pickerPage = 0; showPicker(petId, null); });
        if (mayPlan && !mates(pet).isEmpty()) menu.put(PLAN, button(Material.GOLDEN_CARROT, text("familytree.menu.plan"),
                wrappedLore("familytree.menu.plan.hint")), () -> { pickerPage = 0; showMates(petId); });
        if (mayShowRelatives) menu.put(RELATIVES, button(Material.GLOW_INK_SAC, text("familytree.menu.relatives"),
                wrappedLore("familytree.menu.relatives.hint")), () -> showRelatives(petId));
        menu.put(INFO, infoButton(kids.isEmpty() ? text("familytree.menu.children.none")
                : text("familytree.menu.children.count", kids.size()), kids.size() > CHILD_SLOTS
                ? pageLine(shownPage + 1, pages) : List.of(), manage ? null : "familytree.menu.view_only"), null);
        menu.put(RIGHT_INNER, button(Material.BOOK, text("familytree.menu.book"), hint("familytree.menu.book.hint")),
                () -> openBook(petId));
        menu.put(CLOSE, closeButton(), player::closeInventory);
        menu.fill(MenuItems.filler(), PAGE_SIZE, ChestView.SIZE);
        manager.show(this, menu);
        notice = null;
    }

    private void putRelative(ChestView menu, int slot, AnimalRecord relative, UUID from, String role, String unknownKey, boolean canAdd) {
        if (relative != null) {
            UUID target = relative.id();
            menu.put(slot, petIcon(relative, role, "familytree.menu.pet.open", false), () -> {
                history.push(from);
                childPage = 0;
                showFamily(target);
            });
        } else if (unknownKey != null) {
            menu.put(slot, icon(MenuItems.material("GRAY_DYE", Material.GRAY_DYE), text(unknownKey),
                    canAdd ? hint("familytree.menu.parent_add_hint") : List.of(), false),
                    canAdd ? () -> { pickerPage = 0; showPicker(from, null); } : null);
        }
    }

    private void back() {
        childPage = 0;
        if (history.isEmpty()) showBrowser();
        else showFamily(history.pop());
    }

    private void locate(UUID petId) {
        if (!plugin.cooldowns().locate(player.getUniqueId())) {
            notice("familytree.menu.wait");
            showFamily(petId);
            return;
        }
        player.closeInventory();
        plugin.command().locatePet(player, petId);
    }

    /** The chest menu's version of the relatives key: chat lines, and particles that only this player sees. */
    private void showRelatives(UUID petId) {
        refreshAccess();
        AnimalRecord pet = view(petId);
        if (!mayShowRelatives) {
            showFamily(petId);
            return;
        }
        if (!plugin.cooldowns().relatives(player.getUniqueId())) {
            notice("familytree.menu.wait");
            showFamily(petId);
            return;
        }
        RelativesResult result = plugin.bridge().nearbyRelatives(player, UUID.randomUUID(), petId);
        if (result.status() != RelativesResult.Status.FOUND.ordinal()) {
            notice("familytree.menu.relatives.far", pet == null ? "" : pet.name());
            showFamily(petId);
            return;
        }
        player.closeInventory();
        plugin.chat().send(player, summary(result));
        RelativeMarkers.mark(player, petId, 0xFFFFFF);
        for (RelativesResult.Relative relative : result.relatives()) {
            CloseRelation relation = CloseRelation.byOrdinal(relative.relation());
            if (relation == null) continue;
            plugin.chat().send(player, Text.literal("  ").append(text("familytree.relatives.label",
                    text(relation.translationKey()).rgb(relation.color()), Text.literal(relative.name()))));
            RelativeMarkers.mark(player, relative.id(), relation.color());
        }
    }

    private Text summary(RelativesResult result) {
        if (result.total() == 0) return text("familytree.relatives.none", result.targetName());
        if (result.total() > result.relatives().size())
            return text("familytree.relatives.count_capped", result.targetName(), result.total(), result.relatives().size());
        return text("familytree.relatives.count", result.targetName(), result.total());
    }

    private void openRename(UUID petId) {
        AnimalRecord pet = view(petId);
        if (pet == null || !mayRename || !manageable(pet)) {
            notice("familytree.rename.not_allowed");
            showFamily(petId);
            return;
        }
        String original = pet.originalName();
        ItemStack input = icon(Material.NAME_TAG, Text.literal(pet.name()), List.of(), false);
        manager.prompts.open(player, title("familytree.rename.button"), input, pet.name(),
                typed -> {
                    String name = typed.strip();
                    if (name.isEmpty()) return icon(Material.NAME_TAG, Text.literal(original), hint("familytree.rename.reset"), false);
                    if (!PetRenaming.isValidName(name)) return icon(Material.BARRIER, text("familytree.menu.rename.invalid"),
                            wrappedLore("familytree.rename.invalid"), false);
                    return icon(Material.NAME_TAG, Text.literal(name), hint("familytree.menu.rename.save"), true);
                },
                typed -> rename(petId, typed.strip()),
                () -> showFamily(petId),
                "familytree.plugin.prompt.rename", pet.name());
    }

    private void rename(UUID petId, String name) {
        PetRenaming.Result result = applyRename(petId, name);
        switch (result) {
            case SUCCESS -> {
                AnimalRecord pet = state().get(petId);
                String shown = pet == null ? name : pet.name();
                if (name.isEmpty()) notice("familytree.menu.rename.reset_done", shown);
                else notice("familytree.menu.rename.done", shown);
                reload();
            }
            case NOT_FOUND -> notice("familytree.rename.not_found");
            case NOT_ALLOWED -> notice("familytree.rename.not_allowed");
            case NO_PERMISSION -> notice("familytree.permission.denied", FamilyTreePermission.RENAME.node());
            case INVALID_NAME -> notice("familytree.rename.invalid");
            case READ_ONLY -> notice("familytree.command.read_only");
            case BUSY -> notice("familytree.menu.wait");
        }
        if (result == PetRenaming.Result.INVALID_NAME) openRename(petId);
        else showFamily(petId);
    }

    /** The same checks as the mod's rename packet: cooldown, read-only history, the rename node, then ownership. */
    PetRenaming.Result applyRename(UUID petId, String name) {
        return Edits.rename(plugin, player, petId, name);
    }

    private void openBook(UUID petId) {
        AnimalRecord pet = view(petId);
        if (pet == null) {
            showBrowser();
            return;
        }
        List<AnimalRecord> kids = children.getOrDefault(petId, List.of()).stream().map(this::view).filter(Objects::nonNull).toList();
        FamilyBook book = FamilyBook.build(language(), pet, this::view, kids, WorldClock.currentWorldDay());
        player.closeInventory();
        plugin.chat().renderer().openBook(player, book.title(), "Family Tree", book.pages());
    }

    // Breeding planner

    private List<AnimalRecord> mates(AnimalRecord pet) {
        if (pet == null || pet.deceased() || !BreedingPair.breeds(pet.speciesId())) return List.of();
        List<AnimalRecord> mates = new ArrayList<>();
        for (UUID id : visible) {
            AnimalRecord other = view(id);
            if (other == null || other.deceased() || other.id().equals(pet.id())) continue;
            if (BreedingPair.canBreed(pet.speciesId(), other.speciesId())) mates.add(other);
        }
        return mates;
    }

    private void showMates(UUID petId) {
        refreshAccess();
        AnimalRecord pet = view(petId);
        if (pet == null || !mayPlan) {
            showBrowser();
            return;
        }
        List<AnimalRecord> candidates = mates(pet);
        int pages = Math.max(1, (candidates.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        pickerPage = Math.max(0, Math.min(pickerPage, pages - 1));
        int shownPage = pickerPage;
        ChestView menu = chest(text("familytree.menu.plan.title", pet.name()));
        for (int i = 0; i < PAGE_SIZE && shownPage * PAGE_SIZE + i < candidates.size(); i++) {
            AnimalRecord mate = candidates.get(shownPage * PAGE_SIZE + i);
            UUID chosen = mate.id();
            menu.put(i, petIcon(mate, null, "familytree.menu.plan.choose", false), () -> openPlan(petId, chosen));
        }
        if (candidates.isEmpty()) menu.put(FOCUS, wrapped(Material.PAPER, "familytree.menu.plan.none"), null);
        if (shownPage > 0) menu.put(PREVIOUS, button(Material.ARROW, text("familytree.menu.previous"),
                pageLine(shownPage, pages)), () -> { pickerPage--; showMates(petId); });
        if (shownPage < pages - 1) menu.put(NEXT, button(Material.ARROW, text("familytree.menu.next"),
                pageLine(shownPage + 2, pages)), () -> { pickerPage++; showMates(petId); });
        menu.put(LEFT, button(Material.ARROW, text("familytree.screen.back"), List.of()), () -> { pickerPage = 0; showFamily(petId); });
        menu.put(INFO, infoButton(text("familytree.menu.plan.title", pet.name()), List.of(), "familytree.menu.plan.hint"), null);
        menu.put(CLOSE, closeButton(), player::closeInventory);
        menu.fill(MenuItems.filler(), PAGE_SIZE, ChestView.SIZE);
        manager.show(this, menu);
        notice = null;
    }

    /** Kinship uses only animals this player may view, as the screens do. The plan opens as a book, one topic to a page. */
    private void openPlan(UUID petId, UUID mateId) {
        AnimalRecord pet = view(petId), mate = view(mateId);
        if (pet == null || mate == null || !mayPlan) {
            showBrowser();
            return;
        }
        Map<UUID, AnimalRecord> shown = new HashMap<>();
        for (UUID id : visible) {
            AnimalRecord record = view(id);
            if (record != null) shown.put(id, record);
        }
        BreedingPlan plan = new BreedingPlan(pet, mate, new Kinship(shown));
        List<Text> pages = new ArrayList<>();
        for (List<PlanLines.Line> section : PlanLines.sections(language(), plan)) {
            Text page = Text.empty();
            boolean first = true;
            for (PlanLines.Line line : section) {
                if (!first) page.append("\n\n");
                page.append(line.muted() ? line.text().copy().color(Text.Color.DARK_GRAY) : line.text());
                first = false;
            }
            pages.add(page);
        }
        String title = pet.name() + " × " + mate.name();
        if (title.length() > 32) title = title.substring(0, 31) + "…";
        player.closeInventory();
        plugin.chat().renderer().openBook(player, title, "Family Tree", pages);
    }

    // Parent picker

    private void showPicker(UUID childId, UUID firstParent) {
        refreshAccess();
        AnimalRecord child = view(childId);
        AnimalRecord first = view(firstParent);
        if (child == null || !mayEditParents || !manageable(child) || firstParent != null && first == null) {
            notice("familytree.parents.failed");
            showFamily(childId);
            return;
        }
        Set<UUID> descendants = descendants(childId);
        List<AnimalRecord> candidates = new ArrayList<>();
        for (UUID id : visible) {
            AnimalRecord pet = view(id);
            if (pet == null || !manageable(pet) || id.equals(childId) || id.equals(firstParent) || descendants.contains(id)) continue;
            boolean speciesFits = first == null ? PetRelations.mayBeParent(pet.speciesId(), child.speciesId())
                    : PetRelations.speciesMatch(first.speciesId(), pet.speciesId(), child.speciesId());
            if (speciesFits) candidates.add(pet);
        }
        int pages = Math.max(1, (candidates.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        pickerPage = Math.max(0, Math.min(pickerPage, pages - 1));
        int shownPage = pickerPage;
        int step = first == null ? 1 : 2;
        ChestView menu = chest(text("familytree.menu.pick.title", step, child.name()));
        for (int i = 0; i < PAGE_SIZE && shownPage * PAGE_SIZE + i < candidates.size(); i++) {
            AnimalRecord candidate = candidates.get(shownPage * PAGE_SIZE + i);
            UUID chosen = candidate.id();
            menu.put(i, petIcon(candidate, null, "familytree.menu.pet.choose", false), () -> {
                pickerPage = 0;
                if (first == null) showPicker(childId, chosen);
                else showConfirm(childId, firstParent, chosen);
            });
        }
        if (candidates.isEmpty()) menu.put(FOCUS, wrapped(Material.PAPER, "familytree.parents.no_candidates"), null);
        if (shownPage > 0) menu.put(PREVIOUS, button(Material.ARROW, text("familytree.menu.previous"),
                pageLine(shownPage, pages)), () -> { pickerPage--; showPicker(childId, firstParent); });
        if (shownPage < pages - 1) menu.put(NEXT, button(Material.ARROW, text("familytree.menu.next"),
                pageLine(shownPage + 2, pages)), () -> { pickerPage++; showPicker(childId, firstParent); });
        menu.put(LEFT, button(Material.ARROW, text("familytree.screen.back"), List.of()), () -> {
            pickerPage = 0;
            if (first == null) showFamily(childId);
            else showPicker(childId, null);
        });
        List<Text> chosenLines = new ArrayList<>();
        if (first != null) chosenLines.add(text("familytree.parents.choose", 1, first.name()).color(Text.Color.YELLOW));
        menu.put(INFO, infoButton(text("familytree.menu.pick.title", step, child.name()), chosenLines, "familytree.parents.hint"), null);
        if (first == null && (child.parentA() != null || child.parentB() != null))
            menu.put(RIGHT_CENTER, button(Material.SHEARS, text("familytree.parents.clear"),
                    wrappedLore("familytree.parents.clear_hint")), () -> showClearConfirm(childId));
        menu.put(CLOSE, closeButton(), player::closeInventory);
        menu.fill(MenuItems.filler(), PAGE_SIZE, ChestView.SIZE);
        manager.show(this, menu);
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
        ChestView menu = chest(text("familytree.parents.title", child.name()));
        menu.put(11, petIcon(a, "familytree.menu.role.parent", null, false), null);
        menu.put(15, petIcon(b, "familytree.menu.role.parent", null, false), null);
        menu.put(FOCUS, petIcon(child, null, null, true), null);
        menu.put(CONFIRM, button(MenuItems.material("LIME_CONCRETE", Material.EMERALD_BLOCK), text("familytree.parents.confirm"),
                List.of(text("familytree.tool.confirm_link", a.name(), b.name(), child.name()).color(Text.Color.GRAY))),
                () -> link(childId, parentA, parentB, false));
        menu.put(CANCEL, button(MenuItems.material("RED_CONCRETE", Material.REDSTONE_BLOCK), text("familytree.rename.cancel"), List.of()),
                () -> showFamily(childId));
        menu.put(LEFT, button(Material.ARROW, text("familytree.screen.back"), List.of()), () -> showPicker(childId, parentA));
        menu.put(INFO, infoButton(text("familytree.parents.title", child.name()), List.of(), "familytree.parents.hint"), null);
        menu.put(CLOSE, closeButton(), player::closeInventory);
        menu.fill(MenuItems.filler(), PAGE_SIZE, ChestView.SIZE);
        manager.show(this, menu);
        notice = null;
    }

    private void showClearConfirm(UUID childId) {
        refreshAccess();
        AnimalRecord child = view(childId);
        if (child == null) {
            showBrowser();
            return;
        }
        ChestView menu = chest(text("familytree.parents.title", child.name()));
        menu.put(FOCUS, petIcon(child, null, null, true), null);
        menu.put(CONFIRM, button(MenuItems.material("LIME_CONCRETE", Material.EMERALD_BLOCK), text("familytree.parents.clear"),
                List.of(text("familytree.parents.clear_confirm", child.name()).color(Text.Color.GRAY))),
                () -> link(childId, null, null, true));
        menu.put(CANCEL, button(MenuItems.material("RED_CONCRETE", Material.REDSTONE_BLOCK), text("familytree.rename.cancel"), List.of()),
                () -> showFamily(childId));
        menu.put(LEFT, button(Material.ARROW, text("familytree.screen.back"), List.of()), () -> showPicker(childId, null));
        menu.put(INFO, infoButton(text("familytree.parents.clear_confirm", child.name()), List.of(), "familytree.parents.clear_hint"), null);
        menu.put(CLOSE, closeButton(), player::closeInventory);
        menu.fill(MenuItems.filler(), PAGE_SIZE, ChestView.SIZE);
        manager.show(this, menu);
        notice = null;
    }

    private void link(UUID childId, UUID parentA, UUID parentB, boolean clear) {
        ParentLinks.Result result = Edits.link(plugin, player, childId, parentA, parentB, clear);
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
        AnimalRecord pet = state().get(id);
        return pet == null ? "" : pet.name();
    }

    // Item building

    private ItemStack petIcon(AnimalRecord pet, String role, String hint, boolean focused) {
        String language = language();
        List<Text> lore = new ArrayList<>();
        if (role != null) lore.add(text(role).color(Text.Color.DARK_AQUA));
        lore.add(Text.empty().append(GameText.species(pet.speciesId()).color(Text.Color.GRAY)).append(Text.literal(" · "))
                .append(text(pet.deceased() ? "familytree.screen.status.deceased" : "familytree.screen.status.alive")
                        .color(pet.deceased() ? Text.Color.RED : Text.Color.GREEN)));
        long end = pet.deceased() && pet.deathWorldDay() != null ? pet.deathWorldDay() : WorldClock.currentWorldDay();
        long age = Math.max(0, end - pet.birthWorldDay());
        lore.add(text(pet.deceased() ? "familytree.menu.pet.lifespan" : "familytree.menu.pet.age", pet.birthWorldDay(), age));
        Text variant = GameText.variant(language, pet);
        if (variant != null) {
            Text line = text("familytree.variant.line", variant);
            if (GameText.rare(pet)) line.append(Text.literal(" " + GameText.RARE_MARKER).color(Text.Color.GOLD));
            lore.add(line);
        }
        if (pet.ownerName() != null && !pet.ownerName().isBlank()) lore.add(text(
                AnimalCategory.of(pet.speciesId()) == AnimalCategory.PETS ? "familytree.owner.tamed_by" : "familytree.owner.owner",
                pet.ownerName()));
        Text death = pet.deceased() ? GameText.deathLine(language, pet) : null;
        if (death != null) lore.add(death.withDefaultColor(Text.Color.RED));
        HorseStats stats = pet.horseStats();
        if (stats != null) {
            lore.add(text("familytree.stats.speed", HorseStatUnits.Stat.SPEED.display(stats)));
            lore.add(text("familytree.stats.jump", HorseStatUnits.Stat.JUMP.display(stats)));
            lore.add(text("familytree.stats.health", HorseStatUnits.Stat.HEALTH.display(stats)));
            if (stats.strength() != null) lore.add(text("familytree.stats.strength", stats.strength(),
                    HorseStatUnits.chestSlots(stats.strength())));
        }
        if (hint != null) lore.add(hint(hint).get(0));
        Text name = Text.literal(pet.name()).color(pet.deceased() ? Text.Color.GRAY : Text.Color.WHITE);
        return icon(MenuItems.speciesIcon(pet.speciesId()), name, lore, focused);
    }

    private ItemStack button(Material item, Text name, List<Text> lore) {
        return icon(item, name, lore, false);
    }

    private ItemStack closeButton() {
        return icon(Material.BARRIER, text("familytree.menu.close"), List.of(), false);
    }

    private ItemStack infoButton(Text title, List<Text> lines, String helpKey) {
        List<Text> lore = new ArrayList<>(lines);
        if (notice != null) for (String line : MenuText.wrap(notice, TOOLTIP_COLUMNS))
            lore.add(Text.literal(line).color(Text.Color.YELLOW));
        if (helpKey != null) lore.addAll(wrappedLore(helpKey));
        return icon(Material.PAPER, title, lore, notice != null);
    }

    private ItemStack wrapped(Material item, String key) {
        List<String> lines = MenuText.wrap(Messages.plain(language(), key), TOOLTIP_COLUMNS);
        Text name = Text.literal(lines.isEmpty() ? "" : lines.get(0));
        List<Text> lore = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) lore.add(Text.literal(lines.get(i)));
        return icon(item, name, lore, false);
    }

    private List<Text> wrappedLore(String key) {
        List<Text> lore = new ArrayList<>();
        for (String line : MenuText.wrap(Messages.plain(language(), key), TOOLTIP_COLUMNS))
            lore.add(Text.literal(line).color(Text.Color.GRAY));
        return lore;
    }

    private List<Text> hint(String key) {
        return List.of(text(key).color(Text.Color.YELLOW));
    }

    private List<Text> pageLine(int shown, int total) {
        return List.of(text("familytree.menu.page", shown, total).color(Text.Color.GRAY));
    }

    private Text text(String key, Object... args) {
        return Messages.tr(language(), key, args);
    }

    private String title(String key, Object... args) {
        return Messages.plain(language(), key, args);
    }

    private ItemStack icon(Material item, Text name, List<Text> lore, boolean glint) {
        return MenuItems.icon(plugin.chat().renderer(), item, name, lore, glint);
    }
}
