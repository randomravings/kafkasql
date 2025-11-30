package kafkasql.lang;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import kafkasql.lang.diagnostics.Diagnostics;
import kafkasql.lang.input.FileInput;
import kafkasql.lang.input.Input;
import kafkasql.util.TestHelpers;

import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class IncludeCycleTest {

  @Test
  void detectsSimpleTwoFileCycle() throws Exception {
    try (var fs = Jimfs.newFileSystem(Configuration.unix())) {
      Path wd = fs.getPath("/work");
      Path dir = wd.resolve("com/example");
      Files.createDirectories(dir);

      Files.writeString(dir.resolve("example.kafka"),
        "CREATE STRUCT Example ( Id INT32 );\n");

      Files.writeString(dir.resolve("Foo.kafka"),
        "INCLUDE 'com/example/example.kafka';\n" +
        "INCLUDE 'com/example/Bar.kafka';\n" +
        "CREATE STRUCT Foo ( Id INT32 );\n");

      Files.writeString(dir.resolve("Bar.kafka"),
        "INCLUDE 'com/example/example.kafka';\n" +
        "INCLUDE 'com/example/Foo.kafka';\n" +
        "CREATE STRUCT Bar ( Id INT32 );\n");

      Diagnostics diags = new Diagnostics();
      FileInput input = new FileInput(
        "com/example/Foo.kafka",
        wd.resolve("com/example/Foo.kafka")
      );
      List<Input> res = IncludeResolver.buildIncludeOrder(List.of(input), wd, diags);

      // Add debug output
      TestHelpers.printDiagnosticsWithResult(diags, res);
      
      assertTrue(diags.hasError(), "Should report a cycle");
      assertTrue(res.isEmpty() || res.size() <= 2);
    }
  }

  @Test
  void noCycleProducesOrderedList() throws Exception {
    try (var fs = Jimfs.newFileSystem(Configuration.unix())) {
      Path wd = fs.getPath("/work");
      Path dir = wd.resolve("com/example");
      Files.createDirectories(dir);

      Path a = dir.resolve("A.kafka");
      Path b = dir.resolve("B.kafka");
      Path c = dir.resolve("C.kafka");

      Files.writeString(c, "CREATE STRUCT C ( Id INT32 );\n");
      Files.writeString(b, "INCLUDE 'com/example/C.kafka';\nCREATE STRUCT B ( Id INT32 );\n");
      Files.writeString(a, "INCLUDE 'com/example/B.kafka';\nCREATE STRUCT A ( Id INT32 );\n");

      Diagnostics diags = new Diagnostics();
      FileInput input = new FileInput(
        "com/example/A.kafka",
        wd.resolve("com/example/A.kafka")
      );
      List<Input> res = IncludeResolver.buildIncludeOrder(List.of(input), wd, diags);

      // Use utility for debug output
      TestHelpers.printDiagnosticsWithResult(diags, res);

      List<Input> expected = List.of(
        FileInput.of(c),
        FileInput.of(b),
        input
      );
      
      assertFalse(diags.hasError(), "No cycle expected. Errors: " + diags.all() + " All: " + diags);
      assertEquals(
        expected,
        res,
        "Files should be ordered dependencies-first"
      );
    }
  }
}