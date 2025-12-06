package kafkasql.lang.semantic.bind;

import kafkasql.runtime.*;
import kafkasql.runtime.type.*;
import kafkasql.runtime.value.*;
import kafkasql.lang.diagnostics.DiagnosticCode;
import kafkasql.lang.diagnostics.DiagnosticKind;
import kafkasql.lang.diagnostics.Diagnostics;
import kafkasql.lang.semantic.BindingEnv;
import kafkasql.lang.semantic.factory.LiteralValueFactory;
import kafkasql.lang.syntax.ast.literal.BoolLiteralNode;
import kafkasql.lang.syntax.ast.literal.BytesLiteralNode;
import kafkasql.lang.syntax.ast.literal.EnumLiteralNode;
import kafkasql.lang.syntax.ast.literal.ListLiteralNode;
import kafkasql.lang.syntax.ast.literal.LiteralNode;
import kafkasql.lang.syntax.ast.literal.MapEntryLiteralNode;
import kafkasql.lang.syntax.ast.literal.MapLiteralNode;
import kafkasql.lang.syntax.ast.literal.NullLiteralNode;
import kafkasql.lang.syntax.ast.literal.NumberLiteralNode;
import kafkasql.lang.syntax.ast.literal.StringLiteralNode;
import kafkasql.lang.syntax.ast.literal.StructFieldLiteralNode;
import kafkasql.lang.syntax.ast.literal.StructLiteralNode;
import kafkasql.lang.syntax.ast.literal.UnionLiteralNode;
import kafkasql.lang.syntax.ast.misc.QName;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.*;

public final class LiteralBinder {
    private static final BigDecimal INT8_MIN = BigDecimal.valueOf(Byte.MIN_VALUE);
    private static final BigDecimal INT8_MAX = BigDecimal.valueOf(Byte.MAX_VALUE);
    private static final BigDecimal INT16_MIN = BigDecimal.valueOf(Short.MIN_VALUE);
    private static final BigDecimal INT16_MAX = BigDecimal.valueOf(Short.MAX_VALUE);
    private static final BigDecimal INT32_MIN = BigDecimal.valueOf(Integer.MIN_VALUE);
    private static final BigDecimal INT32_MAX = BigDecimal.valueOf(Integer.MAX_VALUE);
    private static final BigDecimal INT64_MIN = BigDecimal.valueOf(Long.MIN_VALUE);
    private static final BigDecimal INT64_MAX = BigDecimal.valueOf(Long.MAX_VALUE);
    private static final BigDecimal FLOAT32_MIN = BigDecimal.valueOf(Float.MIN_VALUE);
    private static final BigDecimal FLOAT32_MAX = BigDecimal.valueOf(Float.MAX_VALUE);
    private static final BigDecimal FLOAT64_MIN = BigDecimal.valueOf(Double.MIN_VALUE);
    private static final BigDecimal FLOAT64_MAX = BigDecimal.valueOf(Double.MAX_VALUE);


    private LiteralBinder() {}

    // ========================================================================
    //  A) Untyped binding
    // ========================================================================


    // ========================================================================
    //  B) Typed binding
    // ========================================================================

