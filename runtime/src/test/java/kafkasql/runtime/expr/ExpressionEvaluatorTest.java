package kafkasql.runtime.expr;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for the ExpressionEvaluator.
 * Tests every operator individually and in combinations.
 */
public class ExpressionEvaluatorTest {

    // ========================================================================
    // Literals
    // ========================================================================

    @Test
    void evaluateBooleanTrue() {
        RuntimeExpr expr = new RuntimeExpr.Literal(true);
        Object result = ExpressionEvaluator.evaluate(expr, Map.of());
        assertEquals(true, result);
    }

    @Test
    void evaluateBooleanFalse() {
        RuntimeExpr expr = new RuntimeExpr.Literal(false);
        Object result = ExpressionEvaluator.evaluate(expr, Map.of());
        assertEquals(false, result);
    }

    @Test
    void evaluateIntegerLiteral() {
        RuntimeExpr expr = new RuntimeExpr.Literal(42);
        Object result = ExpressionEvaluator.evaluate(expr, Map.of());
        assertEquals(42, result);
    }

    @Test
    void evaluateLongLiteral() {
        RuntimeExpr expr = new RuntimeExpr.Literal(999999999999L);
        Object result = ExpressionEvaluator.evaluate(expr, Map.of());
        assertEquals(999999999999L, result);
    }

    @Test
    void evaluateDoubleLiteral() {
        RuntimeExpr expr = new RuntimeExpr.Literal(3.14159);
        Object result = ExpressionEvaluator.evaluate(expr, Map.of());
        assertEquals(3.14159, result);
    }

    @Test
    void evaluateStringLiteral() {
        RuntimeExpr expr = new RuntimeExpr.Literal("hello");
        Object result = ExpressionEvaluator.evaluate(expr, Map.of());
        assertEquals("hello", result);
    }

    @Test
    void evaluateNullLiteral() {
        RuntimeExpr expr = new RuntimeExpr.Literal(null);
        Object result = ExpressionEvaluator.evaluate(expr, Map.of());
        assertNull(result);
    }

    // ========================================================================
    // Identifiers
    // ========================================================================

    @Test
    void evaluateIdentifier() {
        RuntimeExpr expr = new RuntimeExpr.Identifier("value");
        Map<String, Object> env = Map.of("value", 100);
        Object result = ExpressionEvaluator.evaluate(expr, env);
        assertEquals(100, result);
    }

    @Test
    void evaluateIdentifierWithNullValue() {
        RuntimeExpr expr = new RuntimeExpr.Identifier("value");
        Map<String, Object> env = new HashMap<>();
        env.put("value", null);
        Object result = ExpressionEvaluator.evaluate(expr, env);
        assertNull(result);
    }

    @Test
    void evaluateIdentifierNotFound() {
        RuntimeExpr expr = new RuntimeExpr.Identifier("missing");
        Map<String, Object> env = Map.of("value", 100);
        assertThrows(RuntimeException.class, () -> 
            ExpressionEvaluator.evaluate(expr, env)
        );
    }

    // ========================================================================
    // Comparison Operators - EQ
    // ========================================================================

