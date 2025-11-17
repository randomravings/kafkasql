package kafkasql.lang;

import org.junit.jupiter.api.Test;

import kafkasql.util.TestHelpers;

import static org.junit.jupiter.api.Assertions.*;

public class TestLexErrors {

  @Test
  public void testLexErrors() {
    var result = TestHelpers.parse("X");
    assertEquals(0, result.ast().size());
    assertTrue(result.diags().hasError());
  }
}
