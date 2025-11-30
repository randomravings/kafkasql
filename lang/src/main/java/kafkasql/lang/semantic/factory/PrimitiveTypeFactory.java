package kafkasql.lang.semantic.factory;

import kafkasql.runtime.type.*;
import kafkasql.lang.syntax.ast.type.PrimitiveTypeNode;

public final class PrimitiveTypeFactory {

    private PrimitiveTypeFactory() {}

    public static PrimitiveType fromAst(PrimitiveTypeNode ast) {
        return switch (ast.kind()) {

            // Simple Types
            case BOOL -> PrimitiveType.bool();
            case INT8 -> PrimitiveType.int8();
            case INT16 -> PrimitiveType.int16();
            case INT32 -> PrimitiveType.int32();
            case INT64 -> PrimitiveType.int64();
            case FLOAT32 -> PrimitiveType.float32();
            case FLOAT64 -> PrimitiveType.float64();
            case UUID  -> PrimitiveType.uuid();
            case DATE  -> PrimitiveType.date();

            // Optional Length-based Types
            case STRING -> {
                if (ast.hasLength())
                    yield PrimitiveType.string(ast.length().intValue());
                else
                    yield PrimitiveType.string();
            }
            case BYTES -> {
                if (ast.hasLength())
                    yield PrimitiveType.bytes(ast.length().intValue());
                else
                    yield PrimitiveType.bytes();
            }

            // Precision-based Types
            case TIME  -> PrimitiveType.time(
                ast.precision().byteValue()
            );
            case TIMESTAMP  -> PrimitiveType.timestamp(
                ast.precision().byteValue()
            );
            case TIMESTAMP_TZ  -> PrimitiveType.timestampTz(
                ast.precision().byteValue()
            );

            // Precision/Scale-based Types
            case DECIMAL  -> PrimitiveType.decimal(
                ast.precision().byteValue(),
                ast.scale().byteValue()
            );
        };
    }
}
