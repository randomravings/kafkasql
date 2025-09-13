package streamsql.validation;

import streamsql.Catalog;
import streamsql.ast.*;
import java.util.Stack;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Deque;
import java.util.ArrayDeque;

public final class ExprValidation {

    public static record TypeCheckResult(AnyT type, Boolean valid, String errorMessage) {}

    public static TypeCheckResult typeCheckExpr(Expr expr, List<Field> context, Catalog catalog) {
        Stack<Expr> stack = new Stack<>();
        return typeCheckExpr(expr, context, catalog, stack);
    }

    private static TypeCheckResult typeCheckExpr(Expr expr, List<Field> context, Catalog catalog, Stack<Expr> stack) {
        if (expr == null) return error("Expression is null");
        if (stack.contains(expr)) return error("Cyclic expression detected");
        stack.push(expr);
        try {
            // Literals
            if (expr instanceof BoolV) return ok(BoolT.get());
            if (expr instanceof Int8V) return ok(Int8T.get());
            if (expr instanceof Int16V) return ok(Int16T.get());
            if (expr instanceof Int32V) return ok(Int32T.get());
            if (expr instanceof Int64V) return ok(Int64T.get());
            if (expr instanceof Float32V) return ok(Float32T.get());
            if (expr instanceof Float64V) return ok(Float64T.get());
            if (expr instanceof StringV) return ok(StringT.get());
            if (expr instanceof CharV v) return ok(CharT.get(v.length()));
            if (expr instanceof BytesV) return ok(BytesT.get());
            if (expr instanceof FixedV v) return ok(FixedT.get(v.length()));
            if (expr instanceof UuidV) return ok(UuidT.get());
            if (expr instanceof DateV) return ok(DateT.get());
            if (expr instanceof TimeV v) return ok(TimeT.get(v.precision()));
            if (expr instanceof TimestampV v) return ok(TimestampT.get(v.precision()));
            if (expr instanceof TimestampTzV v) return ok(TimestampTzT.get(v.precision()));
            if (expr instanceof DecimalV v) return ok(DecimalT.get(v.precision(), v.scale()));
            if (expr instanceof NullV) return ok(VoidT.get());

            // Identifier -> variable/field lookup in context
            if (expr instanceof Identifier id) {
                for (Field f : context) {
                    if (f.name().value().equals(id.value())) {
                        return ok(f.typ());
                    }
                }
                return error("Unknown identifier: " + id.value());
            }

            // AccessPath (head + chain of Accessor segments)
            if (expr instanceof Segment ap) {
                return resolveAccessPath(ap, context, catalog, stack);
            }

            // Expressions that contain nested Expr nodes
            if (expr instanceof Unary u) {
                TypeCheckResult inner = typeCheckExpr(u.expr(), context, catalog, stack);
                if (!inner.valid) return inner;
                AnyT t = inner.type;
                return switch (u.op()) {
                    case NOT -> isBoolType(t) ? ok(BoolT.get()) : error("NOT requires boolean operand");
                    case NEG -> isNumericType(t) ? ok(t) : error("Unary - requires numeric operand");
                    default -> error("Unknown unary operator");
                };
            }

            if (expr instanceof Binary b) {
                TypeCheckResult lres = typeCheckExpr(b.left(), context, catalog, stack);
                if (!lres.valid) return lres;
                TypeCheckResult rres = typeCheckExpr(b.right(), context, catalog, stack);
                if (!rres.valid) return rres;
                AnyT lt = lres.type;
                AnyT rt = rres.type;

                switch (b.op()) {
                    case EQ, NEQ -> {
                        if (isNumericType(lt) && isNumericType(rt)) return ok(BoolT.get());
                        if (Objects.equals(lt.getClass(), rt.getClass())) return ok(BoolT.get());
                        if ((lt instanceof AlphaT && rt instanceof AlphaT) ||
                            (lt instanceof BinaryT && rt instanceof BinaryT) ||
                            (lt instanceof TemporalT && rt instanceof TemporalT) ||
                            (lt instanceof CompositeType && rt instanceof CompositeType)) {
                            return ok(BoolT.get());
                        }
                        return error("Incompatible types for equality: " + lt.getClass().getSimpleName() + " and " + rt.getClass().getSimpleName());
                    }
                    case LT, LTE, GT, GTE -> {
                        if ((isNumericType(lt) && isNumericType(rt)) || (lt instanceof TemporalT && rt instanceof TemporalT)) {
                            return ok(BoolT.get());
                        }
                        return error("Ordering comparison requires numeric or temporal types");
                    }
                    case AND, OR, XOR -> {
                        if (isBoolType(lt) && isBoolType(rt)) return ok(BoolT.get());
                        return error("Logical ops require boolean operands");
                    }
                    case IN -> {
                        if (!(rres.type instanceof ListT listType)) return error("RIGHT side of IN must be a list");
                        AnyT itemType = listType.item();
                        if (isCompatible(itemType, lt)) return ok(BoolT.get());
                        return error("Element type of list is not compatible with left side of IN");
                    }
                    case IS_NULL, IS_NOT_NULL -> {
                        return ok(BoolT.get());
                    }
                    case ADD, SUB, MUL, DIV, MOD -> {
                        if (isNumericType(lt) && isNumericType(rt)) {
                            AnyT wider = widerNumericType(lt, rt);
                            return ok(wider);
                        }
                        if (b.op() == BinaryOp.ADD && lt instanceof AlphaT && rt instanceof AlphaT) return ok(StringT.get());
                        if (b.op() == BinaryOp.ADD && lt instanceof BytesT && rt instanceof BytesT) return ok(BytesT.get());
                        return error("Arithmetic requires numeric operands (or string/bytes concat for +)");
                    }
                    case BITAND, BITOR, SHL, SHR -> {
                        if (isIntegerType(lt) && isIntegerType(rt)) {
                            return ok(widerIntegerType(lt, rt));
                        }
                        return error("Bitwise operations require integer operands");
                    }
                    default -> {
                        return error("Unsupported binary operator: " + b.op());
                    }
                }
            }

            if (expr instanceof Ternary t) {
                switch (t.op()) {
                    case BETWEEN -> {
                        TypeCheckResult left = typeCheckExpr(t.left(), context, catalog, stack);
                        if (!left.valid) return left;
                        TypeCheckResult middle = typeCheckExpr(t.middle(), context, catalog, stack);
                        if (!middle.valid) return middle;
                        TypeCheckResult right = typeCheckExpr(t.right(), context, catalog, stack);
                        if (!right.valid) return right;
                        AnyT lt = left.type;
                        AnyT mt = middle.type;
                        AnyT rt = right.type;
                        if ((isNumericType(lt) && isNumericType(mt) && isNumericType(rt)) ||
                            (lt instanceof TemporalT && mt instanceof TemporalT && rt instanceof TemporalT)) {
                            return ok(BoolT.get());
                        }
                        return error("BETWEEN requires numeric or temporal operands");
                    }
                    default -> { return error("Unsupported ternary operator: " + t.op()); }
                }
            }

            return error("Expression type checking not implemented for: " + expr.getClass().getSimpleName());
        } finally {
            stack.pop();
        }
    }

