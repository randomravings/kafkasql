package kafkasql.lang.semantic.bind;

import kafkasql.lang.diagnostics.DiagnosticCode;
import kafkasql.lang.diagnostics.DiagnosticKind;
import kafkasql.lang.diagnostics.Diagnostics;
import kafkasql.lang.semantic.BindingEnv;
import kafkasql.lang.semantic.symbol.SymbolTable;
import kafkasql.lang.semantic.util.FragmentUtils;
import kafkasql.lang.semantic.util.RuntimeExprTranslator;
import kafkasql.lang.syntax.ast.decl.ScalarDecl;
import kafkasql.lang.syntax.ast.decl.StructDecl;
import kafkasql.lang.syntax.ast.decl.TypeDecl;
import kafkasql.lang.syntax.ast.expr.Expr;
import kafkasql.lang.syntax.ast.expr.IdentifierExpr;
import kafkasql.lang.syntax.ast.fragment.CheckNode;
import kafkasql.runtime.expr.RuntimeExpr;
import kafkasql.runtime.type.*;

import java.util.*;

/**
 * Binds CHECK constraints for scalar and struct types.
 */
public final class ConstraintBinder {
    
    private ConstraintBinder() {}
    
    /**
     * Bind CHECK constraint for a scalar type.
     * Scalar can have at most ONE check.
     */
    public static Optional<CheckConstraint> bindScalarCheck(
        TypeDecl typeDecl,
        ScalarDecl scalarDecl,
        PrimitiveType primitiveType,
        SymbolTable symbols,
        BindingEnv bindings,
        Diagnostics diags
    ) {
        Optional<CheckNode> checkOpt = FragmentUtils.extractCheck(typeDecl.fragments(), diags);
        if (checkOpt.isEmpty()) {
            return Optional.empty();
        }
        
        CheckNode check = checkOpt.get();
        
        // For scalars, "value" refers to the scalar itself
        TypeEnv env = new TypeEnv();
        env.define("value", primitiveType);
        
        ExpressionBinder exprBinder = new ExpressionBinder(env, symbols, diags, bindings);
        AnyType resultType = exprBinder.bind(check.expr());
        
        // Validate: CHECK must evaluate to BOOLEAN
        if (!(resultType instanceof PrimitiveType pt) || pt.kind() != PrimitiveKind.BOOLEAN) {
            diags.error(
                check.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.INVALID_CHECK_CONSTRAINT,
                "CHECK constraint must evaluate to BOOLEAN, got: " + debugType(resultType)
            );
            return Optional.empty();
        }
        
        // Translate to runtime expression
        RuntimeExpr runtimeExpr = RuntimeExprTranslator.translate(check.expr());
        
        // Extract referenced identifiers (should only be "value" for scalars)
        Set<String> referenced = extractReferencedIdentifiers(check.expr());
        
        return Optional.of(new CheckConstraint("value", runtimeExpr, referenced));
    }
    
    /**
     * Bind CHECK constraints for a struct type.
     * Struct can have multiple NAMED constraints via CONSTRAINT keyword.
     */
    public static List<CheckConstraint> bindStructChecks(
        TypeDecl typeDecl,
        StructDecl structDecl,
        StructType structType,
        SymbolTable symbols,
        BindingEnv bindings,
        Diagnostics diags
    ) {
        // First, check for direct CHECK (not allowed on structs)
        if (FragmentUtils.hasDirectCheck(typeDecl.fragments())) {
            diags.error(
                typeDecl.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.INVALID_CHECK_ON_STRUCT,
                "STRUCT types cannot have direct CHECK constraints. Use CONSTRAINT <name> CHECK(...) instead."
            );
        }
        
        // Extract named constraints
        List<FragmentUtils.NamedConstraint> namedConstraints = 
            FragmentUtils.extractNamedConstraints(typeDecl.fragments(), diags);
        
        if (namedConstraints.isEmpty()) {
            return List.of();
        }
        
        // Check for duplicate constraint names
        Set<String> seen = new HashSet<>();
        for (FragmentUtils.NamedConstraint nc : namedConstraints) {
            if (!seen.add(nc.name())) {
                diags.error(
                    nc.check().range(),
                    DiagnosticKind.SEMANTIC,
                    DiagnosticCode.DUPLICATE_CONSTRAINT_NAME,
                    "Duplicate constraint name: " + nc.name()
                );
            }
        }
        
        // Build TypeEnv with all struct fields available
        TypeEnv env = new TypeEnv();
        for (var entry : structType.fields().entrySet()) {
            env.define(entry.getKey(), entry.getValue().type());
        }
        
        List<CheckConstraint> result = new ArrayList<>();
        
        for (FragmentUtils.NamedConstraint nc : namedConstraints) {
            CheckNode check = nc.check();
            
            ExpressionBinder exprBinder = new ExpressionBinder(env, symbols, diags, bindings);
            AnyType resultType = exprBinder.bind(check.expr());
            
            // Validate: CHECK must evaluate to BOOLEAN
            if (!(resultType instanceof PrimitiveType pt) || pt.kind() != PrimitiveKind.BOOLEAN) {
                diags.error(
                    check.range(),
                    DiagnosticKind.SEMANTIC,
                    DiagnosticCode.INVALID_CHECK_CONSTRAINT,
                    "CHECK constraint must evaluate to BOOLEAN, got: " + debugType(resultType)
                );
                continue;
            }
            
            // Translate to runtime expression
            RuntimeExpr runtimeExpr = RuntimeExprTranslator.translate(check.expr());
            
            // Extract referenced field names
            Set<String> referenced = extractReferencedIdentifiers(check.expr());
            
            result.add(new CheckConstraint(nc.name(), runtimeExpr, referenced));
        }
        
        return result;
    }
    
    /**
     * Extract all identifier names referenced in an expression.
     */
    private static Set<String> extractReferencedIdentifiers(Expr expr) {
        Set<String> identifiers = new HashSet<>();
        visitExpr(expr, identifiers);
        return identifiers;
    }
    
    private static void visitExpr(Expr expr, Set<String> identifiers) {
        switch (expr) {
            case IdentifierExpr id -> identifiers.add(id.name().name());
            case kafkasql.lang.syntax.ast.expr.InfixExpr inf -> {
                visitExpr(inf.left(), identifiers);
                visitExpr(inf.right(), identifiers);
            }
            case kafkasql.lang.syntax.ast.expr.PrefixExpr pre -> visitExpr(pre.expr(), identifiers);
            case kafkasql.lang.syntax.ast.expr.PostfixExpr post -> visitExpr(post.expr(), identifiers);
            case kafkasql.lang.syntax.ast.expr.TrifixExpr tri -> {
                visitExpr(tri.left(), identifiers);
                visitExpr(tri.middle(), identifiers);
                visitExpr(tri.right(), identifiers);
            }
            case kafkasql.lang.syntax.ast.expr.ParenExpr paren -> visitExpr(paren.inner(), identifiers);
            case kafkasql.lang.syntax.ast.expr.MemberExpr mem -> visitExpr(mem.target(), identifiers);
            case kafkasql.lang.syntax.ast.expr.IndexExpr idx -> {
                visitExpr(idx.target(), identifiers);
                visitExpr(idx.index(), identifiers);
            }
            case kafkasql.lang.syntax.ast.expr.LiteralExpr lit -> {} // No identifiers
        }
    }
    
    private static String debugType(AnyType t) {
        if (t instanceof PrimitiveType pt) {
            return pt.kind().toString();
        }
        if (t instanceof ComplexType ct) {
            return ct.fqn().toString();
        }
        return t.getClass().getSimpleName();
    }
}
