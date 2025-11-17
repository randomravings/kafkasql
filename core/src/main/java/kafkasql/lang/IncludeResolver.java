package kafkasql.lang;

import java.nio.file.*;
import java.util.*;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CodePointCharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import kafkasql.core.lex.SqlStreamLexer;
import kafkasql.lang.ast.Range;

public final class IncludeResolver {

  private static final Path IN_MEM = Paths.get("<in-memory>");

  private enum State {
    UNVISITED, IN_PROGRESS, DONE
  }

  public static List<Path> buildIncludeOrder(String text, Path workingDir, Diagnostics diags) {
    LinkedHashMap<Path, List<Path>> graph = new LinkedHashMap<>();
    scanTopIncludes(text, workingDir, diags, graph);
    var result = new ArrayList<>(resolveFromGraph(graph, workingDir, diags));
    result.remove(IN_MEM);
    return result;
  }

  public static List<Path> buildIncludeOrder(Collection<Path> roots, Path workingDir, Diagnostics diags) {
    LinkedHashMap<Path, List<Path>> graph = new LinkedHashMap<>();
    for (Path root : roots) {
      Path normalizedRoot = normalize(root);
      scanTopIncludes(normalizedRoot, workingDir, diags, graph);
      if (diags.hasError()) {
        return List.of();
      }
    }
    return new ArrayList<>(resolveFromGraph(graph, workingDir, diags));
  }

  private static void scanTopIncludes(String text, Path workingDir, Diagnostics diags, LinkedHashMap<Path, List<Path>> graph) {
    List<Path> incs = new ArrayList<>();
    try (Reader r = new StringReader(text)) {
      scanTopIncludes(r, IN_MEM, workingDir, diags, incs);
      graph.put(IN_MEM, incs);
    } catch (IOException e) {
      diags.fatal(Range.NONE, "Failed to read " + IN_MEM + ": " + e.getMessage());
    }
  }

  private static void scanTopIncludes(Path file, Path workingDir, Diagnostics diags, LinkedHashMap<Path, List<Path>> graph) {
    List<Path> incs = new ArrayList<>();
    try (Reader r = Files.newBufferedReader(file)) {  // Changed from FileReader to work with any FileSystem
      scanTopIncludes(r, file, workingDir, diags, incs);
      graph.put(file, incs);
    } catch (IOException e) {
      diags.fatal(Range.NONE, "Failed to read " + file + ": " + e.getMessage());
    }
  }

  private static void scanTopIncludes(Reader stream, Path source, Path workingDir, Diagnostics diags, List<Path> incs) {
    try {
        CodePointCharStream charStream = CharStreams.fromReader(stream);
        SqlStreamLexer lexer = new SqlStreamLexer(charStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        tokenStream.fill();
        
        List<Token> tokens = tokenStream.getTokens();
        
        for (int i = 0; i < tokens.size(); i++) {
            Token tk = tokens.get(i);
            if (tk.getType() == SqlStreamLexer.INCLUDE) {
                if (i + 1 < tokens.size()) {
                    Token next = tokens.get(i + 1);
                    String text = next.getText();
                    if (text.startsWith("'") && text.endsWith("'")) {
                        String path = unquoteStringLiteral(text);
                        Path resolved = workingDir.resolve(path).toAbsolutePath().normalize();
                        incs.add(resolved);
                    } else {
                        diags.fatal(Range.NONE, "Malformed INCLUDE: expected string literal after INCLUDE");
                    }
                } else {
                    diags.fatal(Range.NONE, "Malformed INCLUDE: expected string literal after INCLUDE");
                }
            }
        }
    } catch (IOException e) {
        diags.fatal(Range.NONE, "Failed to read " + source + ": " + e.getMessage());
    }
  }

  private static String unquoteStringLiteral(String lit) {
    if (lit == null || lit.length() < 2) return lit;
    char q = lit.charAt(0);
    if (lit.charAt(lit.length() - 1) == q) {
      String inner = lit.substring(1, lit.length() - 1);
      if (q == '\'') {
        return inner.replace("''", "'");
      }
      return inner;
    }
    return lit;
  }

  private static Set<Path> resolveFromGraph(LinkedHashMap<Path, List<Path>> graph, Path workingDir, Diagnostics diags) {
    LinkedHashSet<Path> ordered = new LinkedHashSet<>();
    Map<Path, State> state = new HashMap<>();
    Deque<Path> stack = new ArrayDeque<>();

    for (Path r : graph.keySet()) {
      dfsGraph(r, graph, diags, ordered, state, stack, workingDir);
      if (diags.hasError()) break;
    }
    return ordered;
  }

  private static void dfsGraph(Path node,
      LinkedHashMap<Path, List<Path>> graph,
      Diagnostics diags,
      LinkedHashSet<Path> ordered,
      Map<Path, State> state,
      Deque<Path> stack,
      Path workingDir) {

    State st = state.getOrDefault(node, State.UNVISITED);
    if (st == State.DONE || diags.hasError())
      return;

    if (st == State.IN_PROGRESS) {
      List<Path> cyc = new ArrayList<>();
      boolean cap = false;
      for (Path s : stack) {
        if (s.equals(node))
          cap = true;
        if (cap)
          cyc.add(s);
      }
      cyc.add(node);
      diags.fatal(Range.NONE, "Include cycle detected: " + cycleStringFromPaths(cyc));
      return;
    }

    state.put(node, State.IN_PROGRESS);
    stack.push(node);

    List<Path> neighbors = graph.getOrDefault(node, Collections.emptyList());
    for (Path nb : neighbors) {
      if (!graph.containsKey(nb) && !IN_MEM.equals(nb)) {
        // Check exists before trying to scan
        try {
          if (!Files.exists(nb)) {
            diags.fatal(Range.NONE, "Included file not found: " + nb);
            stack.pop();
            return;
          }
        } catch (Exception e) {
          diags.fatal(Range.NONE, "Cannot check file existence: " + nb + ": " + e.getMessage());
          stack.pop();
          return;
        }
        scanTopIncludes(nb, workingDir, diags, graph);
        if (diags.hasError()) {
          stack.pop();
          return;
        }
      }

      dfsGraph(nb, graph, diags, ordered, state, stack, workingDir);
      if (diags.hasError()) {
        stack.pop();
        return;
      }
    }

    stack.pop();
    state.put(node, State.DONE);
    ordered.add(node);
  }

  private static String cycleStringFromPaths(List<Path> cyc) {
    return String.join(" -> ",
        cyc.stream().map(p -> p.getFileName().toString()).toList());
  }

  private static Path normalize(Path p) {
    return p.toAbsolutePath().normalize();
  }

  private IncludeResolver() {
  }
}