    public static Object bindLiteralAsType(
        LiteralNode node,
        AnyType expectedType,
        Diagnostics diags,
        BindingEnv bindings
    ) {
        return switch (expectedType) {

            case PrimitiveType p ->
                bindPrimitiveLiteral(node, p, diags);

            case ScalarType s -> {
                // Bind the primitive value first
                Object value = bindPrimitiveLiteral(node, s.primitive(), diags);
                
                // Validate CHECK constraint if present
                if (value != null && s.check().isPresent()) {
                    var check = s.check().get();
                    var env = java.util.Map.of("value", value);
                    try {
                        Object result = kafkasql.runtime.expr.ExpressionEvaluator.evaluate(check.expr(), env);
                        if (!(result instanceof Boolean) || !((Boolean) result)) {
                            diags.error(
                                node.range(),
                                DiagnosticKind.SEMANTIC,
                                DiagnosticCode.INVALID_CHECK_CONSTRAINT,
                                "CHECK constraint failed for scalar type " + s.fqn() + 
                                ": value " + value + " does not satisfy constraint"
                            );
                            yield null;
                        }
                    } catch (Exception e) {
                        diags.error(
                            node.range(),
                            DiagnosticKind.SEMANTIC,
                            DiagnosticCode.INVALID_CHECK_CONSTRAINT,
                            "Error evaluating CHECK constraint: " + e.getMessage()
                        );
                        yield null;
                    }
                }
                yield value;
            }

            case EnumType e ->
                bindEnumLiteral(node, e, diags);

            case StructType st ->
                bindStructLiteral(node, st, diags, bindings);

            case UnionType ut ->
                bindUnionLiteral(node, ut, diags, bindings);

            case ListType lt ->
                bindListLiteral(node, lt, diags, bindings);

            case MapType mt ->
                bindMapLiteral(node, mt, diags, bindings);

            case TypeReference tr -> {
                diags.error(
                    node.range(),
                    DiagnosticKind.TYPE,
                    DiagnosticCode.INVALID_TYPE_REF,
                    "Internal: unresolved TypeReference " + tr.fqn()
                );
                yield null;
            }

            case VoidType v -> {
                diags.error(
                    node.range(),
                    DiagnosticKind.SEMANTIC,
                    DiagnosticCode.INVALID_LITERAL,
                    "Literal not allowed for VOID"
                );
                yield null;
            }
        };
    }

    public static Object bindLiteralTyped(
        LiteralNode node,
        AnyType expectedType,
        Diagnostics diags
    ) {
        // TODO: What is the empty bindings for?
        return bindLiteralTyped(node, expectedType, diags, new BindingEnv());
    }

    public static Object bindLiteralTyped(
        LiteralNode node,
        AnyType expectedType,
        Diagnostics diags,
        BindingEnv bindings
    ) {
        return switch (expectedType) {

            case PrimitiveType p      -> bindPrimitiveLiteral(node, p, diags);
            case ScalarType s -> {
                // Bind the primitive value first
                Object value = bindPrimitiveLiteral(node, s.primitive(), diags);
                
                // Validate CHECK constraint if present
                if (value != null && s.check().isPresent()) {
                    var check = s.check().get();
                    var env = java.util.Map.of("value", value);
                    try {
                        Object result = kafkasql.runtime.expr.ExpressionEvaluator.evaluate(check.expr(), env);
                        if (!(result instanceof Boolean) || !((Boolean) result)) {
                            diags.error(
                                node.range(),
                                DiagnosticKind.SEMANTIC,
                                DiagnosticCode.INVALID_CHECK_CONSTRAINT,
                                "CHECK constraint failed for scalar type " + s.fqn() + 
                                ": value " + value + " does not satisfy constraint"
                            );
                            yield null;
                        }
                    } catch (Exception e) {
                        diags.error(
                            node.range(),
                            DiagnosticKind.SEMANTIC,
                            DiagnosticCode.INVALID_CHECK_CONSTRAINT,
                            "Error evaluating CHECK constraint: " + e.getMessage()
                        );
                        yield null;
                    }
                }
                yield value;
            }
            case EnumType e           -> bindEnumLiteral(node, e, diags);
            case StructType st        -> bindStructLiteral(node, st, diags, bindings);
            case UnionType ut         -> bindUnionLiteral(node, ut, diags, bindings);
            case ListType lt          -> bindListLiteral(node, lt, diags, bindings);
            case MapType mt           -> bindMapLiteral(node, mt, diags, bindings);

            case TypeReference tr     -> {
                diags.error(
                    node.range(),
                    DiagnosticKind.TYPE,
                    DiagnosticCode.INVALID_TYPE_REF,
                    "Internal: unresolved TypeReference " + tr.fqn()
                );
                yield null;
            }

            case VoidType v -> {
                diags.error(
                    node.range(),
                    DiagnosticKind.SEMANTIC,
                    DiagnosticCode.INVALID_LITERAL,
                    "Literal not allowed for VOID"
                );
                yield null;
            }
        };
    }

    // ========================================================================
    // Primitive binding
    // ========================================================================

