package kafkasql.lang.semantic.factory;

import java.math.BigDecimal;
import java.util.Objects;

import kafkasql.lang.semantic.lazy.UnresolvedComplexLiteral;
import kafkasql.lang.semantic.lazy.UnresolvedCompositeLiteral;
import kafkasql.lang.syntax.ast.literal.BoolLiteralNode;
import kafkasql.lang.syntax.ast.literal.BytesLiteralNode;
import kafkasql.lang.syntax.ast.literal.EnumLiteralNode;
import kafkasql.lang.syntax.ast.literal.ListLiteralNode;
import kafkasql.lang.syntax.ast.literal.LiteralNode;
import kafkasql.lang.syntax.ast.literal.MapLiteralNode;
import kafkasql.lang.syntax.ast.literal.NullLiteralNode;
import kafkasql.lang.syntax.ast.literal.NumberLiteralNode;
import kafkasql.lang.syntax.ast.literal.StringLiteralNode;
import kafkasql.lang.syntax.ast.literal.StructLiteralNode;
import kafkasql.lang.syntax.ast.literal.UnionLiteralNode;

public final class LiteralValueFactory {

    private LiteralValueFactory() {}

    public static Object evaluate(LiteralNode lit) {
        return switch (lit) {

            case BoolLiteralNode b       -> b.value();
            case NumberLiteralNode n     -> parseNumber(n.text());
            case StringLiteralNode s     -> s.value();
            case BytesLiteralNode b      -> decodeBytes(b.text());
            case NullLiteralNode n       -> null;

            // All type-dependent literals must return a placeholder:
            case EnumLiteralNode e       -> new UnresolvedComplexLiteral<>(e);
            case UnionLiteralNode u      -> new UnresolvedComplexLiteral<>(u);
            case StructLiteralNode st    -> new UnresolvedComplexLiteral<>(st);

            case ListLiteralNode l       -> new UnresolvedCompositeLiteral<>(l);
            case MapLiteralNode m        -> new UnresolvedCompositeLiteral<>(m);
        };
    }

    public static BigDecimal parseNumber(String text) {
        return new BigDecimal(text);
    }

    public static long evaluateAsLong(NumberLiteralNode lit) {
        try {
            return new BigDecimal(lit.text()).longValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(
                "Enum symbol value out of range for 64-bit integer: " + lit.text(), ex);
        }
    }

    public static byte[] decodeBytes(String text) {
        var hex = trimHexString(text);
        var len = hex.length() / 2;
        var data = new byte[len];
        for (int i = 0; i < len; i++) {
            String pair = hex.substring(i * 2, i * 2 + 2);
            data[i] = (byte) Integer.parseInt(pair, 16);
        }
        return data;
    }

    private static String trimHexString(String text) {
        Objects.requireNonNull(text, "byte literal cannot be null");
        if (text.length() < 4)
            throw new IllegalArgumentException("Hex literal too short: " + text);
        if (!(text.startsWith("0x") || text.startsWith("0X")))
            throw new IllegalArgumentException("Hex literal must start with 0x or 0X: " + text);
        String hex = text.substring(2);
        if (hex.length() % 2 != 0)
            throw new IllegalArgumentException("Hex literal must have even number of digits: " + text);
        if (!hex.chars().allMatch(c -> isHexDigit((char)c)))
            throw new IllegalArgumentException("Invalid hex digit in literal: " + text);
        return hex;
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') ||
            (c >= 'a' && c <= 'f') ||
            (c >= 'A' && c <= 'F');
    }
}