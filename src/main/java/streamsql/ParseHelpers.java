package streamsql;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import streamsql.ast.Stmt;
import streamsql.lex.SqlStreamLexer;
import streamsql.parse.SqlStreamParser;

public class ParseHelpers {
  public static ParseResult parse(Catalog catalog, String... srcs) {
    var diags = new Diagnostics();
    var asts = new ArrayList<Stmt>();

    for (int i = 0; i < srcs.length; i++) {
      var errs = CollectingErrorListener.withDiagnostics(String.valueOf(i), diags);

      String src = srcs[i];
      var lexer = new SqlStreamLexer(CharStreams.fromString(src));
      lexer.removeErrorListeners();
      lexer.addErrorListener(errs);

      var tokens = new CommonTokenStream(lexer);

      var parser = new SqlStreamParser(tokens);
      parser.removeErrorListeners();
      parser.addErrorListener(errs);
      //parser.setErrorHandler(new BailErrorStrategy());

      var tree = parser.script();

      if (diags.hasErrors() || parser.getNumberOfSyntaxErrors() > 0)
        return new ParseResult(List.of(), diags);

      var stmts = new AstBuilder().visitScript(tree);
      asts.addAll(stmts);
    }
    return new ParseResult(asts, diags);
  }

  /**
   * Parse one or more root files.
   * @param resolveIncludes if true, recursively expands INCLUDE pragmas (top-only)
   *                        producing a topologically ordered file list and ignoring duplicates.
   *                        Cycles are reported as errors.
   */
  public static ParseResult parseFiles(Catalog catalog,
                                       boolean resolveIncludes,
                                       Path workingDir,
                                       Path... roots) {
    var diags = new Diagnostics();
    List<Path> ordered;

    if (resolveIncludes) {
      var incResult = IncludeResolver.resolve(diags, workingDir, roots);
      if (diags.hasErrors())
        return new ParseResult(List.of(), diags);
      ordered = incResult.orderedFiles;
    } else {
      ordered = new ArrayList<>();
      for (Path r : roots) ordered.add(r.toAbsolutePath().normalize());
    }

    var all = new ArrayList<Stmt>();
    for (Path file : ordered) {
      var errs = CollectingErrorListener.withDiagnostics(file.toString(), diags);
      String text;
      try {
        text = java.nio.file.Files.readString(file);
      } catch (Exception e) {
        diags.error("File: " + file.toString() + " [0:0] Failed to read file: " + e.getMessage());
        return new ParseResult(List.of(), diags);
      }

      var lexer = new SqlStreamLexer(CharStreams.fromString(text));
      lexer.removeErrorListeners();
      lexer.addErrorListener(errs);

      var tokens = new CommonTokenStream(lexer);
      var parser = new SqlStreamParser(tokens);
      parser.removeErrorListeners();
      parser.addErrorListener(errs);

      var tree = parser.script();
      if (diags.hasErrors() || parser.getNumberOfSyntaxErrors() > 0)
        return new ParseResult(List.of(), diags);

      all.addAll(new AstBuilder().visitScript(tree));
    }
    return new ParseResult(all, diags);
  }

  // Backward compatible old signature
  public static ParseResult parseFiles(Catalog catalog,
                                       boolean resolveIncludes,
                                       Path... roots) {
    return parseFiles(catalog, resolveIncludes, null, roots);
  }
}