    // Resolve AccessPath / Segment / Accessor head + segments
    private static TypeCheckResult resolveAccessPath(Accessor accessor, List<Field> context, Catalog catalog, Stack<Expr> stack) {
        // The top-level head must not itself be a nested Segment and must be an Identifier.
        Identifier head;
        Deque<Accessor> q = new ArrayDeque<>();

        if (accessor instanceof Identifier id) {
            head = id;
        } else if (accessor instanceof Segment s0) {
            Accessor h = s0.head();
            if (h instanceof Segment) {
                return error("Access path head cannot be a nested segment");
            }
            if (!(h instanceof Identifier id)) {
                return error("Access path must start with an identifier");
            }
            head = id;
            // collect tail sequence from recursive Segment chain
            Accessor cur = s0.tail();
            while (cur != null) {
                if (cur instanceof Segment s) {
                    q.addLast(s.head());
                    cur = s.tail();
                } else {
                    q.addLast(cur);
                    cur = null;
                }
            }
        } else {
            return error("Access path must start with an identifier");
        }

        // find head in provided context
        AnyT current = null;
        for (Field f : context) {
            if (f.name().value().equals(head.value())) {
                current = f.typ();
                break;
            }
        }
        if (current == null) {
            return error("Unknown root identifier in access path: " + head.value());
        }

        // Walk flattened segments
        while (!q.isEmpty()) {
            Accessor seg = q.removeFirst();

            // If the segment itself is a Segment (shouldn't happen after flattening) flatten it
            if (seg instanceof Segment nested) {
                q.addFirst(nested.tail());
                q.addFirst(nested.head());
                continue;
            }

            if (seg instanceof Identifier fieldName) {
                String name = fieldName.value();
                if (current instanceof MapT mt) {
                    current = mt.value();
                    continue;
                }
                if (current instanceof ListT lt) {
                    current = lt.item();
                }
                if (current instanceof TypeRef tref) {
                    Optional<ComplexType> ct = catalog.getType(tref.qName());
                    if (ct.isPresent()) {
                        ComplexType complex = ct.get();
                        if (complex instanceof Struct s2) {
                            Optional<Field> sf = s2.fields().stream().filter(ff -> ff.name().value().equals(name)).findFirst();
                            if (sf.isPresent()) {
                                current = sf.get().typ();
                                continue;
                            } else {
                                return error("Field '" + name + "' not found on struct " + tref.qName().fullName());
                            }
                        } else if (complex instanceof Union u) {
                            return error("Cannot access field '" + name + "' on union type " + tref.qName().fullName());
                        } else if (complex instanceof Scalar sc) {
                            return error("Type " + tref.qName().fullName() + " is scalar and has no fields");
                        }
                    } else {
                        return error("Unknown type reference: " + tref.qName().fullName());
                    }
                } else if (current instanceof Struct inl) {
                    Optional<Field> sf = inl.fields().stream().filter(ff -> ff.name().value().equals(name)).findFirst();
                    if (sf.isPresent()) {
                        current = sf.get().typ();
                        continue;
                    } else {
                        return error("Field '" + name + "' not found on struct");
                    }
                } else {
                    return error("Cannot access field '" + name + "' on type " + current.getClass().getSimpleName());
                }
            } else if (seg instanceof Indexer ia) {
                TypeCheckResult idxRes = typeCheckExpr(ia.index(), context, catalog, stack);
                if (!idxRes.valid) return idxRes;
                AnyT idxT = idxRes.type;
                if (current instanceof ListT lt) {
                    if (!isIntegerType(idxT)) return error("List index must be integer");
                    current = lt.item();
                    continue;
                }
                if (current instanceof MapT mt) {
                    if (!(idxT instanceof PrimitiveType)) return error("Map key must be a primitive type");
                    current = mt.value();
                    continue;
                }
                return error("Type " + current.getClass().getSimpleName() + " is not indexable");
            } else {
                return error("Unknown accessor segment type: " + seg.getClass().getSimpleName());
            }
        }

        return ok(current);
    }

