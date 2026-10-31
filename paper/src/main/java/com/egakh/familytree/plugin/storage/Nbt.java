package com.egakh.familytree.plugin.storage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Minecraft's binary NBT format, as {@code NbtIo.readCompressed} and {@code writeCompressed} use it for
 * {@code familytree.dat}. Values are Byte, Short, Integer, Long, Float, Double, byte[], String, {@link ListTag},
 * {@link Compound}, int[], and long[].
 */
public final class Nbt {
    static final byte END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6, BYTE_ARRAY = 7,
            STRING = 8, LIST = 9, COMPOUND = 10, INT_ARRAY = 11, LONG_ARRAY = 12;
    private static final int MAX_DEPTH = 512;
    private static final int MAX_ARRAY = 1 << 24;

    private Nbt() {}

    public static final class Compound {
        private final Map<String, Object> values = new LinkedHashMap<>();

        public Object get(String key) { return values.get(key); }
        public boolean has(String key) { return values.containsKey(key); }
        public void put(String key, Object value) { values.put(key, check(value)); }
        public void remove(String key) { values.remove(key); }
        public Map<String, Object> entries() { return java.util.Collections.unmodifiableMap(values); }

        public Compound copy() {
            Compound copy = new Compound();
            values.forEach((key, value) -> copy.values.put(key, deepCopy(value)));
            return copy;
        }

        @Override public boolean equals(Object other) {
            return other instanceof Compound compound && Nbt.equal(values, compound.values);
        }

        @Override public int hashCode() { return values.keySet().hashCode(); }
    }

    public static final class ListTag {
        private final List<Object> values = new ArrayList<>();
        private byte type = END;

        public List<Object> values() { return java.util.Collections.unmodifiableList(values); }

        public void add(Object value) {
            byte valueType = typeOf(check(value));
            if (values.isEmpty()) type = valueType;
            else if (valueType != type) throw new IllegalArgumentException("Mixed NBT list types");
            values.add(value);
        }

        @Override public boolean equals(Object other) {
            return other instanceof ListTag list && Nbt.equal(values, list.values);
        }

        @Override public int hashCode() { return values.size(); }
    }

    public static Compound readCompressed(InputStream in) throws IOException {
        DataInputStream data = new DataInputStream(new BufferedInputStream(new GZIPInputStream(in)));
        byte type = data.readByte();
        if (type != COMPOUND) throw new IOException("The NBT root is not a compound");
        data.readUTF();
        return (Compound) read(data, COMPOUND, 0);
    }

    public static void writeCompressed(Compound root, OutputStream out) throws IOException {
        GZIPOutputStream gzip = new GZIPOutputStream(out);
        DataOutputStream data = new DataOutputStream(new BufferedOutputStream(gzip));
        data.writeByte(COMPOUND);
        data.writeUTF("");
        write(data, root);
        data.flush();
        gzip.finish();
    }

    private static Object read(DataInput in, byte type, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("NBT is nested too deeply");
        switch (type) {
            case BYTE: return in.readByte();
            case SHORT: return in.readShort();
            case INT: return in.readInt();
            case LONG: return in.readLong();
            case FLOAT: return in.readFloat();
            case DOUBLE: return in.readDouble();
            case BYTE_ARRAY: {
                byte[] bytes = new byte[length(in)];
                in.readFully(bytes);
                return bytes;
            }
            case STRING: return in.readUTF();
            case LIST: {
                byte elementType = in.readByte();
                int size = in.readInt();
                if (size < 0) throw new IOException("Negative NBT list size");
                ListTag list = new ListTag();
                if (size > 0 && elementType == END) throw new IOException("NBT list of end tags");
                for (int i = 0; i < size; i++) list.add(read(in, elementType, depth + 1));
                return list;
            }
            case COMPOUND: {
                Compound compound = new Compound();
                while (true) {
                    byte entryType = in.readByte();
                    if (entryType == END) return compound;
                    String key = in.readUTF();
                    compound.values.put(key, read(in, entryType, depth + 1));
                }
            }
            case INT_ARRAY: {
                int[] ints = new int[length(in)];
                for (int i = 0; i < ints.length; i++) ints[i] = in.readInt();
                return ints;
            }
            case LONG_ARRAY: {
                long[] longs = new long[length(in)];
                for (int i = 0; i < longs.length; i++) longs[i] = in.readLong();
                return longs;
            }
            default: throw new IOException("Unknown NBT tag type " + type);
        }
    }

