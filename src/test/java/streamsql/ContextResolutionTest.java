package streamsql;

import org.junit.jupiter.api.Test;
import streamsql.ast.*;
import static org.junit.jupiter.api.Assertions.*;

public class ContextResolutionTest {

  @Test
  public void relativeContextChaining(){
    var catalog = new Catalog();
    var result = ParseHelpers.parse(catalog, "USE CONTEXT com; CREATE CONTEXT example; USE CONTEXT example; CREATE STRUCT Foo ( Bar STRING );");
    // stmts: UseContext(com), ContextDecl(com.example), UseContext(com.example), StructDecl(com.example.Foo)
    assertEquals(4, result.stmts().size());
    var uc1 = (UseContext)result.stmts().get(0);
    var cd  = (CreateContext)result.stmts().get(1);
    var uc2 = (UseContext)result.stmts().get(2);
    var sd  = (CreateType)result.stmts().get(3);
    assertEquals("com", uc1.context().qName().fullName());
    assertEquals("com.example", cd.qName().fullName());
    assertEquals("com.example", uc2.context().qName().fullName());
    assertEquals("com.example.Foo", sd.qName().fullName());
  }

  @Test
  public void absoluteContextFromNested(){
    var catalog = new Catalog();
    var result = ParseHelpers.parse(catalog, "USE CONTEXT com; USE CONTEXT .example; CREATE STRUCT Foo ( Bar STRING );");
    // After '.example' context should be root-based 'example'
    assertEquals(3, result.stmts().size());
    var uc1 = (UseContext)result.stmts().get(0);
    var uc2 = (UseContext)result.stmts().get(1);
    var sd  = (CreateType)result.stmts().get(2);
    assertEquals("com", uc1.context().qName().fullName());
    assertEquals("example", uc2.context().qName().fullName());
    assertEquals("example.Foo", sd.qName().fullName());
  }

  @Test
  public void absoluteCreateContext(){
    var catalog = new Catalog();
    var result = ParseHelpers.parse(catalog, "USE CONTEXT com; CREATE CONTEXT example; CREATE STRUCT Foo ( Bar STRING );");
    // CREATE CONTEXT .example declares 'example' at root; struct under com (current context unchanged)
    assertEquals(3, result.stmts().size());
    var uc = (UseContext)result.stmts().get(0);
    var cd = (CreateContext)result.stmts().get(1);
    var sd = (CreateType)result.stmts().get(2);
    assertEquals("com", uc.context().qName().fullName());
    assertEquals("com.example", cd.qName().fullName());
    assertEquals("com.Foo", sd.qName().fullName());
  }
}