    public static TypeCheckResult ok(AnyT type) {
        return new TypeCheckResult(type, true, "");
    }

    public static TypeCheckResult error(String message) {
        return new TypeCheckResult(VoidT.get(), false, message);
    }

    // Helpers

    private static boolean isIntegerType(AnyT t) {
        return t instanceof IntegerT;
    }

    private static boolean isFractionalType(AnyT t) {
        return t instanceof FractionalT;
    }

    private static boolean isNumberType(AnyT t) {
        return t instanceof NumberT;
    }

    private static boolean isNumericType(AnyT t) { return isNumberType(t); }

    private static boolean isBoolType(AnyT t) { return t instanceof BoolT; }

    private static AnyT widerNumericType(AnyT a, AnyT b) {
        int ra = numericRank(a);
        int rb = numericRank(b);
        int r = Math.max(ra, rb);
        return numericTypeForRank(r);
    }

    private static AnyT widerIntegerType(AnyT a, AnyT b) {
        int ra = integerRank(a);
        int rb = integerRank(b);
        int r = Math.max(ra, rb);
        return integerTypeForRank(r);
    }

    // numeric rank: Int8=1, Int16=2, Int32=3, Int64=4, Float32=5, Float64=6, Decimal=7
    private static int numericRank(AnyT t) {
        if (t instanceof DecimalT) return 7;
        if (t instanceof Float64T) return 6;
        if (t instanceof Float32T) return 5;
        if (t instanceof Int64T) return 4;
        if (t instanceof Int32T) return 3;
        if (t instanceof Int16T) return 2;
        if (t instanceof Int8T) return 1;
        return 0;
    }

    private static AnyT numericTypeForRank(int r) {
        return switch (r) {
            case 7 -> getDecimalOrFloat64();
            case 6 -> Float64T.get();
            case 5 -> Float32T.get();
            case 4 -> Int64T.get();
            case 3 -> Int32T.get();
            case 2 -> Int16T.get();
            case 1 -> Int8T.get();
            default -> Float64T.get();
        };
    }

    private static int integerRank(AnyT t) {
        if (t instanceof Int64T) return 4;
        if (t instanceof Int32T) return 3;
        if (t instanceof Int16T) return 2;
        if (t instanceof Int8T) return 1;
        return 0;
    }

    private static AnyT integerTypeForRank(int r) {
        return switch (r) {
            case 4 -> Int64T.get();
            case 3 -> Int32T.get();
            case 2 -> Int16T.get();
            case 1 -> Int8T.get();
            default -> Int64T.get();
        };
    }

    // Best-effort: if DecimalT class provides a factory this helper should be updated.
    private static AnyT getDecimalOrFloat64() {
        try {
            return DecimalT.class.getMethod("get", int.class, int.class)
                .getDeclaringClass() != null ? Float64T.get() : Float64T.get();
        } catch (NoSuchMethodException e) {
            return Float64T.get();
        }
    }

    private static boolean isCompatible(AnyT expected, AnyT actual) {
        if (Objects.equals(expected.getClass(), actual.getClass())) return true;
        if (isNumericType(expected) && isNumericType(actual)) return true;
        if (expected instanceof AlphaT && actual instanceof AlphaT) return true;
        if (expected instanceof BinaryT && actual instanceof BinaryT) return true;
        if (expected instanceof TemporalT && actual instanceof TemporalT) return true;
        return false;
    }
}