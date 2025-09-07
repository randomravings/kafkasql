package streamsql.ast;

public sealed interface PrimitiveType extends DataType
    permits BoolT, Int8T, UInt8T, Int16T, UInt16T, Int32T, UInt32T, Int64T, UInt64T,
            Float32T, Float64T, DecimalT, StringT, FStringT, BytesT, FBytesT, UuidT, DateT,
            TimeT, TimestampT, TimestampTzT {}
