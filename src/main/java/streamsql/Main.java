package streamsql;

import java.nio.file.*;
import java.util.*;

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
    System.out.println("  -h, --help          Show this help");
  }

  public static void main(String[] args) throws Exception {
    Path workingDir = null;
    boolean printAst = false;
    boolean resolveIncludes = true;
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

    var catalog = new Catalog();

    boolean anyErrors = false;

    if (inlineText != null) {
      System.out.println("==> (inline)");
      var pr = ParseHelpers.parse(catalog, inlineText);
      var diags = getDiagnostics(pr);
      if (!diags.errors().isEmpty()) {
        anyErrors = true;
        System.out.println("Errors:");
        diags.errors().forEach(e -> System.out.println(" - " + e));
      } else if (printAst) {
        printAst(pr);
      }
    } else {
      // Normalize file args relative to workingDir (avoid double prefix)
      Path wd = workingDir;
      fileArgs = fileArgs.stream().map(p -> {
        Path abs = p.toAbsolutePath().normalize();
        if (abs.startsWith(wd)) return wd.relativize(abs);
        return p;
      }).toList();

      var pr = ParseHelpers.parseFiles(catalog, resolveIncludes, wd,
        fileArgs.toArray(Path[]::new));
      var diags = getDiagnostics(pr);
      if (!diags.errors().isEmpty()) {
        anyErrors = true;
        System.out.println("Errors:");
        diags.errors().forEach(e -> System.out.println(" - " + e));
      } else if (printAst) {
        printAst(pr);
      }
    }

    if (anyErrors) System.exit(1);
  }

  // Helpers to adapt to possible naming differences
  private static Diagnostics getDiagnostics(Object pr) {
    try {
      return (Diagnostics) pr.getClass().getMethod("diags").invoke(pr);
    } catch (NoSuchMethodException e) {
      try {
        return (Diagnostics) pr.getClass().getMethod("diagnostics").invoke(pr);
      } catch (Exception ex) {
        throw new RuntimeException("ParseResult missing diags()/diagnostics()", ex);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private static List<?> getStatements(Object pr) {
    try {
      return (List<?>) pr.getClass().getMethod("stmts").invoke(pr);
    } catch (NoSuchMethodException e) {
      try {
        return (List<?>) pr.getClass().getMethod("statements").invoke(pr);
      } catch (Exception ex) {
        throw new RuntimeException("ParseResult missing stmts()/statements()", ex);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void printAst(Object pr) {
    var stmts = getStatements(pr);
    // Fallback simple print if AstPrinter not available
    try {
      Class<?> printer = Class.forName("streamsql.AstPrinter");
      var m = printer.getMethod("print", List.class);
      Object out = m.invoke(null, stmts);
      System.out.println(out);
    } catch (Exception ignore) {
      stmts.forEach(s -> System.out.println(s.toString()));
    }
  }

  private static void err(String m) {
    System.err.println("error: " + m);
  }
}