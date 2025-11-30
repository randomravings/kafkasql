package kafkasql.lang;

import org.junit.jupiter.api.Test;

import kafkasql.lang.syntax.ast.decl.ContextDecl;
import kafkasql.lang.syntax.ast.decl.TypeDecl;
import kafkasql.lang.syntax.ast.stmt.CreateStmt;
import kafkasql.lang.syntax.ast.stmt.UseStmt;
import kafkasql.lang.syntax.ast.use.ContextUse;
import kafkasql.util.TestHelpers;

import static org.junit.jupiter.api.Assertions.*;

public class ContextResolutionTest {

    @Test
    public void relativeContextChaining() {
        var stmts = TestHelpers.parseAssert(
            "USE CONTEXT com; " +
            "CREATE CONTEXT example; " +
            "USE CONTEXT example; " +
            "CREATE STRUCT Foo ( Bar STRING );"
        );

        assertEquals(4, stmts.size());

        UseStmt   uc1 = (UseStmt) stmts.get(0);
        CreateStmt cc  = (CreateStmt) stmts.get(1);
        UseStmt   uc2 = (UseStmt) stmts.get(2);
        CreateStmt   ct  = (CreateStmt) stmts.get(3);

        // These reflect EXACT parsing semantics — no resolution is done yet.
        assertEquals("com", ((ContextUse)uc1.target()).qname().fullName());
        TestHelpers.assertDecl(
            ContextDecl.class,
            cc,
            "example"
        );
        assertEquals("example", ((ContextUse) uc2.target()).qname().fullName());
        TestHelpers.assertDecl(
            TypeDecl.class,
            ct,
            "Foo"
        );
    }

    @Test
    public void absoluteCreateContext() {
        var stmts = TestHelpers.parseAssert(
            "USE CONTEXT com; " +
            "CREATE CONTEXT example; " +
            "CREATE STRUCT Foo ( Bar STRING );"
        );

        assertEquals(3, stmts.size());

        UseStmt   uc = (UseStmt) stmts.get(0);
        CreateStmt cc = (CreateStmt) stmts.get(1);
        CreateStmt   ct = (CreateStmt) stmts.get(2);

        assertEquals("com", ((ContextUse) uc.target()).qname().fullName());
        TestHelpers.assertDecl(
            ContextDecl.class,
            cc,
            "example"
        );
        TestHelpers.assertDecl(
            TypeDecl.class,
            ct,
            "Foo"
        );
    }
    
    

}