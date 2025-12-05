package kafkasql.runtime.expr;

/**
 * Runtime expression tree for check constraint evaluation.
 * Simplified from the lang AST - no source locations, just execution.
 */
public sealed interface RuntimeExpr
    permits RuntimeExpr.Literal,
            RuntimeExpr.Identifier,
            RuntimeExpr.Binary,
            RuntimeExpr.Unary,
            RuntimeExpr.Ternary {

    record Literal(Object value) implements RuntimeExpr {}
    
    record Identifier(String name) implements RuntimeExpr {}
    
    record Binary(BinaryOp op, RuntimeExpr left, RuntimeExpr right) implements RuntimeExpr {}
    
    record Unary(UnaryOp op, RuntimeExpr expr) implements RuntimeExpr {}
    
    record Ternary(TernaryOp op, RuntimeExpr first, RuntimeExpr second, RuntimeExpr third) implements RuntimeExpr {}
    
    enum BinaryOp {
        // Comparison
        EQ, NEQ, LT, LTE, GT, GTE,
        // Logical
        AND, OR,
        // Arithmetic
        ADD, SUB, MUL, DIV, MOD,
        // Bitwise
        BIT_AND, BIT_OR, BIT_XOR, SHL, SHR,
        // Special
        IN,
        // String
        CONCAT
    }
    
    enum UnaryOp {
        NOT, NEGATE, BIT_NOT,
        IS_NULL, IS_NOT_NULL
    }
    
    enum TernaryOp {
        BETWEEN
    }
}
