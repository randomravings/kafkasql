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
import streamsql.ast.StructT;
import streamsql.ast.UnionT;
import streamsql.ast.UseContext;
import streamsql.util.TestHelpers;
import streamsql.ast.ScalarT;
import streamsql.ast.Stmt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CreateStatementsTest {

  @Test
  void createContextSimple() {
    List<Stmt> p = TestHelpers.parseAssert("CREATE CONTEXT foo;");
    CreateContext ctx = TestHelpers.only(p, CreateContext.class);
    assertEquals("foo", ctx.qName().fullName().toString());
  }

  @Test
  void createScalarPrimitive() {
    List<Stmt> p = TestHelpers.parseAssert("CREATE SCALAR MyInt AS INT32;");
    CreateType ct = TestHelpers.only(p, CreateType.class);
    assertEquals("MyInt", ct.qName().fullName().toString());
    ScalarT scalar = TestHelpers.assertType(ct.type(), ScalarT.class);
    assertTrue(scalar.primitive() instanceof Int32T, "Expected Int32 primitive");
  }

  @Test
  void createEnumWithSymbols() {
    List<Stmt> p = TestHelpers.parseAssert("""
      CREATE ENUM Color (
        Red: 1,
        Green: 2,
        Blue: 3
      );
      """);
    CreateType ct = TestHelpers.only(p, CreateType.class);
    streamsql.ast.EnumT en = TestHelpers.assertType(ct.type(), streamsql.ast.EnumT.class);
    assertEquals("Color", ct.qName().fullName());
    assertEquals(3, en.symbols().size());
    assertEquals("Green", en.symbols().get(1).name().value());
  }

  @Test
  void createStructVariousFieldTypes() {
    List<Stmt> p = TestHelpers.parseAssert("""
      CREATE STRUCT Person (
        Id INT64,
        Name STRING,
        Nick CHAR(16) OPTIONAL,
        Score DECIMAL(10,2),
        Tags LIST<STRING>,
        Attrs MAP<STRING, INT32>,
        Friend com.example.User OPTIONAL
      );
      """);
    CreateType ct = TestHelpers.only(p, CreateType.class);
    StructT st = TestHelpers.assertType(ct.type(), StructT.class);
    assertEquals("Person", ct.qName().fullName());
    assertEquals(7, st.fields().size());
    assertEquals("Nick", st.fields().get(2).name().value());
  }

  @Test
  void createUnionAlts() {
    List<Stmt> p = TestHelpers.parseAssert("""
      CREATE UNION Value (
        I INT32,
        S STRING,
        Ref com.example.Other
      );
      """);
    CreateType ct = TestHelpers.only(p, CreateType.class);
    UnionT un = TestHelpers.assertType(ct.type(), UnionT.class);
    assertEquals("Value", ct.qName().fullName());
    assertEquals(3, un.types().size());
    assertEquals("Ref", un.types().get(2).name().value());
  }

  @Test
  void createStreamLogWithInlineAndRef() {
    List<Stmt> p = TestHelpers.parseAssert("""
      CREATE LOG STREAM Events
        TYPE ( Id INT32, Kind STRING ) AS Base
        TYPE com.example.Payload AS Payload;
      """);
    CreateStream cs = TestHelpers.only(p, CreateStream.class);
    StreamLog log = TestHelpers.assertType(cs.stream(), StreamLog.class);
    assertEquals("Events", cs.qName().fullName());
    assertEquals(2, log.types().size());
    assertTrue(log.types().get(0) instanceof StreamInlineT);
    assertTrue(log.types().get(1) instanceof StreamReferenceT);
  }

  @Test
  void createStreamCompactMultipleInline() {
    List<Stmt> p = TestHelpers.parseAssert("""
      CREATE COMPACT STREAM Session
        TYPE ( UserId INT64, Start TIMESTAMP(3) ) AS StartRec
        TYPE ( UserId INT64, End TIMESTAMP(3) ) AS EndRec
        TYPE com.example.Extra AS Extra;
      """);
    CreateStream cs = TestHelpers.only(p, CreateStream.class);
    StreamCompact cmp = TestHelpers.assertType(cs.stream(), StreamCompact.class);
    assertEquals("Session", cs.qName().fullName());
    assertEquals(3, cmp.types().size());
    assertTrue(cmp.types().get(0) instanceof StreamInlineT);
    assertTrue(cmp.types().get(1) instanceof StreamInlineT);
    assertTrue(cmp.types().get(2) instanceof StreamReferenceT);
  }

  @Test
  void chainedContextThenCreateStructUsesContext() {
    List<Stmt> p = TestHelpers.parseAssert("""
      CREATE CONTEXT company;
      USE CONTEXT company;
      CREATE CONTEXT finance;
      USE CONTEXT finance;
      CREATE STRUCT Account ( Id INT32 );
      """);
    assertEquals(5, p.size());
    CreateContext ctx0 = TestHelpers.at(p, 0, CreateContext.class);
    UseContext utx0 = TestHelpers.at(p, 1, UseContext.class);
    CreateContext ctx1 = TestHelpers.at(p, 2, CreateContext.class);
    UseContext utx1 = TestHelpers.at(p, 3, UseContext.class);
    CreateType ct = TestHelpers.at(p, 4, CreateType.class);
    StructT struct = TestHelpers.assertType(ct.type(), StructT.class);
    assertEquals("company", ctx0.qName().fullName().toString());
    assertEquals("company", utx0.context().qName().fullName().toString());
    assertEquals("finance", ctx1.qName().fullName().toString());
    assertEquals("finance", utx1.context().qName().fullName().toString());
    assertEquals("Account", struct.qName().fullName().toString());
  }

  @Test
  void nestedCompositeTypes() {
    List<Stmt> p = TestHelpers.parseAssert("""
      CREATE STRUCT X (
        Data LIST<MAP<STRING, LIST<INT32>>>
      );
      """);
    CreateType ct = TestHelpers.only(p, CreateType.class);
    StructT st = TestHelpers.assertType(ct.type(), StructT.class);
    assertEquals(1, st.fields().size());
    assertEquals("Data", st.fields().get(0).name().value());
  }

  @Nested
  @DisplayName("Lenient parsing")
  class Lenient {

    @Test
    void duplicateEnumValuesStillParse() {
      List<Stmt> p = TestHelpers.parseAssert("CREATE ENUM Dups ( A: 1, B: 1 );");
      CreateType ct = TestHelpers.only(p, CreateType.class);
      assertEquals("Dups", ct.qName().fullName());
      assertTrue(ct.type() instanceof streamsql.ast.EnumT);
    }
  }
}