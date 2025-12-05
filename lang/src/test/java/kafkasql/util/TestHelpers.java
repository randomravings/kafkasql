package kafkasql.util;

import kafkasql.lang.KafkaSqlArgs;
import kafkasql.lang.KafkaSqlParser;
import kafkasql.lang.ParseResult;
import kafkasql.lang.diagnostics.Diagnostics;
import kafkasql.lang.input.StringInput;
import kafkasql.lang.syntax.ast.*;
import kafkasql.lang.syntax.ast.decl.Decl;
import kafkasql.lang.syntax.ast.decl.TypeKindDecl;
import kafkasql.lang.syntax.ast.fragment.DeclFragment;
import kafkasql.lang.syntax.ast.stmt.Stmt;
import kafkasql.lang.syntax.ast.type.PrimitiveTypeNode;
import kafkasql.lang.syntax.ast.type.TypeNode;
import kafkasql.runtime.type.PrimitiveKind;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Paths;
import java.util.List;

public final class TestHelpers {

    private TestHelpers() { }

    public static ParseResult parse(String text) {
        var source = new StringInput(
            "<text>",
            text
        );
        var args = new KafkaSqlArgs(
                Paths.get("."),
                false,
                false
        );
        return KafkaSqlParser.parse(List.of(source), args);
    }

    public static List<Stmt> parseAssert(String text) {
        ParseResult result = null;
        try {
            result = parse(text);
            assertFalse(result.diags().hasError());
            return flatten(result.scripts());
        } catch (Exception e) {
            throw new AssertionError("Exception during parse", e);
        } finally {
            if (result != null) {
                printDiagnostics(result.diags());
            }
        }
    }

    public static List<Stmt> flatten(List<Script> scripts) {
        List<Stmt> stmts = new java.util.ArrayList<>();
        for (Script script : scripts)
            stmts.addAll(script.statements());
        return stmts;
    }

    public static <T extends AstNode> T only(List<Stmt> stmts, Class<T> clazz) {
        assertEquals(1, stmts.size(), "Expected exactly 1 statement");
        Stmt stmt = stmts.getFirst();
        assertInstanceOf(clazz, stmt);
        return clazz.cast(stmt);
    }

    public static <T extends AstNode> T at(List<Stmt> stmts, int index, Class<T> clazz) {
        assertTrue(index >= 0 && index < stmts.size(),
                "Index out of range: " + index + " for " + stmts.size() + " statements");
        Stmt stmt = stmts.get(index);
        assertInstanceOf(clazz, stmt);
        return clazz.cast(stmt);
    }

    /**
     * Prints diagnostic information for debugging test failures.
     * 
     * @param diags the Diagnostics object to print
     */
    public static void printDiagnostics(Diagnostics diags) {
        System.out.println("Diagnostics:");

        var sortedEntries = diags.all().stream()
                .sorted((a, b) -> Integer.compare(
                        a.severity().ordinal(),
                        b.severity().ordinal()))
                .toList();

        for (var entry : sortedEntries) {
            System.out.println(entry + ": " + entry.message());
        }
    }

    /**
     * Prints diagnostic information along with result details.
     * 
     * @param diags  the Diagnostics object to print
     * @param result the result collection to print
     */
    public static void printDiagnosticsWithResult(Diagnostics diags, java.util.Collection<?> result) {
        printDiagnostics(diags);
        System.out.println("Result size: " + result.size());
        result.forEach(item -> System.out.println("  Item: " + item));
    }

    public static <T extends Decl> T assertDecl(
        Class<T> clazz,
        Decl decl,
        String name
    ) {
        assertEquals(clazz, decl.getClass());
        assertEquals(name, decl.name().name());
        T declType = clazz.cast(decl);
        return declType;
    }

    public static <T extends TypeKindDecl> T assertTypeDecl(
        Class<T> clazz,
        TypeKindDecl decl
    ) {
        assertInstanceOf(clazz, decl);
        T declType = clazz.cast(decl);
        return declType;
    }

    public static void assertPrimitive(
        TypeNode typeNode,
        PrimitiveKind kind,
        Long expectedSize,
        Long expectedPrecision,
        Long expectedScale
    ) {
        assertInstanceOf(PrimitiveTypeNode.class, typeNode);
        PrimitiveTypeNode primType = PrimitiveTypeNode.class.cast(typeNode);
        assertEquals(kind, primType.kind());
        assertEquals(expectedSize != null, primType.hasLength());
        assertEquals(expectedPrecision != null, primType.hasPrecision());
        assertEquals(expectedScale != null, primType.hasScale());
        if (expectedSize != null)
            assertEquals(expectedSize, primType.length());
        if (expectedPrecision != null)
            assertEquals(expectedPrecision, primType.precision());
        if (expectedScale != null)
            assertEquals(expectedScale, primType.scale());
    }

    public static <T extends DeclFragment> T assertSingleFragmentOf(
        AstListNode<DeclFragment> fragments,
        Class<T> clazz
    ) {
        var ofType = fragments.stream()
            .filter(f -> clazz.isInstance(f))
            .toList();
        assertEquals(1, ofType.size());
        return clazz.cast(ofType.getFirst());
    }
}