package kafkasql.core;

import org.junit.jupiter.api.Test;

import kafkasql.core.util.TestHelpers;

import static org.junit.jupiter.api.Assertions.*;

public class TestLexErrors {

  @Test
  public void testLexErrors() {
    var result = TestHelpers.parse("X");
    assertEquals(0, result.ast().statements().size());
    assertTrue(result.diags().hasErrors());
  }
}
