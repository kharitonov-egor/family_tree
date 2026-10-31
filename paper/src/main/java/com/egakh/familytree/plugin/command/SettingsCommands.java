package com.egakh.familytree.plugin.command;

import com.egakh.familytree.plugin.FamilyTreePlugin;
import com.egakh.familytree.plugin.PluginSettings;
import com.egakh.familytree.plugin.text.Text;
import com.egakh.familytree.plugin.track.PetRules;
import com.egakh.familytree.settings.ServerSettings;
import com.egakh.familytree.settings.SettingProblem;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** {@code /familytree reload} and {@code /familytree settings}, as in the mod. */
final class SettingsCommands {
    private static final int TAG_PREVIEW = 8;
    private static final String FILE = "plugins/FamilyTree/" + PluginSettings.FILE_NAME;

    private SettingsCommands() {}

    static void reload(FamilyTreePlugin plugin, CommandSender sender) {
        List<SettingProblem> problems = plugin.reloadSettings();
        if (problems == null) {
            plugin.chat().fail(sender, "familytree.reload.read_failed", FILE);
            return;
        }
        List<SettingProblem> shown = new ArrayList<>(problems);
        plugin.command().registerAliases(plugin.settings().commandAliases());
        if (plugin.command().aliasesChangedSinceStartup())
            shown.add(new SettingProblem("commandAliases", "familytree.config.restart", "commandAliases"));
        plugin.chat().send(sender, "familytree.reload.done", FILE);
        if (shown.isEmpty()) plugin.chat().send(sender, plugin.chat().tr(sender, "familytree.reload.no_problems").color(Text.Color.GREEN));
        else {
            plugin.chat().send(sender, plugin.chat().tr(sender, "familytree.reload.problems", shown.size()).color(Text.Color.GOLD));
            for (SettingProblem problem : shown)
                plugin.chat().send(sender, Text.literal("- ").append(plugin.chat().tr(sender, problem.message(), problem.arguments()))
                        .color(Text.Color.YELLOW));
        }
        for (Player player : Bukkit.getOnlinePlayers()) player.updateCommands();
    }

    static void show(FamilyTreePlugin plugin, CommandSender sender, boolean all) {
        plugin.chat().send(sender, plugin.chat().tr(sender, "familytree.settings.header", FILE, "Bukkit").color(Text.Color.GOLD));
        ServerSettings current = plugin.settings();
        int shown = 0;
        for (ServerSettings.Setting setting : ServerSettings.SETTINGS) {
            if (setting.key().equals("operatorLevel")) continue;
            boolean changed = !PluginSettings.isDefault(current, setting);
            if (!all && !changed) continue;
            shown++;
            String value = setting.value().apply(current);
            Text line = Text.literal("  " + setting.key() + " = ").color(Text.Color.GRAY)
                    .append(Text.literal(value.isEmpty() ? "-" : value).color(changed ? Text.Color.AQUA : Text.Color.WHITE));
            if (changed) line.append(plugin.chat().tr(sender, "familytree.settings.default",
                    setting.defaultValue().isEmpty() ? "-" : setting.defaultValue()).color(Text.Color.DARK_GRAY));
            plugin.chat().send(sender, line);
        }
        if (all || !plugin.clientModSupport()) {
            shown++;
            plugin.chat().send(sender, Text.literal("  " + PluginSettings.CLIENT_MOD + " = ").color(Text.Color.GRAY)
                    .append(Text.literal(Boolean.toString(plugin.clientModSupport())).color(plugin.clientModSupport() ? Text.Color.WHITE : Text.Color.AQUA)));
        }
        if (shown == 0) plugin.chat().send(sender, "familytree.settings.all_default");
        tagLine(plugin, sender, PetRules.TRACKED);
        tagLine(plugin, sender, PetRules.IGNORED);
        if (!all) plugin.chat().send(sender, plugin.chat().tr(sender, "familytree.settings.hint").color(Text.Color.GRAY));
    }

    private static void tagLine(FamilyTreePlugin plugin, CommandSender sender, NamespacedKey key) {
        List<String> members = new ArrayList<>();
        try {
            Tag<EntityType> tag = Bukkit.getTag(Tag.REGISTRY_ENTITY_TYPES, key, EntityType.class);
            if (tag != null) for (EntityType type : tag.getValues()) members.add(((Keyed) type).getKey().toString());
        } catch (RuntimeException unavailable) {
            members.clear();
        }
        members = new ArrayList<>(new TreeSet<>(members));
        String preview = String.join(", ", members.subList(0, Math.min(TAG_PREVIEW, members.size())))
                + (members.size() > TAG_PREVIEW ? ", ..." : "");
        plugin.chat().send(sender, plugin.chat().tr(sender, "familytree.settings.tag", "#" + key, members.size(),
                members.isEmpty() ? "-" : preview).color(Text.Color.GRAY));
    }
}
