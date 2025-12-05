package kafkasql.runtime.expr;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Evaluates runtime expressions against a value environment.
 * Used for CHECK constraint validation.
 */
public final class ExpressionEvaluator {
    
    private ExpressionEvaluator() {}
    
    /**
     * Evaluate an expression in the given environment.
     * 
     * @param expr The expression to evaluate
     * @param env Map of identifier names to values
     * @return The result value
     */
    public static Object evaluate(RuntimeExpr expr, Map<String, Object> env) {
        return switch (expr) {
            case RuntimeExpr.Literal lit -> lit.value();
            case RuntimeExpr.Identifier id -> {
                Object val = env.get(id.name());
                if (val == null && !env.containsKey(id.name())) {
                    throw new RuntimeException("Undefined identifier: " + id.name());
                }
                yield val;
            }
            case RuntimeExpr.Binary bin -> evaluateBinary(bin, env);
            case RuntimeExpr.Unary un -> evaluateUnary(un, env);
            case RuntimeExpr.Ternary ter -> evaluateTernary(ter, env);
        };
    }
    
    private static Object evaluateBinary(RuntimeExpr.Binary bin, Map<String, Object> env) {
        Object left = evaluate(bin.left(), env);
        Object right = evaluate(bin.right(), env);
        
        return switch (bin.op()) {
            case EQ -> Objects.equals(left, right);
            case NEQ -> !Objects.equals(left, right);
            case LT -> compare(left, right) < 0;
            case LTE -> compare(left, right) <= 0;
            case GT -> compare(left, right) > 0;
            case GTE -> compare(left, right) >= 0;
            case AND -> toBoolean(left) && toBoolean(right);
            case OR -> toBoolean(left) || toBoolean(right);
            case ADD -> add(left, right);
            case SUB -> subtract(left, right);
            case MUL -> multiply(left, right);
            case DIV -> divide(left, right);
            case MOD -> modulo(left, right);
            case BIT_AND -> bitwiseAnd(left, right);
            case BIT_OR -> bitwiseOr(left, right);
            case BIT_XOR -> bitwiseXor(left, right);
            case SHL -> shiftLeft(left, right);
            case SHR -> shiftRight(left, right);
            case IN -> in(left, right);
            case CONCAT -> concat(left, right);
        };
    }
    
    private static Object evaluateUnary(RuntimeExpr.Unary un, Map<String, Object> env) {
        Object val = evaluate(un.expr(), env);
        
        return switch (un.op()) {
            case NOT -> !toBoolean(val);
            case NEGATE -> negate(val);
            case BIT_NOT -> bitwiseNot(val);
            case IS_NULL -> val == null;
            case IS_NOT_NULL -> val != null;
        };
    }
    
    private static Object evaluateTernary(RuntimeExpr.Ternary ter, Map<String, Object> env) {
        return switch (ter.op()) {
            case BETWEEN -> {
                Object val = evaluate(ter.first(), env);
                Object lower = evaluate(ter.second(), env);
                Object upper = evaluate(ter.third(), env);
                yield compare(val, lower) >= 0 && compare(val, upper) <= 0;
            }
        };
    }
    
    // Helper methods for type conversions and operations
    
    private static boolean toBoolean(Object val) {
        if (val instanceof Boolean b) return b;
        throw new RuntimeException("Expected boolean, got: " + val);
    }
    
    @SuppressWarnings("unchecked")
    private static int compare(Object a, Object b) {
        if (a == null || b == null) {
            throw new RuntimeException("Cannot compare null values");
        }
        
        // Handle numbers
        if (a instanceof Number && b instanceof Number) {
            BigDecimal bd1 = toBigDecimal((Number) a);
            BigDecimal bd2 = toBigDecimal((Number) b);
            return bd1.compareTo(bd2);
        }
        
        // Handle comparables
        if (a instanceof Comparable && a.getClass() == b.getClass()) {
            return ((Comparable<Object>) a).compareTo(b);
        }
        
        throw new RuntimeException("Cannot compare " + a.getClass() + " with " + b.getClass());
    }
    