    private static Object bindPrimitiveLiteral(
        LiteralNode node,
        PrimitiveType type,
        Diagnostics diags
    ) {
        if (node instanceof NullLiteralNode) {
            return null;
        }

        return switch (type.kind()) {

            case PrimitiveKind.BOOLEAN -> bindBool(node, diags);

            case PrimitiveKind.INT8 -> bindInt8(node, diags);
            case PrimitiveKind.INT16 -> bindInt16(node, diags);
            case PrimitiveKind.INT32 -> bindInt32(node, diags);
            case PrimitiveKind.INT64 -> bindInt64(node, diags);
            case PrimitiveKind.FLOAT32 -> bindFloat(node, diags);
            case PrimitiveKind.FLOAT64 -> bindFloat64(node, diags);
            case PrimitiveKind.UUID -> bindUuid(node, diags);
            case PrimitiveKind.DATE -> bindDate(node, diags);

            case PrimitiveKind.STRING -> bindString(
                node,
                type.length(),
                diags
            );
            case PrimitiveKind.BYTES -> bindBytes(
                node,
                type.length(),
                diags
            );

            case PrimitiveKind.TIME -> bindTime(
                node,
                type.precision(),diags
            );
            case PrimitiveKind.TIMESTAMP -> bindTimestamp(
                node,
                type.precision(),
                diags
            );
            case PrimitiveKind.TIMESTAMP_TZ -> bindTimestampTz(
                node,
                type.precision(),
                diags
            );

            case PrimitiveKind.DECIMAL -> bindDecimal(
                node,
                type.precision(),
                type.scale(),
                diags
            );
        };
    }

    // Boolean
    private static Boolean bindBool(
        LiteralNode node,
        Diagnostics diags
    ) {
        if (node instanceof BoolLiteralNode b)
            return b.value();
        fail(node, diags, "BOOLEAN");
        return null;
    }

    private static BigDecimal bindNumber(
        LiteralNode node,
        Diagnostics diags
    ) {
        if ((node instanceof NumberLiteralNode n))
            return LiteralValueFactory.parseNumber(n.text());
        fail(node, diags, "number");
        return null;
    }

    private static Byte bindInt8(
        LiteralNode node,
        Diagnostics diags
    ) {
        BigDecimal bd = bindNumber(node, diags);
        if (bd == null) return null;
        if (bd.compareTo(INT8_MIN) < 0 || bd.compareTo(INT8_MAX) > 0) {
            diags.error(
                node.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.OUT_OF_RANGE_LITERAL,
                "Integer literal " + bd + " out of range for INT8"
            );
            return null;
        }
        return bd.byteValue();
    }

    private static Short bindInt16(
        LiteralNode node,
        Diagnostics diags
    ) {
        BigDecimal bd = bindNumber(node, diags);
        if (bd == null) return null;
        if (bd.compareTo(INT16_MIN) < 0 || bd.compareTo(INT16_MAX) > 0) {
            diags.error(
                node.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.OUT_OF_RANGE_LITERAL,
                "Integer literal " + bd + " out of range for INT16"
            );
            return null;
        }
        return bd.shortValue();
    }

    private static Integer bindInt32(
        LiteralNode node,
        Diagnostics diags
    ) {
        BigDecimal bd = bindNumber(node, diags);
        if (bd == null) return null;
        if (bd.compareTo(INT32_MIN) < 0 || bd.compareTo(INT32_MAX) > 0) {
            diags.error(
                node.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.OUT_OF_RANGE_LITERAL,
                "Integer literal " + bd + " out of range for INT32"
            );
            return null;
        }
        return bd.intValue();
    }

    private static Long bindInt64(
        LiteralNode node,
        Diagnostics diags
    ) {
        BigDecimal bd = bindNumber(node, diags);
        if (bd == null) return null;
        if (bd.scale() > 0) {
            diags.error(
                node.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.INVALID_LITERAL,
                "Expected integer literal but found decimal: " + bd
            );
            return null;
        }
        if (bd.compareTo(INT64_MIN) < 0 || bd.compareTo(INT64_MAX) > 0) {
            diags.error(
                node.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.OUT_OF_RANGE_LITERAL,
                "Integer literal " + bd + " out of range for LONG"
            );
            return null;
        }
        return bd.longValue();
    }

