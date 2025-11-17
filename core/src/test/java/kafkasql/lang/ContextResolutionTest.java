package kafkasql.lang;

import org.junit.jupiter.api.Test;

import kafkasql.lang.ast.*;
import kafkasql.util.TestHelpers;

import static org.junit.jupiter.api.Assertions.*;

public class ContextResolutionTest {
  @Test
  public void relativeContextChaining(){
    var result = TestHelpers.parseAssert("USE CONTEXT com; CREATE CONTEXT example; USE CONTEXT example; CREATE STRUCT Foo ( Bar STRING );");
    // stmts: UseContext(com), ContextDecl(com.example), UseContext(com.example), StructDecl(com.example.Foo)
    assertEquals(4, result.size());
    var uc1 = (UseContext)result.get(0);
    var cd  = (CreateContext)result.get(1);
    var uc2 = (UseContext)result.get(2);
    var sd  = (CreateType)result.get(3);
    assertEquals("com", uc1.qname().fullName());
    assertEquals("example", cd.context().qName().fullName());
    assertEquals("example", uc2.qname().fullName());
    assertEquals("Foo", sd.qName().fullName());
  }

  @Test
  public void absoluteCreateContext(){
    var result = TestHelpers.parseAssert("USE CONTEXT com; CREATE CONTEXT example; CREATE STRUCT Foo ( Bar STRING );");
    assertEquals(3, result.size());
    var uc = (UseContext)result.get(0);
    var cd = (CreateContext)result.get(1);
    var sd = (CreateType)result.get(2);
    assertEquals("com", uc.qname().fullName());
    assertEquals("example", cd.qName().fullName());
    assertEquals("Foo", sd.qName().fullName());
  }
}
