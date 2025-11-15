package kafkasql.io.codec;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;

public final class Encoder {
    private Encoder() { }
    public static void writeBool(OutputStream out, boolean v) throws IOException {
        out.write(v ? (byte)1 : (byte)0);
    }
    public static void writeInt8(OutputStream out, byte v) throws IOException {
        out.write(v);
    }
    public static void writeInt16(OutputStream out, short v) throws IOException {
        out.write((v >> 8) & 0xFF);
        out.write(v & 0xFF);
    }
    public static void writeInt32(OutputStream out, int v) throws IOException {
        out.write((v >> 24) & 0xFF);
        out.write((v >> 16) & 0xFF);
        out.write((v >> 8) & 0xFF);
        out.write(v & 0xFF);
    }
    public static void writeInt64(OutputStream out, long v) throws IOException {
        out.write((byte)((v >> 56) & 0xFF));
        out.write((byte)((v >> 48) & 0xFF));
        out.write((byte)((v >> 40) & 0xFF));
        out.write((byte)((v >> 32) & 0xFF));
        out.write((byte)((v >> 24) & 0xFF));
        out.write((byte)((v >> 16) & 0xFF));
        out.write((byte)((v >> 8) & 0xFF));
        out.write((byte)(v & 0xFF));
    }
    public static void writeVarInt64(OutputStream out, long v) throws IOException {
        while ((v & ~0x7FL) != 0L) {
            out.write((byte)((v & 0x7F) | 0x80));
            v >>>= 7;
        }
        out.write((byte)(v & 0x7F));
    }
    public static void writeVarInt32(OutputStream out, int v) throws IOException {
        writeInt64(out, v);
    }
    public static void writeVarInt16(OutputStream out, short v) throws IOException {
        writeInt64(out, v);
    }
    public static void writeFloat32(OutputStream out, float v) throws IOException {
        writeInt32(out, Float.floatToIntBits(v));
    }
    public static void writeFloat64(OutputStream out, double v) throws IOException {
        writeInt64(out, Double.doubleToLongBits(v));
    }
    public static void writeString(OutputStream out, String v) throws IOException {
        var bytes = v.getBytes();
        writeVarInt32(out, bytes.length);
        writeBytes(out, bytes);
    }
    public static void writeChars(OutputStream out, String v) throws IOException {
        var bytes = v.getBytes();
        writeBytes(out, bytes);
    }
    public static void writeBytes(OutputStream out, byte[] v) throws IOException {
        writeVarInt32(out, v.length);
        out.write(v);
    }
    public static void writeFixed(OutputStream out, byte[] v) throws IOException {
        out.write(v);
    }
    public static void writeDecimal(OutputStream out, BigDecimal v) throws IOException {
        var unscaled = v.unscaledValue().toByteArray();
        writeBytes(out, unscaled);
    }
    public static void writeUUID(OutputStream out, java.util.UUID v) throws IOException {
        writeInt64(out, v.getMostSignificantBits());
        writeInt64(out, v.getLeastSignificantBits());
    }
    public static void writeDate(OutputStream out, Date v) throws IOException {
        writeInt32(out, (int) (v.getTime() / 1000));
    }
    public static void writeTime(OutputStream out, LocalTime v) throws IOException {
        writeInt64(out, v.toNanoOfDay() / 1000000);
    }
    public static void writeTimestamp(OutputStream out, LocalDateTime v) throws IOException {
        writeInt64(out, v.toInstant(ZoneOffset.UTC).toEpochMilli());
    }
    public static void writeTimestampTz(OutputStream out, ZonedDateTime v) throws IOException {
        writeInt64(out, v.toInstant().toEpochMilli());
    }
}
