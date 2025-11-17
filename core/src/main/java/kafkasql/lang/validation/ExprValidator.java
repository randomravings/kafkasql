package kafkasql.lang.validation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import kafkasql.lang.Diagnostics;
import kafkasql.lang.ast.*;

public class ExprValidator {
    private static final VoidT VOID_T = new VoidT(Range.NONE);
    public final Diagnostics diags;

    public ExprValidator(Diagnostics diags) {
        this.diags = diags;
    }

    public AnyT trimExpression(Expr e, Map<String, AnyT> symbols) {
        return trimExpression(e, VOID_T, symbols);
    }

    public AnyT trimExpression(Expr e, AnyT constrainingType, Map<String, AnyT> symbols) {
        switch (e) {
            case PostfixExpr pe:
                return trimPostfix(pe, constrainingType, symbols);
            case PrefixExpr ue:
                return trimUnary(ue, constrainingType, symbols);
            case InfixExpr be:
                return trimInfix(be, constrainingType, symbols);
            case Ternary te:
                return trimTernary(te, constrainingType, symbols);
            case IdentifierExpr s:
                AnyT symbolType = symbols != null ? symbols.get(s.name().name()) : null;
                if (symbolType != null)
                    return symbolType;
                diags.error(s.range(), "Unknown identifier '" + s.name().name() + "'");
                break;
            case MemberExpr me:
                return trimMemberExpr(me, constrainingType, symbols);
            case IndexExpr ie:
                return trimIndexExpr(ie, constrainingType, symbols);
            case AnyV av:
                // If we have a constraining type, align the value to it
                if (!(constrainingType instanceof VoidT)) {
                    AnyV aligned = alignValue(av, constrainingType);
                    return getTypeOf(aligned);
                }
                return getTypeOf(av);
            default:
                diags.error(e.range(), "Unsupported expression type '" + e.getClass().getSimpleName() + "'");
                break;
        }
        return constrainingType;
    }

    public AnyT trimPostfix(PostfixExpr e, AnyT constrainingType, Map<String, AnyT> symbols) {
        AnyT operandType = trimExpression(e.expr(), VOID_T, symbols);
        return switch (e.op()) {
            case IS_NULL, IS_NOT_NULL -> new BoolT(e.range());
            case MEMBER, INDEX -> operandType;
        };
    }

    public AnyT trimUnary(PrefixExpr e, AnyT constrainingType, Map<String, AnyT> symbols) {
        AnyT operandType = trimExpression(e.expr(), constrainingType, symbols);
        switch (e.op()) {
            case NOT:
                if (operandType instanceof BoolT)
                    return new BoolT(e.range());
                diags.error(e.range(), "NOT requires BOOL operand");
                break;
            case NEG:
                if (operandType instanceof NumberT)
                    return operandType;
                diags.error(e.range(), "Unary '-' requires NUMBER operand");
                break;
        }
        return constrainingType;
    }

    public AnyT trimInfix(InfixExpr e, AnyT constrainingType, Map<String, AnyT> symbols) {
        AnyT l = trimExpression(e.left(), constrainingType, symbols);
        AnyT r = trimExpression(e.right(), constrainingType, symbols);
        switch (e.op()) {
            case AND, OR, XOR:
                if ((l instanceof BoolT) && (r instanceof BoolT))
                    return new BoolT(l.range());
                diags.error(e.range(), "Logical operators AND/OR require BOOL operands");
                break;
            case ADD, SUB, MUL, DIV, MOD:
                // Arithmetic operations return the numeric type, not bool
                if ((l instanceof NumberT) && (r instanceof NumberT))
                    return l;  // Return the left operand type (could be widened based on both)
                diags.error(e.range(), "Arithmetic operators require NUMBER operands");
                break;
            case EQ, NEQ, LT, LTE, GT, GTE:
                // Comparison operations return bool
                if ((l instanceof NumberT) && (r instanceof NumberT))
                    return new BoolT(l.range());
                if ((l instanceof AlphaT) && (r instanceof AlphaT))
                    return new BoolT(l.range());
                if ((l instanceof TemporalT) && (r instanceof TemporalT))
                    return new BoolT(l.range());
                diags.error(e.range(), "Comparison operators require NUMBER, ALPHA or TEMPORAL operands");
                break;
            case IN:
                if (r instanceof ListT listT) {
                    AnyT itemType = listT.item();
                    if (!areTypesCompatible(itemType, l)) {
                        diags.error(e.left().range(),
                                "IN left operand type '" + typeName(l) + "' does not match list item type '" + typeName(itemType) + "'");
                        break;
                    }
                    return new BoolT(e.range());
                }
                if (r instanceof MapT mapT) {
                    AnyT keyType = mapT.key();
                    if (!areTypesCompatible(keyType, l)) {
                        diags.error(e.left().range(),
                                "IN left operand type '" + typeName(l) + "' does not match map key type '" + typeName(keyType) + "'");
                        break;
                    }
                    return new BoolT(e.range());
                }
                diags.error(e.range(), "IN right operand must be LIST or MAP");
                break;
            case BITAND, BITOR, SHL, SHR:
                if ((l instanceof IntegerT) && (r instanceof IntegerT))
                    return l;
                diags.error(e.range(), "Bitwise operators require INTEGER operands");
                break;
        }
        return constrainingType;
    }

