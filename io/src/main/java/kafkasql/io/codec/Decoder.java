package kafkasql.io.codec;

import java.io.InputStream;
import java.util.UUID;

public final class Decoder {
    private Decoder() { }
    private static final int EOF = -1;
    private static byte next(InputStream in) throws Exception {
        int b = in.read();
        if (b == EOF)
            throw new Exception("Unexpected end of stream");
        return (byte)b;
    }
    private static byte[] nextN(InputStream in, int n) throws Exception {
        byte[] bytes = in.readNBytes(n);
        if (bytes.length != n)
            throw new Exception("Unexpected end of stream");
        return bytes;
    }
    public static boolean decodeBoolean(InputStream in) throws Exception {
        return next(in) != 0;
    }
    public static byte decodeInt8(InputStream in) throws Exception {
        return (byte)next(in);
    }
    public static short decodeInt16(InputStream in) throws Exception {
        var bs = nextN(in, 2);
        return (short)((bs[0] << 8) | (bs[1] & 0xFF));
    }
    public static int decodeInt32(InputStream in) throws Exception {
        var bs = nextN(in, 4);
        return (bs[0] << 24) | (bs[1] << 16) | (bs[2] << 8) | (bs[3] & 0xFF);
    }
    public static long decodeInt64(InputStream in) throws Exception {
        var bs = nextN(in, 8);
        return ((long)(bs[0]) << 56) | ((long)(bs[1] & 0xFF) << 48)
            | ((long)(bs[2] & 0xFF) << 40) | ((long)(bs[3] & 0xFF) << 32)
            | ((long)(bs[4] & 0xFF) << 24) | ((long)(bs[5] & 0xFF) << 16)
            | ((long)(bs[6] & 0xFF) << 8) | ((long)(bs[7] & 0xFF));
    }
    public static long decodeVarInt64(InputStream in) throws Exception {
        long value = 0L;
        int shift = 0;
        while (true) {
            int b = next(in);
            value |= (long)(b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
            if (shift > 63) throw new Exception("VarInt64 is too long");
        }
        return value;
    }
    public static int decodeVarInt32(InputStream in) throws Exception {
        return (int)decodeVarInt64(in);
    }
    public static short decodeVarInt16(InputStream in) throws Exception {
        return (short)decodeVarInt64(in);
    }
    public static float decodeFloat32(InputStream in) throws Exception {
        return Float.intBitsToFloat(decodeInt32(in));
    }
    public static double decodeFloat64(InputStream in) throws Exception {
        return Double.longBitsToDouble(decodeInt64(in));
    }
    public static String decodeString(InputStream in) throws Exception {
        int length = decodeVarInt32(in);
        byte[] bytes = nextN(in, length);
        return new String(bytes);
    }
    public static String decodeChars(InputStream in, int length) throws Exception {
        byte[] bytes = nextN(in, length);
        return new String(bytes);
    }
    public static byte[] decodeBytes(InputStream in) throws Exception {
        int length = decodeVarInt32(in);
        return nextN(in, length);
    }
    public static byte[] decodeFixed(InputStream in, int length) throws Exception {
        return nextN(in, length);
    }
    public static UUID decodeUUID(InputStream in) throws Exception {
        long msb = decodeInt64(in);
        long lsb = decodeInt64(in);
        return new UUID(msb, lsb);
    }
}
