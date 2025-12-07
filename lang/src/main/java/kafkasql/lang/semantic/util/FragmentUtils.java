package kafkasql.lang.semantic.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import kafkasql.runtime.diagnostics.DiagnosticCode;
import kafkasql.runtime.diagnostics.DiagnosticKind;
import kafkasql.runtime.diagnostics.Diagnostics;
import kafkasql.lang.semantic.factory.LiteralValueFactory;
import kafkasql.lang.syntax.ast.AstListNode;
import kafkasql.lang.syntax.ast.fragment.CheckNode;
import kafkasql.lang.syntax.ast.fragment.ConstraintNode;
import kafkasql.lang.syntax.ast.fragment.DeclFragment;
import kafkasql.lang.syntax.ast.fragment.DefaultNode;
import kafkasql.lang.syntax.ast.fragment.DocNode;

public class FragmentUtils {
    private FragmentUtils() { }

    public static Optional<String> extractDoc(
        AstListNode<DeclFragment> fragments,
        Diagnostics diags
    ) {
        return extractFragment(
            fragments,
            (DocNode d, Diagnostics dg) -> d.comment(),
            diags,
            DocNode.class
        );
    }

    public static Optional<Object> extractDefault(
        AstListNode<DeclFragment> fragments,
        Diagnostics diags
    ) {
        return extractFragment(
            fragments,
            (k, dg) -> LiteralValueFactory.evaluate(k.value()),
            diags,
            DefaultNode.class
        );
    }
    
    /**
     * Extract a single CHECK fragment (for scalar types).
     * Returns error if multiple CHECKs found.
     */
    public static Optional<CheckNode> extractCheck(
        AstListNode<DeclFragment> fragments,
        Diagnostics diags
    ) {
        return extractFragment(
            fragments,
            (CheckNode c, Diagnostics dg) -> c,
            diags,
            CheckNode.class
        );
    }
    
    /**
     * Extract all named CONSTRAINT fragments containing CHECKs (for struct types).
     * Returns list of constraint names and their check nodes.
     */
    public static List<NamedConstraint> extractNamedConstraints(
        AstListNode<DeclFragment> fragments,
        Diagnostics diags
    ) {
        List<NamedConstraint> result = new ArrayList<>();
        
        for (DeclFragment frag : fragments) {
            if (frag instanceof ConstraintNode constraint) {
                // The constraint's nested fragment should be a CHECK
                if (constraint.fragment() instanceof CheckNode check) {
                    result.add(new NamedConstraint(constraint.name().name(), check));
                }
            }
        }
        
        return result;
    }
    
    /**
     * Check if there are any direct CHECK fragments (not wrapped in CONSTRAINT).
     * Used to validate that structs don't have direct CHECKs.
     */
    public static boolean hasDirectCheck(AstListNode<DeclFragment> fragments) {
        return fragments.stream().anyMatch(f -> f instanceof CheckNode);
    }
    
    public record NamedConstraint(String name, CheckNode check) { }
        
    private static <T extends DeclFragment, V> Optional<V> extractFragment(
        AstListNode<DeclFragment> fragments,
        DeclFragmentMapper<T, V> mapper,
        Diagnostics diags,
        Class<T> clazz
    ) {
        List<T> result = fragments.stream()
            .filter(f -> clazz.isInstance(f))
            .map(f -> clazz.cast(f))
            .toList();
        if (result.isEmpty())
            return Optional.empty();
        if (result.size() > 1)
            diags.error(
                result.get(1).range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.DUPLICATE_FRAGMENTS,
                "Multiple declaration fragments of type "
                    + clazz.getSimpleName()
                    + " found; only one is allowed."
            );
        return Optional.of(mapper.apply(clazz.cast(result.getFirst()), diags));

    }

    @FunctionalInterface
    interface DeclFragmentMapper<T extends DeclFragment, V> {
        public V apply(T fragment, Diagnostics diags);
    }
}
