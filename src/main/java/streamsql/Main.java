package streamsql;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.*;
import java.util.*;

import streamsql.ast.Stmt;

public class Main {

  private static void usage() {
    System.out.println("Usage:");
    System.out.println("  kafkasql [-a] [-n] (-f <f1.sqls>[,<f2.sqls>...] ...)");
    System.out.println("  kafkasql [-a] -t <script...>");
    System.out.println("Options:");
    System.out.println("  -w, --working-dir   Base directory for includes (default: .)");
    System.out.println("  -f, --files         Comma separated list (can repeat)");
    System.out.println("  -t, --text          Inline script (consumes all remaining args)");
    System.out.println("  -n, --no-include    Disable INCLUDE resolution");
    System.out.println("  -a, --print-ast     Print AST if parse succeeds");
    System.out.println("  -T, --trace         Enable parser trace output");
    System.out.println("  -h, --help          Show this help");
  }

  public static void main(String[] args) throws Exception {
    Path workingDir = null;
    boolean printAst = false;
    boolean resolveIncludes = true;
    boolean trace = false;
    String inlineText = null;
    List<Path> fileArgs = new ArrayList<>();

    for (int i = 0; i < args.length; i++) {
      String a = args[i];
      switch (a) {
        case "-w", "--working-dir" -> {
          if (++i >= args.length) { err("missing value for --working-dir/-w"); return; }
          workingDir = Paths.get(args[i]);
        }
        case "-f", "--files" -> {
          if (++i >= args.length) { err("missing value for --files/-f"); return; }
            for (String f : args[i].split(",")) {
              if (!f.isBlank()) fileArgs.add(Paths.get(f.trim()));
            }
        }
        case "-n", "--no-include" -> resolveIncludes = false;
        case "-a", "--print-ast" -> printAst = true;
        case "-T", "--trace" -> trace = true;
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
            fileArgs.add(Paths.get(a));
          }
        }
      }
    }

    if (trace) {
      // make trace flag available globally as well (keeps compatibility)
      System.setProperty("kafkasql.trace", "true");
    }

    if (inlineText != null && !fileArgs.isEmpty()) {
      err("cannot combine --text with --files");
      usage();
      return;
    }
    if (inlineText == null && fileArgs.isEmpty()) {
      err("no input (use --files or --text)");
      usage();
      return;
    }

    if (workingDir == null) workingDir = Paths.get(".").toAbsolutePath().normalize();
    else workingDir = workingDir.toAbsolutePath().normalize();

    // New: validate working directory
    if (!Files.exists(workingDir)) {
      err("working directory does not exist: " + workingDir);
      System.exit(2);
    }
    if (!Files.isDirectory(workingDir)) {
      err("working directory is not a directory: " + workingDir);
      System.exit(2);
    }
    if (!Files.isReadable(workingDir)) {
      err("working directory not readable: " + workingDir);
      System.exit(2);
    }

    // catalog still created for later phases (validation, etc.)
    var catalog = new Catalog();

    boolean anyErrors = false;

    // Build ParseArgs and pass to ParseHelpers
    ParseArgs parseArgs = new ParseArgs(resolveIncludes, trace);

    if (inlineText != null) {
      System.out.println("==> (inline)");
      var pr = ParseHelpers.parse(parseArgs, inlineText);
      if (!pr.diags().errors().isEmpty()) {
        anyErrors = true;
        System.out.println("Errors:");
        pr.diags().errors().forEach(e -> System.out.println(" - " + e));
      } else if (printAst) {
        printAst(pr.stmts());
      }
    } else {
      // Normalize file args relative to workingDir (avoid double prefix)
      Path wd = workingDir;
      fileArgs = fileArgs.stream().map(p -> {
        Path abs = p.toAbsolutePath().normalize();
        if (abs.startsWith(wd)) return wd.relativize(abs);
        return p;
      }).toList();

      var pr = ParseHelpers.parseFiles(wd, fileArgs, parseArgs);
      if (pr.diags().hasErrors()) {
        anyErrors = true;
        System.out.println("Syntax errors:");
        pr.diags().errors().forEach(e -> System.out.println(" - " + e));
      }

      var vr = ParseHelpers.validate(catalog, pr.stmts());
      if (vr.diags().hasErrors()) {
        anyErrors = true;
        System.out.println("Validation errors:");
        vr.diags().errors().forEach(e -> System.out.println(" - " + e));
      } else if (printAst) {
        printAst(vr.stmts());
      }
    }

    if (anyErrors) System.exit(1);
  }

  private static void printAst(List<Stmt> stmts) throws IOException {
    Writer out = new OutputStreamWriter(System.out);
    Printer printer = new AstPrinter(out);
    printer.write(stmts);
    out.flush();
  }

  private static void err(String m) {
    System.err.println("error: " + m);
  }
}