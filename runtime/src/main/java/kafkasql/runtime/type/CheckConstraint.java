package kafkasql.runtime.type;

import kafkasql.runtime.expr.RuntimeExpr;
import java.util.Set;

/**
 * Represents a CHECK constraint that can be evaluated at runtime.
 * 
 * For SCALAR types: always unnamed, references "value"
 * For STRUCT types: always named via CONSTRAINT, references field names
 */
public record CheckConstraint(
    String name,                    // Required for struct constraints
    RuntimeExpr expr,               // Runtime expression tree (independent of lang AST)
    Set<String> referencedFields    // Field names used in expression (or "value" for scalars)
) { }
