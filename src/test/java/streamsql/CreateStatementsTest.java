package streamsql;

import org.junit.jupiter.api.*;

import streamsql.ast.CreateContext;
import streamsql.ast.CreateType;
import streamsql.ast.Int32T;
import streamsql.ast.CreateStream;
import streamsql.ast.StreamCompact;
import streamsql.ast.StreamInlineT;
import streamsql.ast.StreamLog;
import streamsql.ast.StreamReferenceT;
import streamsql.ast.Struct;
import streamsql.ast.Union;
import streamsql.ast.UseContext;
import streamsql.ast.Scalar;
import streamsql.ast.Stmt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CreateStatementsTest {

  /* ---------- Helpers ---------- */

  private record Parsed(List<Stmt> stmts, Diagnostics diags) {}

  private Parsed parse(String script) {
    var catalog = new Catalog();
    var result = ParseHelpers.parse(catalog, script);
    if (!result.diags().errors().isEmpty()) {
      fail("Parse errors:\n" + String.join("\n", result.diags().errors()));
    }
    return new Parsed(result.stmts(), result.diags());
  }

  private static <T> T assertType(Object stmt, Class<T> clazz) {
    assertNotNull(stmt, "Statement is null");
    assertTrue(clazz.isInstance(stmt),
      "Expected " + clazz.getSimpleName() + " but got " + stmt.getClass().getSimpleName());
    return clazz.cast(stmt);
  }

  private static <T> T only(List<Stmt> stmts, Class<T> clazz) {
    assertEquals(1, stmts.size(), "Expected exactly one statement");
    return assertType(stmts.get(0), clazz);
  }

  private static <T> T at(List<Stmt> stmts, int index, Class<T> clazz) {
    assertTrue(index >= 0 && index < stmts.size(), "Index out of range");
    return assertType(stmts.get(index), clazz);
  }

  /* ---------- Tests (strongly typed style) ---------- */

  @Test
  void createContextSimple() {
    Parsed p = parse("CREATE CONTEXT foo;");
    CreateContext ctx = only(p.stmts(), CreateContext.class);
    assertEquals("foo", ctx.qName().fullName().toString());
  }

  @Test
  void createScalarPrimitive() {
    Parsed p = parse("CREATE SCALAR MyInt AS INT32;");
    CreateType ct = only(p.stmts(), CreateType.class);
    assertEquals("MyInt", ct.qName().fullName().toString());
    Scalar scalar = assertType(ct.type(), Scalar.class);
    assertTrue(scalar.primitive() instanceof Int32T, "Expected Int32 primitive");
  }

  @Test
  void createEnumWithSymbols() {
    Parsed p = parse("""
      CREATE ENUM Color (
        Red = 1,
        Green = 2,
        Blue = 3
      );
      """);
    CreateType ct = only(p.stmts(), CreateType.class);
    streamsql.ast.Enum en = assertType(ct.type(), streamsql.ast.Enum.class);
    assertEquals("Color", ct.qName().fullName());
    assertEquals(3, en.symbols().size());
    assertEquals("Green", en.symbols().get(1).name().value());
  }

  @Test
  void createStructVariousFieldTypes() {
    Parsed p = parse("""
      CREATE STRUCT Person (
        Id INT64,
        Name STRING,
        Nick FSTRING(16) OPTIONAL,
        Score DECIMAL(10,2),
        Tags LIST<STRING>,
        Attrs MAP<STRING, INT32>,
        Friend com.example.User OPTIONAL DEFAULT 'null'
      );
      """);
    CreateType ct = only(p.stmts(), CreateType.class);
    Struct st = assertType(ct.type(), Struct.class);
    assertEquals("Person", ct.qName().fullName());
    assertEquals(7, st.fields().size());
    assertEquals("Nick", st.fields().get(2).name().value());
  }

  @Test
  void createUnionAlts() {
    Parsed p = parse("""
      CREATE UNION Value (
        I INT32,
        S STRING,
        Ref com.example.Other
      );
      """);
    CreateType ct = only(p.stmts(), CreateType.class);
    Union un = assertType(ct.type(), Union.class);
    assertEquals("Value", ct.qName().fullName());
    assertEquals(3, un.types().size());
    assertEquals("Ref", un.types().get(2).name().value());
  }

  @Test
  void createStreamLogWithInlineAndRef() {
    Parsed p = parse("""
      CREATE LOG STREAM Events
        TYPE ( Id INT32, Kind STRING ) AS Base
        TYPE com.example.Payload AS Payload;
      """);
    CreateStream cs = only(p.stmts(), CreateStream.class);
    StreamLog log = assertType(cs.stream(), StreamLog.class);
    assertEquals("Events", cs.qName().fullName());
    assertEquals(2, log.types().size());
    assertTrue(log.types().get(0) instanceof StreamInlineT);
    assertTrue(log.types().get(1) instanceof StreamReferenceT);
  }

  @Test
  void createStreamCompactMultipleInline() {
    Parsed p = parse("""
      CREATE COMPACT STREAM Session
        TYPE ( UserId INT64, Start TIMESTAMP(3) ) AS StartRec
        TYPE ( UserId INT64, End TIMESTAMP(3) ) AS EndRec
        TYPE com.example.Extra AS Extra;
      """);
    CreateStream cs = only(p.stmts(), CreateStream.class);
    StreamCompact cmp = assertType(cs.stream(), StreamCompact.class);
    assertEquals("Session", cs.qName().fullName());
    assertEquals(3, cmp.types().size());
    assertTrue(cmp.types().get(0) instanceof StreamInlineT);
    assertTrue(cmp.types().get(1) instanceof StreamInlineT);
    assertTrue(cmp.types().get(2) instanceof StreamReferenceT);
  }

  @Test
  void chainedContextThenCreateStructUsesContext() {
    Parsed p = parse("""
      CREATE CONTEXT company;
      USE CONTEXT company;
      CREATE CONTEXT finance;
      USE CONTEXT finance;
      CREATE STRUCT Account ( Id INT32 );
      """);
    assertEquals(5, p.stmts().size());
    CreateContext ctx0 = at(p.stmts(), 0, CreateContext.class);
    UseContext utx0 = at(p.stmts(), 1, UseContext.class);
    CreateContext ctx1 = at(p.stmts(), 2, CreateContext.class);
    UseContext utx1 = at(p.stmts(), 3, UseContext.class);
    CreateType ct = at(p.stmts(), 4, CreateType.class);
    Struct struct = assertType(ct.type(), Struct.class);
    assertEquals("company", ctx0.qName().fullName().toString());
    assertEquals("company", utx0.context().qName().fullName().toString());
    assertEquals("company.finance", ctx1.qName().fullName().toString());
    assertEquals("company.finance", utx1.context().qName().fullName().toString());
    assertEquals("company.finance.Account", struct.qName().fullName().toString());
  }

  @Test
  void nestedCompositeTypes() {
    Parsed p = parse("""
      CREATE STRUCT X (
        Data LIST<MAP<STRING, LIST<INT32>>>
      );
      """);
    CreateType ct = only(p.stmts(), CreateType.class);
    Struct st = assertType(ct.type(), Struct.class);
    assertEquals(1, st.fields().size());
    assertEquals("Data", st.fields().get(0).name().value());
  }

  @Nested
  @DisplayName("Lenient parsing")
  class Lenient {

    @Test
    void duplicateEnumValuesStillParse() {
      Parsed p = parse("CREATE ENUM Dups ( A = 1, B = 1 );");
      CreateType ct = only(p.stmts(), CreateType.class);
      assertEquals("Dups", ct.qName().fullName());
      assertTrue(ct.type() instanceof streamsql.ast.Enum);
    }
  }
}