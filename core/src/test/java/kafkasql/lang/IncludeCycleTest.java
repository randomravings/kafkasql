package kafkasql.lang;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

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
      var res = IncludeResolver.buildIncludeOrder(List.of(wd.resolve("com/example/Foo.kafka")), wd, diags);

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
      var res = IncludeResolver.buildIncludeOrder(List.of(wd.resolve("com/example/A.kafka")), wd, diags);

      // Use utility for debug output
      TestHelpers.printDiagnosticsWithResult(diags, res);
      
      assertFalse(diags.hasError(), "No cycle expected. Errors: " + diags.errors() + " All: " + diags);
      assertEquals(List.of(c.toAbsolutePath().normalize(),
                           b.toAbsolutePath().normalize(),
                           a.toAbsolutePath().normalize()),
                   res,
                   "Files should be ordered dependencies-first");
    }
  }
}