    @Test
    void evaluateEQ_IntegersEqual() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.EQ,
            new RuntimeExpr.Literal(5),
            new RuntimeExpr.Literal(5)
        );
        assertEquals(true, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateEQ_IntegersNotEqual() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.EQ,
            new RuntimeExpr.Literal(5),
            new RuntimeExpr.Literal(3)
        );
        assertEquals(false, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateEQ_StringsEqual() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.EQ,
            new RuntimeExpr.Literal("hello"),
            new RuntimeExpr.Literal("hello")
        );
        assertEquals(true, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateEQ_NullEqualsNull() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.EQ,
            new RuntimeExpr.Literal(null),
            new RuntimeExpr.Literal(null)
        );
        assertEquals(true, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateEQ_NullNotEqualsValue() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.EQ,
            new RuntimeExpr.Literal(null),
            new RuntimeExpr.Literal(5)
        );
        assertEquals(false, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    // ========================================================================
    // Comparison Operators - NEQ
    // ========================================================================

    @Test
    void evaluateNEQ_DifferentValues() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.NEQ,
            new RuntimeExpr.Literal(5),
            new RuntimeExpr.Literal(3)
        );
        assertEquals(true, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateNEQ_SameValues() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.NEQ,
            new RuntimeExpr.Literal(5),
            new RuntimeExpr.Literal(5)
        );
        assertEquals(false, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    // ========================================================================
    // Comparison Operators - LT, LTE, GT, GTE
    // ========================================================================

    @Test
    void evaluateLT_True() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.LT,
            new RuntimeExpr.Literal(3),
            new RuntimeExpr.Literal(5)
        );
        assertEquals(true, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateLT_False() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.LT,
            new RuntimeExpr.Literal(5),
            new RuntimeExpr.Literal(3)
        );
        assertEquals(false, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateLT_Equal() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.LT,
            new RuntimeExpr.Literal(5),
            new RuntimeExpr.Literal(5)
        );
        assertEquals(false, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateLTE_True() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.LTE,
            new RuntimeExpr.Literal(5),
            new RuntimeExpr.Literal(5)
        );
        assertEquals(true, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateLTE_LessThan() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.LTE,
            new RuntimeExpr.Literal(3),
            new RuntimeExpr.Literal(5)
        );
        assertEquals(true, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateGT_True() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.GT,
            new RuntimeExpr.Literal(10),
            new RuntimeExpr.Literal(5)
        );
        assertEquals(true, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateGT_False() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.GT,
            new RuntimeExpr.Literal(3),
            new RuntimeExpr.Literal(5)
        );
        assertEquals(false, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateGTE_True() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.GTE,
            new RuntimeExpr.Literal(5),
            new RuntimeExpr.Literal(5)
        );
        assertEquals(true, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateGTE_GreaterThan() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.GTE,
            new RuntimeExpr.Literal(10),
            new RuntimeExpr.Literal(5)
        );
        assertEquals(true, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    // ========================================================================
    // Logical Operators - AND
    // ========================================================================

    @Test
    void evaluateAND_TrueTrue() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.AND,
            new RuntimeExpr.Literal(true),
            new RuntimeExpr.Literal(true)
        );
        assertEquals(true, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateAND_TrueFalse() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.AND,
            new RuntimeExpr.Literal(true),
            new RuntimeExpr.Literal(false)
        );
        assertEquals(false, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateAND_FalseFalse() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.AND,
            new RuntimeExpr.Literal(false),
            new RuntimeExpr.Literal(false)
        );
        assertEquals(false, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateAND_ShortCircuit() {
        // Note: The evaluator does NOT short-circuit - it always evaluates both sides
        // This test verifies false AND <anything> = false
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.AND,
            new RuntimeExpr.Literal(false),
            new RuntimeExpr.Literal(true)
        );
        assertEquals(false, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    // ========================================================================
    // Logical Operators - OR
    // ========================================================================

    @Test
    void evaluateOR_TrueTrue() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.OR,
            new RuntimeExpr.Literal(true),
            new RuntimeExpr.Literal(true)
        );
        assertEquals(true, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateOR_TrueFalse() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.OR,
            new RuntimeExpr.Literal(true),
            new RuntimeExpr.Literal(false)
        );
        assertEquals(true, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateOR_FalseFalse() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.OR,
            new RuntimeExpr.Literal(false),
            new RuntimeExpr.Literal(false)
        );
        assertEquals(false, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateOR_ShortCircuit() {
        // Note: The evaluator does NOT short-circuit - it always evaluates both sides
        // This test verifies true OR <anything> = true
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.OR,
            new RuntimeExpr.Literal(true),
            new RuntimeExpr.Literal(false)
        );
        assertEquals(true, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    // ========================================================================
    // Arithmetic Operators - ADD
    // ========================================================================

    @Test
    void evaluateADD_Integers() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.ADD,
            new RuntimeExpr.Literal(10),
            new RuntimeExpr.Literal(5)
        );
        assertEquals(15L, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateADD_Negative() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.ADD,
            new RuntimeExpr.Literal(10),
            new RuntimeExpr.Literal(-3)
        );
        assertEquals(7L, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateCONCAT_Strings() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.CONCAT,
            new RuntimeExpr.Literal("Hello, "),
            new RuntimeExpr.Literal("World!")
        );
        assertEquals("Hello, World!", ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateCONCAT_Mixed() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.CONCAT,
            new RuntimeExpr.Literal("Count: "),
            new RuntimeExpr.Literal(42)
        );
        assertEquals("Count: 42", ExpressionEvaluator.evaluate(expr, Map.of()));
    }
    
    @Test
    void evaluateADD_NonNumericThrows() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.ADD,
            new RuntimeExpr.Literal("Hello, "),
            new RuntimeExpr.Literal("World!")
        );
        assertThrows(RuntimeException.class, () ->
            ExpressionEvaluator.evaluate(expr, Map.of())
        );
    }

    @Test
    void evaluateADD_NumberPlusStringThrows() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.ADD,
            new RuntimeExpr.Literal(42),
            new RuntimeExpr.Literal("text")
        );
        assertThrows(RuntimeException.class, () ->
            ExpressionEvaluator.evaluate(expr, Map.of())
        );
    }

    @Test
    void evaluateADD_StringPlusNumberThrows() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.ADD,
            new RuntimeExpr.Literal("Count: "),
            new RuntimeExpr.Literal(42)
        );
        assertThrows(RuntimeException.class, () ->
            ExpressionEvaluator.evaluate(expr, Map.of())
        );
    }

    @Test
    void evaluateCONCAT_WithNull() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.CONCAT,
            new RuntimeExpr.Literal("Value: "),
            new RuntimeExpr.Literal(null)
        );
        assertEquals("Value: null", ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    // ========================================================================
    // Arithmetic Operators - SUB, MUL, DIV, MOD
    // ========================================================================

    @Test
    void evaluateSUB() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.SUB,
            new RuntimeExpr.Literal(10),
            new RuntimeExpr.Literal(3)
        );
        assertEquals(7L, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateMUL() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.MUL,
            new RuntimeExpr.Literal(4),
            new RuntimeExpr.Literal(5)
        );
        assertEquals(20L, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateDIV() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.DIV,
            new RuntimeExpr.Literal(20),
            new RuntimeExpr.Literal(4)
        );
        assertEquals(5L, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateDIV_WithRemainder() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.DIV,
            new RuntimeExpr.Literal(10),
            new RuntimeExpr.Literal(3)
        );
        // Integer division truncates: 10 / 3 = 3
        assertEquals(3L, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateDIV_ByZero() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.DIV,
            new RuntimeExpr.Literal(10),
            new RuntimeExpr.Literal(0)
        );
        assertThrows(ArithmeticException.class, () -> 
            ExpressionEvaluator.evaluate(expr, Map.of())
        );
    }

    @Test
    void evaluateMOD() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.MOD,
            new RuntimeExpr.Literal(17),
            new RuntimeExpr.Literal(5)
        );
        assertEquals(2L, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateMOD_ExactDivision() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.MOD,
            new RuntimeExpr.Literal(20),
            new RuntimeExpr.Literal(5)
        );
        assertEquals(0L, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    // ========================================================================
    // Bitwise Operators
    // ========================================================================

    @Test
    void evaluateBIT_AND() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.BIT_AND,
            new RuntimeExpr.Literal(12),  // 1100
            new RuntimeExpr.Literal(10)   // 1010
        );
        assertEquals(8L, ExpressionEvaluator.evaluate(expr, Map.of())); // 1000
    }

    @Test
    void evaluateBIT_OR() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.BIT_OR,
            new RuntimeExpr.Literal(12),  // 1100
            new RuntimeExpr.Literal(10)   // 1010
        );
        assertEquals(14L, ExpressionEvaluator.evaluate(expr, Map.of())); // 1110
    }

    @Test
    void evaluateBIT_XOR() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.BIT_XOR,
            new RuntimeExpr.Literal(12),  // 1100
            new RuntimeExpr.Literal(10)   // 1010
        );
        assertEquals(6L, ExpressionEvaluator.evaluate(expr, Map.of())); // 0110
    }

    @Test
    void evaluateBIT_XOR_SameValue() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.BIT_XOR,
            new RuntimeExpr.Literal(42),
            new RuntimeExpr.Literal(42)
        );
        assertEquals(0L, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateSHL() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.SHL,
            new RuntimeExpr.Literal(5),
            new RuntimeExpr.Literal(2)
        );
        assertEquals(20L, ExpressionEvaluator.evaluate(expr, Map.of())); // 5 << 2 = 20
    }

    @Test
    void evaluateSHL_Large() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.SHL,
            new RuntimeExpr.Literal(1),
            new RuntimeExpr.Literal(10)
        );
        assertEquals(1024L, ExpressionEvaluator.evaluate(expr, Map.of())); // 1 << 10 = 1024
    }

    @Test
    void evaluateSHR() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.SHR,
            new RuntimeExpr.Literal(20),
            new RuntimeExpr.Literal(2)
        );
        assertEquals(5L, ExpressionEvaluator.evaluate(expr, Map.of())); // 20 >> 2 = 5
    }

    @Test
    void evaluateSHR_ToZero() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.SHR,
            new RuntimeExpr.Literal(5),
            new RuntimeExpr.Literal(10)
        );
        assertEquals(0L, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    // ========================================================================
    // Unary Operators - NOT
    // ========================================================================

    @Test
    void evaluateNOT_True() {
        RuntimeExpr expr = new RuntimeExpr.Unary(
            RuntimeExpr.UnaryOp.NOT,
            new RuntimeExpr.Literal(true)
        );
        assertEquals(false, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateNOT_False() {
        RuntimeExpr expr = new RuntimeExpr.Unary(
            RuntimeExpr.UnaryOp.NOT,
            new RuntimeExpr.Literal(false)
        );
        assertEquals(true, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateNOT_DoubleNegation() {
        RuntimeExpr expr = new RuntimeExpr.Unary(
            RuntimeExpr.UnaryOp.NOT,
            new RuntimeExpr.Unary(
                RuntimeExpr.UnaryOp.NOT,
                new RuntimeExpr.Literal(true)
            )
        );
        assertEquals(true, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    // ========================================================================
    // Unary Operators - NEGATE
    // ========================================================================

    @Test
    void evaluateNEGATE_Positive() {
        RuntimeExpr expr = new RuntimeExpr.Unary(
            RuntimeExpr.UnaryOp.NEGATE,
            new RuntimeExpr.Literal(42)
        );
        assertEquals(-42, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateNEGATE_Negative() {
        RuntimeExpr expr = new RuntimeExpr.Unary(
            RuntimeExpr.UnaryOp.NEGATE,
            new RuntimeExpr.Literal(-42)
        );
        assertEquals(42, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateNEGATE_Zero() {
        RuntimeExpr expr = new RuntimeExpr.Unary(
            RuntimeExpr.UnaryOp.NEGATE,
            new RuntimeExpr.Literal(0)
        );
        assertEquals(0, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    // ========================================================================
    // Unary Operators - BIT_NOT
    // ========================================================================

    @Test
    void evaluateBIT_NOT() {
        RuntimeExpr expr = new RuntimeExpr.Unary(
            RuntimeExpr.UnaryOp.BIT_NOT,
            new RuntimeExpr.Literal(5)
        );
        assertEquals(~5L, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateBIT_NOT_Zero() {
        RuntimeExpr expr = new RuntimeExpr.Unary(
            RuntimeExpr.UnaryOp.BIT_NOT,
            new RuntimeExpr.Literal(0)
        );
        assertEquals(~0L, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    // ========================================================================
    // Unary Operators - IS_NULL, IS_NOT_NULL
    // ========================================================================

    @Test
    void evaluateIS_NULL_Null() {
        RuntimeExpr expr = new RuntimeExpr.Unary(
            RuntimeExpr.UnaryOp.IS_NULL,
            new RuntimeExpr.Literal(null)
        );
        assertEquals(true, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateIS_NULL_NotNull() {
        RuntimeExpr expr = new RuntimeExpr.Unary(
            RuntimeExpr.UnaryOp.IS_NULL,
            new RuntimeExpr.Literal(42)
        );
        assertEquals(false, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateIS_NOT_NULL_Null() {
        RuntimeExpr expr = new RuntimeExpr.Unary(
            RuntimeExpr.UnaryOp.IS_NOT_NULL,
            new RuntimeExpr.Literal(null)
        );
        assertEquals(false, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateIS_NOT_NULL_NotNull() {
        RuntimeExpr expr = new RuntimeExpr.Unary(
            RuntimeExpr.UnaryOp.IS_NOT_NULL,
            new RuntimeExpr.Literal(42)
        );
        assertEquals(true, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    // ========================================================================
    // Special Operators - IN
    // ========================================================================

    @Test
    void evaluateIN_ValueInList() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.IN,
            new RuntimeExpr.Literal(5),
            new RuntimeExpr.Literal(List.of(1, 3, 5, 7, 9))
        );
        assertEquals(true, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateIN_ValueNotInList() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.IN,
            new RuntimeExpr.Literal(4),
            new RuntimeExpr.Literal(List.of(1, 3, 5, 7, 9))
        );
        assertEquals(false, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateIN_EmptyList() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.IN,
            new RuntimeExpr.Literal(5),
            new RuntimeExpr.Literal(List.of())
        );
        assertEquals(false, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateIN_StringInList() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.IN,
            new RuntimeExpr.Literal("apple"),
            new RuntimeExpr.Literal(List.of("apple", "banana", "cherry"))
        );
        assertEquals(true, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateIN_NullInList() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.IN,
            new RuntimeExpr.Literal(null),
            new RuntimeExpr.Literal(java.util.Arrays.asList(1, null, 3))
        );
        assertEquals(true, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    // ========================================================================
    // Ternary Operators - BETWEEN
    // ========================================================================

    @Test
    void evaluateBETWEEN_ValueInRange() {
        RuntimeExpr expr = new RuntimeExpr.Ternary(
            RuntimeExpr.TernaryOp.BETWEEN,
            new RuntimeExpr.Literal(5),
            new RuntimeExpr.Literal(1),
            new RuntimeExpr.Literal(10)
        );
        assertEquals(true, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateBETWEEN_ValueAtLowerBound() {
        RuntimeExpr expr = new RuntimeExpr.Ternary(
            RuntimeExpr.TernaryOp.BETWEEN,
            new RuntimeExpr.Literal(1),
            new RuntimeExpr.Literal(1),
            new RuntimeExpr.Literal(10)
        );
        assertEquals(true, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateBETWEEN_ValueAtUpperBound() {
        RuntimeExpr expr = new RuntimeExpr.Ternary(
            RuntimeExpr.TernaryOp.BETWEEN,
            new RuntimeExpr.Literal(10),
            new RuntimeExpr.Literal(1),
            new RuntimeExpr.Literal(10)
        );
        assertEquals(true, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateBETWEEN_ValueBelowRange() {
        RuntimeExpr expr = new RuntimeExpr.Ternary(
            RuntimeExpr.TernaryOp.BETWEEN,
            new RuntimeExpr.Literal(0),
            new RuntimeExpr.Literal(1),
            new RuntimeExpr.Literal(10)
        );
        assertEquals(false, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateBETWEEN_ValueAboveRange() {
        RuntimeExpr expr = new RuntimeExpr.Ternary(
            RuntimeExpr.TernaryOp.BETWEEN,
            new RuntimeExpr.Literal(15),
            new RuntimeExpr.Literal(1),
            new RuntimeExpr.Literal(10)
        );
        assertEquals(false, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    // ========================================================================
    // Complex Expressions - Combinations
    // ========================================================================

    @Test
    void evaluateComplex_RangeCheck() {
        // (value > 0) AND (value < 100)
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.AND,
            new RuntimeExpr.Binary(
                RuntimeExpr.BinaryOp.GT,
                new RuntimeExpr.Identifier("value"),
                new RuntimeExpr.Literal(0)
            ),
            new RuntimeExpr.Binary(
                RuntimeExpr.BinaryOp.LT,
                new RuntimeExpr.Identifier("value"),
                new RuntimeExpr.Literal(100)
            )
        );
        
        assertEquals(true, ExpressionEvaluator.evaluate(expr, Map.of("value", 50)));
        assertEquals(false, ExpressionEvaluator.evaluate(expr, Map.of("value", -10)));
        assertEquals(false, ExpressionEvaluator.evaluate(expr, Map.of("value", 150)));
        assertEquals(false, ExpressionEvaluator.evaluate(expr, Map.of("value", 0)));
    }

    @Test
    void evaluateComplex_NestedArithmetic() {
        // ((10 + 5) * 2) - 3 = 27
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.SUB,
            new RuntimeExpr.Binary(
                RuntimeExpr.BinaryOp.MUL,
                new RuntimeExpr.Binary(
                    RuntimeExpr.BinaryOp.ADD,
                    new RuntimeExpr.Literal(10),
                    new RuntimeExpr.Literal(5)
                ),
                new RuntimeExpr.Literal(2)
            ),
            new RuntimeExpr.Literal(3)
        );
        assertEquals(27L, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateComplex_MultipleIdentifiers() {
        // startDate < endDate AND (endDate - startDate) > minDuration
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.AND,
            new RuntimeExpr.Binary(
                RuntimeExpr.BinaryOp.LT,
                new RuntimeExpr.Identifier("startDate"),
                new RuntimeExpr.Identifier("endDate")
            ),
            new RuntimeExpr.Binary(
                RuntimeExpr.BinaryOp.GT,
                new RuntimeExpr.Binary(
                    RuntimeExpr.BinaryOp.SUB,
                    new RuntimeExpr.Identifier("endDate"),
                    new RuntimeExpr.Identifier("startDate")
                ),
                new RuntimeExpr.Identifier("minDuration")
            )
        );
        
        Map<String, Object> env = Map.of(
            "startDate", 100,
            "endDate", 200,
            "minDuration", 50
        );
        assertEquals(true, ExpressionEvaluator.evaluate(expr, env));
        
        Map<String, Object> env2 = Map.of(
            "startDate", 100,
            "endDate", 130,
            "minDuration", 50
        );
        assertEquals(false, ExpressionEvaluator.evaluate(expr, env2));
    }

    @Test
    void evaluateComplex_NullCoalescing() {
        // email IS NOT NULL OR name <> ''
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.OR,
            new RuntimeExpr.Unary(
                RuntimeExpr.UnaryOp.IS_NOT_NULL,
                new RuntimeExpr.Identifier("email")
            ),
            new RuntimeExpr.Binary(
                RuntimeExpr.BinaryOp.NEQ,
                new RuntimeExpr.Identifier("name"),
                new RuntimeExpr.Literal("")
            )
        );
        
        Map<String, Object> env1 = new HashMap<>();
        env1.put("email", null);
        env1.put("name", "John");
        assertEquals(true, ExpressionEvaluator.evaluate(expr, env1));
        
        Map<String, Object> env2 = new HashMap<>();
        env2.put("email", "john@example.com");
        env2.put("name", "");
        assertEquals(true, ExpressionEvaluator.evaluate(expr, env2));
        
        Map<String, Object> env3 = new HashMap<>();
        env3.put("email", null);
        env3.put("name", "");
        assertEquals(false, ExpressionEvaluator.evaluate(expr, env3));
    }

    @Test
    void evaluateComplex_BitwiseAndLogical() {
        // (flags & 0x0F) > 0 AND (flags & 0xF0) == 0
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.AND,
            new RuntimeExpr.Binary(
                RuntimeExpr.BinaryOp.GT,
                new RuntimeExpr.Binary(
                    RuntimeExpr.BinaryOp.BIT_AND,
                    new RuntimeExpr.Identifier("flags"),
                    new RuntimeExpr.Literal(0x0F)
                ),
                new RuntimeExpr.Literal(0)
            ),
            new RuntimeExpr.Binary(
                RuntimeExpr.BinaryOp.EQ,
                new RuntimeExpr.Binary(
                    RuntimeExpr.BinaryOp.BIT_AND,
                    new RuntimeExpr.Identifier("flags"),
                    new RuntimeExpr.Literal(0xF0)
                ),
                new RuntimeExpr.Literal(0L) // Bitwise ops return Long, compare with Long
            )
        );
        
        assertEquals(true, ExpressionEvaluator.evaluate(expr, Map.of("flags", 0x05)));
        assertEquals(false, ExpressionEvaluator.evaluate(expr, Map.of("flags", 0x50)));
        assertEquals(false, ExpressionEvaluator.evaluate(expr, Map.of("flags", 0x00)));
    }

    @Test
    void evaluateComplex_InAndBetween() {
        // status IN ['ACTIVE', 'PENDING'] AND age BETWEEN 18 AND 65
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.AND,
            new RuntimeExpr.Binary(
                RuntimeExpr.BinaryOp.IN,
                new RuntimeExpr.Identifier("status"),
                new RuntimeExpr.Literal(List.of("ACTIVE", "PENDING"))
            ),
            new RuntimeExpr.Ternary(
                RuntimeExpr.TernaryOp.BETWEEN,
                new RuntimeExpr.Identifier("age"),
                new RuntimeExpr.Literal(18),
                new RuntimeExpr.Literal(65)
            )
        );
        
        Map<String, Object> env1 = Map.of("status", "ACTIVE", "age", 30);
        assertEquals(true, ExpressionEvaluator.evaluate(expr, env1));
        
        Map<String, Object> env2 = Map.of("status", "INACTIVE", "age", 30);
        assertEquals(false, ExpressionEvaluator.evaluate(expr, env2));
        
        Map<String, Object> env3 = Map.of("status", "ACTIVE", "age", 70);
        assertEquals(false, ExpressionEvaluator.evaluate(expr, env3));
    }

    // ========================================================================
    // Edge Cases
    // ========================================================================

    @Test
    void evaluateEdgeCase_LargeNumbers() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.LT,
            new RuntimeExpr.Literal(Long.MAX_VALUE),
            new RuntimeExpr.Literal(Long.MAX_VALUE)
        );
        assertEquals(false, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateEdgeCase_TypeMismatch() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.LT,
            new RuntimeExpr.Literal("string"),
            new RuntimeExpr.Literal(42)
        );
        assertThrows(Exception.class, () -> 
            ExpressionEvaluator.evaluate(expr, Map.of())
        );
    }

    @Test
    void evaluateEdgeCase_NullComparison() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.LT,
            new RuntimeExpr.Literal(null),
            new RuntimeExpr.Literal(42)
        );
        assertThrows(Exception.class, () -> 
            ExpressionEvaluator.evaluate(expr, Map.of())
        );
    }

    @Test
    void evaluateEdgeCase_EmptyString() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.EQ,
            new RuntimeExpr.Literal(""),
            new RuntimeExpr.Literal("")
        );
        assertEquals(true, ExpressionEvaluator.evaluate(expr, Map.of()));
    }

    @Test
    void evaluateEdgeCase_StringComparison() {
        RuntimeExpr expr = new RuntimeExpr.Binary(
            RuntimeExpr.BinaryOp.LT,
            new RuntimeExpr.Literal("apple"),
            new RuntimeExpr.Literal("banana")
        );
        assertEquals(true, ExpressionEvaluator.evaluate(expr, Map.of()));
    }
}
