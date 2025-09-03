package streamsql.ast;

public sealed interface PrimitiveType extends DataType
    permits Primitive.Bool, Primitive.Int8, Primitive.UInt8, Primitive.Int16, Primitive.UInt16, Primitive.Int32, Primitive.UInt32, Primitive.Int64, Primitive.UInt64,
            Primitive.Single, Primitive.Double, Primitive.Decimal, Primitive.String, Primitive.FString, Primitive.Bytes, Primitive.FBytes, Primitive.Uuid, Primitive.Date,
            Primitive.Time, Primitive.Timestamp, Primitive.TimestampTz {}
