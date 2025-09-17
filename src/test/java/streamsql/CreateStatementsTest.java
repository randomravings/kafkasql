package streamsql;

import org.junit.jupiter.api.*;

import streamsql.ast.Ast;
import streamsql.ast.CreateContext;
import streamsql.ast.CreateType;
import streamsql.ast.Int32T;
import streamsql.ast.CreateStream;
import streamsql.ast.StreamInlineT;
import streamsql.ast.StreamReferenceT;
import streamsql.ast.StreamT;
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
    Ast p = TestHelpers.parseAssert("CREATE CONTEXT foo;");
    CreateContext ctx = TestHelpers.only(p.statements(), CreateContext.class);
    assertEquals("foo", ctx.qName().fullName().toString());
  }

  @Test
  void createScalarPrimitive() {
    Ast p = TestHelpers.parseAssert("CREATE SCALAR MyInt AS INT32;");
    CreateType ct = TestHelpers.only(p.statements(), CreateType.class);
    assertEquals("MyInt", ct.qName().fullName().toString());
    ScalarT scalar = TestHelpers.assertType(ct.type(), ScalarT.class);
    assertTrue(scalar.primitive() instanceof Int32T, "Expected Int32 primitive");
  }

  @Test
  void createEnumWithSymbols() {
    Ast p = TestHelpers.parseAssert("""
      CREATE ENUM Color (
        Red: 1,
        Green: 2,
        Blue: 3
      );
      """);
    CreateType ct = TestHelpers.only(p.statements(), CreateType.class);
    streamsql.ast.EnumT en = TestHelpers.assertType(ct.type(), streamsql.ast.EnumT.class);
    assertEquals("Color", ct.qName().fullName());
    assertEquals(3, en.symbols().size());
    assertEquals("Green", en.symbols().get(1).name().name());
  }

  @Test
  void createStructVariousFieldTypes() {
    Ast p = TestHelpers.parseAssert("""
      CREATE STRUCT Person (
        Id INT64,
        Name STRING,
        Nick CHAR(16) NULL,
        Score DECIMAL(10,2),
        Tags LIST<STRING>,
        Attrs MAP<STRING, INT32>,
        Friend com.example.User NULL
      );
      """);
    CreateType ct = TestHelpers.only(p.statements(), CreateType.class);
    StructT st = TestHelpers.assertType(ct.type(), StructT.class);
    assertEquals("Person", ct.qName().fullName());
    assertEquals(7, st.fieldList().size());
    assertEquals("Nick", st.fieldList().get(2).name().name());
  }

  @Test
  void createUnionAlts() {
    Ast p = TestHelpers.parseAssert("""
      CREATE UNION Value (
        I INT32,
        S STRING,
        Ref com.example.Other
      );
      """);
    CreateType ct = TestHelpers.only(p.statements(), CreateType.class);
    UnionT un = TestHelpers.assertType(ct.type(), UnionT.class);
    assertEquals("Value", ct.qName().fullName());
    assertEquals(3, un.types().size());
    assertEquals("Ref", un.types().get(2).name().name());
  }

  @Test
  void createStreamLogWithInlineAndRef() {
    Ast p = TestHelpers.parseAssert("""
      CREATE STREAM Events
        TYPE ( Id INT32, Kind STRING ) AS Base
        TYPE com.example.Payload AS Payload;
      """);
    CreateStream cs = TestHelpers.only(p.statements(), CreateStream.class);
    StreamT log = TestHelpers.assertType(cs.stream(), StreamT.class);
    assertEquals("Events", cs.qName().fullName());
    assertEquals(2, log.types().size());
    assertTrue(log.types().get(0) instanceof StreamInlineT);
    assertTrue(log.types().get(1) instanceof StreamReferenceT);
  }

  @Test
  void createStreamCompactMultipleInline() {
    Ast p = TestHelpers.parseAssert("""
      CREATE STREAM Session
        TYPE ( UserId INT64, Start TIMESTAMP(3) ) AS StartRec
        TYPE ( UserId INT64, End TIMESTAMP(3) ) AS EndRec
        TYPE com.example.Extra AS Extra;
      """);
    CreateStream cs = TestHelpers.only(p.statements(), CreateStream.class);
    StreamT cmp = TestHelpers.assertType(cs.stream(), StreamT.class);
    assertEquals("Session", cs.qName().fullName());
    assertEquals(3, cmp.types().size());
    assertTrue(cmp.types().get(0) instanceof StreamInlineT);
    assertTrue(cmp.types().get(1) instanceof StreamInlineT);
    assertTrue(cmp.types().get(2) instanceof StreamReferenceT);
  }

  @Test
  void chainedContextThenCreateStructUsesContext() {
    Ast p = TestHelpers.parseAssert("""
      CREATE CONTEXT company;
      USE CONTEXT company;
      CREATE CONTEXT finance;
      USE CONTEXT finance;
      CREATE STRUCT Account ( Id INT32 );
      """);
    assertEquals(5, p.statements().size());
    CreateContext ctx0 = TestHelpers.at(p.statements(), 0, CreateContext.class);
    UseContext utx0 = TestHelpers.at(p.statements(), 1, UseContext.class);
    CreateContext ctx1 = TestHelpers.at(p.statements(), 2, CreateContext.class);
    UseContext utx1 = TestHelpers.at(p.statements(), 3, UseContext.class);
    CreateType ct = TestHelpers.at(p.statements(), 4, CreateType.class);
    StructT struct = TestHelpers.assertType(ct.type(), StructT.class);
    assertEquals("company", ctx0.qName().fullName().toString());
    assertEquals("company", utx0.qname().fullName().toString());
    assertEquals("finance", ctx1.qName().fullName().toString());
    assertEquals("finance", utx1.qname().fullName().toString());
    assertEquals("Account", struct.qName().fullName().toString());
  }

  @Test
  void nestedCompositeTypes() {
    Ast p = TestHelpers.parseAssert("""
      CREATE STRUCT X (
        Data LIST<MAP<STRING, LIST<INT32>>>
      );
      """);
    CreateType ct = TestHelpers.only(p.statements(), CreateType.class);
    StructT st = TestHelpers.assertType(ct.type(), StructT.class);
    assertEquals(1, st.fieldList().size());
    assertEquals("Data", st.fieldList().get(0).name().name());
  }

  @Nested
  @DisplayName("Lenient parsing")
  class Lenient {

    @Test
    void duplicateEnumValuesStillParse() {
      Ast p = TestHelpers.parseAssert("CREATE ENUM Dups ( A: 1, B: 1 );");
      CreateType ct = TestHelpers.only(p.statements(), CreateType.class);
      assertEquals("Dups", ct.qName().fullName());
      assertTrue(ct.type() instanceof streamsql.ast.EnumT);
    }
  }
}