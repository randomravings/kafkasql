package kafkasql.lang.semantic.util;

import kafkasql.lang.syntax.ast.expr.*;
import kafkasql.lang.syntax.ast.literal.*;
import kafkasql.runtime.expr.RuntimeExpr;

import java.util.ArrayList;
import java.util.List;

/**
 * Translates lang AST expressions to runtime expression trees.
 * This allows runtime module to remain independent of lang AST.
 */
public final class RuntimeExprTranslator {
    
    private RuntimeExprTranslator() {}
    
    public static RuntimeExpr translate(Expr expr) {
        return switch (expr) {
            case LiteralExpr lit -> translateLiteral(lit.literal());
            case IdentifierExpr id -> new RuntimeExpr.Identifier(id.name().name());
            case InfixExpr inf -> translateInfix(inf);
            case PrefixExpr pre -> translatePrefix(pre);
            case PostfixExpr post -> translatePostfix(post);
            case TrifixExpr tri -> translateTrifix(tri);
            case ParenExpr paren -> translate(paren.inner());
            case MemberExpr mem -> translateMember(mem);
            case IndexExpr idx -> translateIndex(idx);
        };
    }
    
    private static RuntimeExpr translateLiteral(LiteralNode lit) {
        Object value = switch (lit) {
            case BoolLiteralNode b -> b.value();
            case NumberLiteralNode n -> parseNumber(n.text());
            case StringLiteralNode s -> s.value();
            case BytesLiteralNode b -> b.text();  // Store raw text
            case NullLiteralNode n -> null;
            case EnumLiteralNode e -> throw new UnsupportedOperationException("Enum literals in checks not yet supported");
            case StructLiteralNode s -> throw new UnsupportedOperationException("Struct literals in checks not yet supported");
            case UnionLiteralNode u -> throw new UnsupportedOperationException("Union literals in checks not yet supported");
            case ListLiteralNode l -> translateList(l);
            case MapLiteralNode m -> throw new UnsupportedOperationException("Map literals in checks not yet supported");
        };
        return new RuntimeExpr.Literal(value);
    }
    
    private static Object parseNumber(String text) {
        text = text.replace("_", "");
        try {
            if (text.contains(".") || text.toLowerCase().contains("e")) {
                return Double.parseDouble(text);
            } else {
                long val = Long.parseLong(text);
                if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) {
                    return (int) val;
                }
                return val;
            }
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid number: " + text, e);
        }
    }
    
    private static Object translateList(ListLiteralNode list) {
        List<Object> values = new ArrayList<>();
        for (LiteralNode elem : list.elements()) {
            RuntimeExpr translated = translateLiteral(elem);
            if (translated instanceof RuntimeExpr.Literal lit) {
                values.add(lit.value());
            }
        }
        return values;
    }
    
    private static RuntimeExpr translateInfix(InfixExpr inf) {
        RuntimeExpr.BinaryOp op = switch (inf.op()) {
            case EQ -> RuntimeExpr.BinaryOp.EQ;
            case NEQ -> RuntimeExpr.BinaryOp.NEQ;
            case LT -> RuntimeExpr.BinaryOp.LT;
            case LTE -> RuntimeExpr.BinaryOp.LTE;
            case GT -> RuntimeExpr.BinaryOp.GT;
            case GTE -> RuntimeExpr.BinaryOp.GTE;
            case AND -> RuntimeExpr.BinaryOp.AND;
            case OR -> RuntimeExpr.BinaryOp.OR;
            case ADD -> RuntimeExpr.BinaryOp.ADD;
            case SUB -> RuntimeExpr.BinaryOp.SUB;
            case MUL -> RuntimeExpr.BinaryOp.MUL;
            case DIV -> RuntimeExpr.BinaryOp.DIV;
            case MOD -> RuntimeExpr.BinaryOp.MOD;
            case BITAND -> RuntimeExpr.BinaryOp.BIT_AND;
            case BITOR -> RuntimeExpr.BinaryOp.BIT_OR;
            case XOR -> RuntimeExpr.BinaryOp.BIT_XOR;
            case SHL -> RuntimeExpr.BinaryOp.SHL;
            case SHR -> RuntimeExpr.BinaryOp.SHR;
            case IN -> RuntimeExpr.BinaryOp.IN;
            case CONCAT -> RuntimeExpr.BinaryOp.CONCAT;
        };
        return new RuntimeExpr.Binary(op, translate(inf.left()), translate(inf.right()));
    }
    
    private static RuntimeExpr translatePrefix(PrefixExpr pre) {
        RuntimeExpr.UnaryOp op = switch (pre.op()) {
            case NOT -> RuntimeExpr.UnaryOp.NOT;
            case NEG -> RuntimeExpr.UnaryOp.NEGATE;
        };
        return new RuntimeExpr.Unary(op, translate(pre.expr()));
    }
    
    private static RuntimeExpr translatePostfix(PostfixExpr post) {
        RuntimeExpr.UnaryOp op = switch (post.op()) {
            case IS_NULL -> RuntimeExpr.UnaryOp.IS_NULL;
            case IS_NOT_NULL -> RuntimeExpr.UnaryOp.IS_NOT_NULL;
        };
        return new RuntimeExpr.Unary(op, translate(post.expr()));
    }
    
    private static RuntimeExpr translateTrifix(TrifixExpr tri) {
        RuntimeExpr.TernaryOp op = switch (tri.op()) {
            case BETWEEN -> RuntimeExpr.TernaryOp.BETWEEN;
        };
        return new RuntimeExpr.Ternary(op, translate(tri.left()), translate(tri.middle()), translate(tri.right()));
    }
    
    private static RuntimeExpr translateMember(MemberExpr mem) {
        // For checks, member access like "person.age" should be flattened to just "age"
        // assuming the check is in the context of the person struct
        throw new UnsupportedOperationException("Member expressions in checks not yet supported");
    }
    
    private static RuntimeExpr translateIndex(IndexExpr idx) {
        throw new UnsupportedOperationException("Index expressions in checks not yet supported");
    }
}
