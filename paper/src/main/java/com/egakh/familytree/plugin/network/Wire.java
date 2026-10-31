package com.egakh.familytree.plugin.network;

import java.io.ByteArrayOutputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Minecraft's {@code FriendlyByteBuf} encodings: big-endian numbers, VarInts, UUIDs as two longs, and length-prefixed UTF-8. */
final class Wire {
    private Wire() {}

    static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        Writer bool(boolean value) { out.write(value ? 1 : 0); return this; }

        Writer varInt(int value) {
            while ((value & ~0x7F) != 0) {
                out.write(value & 0x7F | 0x80);
                value >>>= 7;
            }
            out.write(value);
            return this;
        }

        Writer longValue(long value) {
            for (int shift = 56; shift >= 0; shift -= 8) out.write((int) (value >>> shift) & 0xFF);
            return this;
        }

        Writer doubleValue(double value) { return longValue(Double.doubleToRawLongBits(value)); }

        Writer uuid(UUID id) { return longValue(id.getMostSignificantBits()).longValue(id.getLeastSignificantBits()); }

        Writer string(String value, int maxLength) {
            if (value.length() > maxLength) throw new IllegalArgumentException("String too big (was " + value.length() + " characters, max " + maxLength + ")");
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > maxLength * 3) throw new IllegalArgumentException("String too big (was " + bytes.length + " bytes encoded)");
            varInt(bytes.length);
            out.write(bytes, 0, bytes.length);
            return this;
        }

        Writer string(String value) { return string(value, Short.MAX_VALUE); }

        int size() { return out.size(); }
        byte[] bytes() { return out.toByteArray(); }
    }

    static final class Reader {
        private final ByteBuffer in;

        Reader(byte[] bytes) { in = ByteBuffer.wrap(bytes); }

        boolean bool() { return in.get() != 0; }

        int varInt() {
            int value = 0;
            for (int shift = 0; shift < 35; shift += 7) {
                byte b = in.get();
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) return value;
            }
            throw new IllegalArgumentException("VarInt too big");
        }

        long longValue() { return in.getLong(); }
        double doubleValue() { return in.getDouble(); }
        UUID uuid() { return new UUID(in.getLong(), in.getLong()); }

        String string(int maxLength) {
            int length = varInt();
            if (length > maxLength * 3 || length < 0) throw new IllegalArgumentException("String too long");
            if (length > in.remaining()) throw new BufferUnderflowException();
            byte[] bytes = new byte[length];
            in.get(bytes);
            String value = new String(bytes, StandardCharsets.UTF_8);
            if (value.length() > maxLength) throw new IllegalArgumentException("String too long");
            return value;
        }

        String string() { return string(Short.MAX_VALUE); }
        int remaining() { return in.remaining(); }
    }
}