    private static int length(DataInput in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_ARRAY) throw new IOException("Invalid NBT array length " + length);
        return length;
    }

    private static void write(DataOutput out, Object value) throws IOException {
        if (value instanceof Byte b) out.writeByte(b);
        else if (value instanceof Short s) out.writeShort(s);
        else if (value instanceof Integer i) out.writeInt(i);
        else if (value instanceof Long l) out.writeLong(l);
        else if (value instanceof Float f) out.writeFloat(f);
        else if (value instanceof Double d) out.writeDouble(d);
        else if (value instanceof byte[] bytes) {
            out.writeInt(bytes.length);
            out.write(bytes);
        } else if (value instanceof String s) out.writeUTF(s);
        else if (value instanceof ListTag list) {
            out.writeByte(list.values.isEmpty() ? END : list.type);
            out.writeInt(list.values.size());
            for (Object element : list.values) write(out, element);
        } else if (value instanceof Compound compound) {
            for (Map.Entry<String, Object> entry : compound.values.entrySet()) {
                out.writeByte(typeOf(entry.getValue()));
                out.writeUTF(entry.getKey());
                write(out, entry.getValue());
            }
            out.writeByte(END);
        } else if (value instanceof int[] ints) {
            out.writeInt(ints.length);
            for (int i : ints) out.writeInt(i);
        } else if (value instanceof long[] longs) {
            out.writeInt(longs.length);
            for (long l : longs) out.writeLong(l);
        } else throw new IllegalArgumentException("Not an NBT value: " + value);
    }

    static byte typeOf(Object value) {
        if (value instanceof Byte) return BYTE;
        if (value instanceof Short) return SHORT;
        if (value instanceof Integer) return INT;
        if (value instanceof Long) return LONG;
        if (value instanceof Float) return FLOAT;
        if (value instanceof Double) return DOUBLE;
        if (value instanceof byte[]) return BYTE_ARRAY;
        if (value instanceof String) return STRING;
        if (value instanceof ListTag) return LIST;
        if (value instanceof Compound) return COMPOUND;
        if (value instanceof int[]) return INT_ARRAY;
        if (value instanceof long[]) return LONG_ARRAY;
        throw new IllegalArgumentException("Not an NBT value: " + value);
    }

    private static Object check(Object value) {
        typeOf(value);
        return value;
    }

    private static Object deepCopy(Object value) {
        if (value instanceof Compound compound) return compound.copy();
        if (value instanceof ListTag list) {
            ListTag copy = new ListTag();
            for (Object element : list.values) copy.add(deepCopy(element));
            return copy;
        }
        if (value instanceof byte[] bytes) return bytes.clone();
        if (value instanceof int[] ints) return ints.clone();
        if (value instanceof long[] longs) return longs.clone();
        return value;
    }

    private static boolean equal(Object a, Object b) {
        if (a instanceof byte[] x && b instanceof byte[] y) return java.util.Arrays.equals(x, y);
        if (a instanceof int[] x && b instanceof int[] y) return java.util.Arrays.equals(x, y);
        if (a instanceof long[] x && b instanceof long[] y) return java.util.Arrays.equals(x, y);
        if (a instanceof Map<?, ?> x && b instanceof Map<?, ?> y) {
            if (!x.keySet().equals(y.keySet())) return false;
            for (Object key : x.keySet()) if (!equal(x.get(key), y.get(key))) return false;
            return true;
        }
        if (a instanceof List<?> x && b instanceof List<?> y) {
            if (x.size() != y.size()) return false;
            for (int i = 0; i < x.size(); i++) if (!equal(x.get(i), y.get(i))) return false;
            return true;
        }
        return java.util.Objects.equals(a, b);
    }
}