    private static Float bindFloat(
        LiteralNode node,
        Diagnostics diags
    ) {
        BigDecimal bd = bindNumber(node, diags);
        if (bd == null) return null;
        if (bd.compareTo(FLOAT32_MIN) < 0 || bd.compareTo(FLOAT32_MAX) > 0) {
            diags.error(
                node.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.OUT_OF_RANGE_LITERAL,
                "Float literal " + bd + " out of range for FLOAT32"
            );
            return null;
        }
        return bd.floatValue();
    }

    private static Double bindFloat64(
        LiteralNode node,
        Diagnostics diags
    ) {
        BigDecimal bd = bindNumber(node, diags);
        if (bd == null) return null;
        if (bd.compareTo(FLOAT64_MIN) < 0 || bd.compareTo(FLOAT64_MAX) > 0) {
            diags.error(
                node.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.OUT_OF_RANGE_LITERAL,
                "Float literal " + bd + " out of range for FLOAT64"
            );
            return null;
        }
        return bd.doubleValue();
    }

    private static BigDecimal bindDecimal(
        LiteralNode node,
        Byte p,
        Byte s,
        Diagnostics diags
    ) {
        BigDecimal bd = bindNumber(node, diags);
        if (bd == null) return null;
        if (bd.precision() > 38) {
            diags.error(
                node.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.OUT_OF_RANGE_LITERAL,
                "Decimal literal " + bd + " exceeds max precision of 38"
            );
            return null;
        }
        if (s > p) {
            diags.error(
                node.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.OUT_OF_RANGE_LITERAL,
                "Decimal literal scale " + s + " exceeds precision " + p
            );
            return null;
        }
        if (bd.scale() > s) {
            diags.error(
                node.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.OUT_OF_RANGE_LITERAL,
                "Decimal literal " + bd + " exceeds scale " + s
            );
            return null;
        }
        return bd.setScale(s, RoundingMode.UNNECESSARY);
    }

    private static String bindString(
        LiteralNode node,
        int l,
        Diagnostics diags
    ) {
        if (node instanceof StringLiteralNode s) {
            if (l != -1 && s.value().length() > l) {
                diags.error(
                    node.range(),
                    DiagnosticKind.SEMANTIC,
                    DiagnosticCode.OUT_OF_RANGE_LITERAL,
                    "String literal length " + s.value().length() +
                    " exceeds defined length " + l
                );
            }
            return s.value();
        }
        fail(node, diags, "STRING");
        return null;
    }

    private static byte[] bindBytes(
        LiteralNode node,
        int l,
        Diagnostics diags
    ) {
        if (node instanceof BytesLiteralNode b) {
            var bytes = LiteralValueFactory.decodeBytes(b.text());
            if (l != -1 && bytes.length > l) {
                diags.error(
                    node.range(),
                    DiagnosticKind.SEMANTIC,
                    DiagnosticCode.OUT_OF_RANGE_LITERAL,
                    "BYTES literal length " + bytes.length +
                    " exceeds defined length " + l
                );
            }
        }
        fail(node, diags, "BYTES");
        return null;
    }

    private static UUID bindUuid(
        LiteralNode node,
        Diagnostics diags
    ) {
        if ((node instanceof StringLiteralNode s)) {
            var text = s.value();
            try {
                return java.util.UUID.fromString(text);
            } catch (Exception ex) {
                diags.error(
                    node.range(),
                    DiagnosticKind.SEMANTIC,
                    DiagnosticCode.INVALID_LITERAL,
                    "Invalid UUID literal: " + text
                );
                return null;
            }
        }
        fail(node, diags, "UUID");
        return null;
    }

    private static LocalDate bindDate(
        LiteralNode node,
        Diagnostics diags
    ) {
        if ((node instanceof StringLiteralNode s)) {
            try {
                return LocalDate.parse(s.value());
            } catch (Exception ex) {
                diags.error(
                    node.range(),
                    DiagnosticKind.SEMANTIC,
                    DiagnosticCode.INVALID_LITERAL,
                    "Invalid DATE literal: " + s.value()
                );
                return null;
            }
        }
        fail(node, diags, "DATE");
        return null;
    }

