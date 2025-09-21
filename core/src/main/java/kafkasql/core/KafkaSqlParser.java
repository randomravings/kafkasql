package kafkasql.core;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import kafkasql.core.ast.Ast;
import kafkasql.core.lex.SqlStreamLexer;
import kafkasql.core.parse.SqlStreamParser;

public class KafkaSqlParser {

  public static ParseResult validate(Ast ast) {
    return validate(ast, new Diagnostics());
  }

  public static ParseResult validate(ParseResult result) {
    return validate(result.ast(), result.diags());
  }

  private static ParseResult validate(Ast ast, Diagnostics diags) {
    var validator = new AstValidator(diags);
    var validatedAst = validator.validate(ast);
    return new ParseResult(validatedAst, diags);
  }

  public static ParseResult parseFiles(List<Path> files, ParseArgs args) {
    var diags = new Diagnostics();
    List<Path> paths;

    if (args.resolveIncludes()) {
      var ordered = IncludeResolver.buildIncludeOrder(files, args.workspaceRoot(), diags);
      for (var s : ordered) {
        System.out.println("Including: " + s);
      }
      if (diags.hasErrors())
        return new ParseResult(Ast.EMPTY, diags);
      paths = ordered.stream().map(Path::of).toList();
    } else {
      paths = files;
    }

    var ast = new Ast();
    for (Path f : paths) {
      if (diags.hasErrors())
        break;
      try {
        var text = Files.readString(f);
        var src = new ParseSource(f.toString(), text);
        var result = parseText(src, diags, args);
        ast.addAll(result);
      } catch (IOException e) {
        diags.addFatal(Range.NONE, "Error reading file: " + f.toAbsolutePath().toString() + ": " + e.getMessage());
      }
    }
    return new ParseResult(ast, diags);
  }

  public static ParseResult parseText(String text, ParseArgs args) {
    var diags = new Diagnostics();
    var ast = new Ast();
    if (args.resolveIncludes()) {
      var dependencies = IncludeResolver.buildIncludeOrder(text, args.workspaceRoot(), diags);
      if (diags.hasErrors())
        return new ParseResult(ast, diags);
      List<Path> files = new ArrayList<>();
      for (var s : dependencies)
        files.add(Paths.get(s));
      var a = new ParseArgs(args.workspaceRoot(), false, args.trace());
      var part = parseFiles(files, a);
      if (part.diags().hasErrors())
        return part;
      else {
        ast.addAll(part.ast());
      }
    }
    var src = new ParseSource("<in-memory>", text);
    var result = parseText(src, diags, args);
    ast.addAll(result);
    return new ParseResult(ast, diags);
  }

  private static Ast parseText(ParseSource src, Diagnostics diags, ParseArgs args) {
    var errs = new CollectingErrorListener(src.source(), diags);

    var lexer = new SqlStreamLexer(CharStreams.fromString(src.text(), src.source()));
    lexer.removeErrorListeners();
    lexer.addErrorListener(errs);

    var tokens = new CommonTokenStream(lexer);
    var parser = new SqlStreamParser(tokens);
    parser.removeErrorListeners();
    parser.addErrorListener(errs);
    parser.setTrace(args.trace());

    var tree = parser.script();
    if (diags.hasErrors()) {
      return Ast.EMPTY;
    }

    var builder = new AstBuilder(src.source());
    return builder.visitScript(tree);
  }
}
