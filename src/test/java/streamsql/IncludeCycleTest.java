package streamsql;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
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

      Files.writeString(dir.resolve("example.sqls"),
        "CREATE STRUCT Example ( Id INT32 );\n");

      Files.writeString(dir.resolve("Foo.sqls"),
        "INCLUDE 'com/example/example.sqls';\n" +
        "INCLUDE 'com/example/Bar.sqls';\n" +
        "CREATE STRUCT Foo ( Id INT32 );\n");

      Files.writeString(dir.resolve("Bar.sqls"),
        "INCLUDE 'com/example/example.sqls';\n" +
        "INCLUDE 'com/example/Foo.sqls';\n" +
        "CREATE STRUCT Bar ( Id INT32 );\n");

      Diagnostics diags = new Diagnostics();
      IncludeResolver.Result res =
        IncludeResolver.resolve(diags, wd, wd.resolve("com/example/Foo.sqls"));

      assertTrue(diags.hasErrors(), "Should report a cycle");
      String all = String.join("\n", diags.errors());
      assertTrue(all.matches("(?s).*Include cycle detected.*"),
        "Cycle message should mention cycle: " + all);
      assertTrue(res.orderedFiles.isEmpty() || res.orderedFiles.size() <= 2);
    }
  }

  @Test
  void noCycleProducesOrderedList() throws Exception {
    try (var fs = Jimfs.newFileSystem(Configuration.unix())) {
      Path wd = fs.getPath("/work");
      Path dir = wd.resolve("com/example");
      Files.createDirectories(dir);

      Path a = dir.resolve("A.sqls");
      Path b = dir.resolve("B.sqls");
      Path c = dir.resolve("C.sqls");

      Files.writeString(c, "CREATE STRUCT C ( Id INT32 );\n");
      Files.writeString(b, "INCLUDE 'com/example/C.sqls';\nCREATE STRUCT B ( Id INT32 );\n");
      Files.writeString(a, "INCLUDE 'com/example/B.sqls';\nCREATE STRUCT A ( Id INT32 );\n");

      Diagnostics diags = new Diagnostics();
      IncludeResolver.Result res =
        IncludeResolver.resolve(diags, wd, wd.resolve("com/example/A.sqls"));

      assertFalse(diags.hasErrors(), "No cycle expected");
      assertEquals(List.of(c.toAbsolutePath().normalize(),
                           b.toAbsolutePath().normalize(),
                           a.toAbsolutePath().normalize()),
                   res.orderedFiles,
                   "Files should be ordered dependencies-first");
    }
  }
}