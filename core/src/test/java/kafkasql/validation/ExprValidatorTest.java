package kafkasql.validation;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kafkasql.lang.Diagnostics;
import kafkasql.lang.ast.*;
import kafkasql.lang.validation.ExprValidator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ExprValidatorTest {

    private Diagnostics diags;
    private ExprValidator validator;

    @BeforeEach
    void setUp() {
        diags = new Diagnostics();
        validator = new ExprValidator(diags);
    }

    @Test
    void resolvesIdentifierFromSymbolTable() {
        var resolved = validator.trimExpression(idExpr("count"), Map.of("count", new Int32T(Range.NONE)));
        assertTrue(resolved instanceof Int32T);
        assertFalse(diags.hasError());
    }

    @Test
    void unknownIdentifierProducesDiagnostic() {
        validator.trimExpression(idExpr("missing"), Map.of());
        assertTrue(diags.hasError());
        assertThat(diags.errors()).hasSize(1);
    }

    @Test
    void memberAccessReturnsFieldType() {
        var struct = structWithField("payload", new StringT(Range.NONE));
        var expr = new MemberExpr(Range.NONE, idExpr("record"), id("payload"), null);
        var type = validator.trimExpression(expr, Map.of("record", struct));
        assertTrue(type instanceof StringT);
        assertFalse(diags.hasError());
    }

    @Test
    void memberAccessMissingFieldReportsError() {
        var struct = structWithField("other", new StringT(Range.NONE));
        var expr = new MemberExpr(Range.NONE, idExpr("record"), id("payload"), null);
        validator.trimExpression(expr, Map.of("record", struct));
        assertTrue(diags.hasError());
    }

    @Test
    void memberAccessOnScalarReportsError() {
        var expr = new MemberExpr(Range.NONE, idExpr("value"), id("payload"), null);
        validator.trimExpression(expr, Map.of("value", new Int32T(Range.NONE)));
        assertTrue(diags.hasError());
    }

    @Test
    void listIndexReturnsItemType() {
        var expr = new IndexExpr(Range.NONE, idExpr("values"), new Int32V(Range.NONE, 0), null);
        var type = validator.trimExpression(expr, Map.of("values", new ListT(Range.NONE, new Int32T(Range.NONE))));
        assertTrue(type instanceof Int32T);
        assertFalse(diags.hasError());
    }

    @Test
    void listIndexRequiresInteger() {
        var expr = new IndexExpr(Range.NONE, idExpr("values"), new BoolV(Range.NONE, true), null);
        validator.trimExpression(expr, Map.of("values", new ListT(Range.NONE, new Int32T(Range.NONE))));
        assertTrue(diags.hasError());
    }

    @Test
    void mapIndexValidatesKeyType() {
        var expr = new IndexExpr(Range.NONE, idExpr("values"), new StringV(Range.NONE, "k"), null);
        var type = validator.trimExpression(expr,
                Map.of("values", new MapT(Range.NONE, new StringT(Range.NONE), new BoolT(Range.NONE))));
        assertTrue(type instanceof BoolT);
        assertFalse(diags.hasError());
    }

    @Test
    void mapIndexMismatchedKeyRaisesDiagnostic() {
        var expr = new IndexExpr(Range.NONE, idExpr("values"), new Int32V(Range.NONE, 1), null);
        validator.trimExpression(expr,
                Map.of("values", new MapT(Range.NONE, new StringT(Range.NONE), new BoolT(Range.NONE))));
        assertTrue(diags.hasError());
    }

    @Test
    void postfixIsNullYieldsBool() {
        var expr = new PostfixExpr(Range.NONE, PostfixOp.IS_NULL, idExpr("v"), null);
        var type = validator.trimExpression(expr, Map.of("v", new Int32T(Range.NONE)));
        assertTrue(type instanceof BoolT);
    }

    @Test
    void prefixNotRequiresBoolean() {
        var ok = new PrefixExpr(Range.NONE, PrefixOp.NOT, boolLiteral(true), null);
        var type = validator.trimExpression(ok, Map.of());
        assertTrue(type instanceof BoolT);

        var bad = new PrefixExpr(Range.NONE, PrefixOp.NOT, new Int32V(Range.NONE, 1), null);
        validator.trimExpression(bad, Map.of());
        assertTrue(diags.hasError());
    }

    @Test
    void arithmeticRequiresNumbers() {
        var add = new InfixExpr(Range.NONE, InfixOp.ADD, new Int32V(Range.NONE, 1), new Int32V(Range.NONE, 2), null);
        var type = validator.trimExpression(add, Map.of());
        assertTrue(type instanceof Int32T);

        var invalid = new InfixExpr(Range.NONE, InfixOp.ADD, boolLiteral(true), new Int32V(Range.NONE, 1), null);
        validator.trimExpression(invalid, Map.of());
        assertTrue(diags.hasError());
    }

    @Test
    void logicalOperatorsRequireBooleans() {
        var expr = new InfixExpr(Range.NONE, InfixOp.AND, boolLiteral(true), boolLiteral(false), null);
        var type = validator.trimExpression(expr, Map.of());
        assertTrue(type instanceof BoolT);

        var invalid = new InfixExpr(Range.NONE, InfixOp.AND, new Int32V(Range.NONE, 1), boolLiteral(true), null);
        validator.trimExpression(invalid, Map.of());
        assertTrue(diags.hasError());
    }

    @Test
    void inOperatorSupportsListAndMap() {
        var listExpr = new InfixExpr(Range.NONE, InfixOp.IN, new Int32V(Range.NONE, 1), idExpr("numbers"), null);
        var listType = validator.trimExpression(listExpr,
                Map.of("numbers", new ListT(Range.NONE, new Int32T(Range.NONE))));
        assertTrue(listType instanceof BoolT);
        assertFalse(diags.hasError());

        diags = new Diagnostics();
        validator = new ExprValidator(diags);
        var mapExpr = new InfixExpr(Range.NONE, InfixOp.IN, new StringV(Range.NONE, "key"), idExpr("dictionary"), null);
        var mapType = validator.trimExpression(mapExpr,
                Map.of("dictionary", new MapT(Range.NONE, new StringT(Range.NONE), new BoolT(Range.NONE))));
        assertTrue(mapType instanceof BoolT);
        assertFalse(diags.hasError());

        diags = new Diagnostics();
        validator = new ExprValidator(diags);
        var invalid = new InfixExpr(Range.NONE, InfixOp.IN, new Int32V(Range.NONE, 1), boolLiteral(true), null);
        validator.trimExpression(invalid, Map.of());
        assertTrue(diags.hasError());
    }

    @Test
    void betweenRequiresBooleanLeftAndNumericBounds() {
        var ok = new Ternary(Range.NONE, TernaryOp.BETWEEN, boolLiteral(true), new Int32V(Range.NONE, 1),
                new Int32V(Range.NONE, 10), null);
        validator.trimExpression(ok, Map.of());
        assertFalse(diags.hasError());

        diags = new Diagnostics();
        validator = new ExprValidator(diags);
        var invalid = new Ternary(Range.NONE, TernaryOp.BETWEEN, new Int32V(Range.NONE, 1), boolLiteral(true),
                boolLiteral(false), null);
        validator.trimExpression(invalid, Map.of());
        assertTrue(diags.hasError());
    }

    @Test
    void decimalLiteralAlignsToTargetScale() {
        var decimalType = new DecimalT(Range.NONE, (byte)18, (byte)2);
        var literal = new Float64V(Range.NONE, 100.0);
        var aligned = validator.alignFractionalValue(literal, decimalType);

        assertTrue(aligned instanceof DecimalV dv && dv.value().scale() == 2);
        assertFalse(diags.hasError());
    }

    @Test
    void decimalLiteralOverflowRaisesDiagnostic() {
        var decimalType = new DecimalT(Range.NONE, (byte)5, (byte)2);
        var literal = new DecimalV(Range.NONE, new BigDecimal("1234.567"));
        validator.alignFractionalValue(literal, decimalType);

        assertTrue(diags.hasError());
    }

    private IdentifierExpr idExpr(String name) {
        return new IdentifierExpr(Range.NONE, id(name), null);
    }

    private Identifier id(String name) {
        return new Identifier(Range.NONE, name);
    }

    private BoolV boolLiteral(boolean value) {
        return new BoolV(Range.NONE, value);
    }

    private StructT structWithField(String fieldName, AnyT type) {
        var fields = new FieldList(Range.NONE);
        fields.add(new Field(Range.NONE, id(fieldName), type, null, null));
        return new StructT(Range.NONE, null, fields, null);
    }
}