    public AnyT trimTernary(Ternary e, AnyT constrainingType, Map<String, AnyT> symbols) {
        AnyT l = trimExpression(e.left(), constrainingType, symbols);
        AnyT m = trimExpression(e.middle(), constrainingType, symbols);
        AnyT r = trimExpression(e.right(), constrainingType, symbols);
        switch (e.op()) {
            case BETWEEN:
                if (!(l instanceof BoolT))
                    diags.error(e.left().range(), "BETWEEN requires BOOL left operand");
                if (!(m instanceof NumberT) || !(r instanceof NumberT))
                    diags.error(e.middle().range(), "BETWEEN requires NUMBER middle and right operands");
                break;
        }
        return constrainingType;
    }

    public AnyV alignValue(AnyV value, AnyT type) {
        AnyV result = value;
        AnyT constrainingType = type;
        if (type instanceof VoidT)
            constrainingType = getTypeOf(value);

        switch (constrainingType) {
            case PrimitiveT pt:
                if (value instanceof PrimitiveV l)
                    result = alignPrimitive(l, pt);
                else
                    diags.error(value.range(),
                            "Expected literal value for primitive type, got '" + value.getClass().getSimpleName()
                                    + "'");
                break;
            case ComplexT ct:
                diags.error(value.range(), "Cannot assign value to COMPLEX type (yet ...)");
                break;
            case CompositeT ct:
                diags.error(value.range(), "Cannot assign value to COMPOSITE type (yet ...)");
                break;
            case TypeReference tr:
                diags.error(value.range(), "Cannot assign value to TYPE REF (yet ...)");
                break;
            default:
                diags.error(value.range(), "Unsupported combination '" + type.getClass().getSimpleName() + "' and  '"
                        + value.getClass().getSimpleName() + "'");
                break;
        }
        return result;
    }

    public PrimitiveV alignPrimitive(PrimitiveV lit, PrimitiveT type) {
        PrimitiveV result = lit;
        switch (type) {
            case BoolT __:
                if (!(lit instanceof BoolV))
                    diags.error(lit.range(),
                            "Expected BOOL literal, got '" + lit.getClass().getSimpleName().toString() + "'");
                break;
            case AlphaT ct:
                if (!(lit instanceof AlphaV av))
                    diags.error(lit.range(), "Expected ALPHA literal, got '" + lit.toString() + "'");
                else
                    result = alignValueNumeric(av, ct);
                break;
            case BinaryT __:
                if (!(lit instanceof BinaryV))
                    diags.error(lit.range(),
                            "Expected BINARY literal, got '" + lit.getClass().getSimpleName().toString() + "'");
                break;
            case NumberT nt:
                if (!(lit instanceof NumberV nv))
                    diags.error(lit.range(),
                            "Expected NUMBER literal, got '" + lit.getClass().getSimpleName().toString() + "'");
                else
                    result = alignNumericValue(nv, nt);
                break;
            case TemporalT tt:
                if (!(lit instanceof TemporalV))
                    diags.error(lit.range(),
                            "Expected TEMPORAL literal, got '" + lit.getClass().getSimpleName().toString() + "'");
                break;
            default:
                diags.error(lit.range(),
                        "Unsupported combination of literal type '" + type.getClass().getSimpleName()
                                + "' and literal '"
                                + lit.getClass().getSimpleName() + "'");
                break;
        }
        return result;
    }

    public AlphaV alignValueNumeric(AlphaV value, AlphaT type) {
        if (value instanceof StringV sv) {
        }
        return value;
    }