    private static LocalTime bindTime(
        LiteralNode node,
        byte p,
        Diagnostics diags
    ) {
        if ((node instanceof StringLiteralNode s)) {
            String text = s.value();
            int litPrecision = fractionalPrecision(text);
            if (litPrecision > p) {
                diags.error(
                    node.range(),
                    DiagnosticKind.SEMANTIC,
                    DiagnosticCode.OUT_OF_RANGE_LITERAL,
                    "TIME literal precision " + litPrecision +
                        " exceeds TIME(" + p + ")"
                );
                return null;
            }

            try {
                return LocalTime.parse(text);
            } catch (Exception ex) {
                diags.error(
                    node.range(),
                    DiagnosticKind.SEMANTIC,
                    DiagnosticCode.INVALID_LITERAL,
                    "Invalid TIME literal: " + text
                );
                return null;
            }
            
        }
        fail(node, diags, "TIME");
        return null;
    }

    private static LocalDateTime bindTimestamp(
        LiteralNode node,
        byte p,
        Diagnostics diags
    ) {
        if ((node instanceof StringLiteralNode s)) {
            String text = s.value();
            int litPrecision = fractionalPrecision(text);
            if (litPrecision > p) {
                diags.error(
                    node.range(),
                    DiagnosticKind.SEMANTIC,
                    DiagnosticCode.OUT_OF_RANGE_LITERAL,
                    "TIMESTAMP literal precision " + litPrecision +
                        " exceeds TIMESTAMP(" + p + ")"
                );
                return null;
            }

            try {
                return LocalDateTime.parse(text);
            } catch (Exception ex) {
                diags.error(
                    node.range(),
                    DiagnosticKind.SEMANTIC,
                    DiagnosticCode.INVALID_LITERAL,
                    "Invalid TIMESTAMP literal: " + text
                );
                return null;
            }
        }
        fail(node, diags, "TIMESTAMP");
        return null;
    }

    private static ZonedDateTime bindTimestampTz(
        LiteralNode node,
        byte p,
        Diagnostics diags
    ) {
        if ((node instanceof StringLiteralNode s)) {
            String text = s.value();
            int litPrecision = fractionalPrecision(text);
            if (litPrecision > p) {
                diags.error(
                    node.range(),
                    DiagnosticKind.SEMANTIC,
                    DiagnosticCode.OUT_OF_RANGE_LITERAL,
                    "TIMESTAMPTZ literal precision " + litPrecision +
                        " exceeds TIMESTAMPTZ(" + p + ")"
                );
                return null;
            }

            try {
                return ZonedDateTime.parse(text);
            } catch (Exception ex) {
                diags.error(
                    node.range(),
                    DiagnosticKind.SEMANTIC,
                    DiagnosticCode.INVALID_LITERAL,
                    "Invalid TIMESTAMPTZ literal: " + text
                );
                return null;
            }
        }
        fail(node, diags, "TIMESTAMPTZ");
        return null;
    }

    // ========================================================================
    // Composite literals
    // ========================================================================

    private static Object bindEnumLiteral(
        LiteralNode node,
        EnumType type,
        Diagnostics diags
    ) {
        if (!(node instanceof EnumLiteralNode e)) {
            fail(node, diags, "ENUM(" + type.fqn().toString() + ")");
            return null;
        }

        String symbolName = e.symbol().name();
        EnumTypeSymbol symbol = type.symbols().stream()
            .filter(s -> s.name().equals(symbolName))
            .findFirst()
            .orElse(null);

        if (symbol == null) {
            diags.error(
                node.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.INVALID_LITERAL,
                "Unknown enum symbol '" + symbolName + "' for " + type.fqn().toString()
            );
            return null;
        }

        return new EnumValue(type, symbol);
    }