    private static Object add(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return addNumbers((Number) a, (Number) b);
        }
        throw new RuntimeException("Cannot add non-numeric types");
    }
    
    private static Object concat(Object a, Object b) {
        return String.valueOf(a) + String.valueOf(b);
    }
    
    private static Object subtract(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return subtractNumbers((Number) a, (Number) b);
        }
        throw new RuntimeException("Cannot subtract non-numbers");
    }
    
    private static Object multiply(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return multiplyNumbers((Number) a, (Number) b);
        }
        throw new RuntimeException("Cannot multiply non-numbers");
    }
    
    private static Object divide(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return divideNumbers((Number) a, (Number) b);
        }
        throw new RuntimeException("Cannot divide non-numbers");
    }
    
    private static Object modulo(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return moduloNumbers((Number) a, (Number) b);
        }
        throw new RuntimeException("Cannot modulo non-numbers");
    }
    
    private static Object negate(Object val) {
        if (val instanceof Number n) {
            if (n instanceof Integer) return -n.intValue();
            if (n instanceof Long) return -n.longValue();
            if (n instanceof Float) return -n.floatValue();
            if (n instanceof Double) return -n.doubleValue();
            return toBigDecimal(n).negate();
        }
        throw new RuntimeException("Cannot negate non-number");
    }
    
    private static Object bitwiseAnd(Object a, Object b) {
        return toLong(a) & toLong(b);
    }
    
    private static Object bitwiseOr(Object a, Object b) {
        return toLong(a) | toLong(b);
    }
    
    private static Object bitwiseXor(Object a, Object b) {
        return toLong(a) ^ toLong(b);
    }
    
    private static Object bitwiseNot(Object val) {
        return ~toLong(val);
    }
    
    private static Object shiftLeft(Object a, Object b) {
        return toLong(a) << toInt(b);
    }
    
    private static Object shiftRight(Object a, Object b) {
        return toLong(a) >> toInt(b);
    }
    
    private static boolean in(Object needle, Object haystack) {
        if (haystack instanceof Collection<?> coll) {
            return coll.contains(needle);
        }
        throw new RuntimeException("IN operator requires collection");
    }
    
    // Number arithmetic helpers
    
    private static Number addNumbers(Number a, Number b) {
        if (isInteger(a) && isInteger(b)) {
            return a.longValue() + b.longValue();
        }
        return toBigDecimal(a).add(toBigDecimal(b));
    }
    
    private static Number subtractNumbers(Number a, Number b) {
        if (isInteger(a) && isInteger(b)) {
            return a.longValue() - b.longValue();
        }
        return toBigDecimal(a).subtract(toBigDecimal(b));
    }
    
    private static Number multiplyNumbers(Number a, Number b) {
        if (isInteger(a) && isInteger(b)) {
            return a.longValue() * b.longValue();
        }
        return toBigDecimal(a).multiply(toBigDecimal(b));
    }
    
    private static Number divideNumbers(Number a, Number b) {
        if (isInteger(a) && isInteger(b)) {
            return a.longValue() / b.longValue();
        }
        return toBigDecimal(a).divide(toBigDecimal(b), RoundingMode.HALF_UP);
    }
    
    private static Number moduloNumbers(Number a, Number b) {
        if (isInteger(a) && isInteger(b)) {
            return a.longValue() % b.longValue();
        }
        return toBigDecimal(a).remainder(toBigDecimal(b));
    }
    
    private static boolean isInteger(Number n) {
        return n instanceof Byte || n instanceof Short || n instanceof Integer || n instanceof Long;
    }
    
    private static BigDecimal toBigDecimal(Number n) {
        if (n instanceof BigDecimal bd) return bd;
        if (n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte) {
            return BigDecimal.valueOf(n.longValue());
        }
        return BigDecimal.valueOf(n.doubleValue());
    }
    
    private static long toLong(Object val) {
        if (val instanceof Number n) {
            return n.longValue();
        }
        throw new RuntimeException("Expected number, got: " + val);
    }
    
    private static int toInt(Object val) {
        if (val instanceof Number n) {
            return n.intValue();
        }
        throw new RuntimeException("Expected number, got: " + val);
    }
}
