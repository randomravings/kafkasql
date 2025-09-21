package kafkasql.cli;

import java.io.Console;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.*;
import java.util.*;

import kafkasql.core.AstPrinter;
import kafkasql.core.Diagnostics;
import kafkasql.core.ParseArgs;
import kafkasql.core.KafkaSqlParser;
import kafkasql.core.ParseResult;
import kafkasql.core.Printer;
import kafkasql.core.ast.Ast;

public class Main {

  private static void usage() {
    System.out.println("Usage:");
    System.out.println("  kafkasql [-a] [-n] (-f <f1.kafka>[,<f2.kafka>...] ...)");
    System.out.println("  kafkasql [-a] -t <script...>");
    System.out.println("Options:");
    System.out.println("  -w, --working-dir   Base directory for includes (default: .)");
    System.out.println("  -f, --files         Comma separated list (can repeat)");
    System.out.println("  -t, --text          Inline script (consumes all remaining args)");
    System.out.println("  -n, --no-include    Disable INCLUDE resolution");
    System.out.println("  -a, --print-ast     Print AST if parse succeeds");
    System.out.println("  -v, --verbose       Enable antlr trace output");
    System.out.println("  -h, --help          Show this help");
  }

  public static void main(String[] args) throws Exception {
    String workingDir = null;
    boolean printAst = false;
    boolean resolveIncludes = true;
    boolean trace = false;
    String inlineText = null;
    List<String> fileArgs = new ArrayList<>();

    for (int i = 0; i < args.length; i++) {
      String a = args[i];
      switch (a) {
        case "-w", "--working-dir" -> {
          if (++i >= args.length) { err("missing value for --working-dir/-w"); return; }
          workingDir = args[i].trim();
        }
        case "-f", "--files" -> {
          if (++i >= args.length) { err("missing value for --files/-f"); return; }
            for (String f : args[i].split(",")) {
              if (!f.isBlank()) fileArgs.add(f.trim());
            }
        }
        case "-n", "--no-include" -> resolveIncludes = false;
        case "-a", "--print-ast" -> printAst = true;
        case "-v", "--verbose" -> trace = true;
        case "-t", "--text" -> {
          if (inlineText != null) { err("--text/-t specified multiple times"); return; }
          if (++i >= args.length) { err("missing inline script after --text/-t"); return; }
          var sb = new StringBuilder();
          while (i < args.length) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(args[i++]);
          }
          inlineText = sb.toString();
          break;
        }
        case "-h", "--help" -> { usage(); return; }
        default -> {
          if (a.startsWith("-")) {
            err("Unknown option: " + a);
            usage();
            return;
          } else {
            fileArgs.add(a);
          }
        }
      }
    }

    if (inlineText != null && !fileArgs.isEmpty()) {
      err("cannot combine --text with --files");
      usage();
      return;
    }

    Path wd = Path.of(workingDir != null ? workingDir : ".").toAbsolutePath().normalize();
    ParseArgs parseArgs = new ParseArgs(wd, resolveIncludes, trace);
    ParseResult parseResult = null;

    if (inlineText != null) {
      parseResult = KafkaSqlParser.parseText(inlineText, parseArgs);
    } else if (!fileArgs.isEmpty()) {
      List<Path> fs = fileArgs.stream().map(p -> {
        Path path = Path.of(p);
        if (path.isAbsolute())
          return path.normalize();
        else
          return wd.resolve(p).toAbsolutePath().normalize();
      }).toList();

      parseResult = KafkaSqlParser.parseFiles(fs, parseArgs);
    } else {
      System.err.println("error: no input (use --files or --text)");
      usage();
      System.exit(1);
    }

    if (parseResult.diags().hasErrors()) {
      System.out.println("Parsing failed with errors:");
      printDiags(parseResult.diags());
      System.exit(1);
    }

    parseResult = KafkaSqlParser.validate(parseResult);
    if (parseResult.diags().hasErrors()) {
      System.out.println("Validation failed with errors:");
      printDiags(parseResult.diags());
      System.exit(1);
    }

    if(printAst) {
      printAst(parseResult.ast());
    }

    System.exit(0);
  }

  private static void printDiags(Diagnostics diags) {
    for (var e : diags.all()) {
      System.out.println(" - " + e);
    }
  }

  private static void printAst(Ast ast) throws IOException {
    Writer out = new OutputStreamWriter(System.out);
    Printer printer = new AstPrinter(out);
    printer.write(ast);
    out.flush();
  }

  private static void err(String m) {
    System.err.println("error: " + m);
  }
}