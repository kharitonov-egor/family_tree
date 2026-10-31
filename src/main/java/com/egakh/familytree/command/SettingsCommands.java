package com.egakh.familytree.command;

import com.egakh.familytree.permissions.ServerPetAccess;
import com.egakh.familytree.settings.FamilyTreeServerSettings;
import com.egakh.familytree.settings.ServerSettings;
import com.egakh.familytree.settings.SettingProblem;
import com.egakh.familytree.text.ServerText;
import com.egakh.familytree.util.SpeciesTags;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** {@code /familytree reload} and {@code /familytree settings}. */
final class SettingsCommands {
    private static final int TAG_PREVIEW = 8;

    private SettingsCommands() {}

    static int reload(CommandSourceStack source) {
        FamilyTreeServerSettings.LoadResult result = FamilyTreeServerSettings.load();
        String file = "config/" + result.path().getFileName();
        if (result.readFailed()) {
            ServerText.failure(source, Component.translatable("familytree.reload.read_failed", file));
            return 0;
        }
        ServerText.success(source, () -> Component.translatable("familytree.reload.done", file), true);
        List<SettingProblem> problems = result.problems();
        ServerText.success(source, () -> Component.translatable(SettingProblem.summaryKey(problems.size()), problems.size())
                .withStyle(problems.isEmpty() ? ChatFormatting.GREEN : ChatFormatting.GOLD), false);
        for (SettingProblem problem : problems) {
            MutableComponent line = Component.literal("- ").append(Component.translatable(problem.message(), problem.arguments()));
            ServerText.success(source, () -> line.withStyle(ChatFormatting.YELLOW), false);
        }
        for (SettingProblem note : result.notes())
            ServerText.success(source, () -> Component.translatable(note.message(), note.arguments()).withStyle(ChatFormatting.GRAY), false);
        resendCommands(source.getServer());
        return problems.isEmpty() ? 1 : 0;
    }

    /** The command tree still hides /familytree from players denied familytree.use, and prune from those denied it. */
    private static void resendCommands(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) server.getCommands().sendCommands(player);
    }

    static int show(CommandSourceStack source, boolean all) {
        String file = "config/" + FamilyTreeServerSettings.FILE_NAME;
        ServerText.success(source, () -> Component.translatable("familytree.settings.header", file,
                ServerPetAccess.backendName()).withStyle(ChatFormatting.GOLD), false);
        ServerSettings current = FamilyTreeServerSettings.current();
        int shown = 0;
        for (ServerSettings.Setting setting : ServerSettings.SETTINGS) {
            boolean changed = !FamilyTreeServerSettings.isDefault(setting);
            if (!all && !changed) continue;
            shown++;
            String value = setting.value().apply(current);
            MutableComponent line = Component.literal("  " + setting.key() + " = ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(value.isEmpty() ? "-" : value).withStyle(changed ? ChatFormatting.AQUA : ChatFormatting.WHITE));
            if (changed) line.append(Component.translatable("familytree.settings.default",
                    setting.defaultValue().isEmpty() ? "-" : setting.defaultValue()).withStyle(ChatFormatting.DARK_GRAY));
            ServerText.success(source, () -> line, false);
        }
        if (shown == 0) ServerText.success(source, () -> Component.translatable("familytree.settings.all_default"), false);
        tagLine(source, "#familytree:tracked", SpeciesTags.members(SpeciesTags.TRACKED));
        tagLine(source, "#familytree:ignored", SpeciesTags.members(SpeciesTags.IGNORED));
        if (!all) ServerText.success(source, () -> Component.translatable("familytree.settings.hint").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static void tagLine(CommandSourceStack source, String tag, List<String> members) {
        String preview = String.join(", ", members.subList(0, Math.min(TAG_PREVIEW, members.size())))
                + (members.size() > TAG_PREVIEW ? ", ..." : "");
        ServerText.success(source, () -> Component.translatable("familytree.settings.tag", tag, members.size(),
                members.isEmpty() ? "-" : preview).withStyle(ChatFormatting.GRAY), false);
    }
}
