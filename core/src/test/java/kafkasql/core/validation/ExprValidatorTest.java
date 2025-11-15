package kafkasql.core.validation;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import kafkasql.core.Diagnostics;
import kafkasql.core.ast.Int32V;
import kafkasql.core.ast.Int8V;
import kafkasql.core.ast.Range;
import kafkasql.core.ast.VoidT;
import kafkasql.core.ast.Int8T;
import kafkasql.core.ast.AnyT;
import kafkasql.core.ast.AnyV;
import kafkasql.core.ast.Expr;
import kafkasql.core.ast.InfixExpr;
import kafkasql.core.ast.InfixOp;
import kafkasql.core.ast.Int32T;

/**
 * Basic tests for ExprValidator.trimExpression focused on literal alignment.
 */
public class ExprValidatorTest {

    @Test
    public void trimExpression_x() {
        Diagnostics diags = new Diagnostics();
        ExprValidator ev = new ExprValidator(diags);

        AnyV l = new Int32V(Range.NONE, 42);
        AnyV r = new Int32V(Range.NONE, 43);

        Expr expr = new InfixExpr(Range.NONE, InfixOp.ADD, l, r, new VoidT(Range.NONE));
        AnyT out = ev.trimExpression(expr, Map.of());

        assertNotNull(out);
        assertFalse(diags.hasErrors(), () -> "No diagnostics expected but had: " + diags);
        assertThat(out).isInstanceOf(Int32T.class);
    }

    @Test
    public void trimExpression_alignInteger_withinBoundsProducesSmallerIntegerType() {
        Diagnostics diags = new Diagnostics();
        ExprValidator ev = new ExprValidator(diags);

        // 127 fits in int8
        AnyV lit = new Int32V(Range.NONE, 127);
        AnyT target = new Int8T(Range.NONE);

        var out = ev.trimExpression(lit, target, Map.of());

        // Expect the returned expression to have been aligned to Int8V
        assertNotNull(out);
        assertFalse(diags.hasErrors(), () -> "No diagnostics expected but had: " + diags);
        assertThat(out).isInstanceOf(Int8T.class);
    }

    @Test
    public void trimExpression_alignInteger_outOfBoundsEmitsError() {
        Diagnostics diags = new Diagnostics();
        ExprValidator ev = new ExprValidator(diags);

        // 128 does not fit in int8 -> should record an error
        AnyV lit = new Int32V(Range.NONE, 128);
        AnyT target = new Int8T(Range.NONE);

        var out = ev.trimExpression(lit, target, Map.of());

        // alignment may still return the original node, but diagnostics should contain an error
        assertTrue(diags.hasErrors(), "Expected diagnostic error for out-of-range integer");
    }

    @Test
    public void trimExpression_noConstraint_returnsSameLiteral() {
        Diagnostics diags = new Diagnostics();
        ExprValidator ev = new ExprValidator(diags);

        AnyV lit = new Int32V(Range.NONE, 42);

        var out = ev.trimExpression(lit, Map.of());

        // No constraint -> should return the same/compatible literal (still an AnyV)
        assertNotNull(out);
        assertFalse(diags.hasErrors(), () -> "No diagnostics expected but had: " + diags);
    }
}