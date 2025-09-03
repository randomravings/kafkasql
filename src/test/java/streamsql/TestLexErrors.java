package streamsql;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TestLexErrors {

  @Test
  public void testLexErrors() {
    var catalog = new Catalog();
    var result = ParseHelpers.parse(catalog, "X");
    assertEquals(0, result.stmts().size());
    assertTrue(result.diags().hasErrors());
  }
}
