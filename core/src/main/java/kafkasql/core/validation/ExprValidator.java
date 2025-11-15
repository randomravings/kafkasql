package kafkasql.core.validation;

import java.math.BigDecimal;
import java.util.Map;

import kafkasql.core.Diagnostics;
import kafkasql.core.ast.*;

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
                // Handle symbol trimming
                break;
            case MemberExpr me:
                // Handle member expression trimming
                break;
            case IndexExpr ie:
                // Handle index expression trimming
                break;
            case AnyV av:
                return getTypeOf(av);
            default:
                diags.addError(e.range(), "Unsupported expression type '" + e.getClass().getSimpleName() + "'");
                break;
        }
        return constrainingType;
    }

    public AnyT trimPostfix(PostfixExpr e, AnyT constrainingType, Map<String, AnyT> symbols) {
        return constrainingType;
    }

    public AnyT trimUnary(PrefixExpr e, AnyT constrainingType, Map<String, AnyT> symbols) {
        return constrainingType;
    }

    public AnyT trimInfix(InfixExpr e, AnyT constrainingType, Map<String, AnyT> symbols) {
        AnyT l = trimExpression(e.left(), constrainingType, symbols);
        AnyT r = trimExpression(e.right(), constrainingType, symbols);
        switch (e.op()) {
            case AND, OR, XOR:
                if ((l instanceof BoolT) && (r instanceof BoolT))
                    return new BoolT(l.range());
                diags.addError(e.range(), "Logical operators AND/OR require BOOL operands");
                break;
            case ADD, SUB, MUL, DIV, MOD:
            case EQ, NEQ, LT, LTE, GT, GTE:
                if ((l instanceof NumberT) && (r instanceof NumberT))
                    return new BoolT(l.range());
                if ((l instanceof AlphaT) && (r instanceof AlphaT))
                    return new BoolT(l.range());
                if ((l instanceof TemporalT) && (r instanceof TemporalT))
                    return new BoolT(l.range());
                diags.addError(e.range(), "Comparison operators require NUMBER, ALPHA or TEMPORAL operands");
                break;
            case IN:
                break;
            case BITAND, BITOR, SHL, SHR:
                if ((l instanceof IntegerT) && (r instanceof IntegerT))
                    return l;
                diags.addError(e.range(), "Bitwise operators require INTEGER operands");
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
                    diags.addError(e.left().range(), "BETWEEN requires BOOL left operand");
                if (!(m instanceof NumberT) || !(r instanceof NumberT))
                    diags.addError(e.middle().range(), "BETWEEN requires NUMBER middle and right operands");
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
                    diags.addError(value.range(),
                            "Expected literal value for primitive type, got '" + value.getClass().getSimpleName()
                                    + "'");
                break;
            case ComplexT ct:
                diags.addError(value.range(), "Cannot assign value to COMPLEX type (yet ...)");
                break;
            case CompositeT ct:
                diags.addError(value.range(), "Cannot assign value to COMPOSITE type (yet ...)");
                break;
            case TypeReference tr:
                diags.addError(value.range(), "Cannot assign value to TYPE REF (yet ...)");
                break;
            default:
                diags.addError(value.range(), "Unsupported combination '" + type.getClass().getSimpleName() + "' and  '"
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
                    diags.addError(lit.range(),
                            "Expected BOOL literal, got '" + lit.getClass().getSimpleName().toString() + "'");
                break;
            case AlphaT ct:
                if (!(lit instanceof AlphaV av))
                    diags.addError(lit.range(), "Expected ALPHA literal, got '" + lit.toString() + "'");
                else
                    result = alignValueNumeric(av, ct);
                break;
            case BinaryT __:
                if (!(lit instanceof BinaryV))
                    diags.addError(lit.range(),
                            "Expected BINARY literal, got '" + lit.getClass().getSimpleName().toString() + "'");
                break;
            case NumberT nt:
                if (!(lit instanceof NumberV nv))
                    diags.addError(lit.range(),
                            "Expected NUMBER literal, got '" + lit.getClass().getSimpleName().toString() + "'");
                else
                    result = alignNumericValue(nv, nt);
                break;
            case TemporalT tt:
                if (!(lit instanceof TemporalV))
                    diags.addError(lit.range(),
                            "Expected TEMPORAL literal, got '" + lit.getClass().getSimpleName().toString() + "'");
                break;
            default:
                diags.addError(lit.range(),
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
        } else {
            // Safeguard for future implementations
            diags.addError(value.range(), "Unsupported numeric value type '" + value.getClass().getSimpleName() + "'");
            return value;
        }
    }

    public FractionalV alignFractionalValue(FractionalV value, FractionalT type) {
        double v = getFractionalValue(value);
        FractionalV result = value;
        switch (type) {
            case DecimalT d:
                var dec = BigDecimal.valueOf(v);
                if (dec.precision() > d.precision() || dec.scale() > d.scale())
                    diags.addError(value.range(),
                            "Value '" + v + "' does not fit in DECIMAL(" + d.precision() + "," + d.scale() + ")");
                else
                    result = new DecimalV(value.range(), dec);
                break;
            case Float32T __:
                if (v < Float.MIN_VALUE || v > Float.MAX_VALUE)
                    diags.addError(value.range(), "Value '" + v + "' does not fit in FLOAT32");
                else
                    result = new Float32V(value.range(), (float) v);
                break;
            case Float64T __:
                // all doubles fit in Float64
                break;
            default:
                // Safeguard for future implementations
                diags.addError(value.range(), "Unknown fractional type '" + type.toString() + "'");
        }
        return result;
    }

    public IntegerV alignIntegerValue(IntegerV value, IntegerT type) {
        long v = getIntegerValue(value);
        IntegerV result = value;
        switch (type) {
            case Int8T __:
                if (v < Byte.MIN_VALUE || v > Byte.MAX_VALUE)
                    diags.addError(value.range(), "Value '" + v + "' does not fit in INT8");
                else
                    result = new Int8V(value.range(), (byte) v);
                break;
            case Int16T __:
                if (v < Short.MIN_VALUE || v > Short.MAX_VALUE)
                    diags.addError(value.range(), "Value '" + v + "' does not fit in INT16");
                else
                    result = new Int16V(value.range(), (short) v);
                break;
            case Int32T __:
                if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE)
                    diags.addError(value.range(), "Value '" + v + "' does not fit in INT32");
                else
                    result = new Int32V(value.range(), (int) v);
                break;
            case Int64T __:
                // all longs fit in Int64
                break;
            default:
                // Safeguard for future implementations
                diags.addError(value.range(), "Unknown integer type '" + type.toString() + "'");
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
}
