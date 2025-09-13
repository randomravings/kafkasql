package streamsql;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import streamsql.ast.Stmt;
import streamsql.lex.SqlStreamLexer;
import streamsql.parse.SqlStreamParser;

public class ParseHelpers {

  public static ParseResult validate(Catalog catalog, List<Stmt> tree) {
    var validator = new Validator(catalog);
    return validator.validate(tree);
  }

  public static ParseResult parse(ParseArgs args, String... texts) {
    var diags = new Diagnostics();
    var all = new ArrayList<Stmt>();
    for (int i = 0; i < texts.length; i++) {
      ParseSource src = new ParseSource("input_" + i, texts[i]);
      ParseResult result = parseText(src, diags, args);
      if (result.diags().hasErrors())
        break;
      all.addAll(result.stmts());
    }
    return new ParseResult(all, diags);
  }

  public static ParseResult parseFiles(Path workingDir, List<Path> files, ParseArgs args) {
    var diags = new Diagnostics();
    List<Path> ordered;

    if (args.resolveIncludes) {
      var incResult = IncludeResolver.resolve(diags, workingDir, files);
      if (diags.hasErrors())
        return new ParseResult(List.of(), diags);
      ordered = incResult.orderedFiles;
    } else {
      ordered = new ArrayList<>();
      for (Path r : files) ordered.add(r.toAbsolutePath().normalize());
    }

    var all = new ArrayList<Stmt>();
    for (Path file : ordered) {
      String text;
      try {
        text = Files.readString(file);
      } catch (Exception e) {
        diags.error("File: " + file.toString() + " [0:0] Failed to read file: " + e.getMessage());
        return new ParseResult(List.of(), diags);
      }
      var result = parseText(new ParseSource(file.toString(), text), diags, args);
      if (result.diags().hasErrors())
        break;
      all.addAll(result.stmts());
    }
    return new ParseResult(all, diags);
  }

  // Centralized parsing for a single source string.
  private static ParseResult parseText(ParseSource src, Diagnostics diags, ParseArgs args) {
    var errs = CollectingErrorListener.withDiagnostics(src.source(), diags);

    var lexer = new SqlStreamLexer(CharStreams.fromString(src.text(), src.source()));
    lexer.removeErrorListeners();
    lexer.addErrorListener(errs);

    var tokens = new CommonTokenStream(lexer);
    var parser = new SqlStreamParser(tokens);
    parser.removeErrorListeners();
    parser.addErrorListener(errs);
    parser.setTrace(args.trace);

    var tree = parser.script();
    if (diags.hasErrors()) {
      return new ParseResult(List.of(), diags);
    }
    var stmts = new AstBuilder().visitScript(tree);
    return new ParseResult(stmts, diags);
  }
}