    public NumberV alignNumericValue(NumberV value, NumberT type) {
        if (value instanceof IntegerV iv && type instanceof IntegerT it) {
            // both integer
            return alignIntegerValue(iv, it);
        } else if (value instanceof FractionalV fv && type instanceof FractionalT ft) {
            // both fractional
            return alignFractionalValue(fv, ft);
        } else if (value instanceof IntegerV iv && type instanceof FractionalT ft) {
            // integer to fractional
            double v = getIntegerValue(iv);
            return alignFractionalValue(new Float64V(iv.range(), v), ft);
        } else if (value instanceof FractionalV fv && type instanceof IntegerT it) {
            // fractional to integer (truncate/round)
            long v = (long) getFractionalValue(fv);
            return alignIntegerValue(new Int64V(fv.range(), v), it);
        } else {
            // Safeguard for future implementations
            diags.error(value.range(), "Unsupported numeric value type '" + value.getClass().getSimpleName() + "'");
            return value;
        }
    }

    public FractionalV alignFractionalValue(FractionalV value, FractionalT type) {
        double v = getFractionalValue(value);
        FractionalV result = value;
        switch (type) {
            case DecimalT d:
                BigDecimal decimal = (value instanceof DecimalV dv) ? dv.value() : BigDecimal.valueOf(v);
                BigDecimal scaled;
                try {
                    scaled = decimal.setScale(d.scale(), RoundingMode.UNNECESSARY);
                } catch (ArithmeticException ex) {
                    diags.error(value.range(),
                            "Value '" + decimal + "' cannot be represented with scale " + d.scale());
                    break;
                }
                if (scaled.precision() > d.precision()) {
                    diags.error(value.range(),
                            "Value '" + scaled + "' does not fit in DECIMAL(" + d.precision() + "," + d.scale() + ")");
                    break;
                }
                result = new DecimalV(value.range(), scaled);
                break;
            case Float32T __:
                if (v < Float.MIN_VALUE || v > Float.MAX_VALUE)
                    diags.error(value.range(), "Value '" + v + "' does not fit in FLOAT32");
                else
                    result = new Float32V(value.range(), (float) v);
                break;
            case Float64T __:
                // all doubles fit in Float64
                break;
            default:
                // Safeguard for future implementations
                diags.error(value.range(), "Unknown fractional type '" + type.toString() + "'");
        }
        return result;
    }

