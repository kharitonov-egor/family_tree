package com.egakh.familytree.plugin.storage;

import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Set;

/** Writes NBT as the JSON that the mod's backups contain, where saved booleans read as {@code true} and {@code false}. */
final class NbtJson {
    private static final Set<String> BOOLEANS = Set.of("name_auto", "deceased");

    private NbtJson() {}

    static void write(Nbt.Compound root, Writer out, boolean pretty) throws IOException {
        JsonWriter json = new JsonWriter(out);
        json.setLenient(true);
        if (pretty) json.setIndent("  ");
        value(json, null, root);
        json.flush();
    }

    private static void value(JsonWriter json, String key, Object value) throws IOException {
        if (value instanceof Nbt.Compound compound) {
            json.beginObject();
            for (Map.Entry<String, Object> entry : compound.entries().entrySet()) {
                json.name(entry.getKey());
                value(json, entry.getKey(), entry.getValue());
            }
            json.endObject();
        } else if (value instanceof Nbt.ListTag list) {
            json.beginArray();
            for (Object element : list.values()) value(json, null, element);
            json.endArray();
        } else if (value instanceof byte[] bytes) {
            json.beginArray();
            for (byte b : bytes) json.value(b);
            json.endArray();
        } else if (value instanceof int[] ints) {
            json.beginArray();
            for (int i : ints) json.value(i);
            json.endArray();
        } else if (value instanceof long[] longs) {
            json.beginArray();
            for (long l : longs) json.value(l);
            json.endArray();
        } else if (value instanceof Byte b && BOOLEANS.contains(key)) {
            json.value(b != 0);
        } else if (value instanceof Double || value instanceof Float) {
            json.value(((Number) value).doubleValue());
        } else if (value instanceof Number number) {
            json.value(number.longValue());
        } else {
            json.value((String) value);
        }
    }
}
