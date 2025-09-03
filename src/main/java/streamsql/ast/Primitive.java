package streamsql.ast;

import java.util.HashMap;

public class Primitive {
    private static final HashMap<java.lang.String, PrimitiveType> TYPES = new HashMap<>();

    private static final Bool BOOL = new Bool();
    private static final Int8 INT8 = new Int8();
    private static final UInt8 UINT8 = new UInt8();
    private static final Int16 INT16 = new Int16();
    private static final UInt16 UINT16 = new UInt16();
    private static final Int32 INT32 = new Int32();
    private static final UInt32 UINT32 = new UInt32();
    private static final Int64 INT64 = new Int64();
    private static final UInt64 UINT64 = new UInt64();
    private static final Single SINGLE = new Single();
    private static final Double DOUBLE = new Double();
    private static final Primitive.String STRING = new Primitive.String();
    private static final Bytes BYTES = new Bytes();
    private static final Uuid UUID = new Uuid();
    private static final Date DATE = new Date();

    public static final class Bool implements PrimitiveType { protected Bool() {} }
    public static final class Int8 implements PrimitiveType { private Int8() {} }
    public static final class UInt8 implements PrimitiveType { private UInt8() {} }
    public static final class Int16 implements PrimitiveType { private Int16() {} }
    public static final class UInt16 implements PrimitiveType { private UInt16() {} }
    public static final class Int32 implements PrimitiveType { private Int32() {} }
    public static final class UInt32 implements PrimitiveType { private UInt32() {} }
    public static final class Int64 implements PrimitiveType { private Int64() {} }
    public static final class UInt64 implements PrimitiveType { private UInt64() {} }
    public static final class Single implements PrimitiveType { private Single() {} }
    public static final class Double implements PrimitiveType { private Double() {} }
    public static final class Decimal implements PrimitiveType {
        private final int precision;
        private final int scale;
        public Decimal(int precision, int scale) {
            this.precision = precision;
            this.scale = scale;
        }
        public int precision() { return precision; }
        public int scale() { return scale; }
    }
    public static final class String implements PrimitiveType {}
    public static final class FString implements PrimitiveType {
        private final int size;
        public FString(int size) {
            this.size = size;
        }
        public int size() { return size; }
    }
    public static final class Bytes implements PrimitiveType {}
    public static final class FBytes implements PrimitiveType {
        private final int size;
        public FBytes(int size) {
            this.size = size;
        }
        public int size() { return size; }
    }
    public static final class Uuid implements PrimitiveType {}
    public static final class Date implements PrimitiveType {}
    public static final class Time implements PrimitiveType {
        private final int precision;
        public Time(int precision) {
            this.precision = precision;
        }
        public int precision() { return precision; }
    }
    public static final class Timestamp implements PrimitiveType {
        private final int precision;
        public Timestamp(int precision) {
            this.precision = precision;
        }
        public int precision() { return precision; }
    }
    public static final class TimestampTz implements PrimitiveType {
        private final int precision;
        public TimestampTz(int precision) {
            this.precision = precision;
        }
        public int precision() { return precision; }
    }

    public static Bool BOOL() { return BOOL; }
    public static Int8 INT8() { return INT8; }
    public static UInt8 UINT8() { return UINT8; }
    public static Int16 INT16() { return INT16; }
    public static UInt16 UINT16() { return UINT16; }
    public static Int32 INT32() { return INT32; }
    public static UInt32 UINT32() { return UINT32; }
    public static Int64 INT64() { return INT64; }
    public static UInt64 UINT64() { return UINT64; }
    public static Single SINGLE() { return SINGLE; }
    public static Double DOUBLE() { return DOUBLE; }
    public static Decimal DECIMAL(int precision, int scale) {
        var key = "d_" + precision + "_" + scale;
        if(TYPES.containsKey(key))
            return (Decimal) TYPES.get(key);
        var decimal = new Decimal(precision, scale);
        TYPES.put(key, decimal);
        return decimal;
    }
    public static Primitive.String STRING() { return STRING; }
    public static FString FSTRING(int size) {
        var key = "s_" + size;
        if(TYPES.containsKey(key))
            return (FString) TYPES.get(key);
        var fstring = new FString(size);
        TYPES.put(key, fstring);
        return fstring;
    }
    public static Bytes BYTES() { return BYTES; }
    public static FBytes FBYTES(int size) {
        var key = "b_" + size;
        if(TYPES.containsKey(key))
            return (FBytes) TYPES.get(key);
        var fbytes = new FBytes(size);
        TYPES.put(key, fbytes);
        return fbytes;
    }
    public static Uuid UUID() { return UUID; }
    public static Date DATE() { return DATE; }
    public static Time TIME(int precision) {
        var key = "t_" + precision;
        if(TYPES.containsKey(key))
            return (Time) TYPES.get(key);
        var time = new Time(precision);
        TYPES.put(key, time);
        return time;
    }
    public static Timestamp TIMESTAMP(int precision) {
        var key = "ts_" + precision;
        if(TYPES.containsKey(key))
            return (Timestamp) TYPES.get(key);
        var timestamp = new Timestamp(precision);
        TYPES.put(key, timestamp);
        return timestamp;
    }
    public static TimestampTz TIMESTAMP_TZ(int precision) {
        var key = "tsz_" + precision;
        if(TYPES.containsKey(key))
            return (TimestampTz) TYPES.get(key);
        var timestampTz = new TimestampTz(precision);
        TYPES.put(key, timestampTz);
        return timestampTz;
    }
}