    public IntegerV alignIntegerValue(IntegerV value, IntegerT type) {
        long v = getIntegerValue(value);
        IntegerV result = value;
        switch (type) {
            case Int8T __:
                if (v < Byte.MIN_VALUE || v > Byte.MAX_VALUE)
                    diags.error(value.range(), "Value '" + v + "' does not fit in INT8");
                else
                    result = new Int8V(value.range(), (byte) v);
                break;
            case Int16T __:
                if (v < Short.MIN_VALUE || v > Short.MAX_VALUE)
                    diags.error(value.range(), "Value '" + v + "' does not fit in INT16");
                else
                    result = new Int16V(value.range(), (short) v);
                break;
            case Int32T __:
                if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE)
                    diags.error(value.range(), "Value '" + v + "' does not fit in INT32");
                else
                    result = new Int32V(value.range(), (int) v);
                break;
            case Int64T __:
                // all longs fit in Int64
                break;
            default:
                // Safeguard for future implementations
                diags.error(value.range(), "Unknown integer type '" + type.toString() + "'");
                break;
        }
        return result;
    }

    public static long getIntegerValue(IntegerV v) {
        return switch (v) {
            case Int8V iv -> iv.value();
            case Int16V iv -> iv.value();
            case Int32V iv -> iv.value();
            case Int64V iv -> iv.value();
        };
    }

    public static double getFractionalValue(FractionalV v) {
        return switch (v) {
            case Float32V fv -> fv.value();
            case Float64V fv -> fv.value();
            case DecimalV dv -> dv.value().doubleValue();
        };
    }

    public static double getNumericValue(NumberV v) {
        return switch (v) {
            case IntegerV iv -> getIntegerValue(iv);
            case FractionalV fv -> getFractionalValue(fv);
        };
    }

    public static AnyT getTypeOf(AnyV v) {
        return switch (v) {
            case BoolV bv -> new BoolT(v.range());
            case AlphaV av -> getTypeOf(av);
            case BinaryV bv -> getTypeOf(bv);
            case NumberV nv -> getTypeOf(nv);
            case TemporalV tv -> getTypeOf(tv);
            default -> null;
        };
    }

    public static AlphaT getTypeOf(AlphaV av) {
        return switch (av) {
            case StringV v -> new StringT(v.range());
            case CharV v -> new CharT(v.range(), v.size());
            case UuidV v -> new UuidT(v.range());
        };
    }

    private static BinaryT getTypeOf(BinaryV bv) {
        return switch (bv) {
            case BytesV v -> new BytesT(v.range());
            case FixedV v -> new FixedT(v.range(), v.size());
        };
    }

    private static NumberT getTypeOf(NumberV nv) {
        return switch (nv) {
            case IntegerV iv -> getTypeOf(iv);
            case FractionalV fv -> getTypeOf(fv);
        };
    }

    private static IntegerT getTypeOf(IntegerV iv) {
        return switch (iv) {
            case Int8V __ -> new Int8T(iv.range());
            case Int16V __ -> new Int16T(iv.range());
            case Int32V __ -> new Int32T(iv.range());
            case Int64V __ -> new Int64T(iv.range());
        };
    }

    private static FractionalT getTypeOf(FractionalV fv) {
        return switch (fv) {
            case Float32V __ -> new Float32T(fv.range());
            case Float64V __ -> new Float64T(fv.range());
            case DecimalV dv -> new DecimalT(fv.range(), dv.precision(), dv.scale());
        };
    }

    private static TemporalT getTypeOf(TemporalV tv) {
        return switch (tv) {
            case DateV v -> new DateT(v.range());
            case TimeV v -> new TimeT(v.range(), v.precision());
            case TimestampV v -> new TimestampT(v.range(), v.precision());
            case TimestampTzV v -> new TimestampTzT(v.range(), v.precision());
        };
    }

    private AnyT trimMemberExpr(MemberExpr expr, AnyT constrainingType, Map<String, AnyT> symbols) {
        AnyT targetType = trimExpression(expr.target(), VOID_T, symbols);
        if (targetType == null) {
            diags.error(expr.target().range(), "Unable to determine type of member target");
            return constrainingType;
        }
        if (targetType instanceof StructT structT) {
            AnyT fieldType = findFieldType(structT, expr.name());
            if (fieldType != null)
                return fieldType;
            String structName = structT.qName() != null ? structT.qName().fullName() : "struct";
            diags.error(expr.range(), "Struct '" + structName + "' has no field '" + expr.name().name() + "'");
            return constrainingType;
        }
        diags.error(expr.range(), "Type '" + typeName(targetType) + "' does not support member access");
        return constrainingType;
    }

    private AnyT trimIndexExpr(IndexExpr expr, AnyT constrainingType, Map<String, AnyT> symbols) {
        AnyT targetType = trimExpression(expr.target(), VOID_T, symbols);
        AnyT indexType = trimExpression(expr.index(), VOID_T, symbols);
        if (targetType == null) {
            diags.error(expr.target().range(), "Unable to determine type of index target");
            return constrainingType;
        }
        if (targetType instanceof ListT listT) {
            if (!(indexType instanceof IntegerT)) {
                diags.error(expr.index().range(), "List index must be INTEGER");
                return constrainingType;
            }
            return listT.item();
        }
        if (targetType instanceof MapT mapT) {
            AnyT expectedKeyType = mapT.key();
            if (!areTypesCompatible(expectedKeyType, indexType)) {
                diags.error(expr.index().range(),
                        "Map key type mismatch: expected '" + typeName(expectedKeyType) + "' but got '" + typeName(indexType) + "'");
                return constrainingType;
            }
            return mapT.value();
        }
        diags.error(expr.range(), "Type '" + typeName(targetType) + "' does not support indexing");
        return constrainingType;
    }

    private AnyT findFieldType(StructT structT, Identifier name) {
        if (structT.fieldList() != null) {
            for (Field field : structT.fieldList()) {
                if (field.name().name().equals(name.name()))
                    return field.type();
            }
        }
        return null;
    }

    private boolean areTypesCompatible(AnyT expected, AnyT actual) {
        if (expected == null || actual == null)
            return false;
        if (expected instanceof VoidT || actual instanceof VoidT)
            return true;
        if (expected.getClass().equals(actual.getClass()))
            return true;
        if (expected instanceof NumberT && actual instanceof NumberT)
            return true;
        if (expected instanceof AlphaT && actual instanceof AlphaT)
            return true;
        if (expected instanceof TemporalT && actual instanceof TemporalT)
            return true;
        if (expected instanceof BinaryT && actual instanceof BinaryT)
            return true;
        if (expected instanceof BoolT && actual instanceof BoolT)
            return true;
        return false;
    }

    private static String typeName(AnyT type) {
        return type == null ? "UNKNOWN" : type.getClass().getSimpleName();
    }
}
