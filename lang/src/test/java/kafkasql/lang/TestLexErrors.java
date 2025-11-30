package kafkasql.lang;

import org.junit.jupiter.api.Test;

import kafkasql.util.TestHelpers;

import static org.junit.jupiter.api.Assertions.*;

public class TestLexErrors {

  @Test
  public void testInvalidInputProducesErrors() {
    var result = TestHelpers.parse("X");

    // ANY error is fine (lex or parse)
    assertTrue(result.diags().hasError(), "Expected parse to produce diagnostic errors");
  }
}