    private static Object bindStructLiteral(
        LiteralNode node,
        StructType type,
        Diagnostics diags,
        BindingEnv bindings
    ) {
        // Accept empty MapLiteralNode as empty struct (type-based interpretation)
        if (node instanceof MapLiteralNode ml && ml.entries().isEmpty()) {
            return new StructValue(type, new LinkedHashMap<>());
        }
        
        if (!(node instanceof StructLiteralNode st)) {
            fail(node, diags, "STRUCT(" + type.fqn().toString() + ")");
            return null;
        }

        LinkedHashMap<String,Object> values = new LinkedHashMap<>();

        for (StructFieldLiteralNode f : st.fields()) {
            String fieldName = f.name().name();
            StructTypeField field = type.fields().get(fieldName);

            if (field == null) {
                diags.error(
                    f.range(),
                    DiagnosticKind.SEMANTIC,
                    DiagnosticCode.UNKNOWN_FIELD,
                    "Unknown field '" + fieldName + "' on struct " + type.fqn().toString()
                );
                continue;
            }

            Object v = bindLiteralTyped(
                f.value(),
                field.type(),
                diags,
                bindings
            );

            values.put(fieldName, v);
        }

        // NOTE: We do NOT validate missing fields here because:
        // 1. Defaults are stored separately in BindingEnv, not in StructType
        // 2. The field.defaultValue() is always Optional.empty() at this point
        // 3. Validation of required fields should happen at a higher level
        //    (e.g., in StatementBinder when processing WRITE statements)

        return new StructValue(type, values);
    }

    private static Object bindUnionLiteral(
        LiteralNode node,
        UnionType type,
        Diagnostics diags,
        BindingEnv bindings
    ) {
        if (!(node instanceof UnionLiteralNode ul)) {
            fail(node, diags, "UNION(" + type.fqn().toString() + ")");
            return null;
        }

        String memberName = ul.memberName().name();
        UnionTypeMember member = type.members().get(memberName);

        if (member == null) {
            diags.error(
                ul.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.UNKNOWN_FIELD,
                "Unknown union member '" + memberName + "' for " + type.fqn().toString()
            );
            return null;
        }

        Object value = bindLiteralTyped(
            ul.value(),
            member.typ(),
            diags,
            bindings
        );

        return new UnionValue(type, memberName, value);
    }

    private static Object bindListLiteral(
        LiteralNode node,
        ListType type,
        Diagnostics diags,
        BindingEnv bindings
    ) {
        if (!(node instanceof ListLiteralNode ll)) {
            fail(node, diags, "LIST");
            return null;
        }

        List<Object> result = new ArrayList<>(ll.elements().size());

        for (LiteralNode n : ll.elements()) {
            result.add(
                bindLiteralAsType(
                    n,
                    type.item(),
                    diags,
                    bindings
                )
            );
        }

        return result;
    }

    private static Object bindMapLiteral(
        LiteralNode node,
        MapType type,
        Diagnostics diags,
        BindingEnv bindings
    ) {
        // Accept empty StructLiteralNode as empty map (type-based interpretation)
        if (node instanceof StructLiteralNode st && st.fields().isEmpty()) {
            return new LinkedHashMap<>();
        }
        
        if (!(node instanceof MapLiteralNode ml)) {
            fail(node, diags, "MAP");
            return null;
        }

        LinkedHashMap<Object,Object> result = new LinkedHashMap<>();

        for (MapEntryLiteralNode e : ml.entries()) {

            Object key = bindPrimitiveLiteral(
                e.key(),
                type.key(),
                diags
            );

            Object value = bindLiteralAsType(
                e.value(),
                type.value(),
                diags,
                bindings
            );

            if (key != null)
                result.put(key, value);
        }

        return result;
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static int fractionalPrecision(String text) {
        int dot = text.indexOf('.');
        if (dot < 0) return 0;

        int i = dot + 1;
        int count = 0;

        while (i < text.length()) {
            char c = text.charAt(i);
            if (!Character.isDigit(c)) break;
            count++;
            i++;
        }

        return count;
    }

    private static void fail(
        LiteralNode node,
        Diagnostics diags,
        String expected
    ) {
        diags.error(
            node.range(),
            DiagnosticKind.SEMANTIC,
            DiagnosticCode.INVALID_LITERAL,
            "Literal " + node.getClass().getSimpleName() +
                " is not compatible with expected " + expected
        );
    }

    public static Name toName(QName qname) {
        return Name.of(qname.context(), qname.name());
    }
}