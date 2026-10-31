package com.egakh.familytree.plugin.text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A chat component that either Adventure or BungeeCord chat can render. Family Tree's own keys are resolved on the
 * server by {@link Messages}; {@link #vanilla} keys such as {@code entity.minecraft.wolf} stay translatable, so
 * every client shows them in its own language.
 */
public final class Text {
    public enum Color { BLACK, DARK_GREEN, DARK_AQUA, DARK_RED, GOLD, GRAY, DARK_GRAY, GREEN, AQUA, RED, YELLOW, WHITE }

    private final String literal;
    private final String key;
    private String keybind;
    private final List<Text> args;
    private final List<Text> children = new ArrayList<>();
    private Color color;
    private Boolean bold;
    private Boolean italic;
    private Boolean underlined;
    private String suggestCommand;
    private String runCommand;

    private Text(String literal, String key, List<Text> args) {
        this.literal = literal;
        this.key = key;
        this.args = args;
    }

    public static Text literal(String text) {
        return new Text(text == null ? "" : text, null, List.of());
    }

    public static Text empty() {
        return literal("");
    }

    /** The key bound to a control, as the client shows it. A client without the mod shows the raw name. */
    public static Text keybind(String name) {
        Text text = new Text(null, null, List.of());
        text.keybind = name;
        return text;
    }

    /** A key every Minecraft client already has. Arguments are Text, or anything else as its string value. */
    public static Text vanilla(String key, Object... args) {
        List<Text> converted = new ArrayList<>(args.length);
        for (Object arg : args) converted.add(of(arg));
        return new Text(null, key, List.copyOf(converted));
    }

    static Text of(Object value) {
        return value instanceof Text text ? text : literal(String.valueOf(value));
    }

    public Text append(Text child) {
        children.add(child);
        return this;
    }

    public Text append(String child) {
        return append(literal(child));
    }

    public Text color(Color value) { color = value; return this; }
    public Text bold(boolean value) { bold = value; return this; }
    public Text italic(boolean value) { italic = value; return this; }
    public Text underlined(boolean value) { underlined = value; return this; }
    public Text suggest(String command) { suggestCommand = command; return this; }
    public Text run(String command) { runCommand = command; return this; }

    /** A copy with this color unless the text already has one. */
    public Text withDefaultColor(Color value) {
        Text copy = copy();
        if (copy.color == null) copy.color = value;
        return copy;
    }

    public Text copy() {
        Text copy = new Text(literal, key, args);
        copy.keybind = keybind;
        copy.children.addAll(children);
        copy.color = color;
        copy.bold = bold;
        copy.italic = italic;
        copy.underlined = underlined;
        copy.suggestCommand = suggestCommand;
        copy.runCommand = runCommand;
        return copy;
    }

    public String literal() { return literal; }
    public String key() { return key; }
    public String keybind() { return keybind; }
    public List<Text> args() { return args; }
    public List<Text> children() { return children; }
    public Color color() { return color; }
    public Boolean bold() { return bold; }
    public Boolean italic() { return italic; }
    public Boolean underlined() { return underlined; }
    public String suggestCommand() { return suggestCommand; }
    public String runCommand() { return runCommand; }

    /** Plain text, with vanilla keys looked up through {@code vanillaText}. */
    public String plain(Function<String, String> vanillaText) {
        StringBuilder out = new StringBuilder();
        appendPlain(out, vanillaText);
        return out.toString();
    }

    void appendPlain(StringBuilder out, Function<String, String> vanillaText) {
        if (literal != null) out.append(literal);
        else if (keybind != null) out.append(keybind);
        else {
            String template = vanillaText.apply(key);
            List<Object> plainArgs = new ArrayList<>(args.size());
            for (Text arg : args) plainArgs.add(arg.plain(vanillaText));
            for (Object part : Templates.split(template == null ? key : template, plainArgs)) out.append(part);
        }
        for (Text child : children) child.appendPlain(out, vanillaText);
    }
}
