package kafkasql.core;

import java.nio.file.*;
import java.util.*;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.File;
import java.io.FileReader;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CodePointCharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import kafkasql.core.ast.Range;
import kafkasql.core.lex.SqlStreamLexer;

public final class IncludeResolver {

  private static final String IN_MEM = "<in-memory>";

  private enum State {
    UNVISITED, IN_PROGRESS, DONE
  }

  public static Collection<String> buildIncludeOrder(String text, Path workingDir, Diagnostics diags) {
    LinkedHashMap<String, List<String>> graph = new LinkedHashMap<>();
    scanTopIncludes(text, workingDir, diags, graph);
    var result = resolveFromGraph(graph, workingDir, diags);
    result.remove(IN_MEM);
    return result;
  }

  public static Collection<String> buildIncludeOrder(Collection<Path> roots, Path workingDir, Diagnostics diags) {
    LinkedHashMap<String, List<String>> graph = new LinkedHashMap<>();
    for (Path root : roots) {
      scanTopIncludes(root, workingDir, diags, graph);
      if (diags.hasErrors()) {
        return List.of();
      }
    }
    return resolveFromGraph(graph, workingDir, diags);
  }

  private static void scanTopIncludes(String text, Path workingDir, Diagnostics diags, LinkedHashMap<String, List<String>> graph) {
    List<String> incs = new ArrayList<>();
    try (Reader r = new StringReader(text)) {
      scanTopIncludes(r, IN_MEM, workingDir, diags, incs);
      graph.put(IN_MEM, incs);
    } catch (IOException e) {
      diags.addFatal(Range.NONE, "Failed to read " + IN_MEM + ": " + e.getMessage());
    }
  }

  private static void scanTopIncludes(Path file, Path workingDir, Diagnostics diags, LinkedHashMap<String, List<String>> graph) {
    List<String> incs = new ArrayList<>();
    try (Reader r = new FileReader(new File(file.toAbsolutePath().toString()))) {
      scanTopIncludes(r, file.toString(), workingDir, diags, incs);
      graph.put(file.toString(), incs);
    } catch (IOException e) {
      diags.addFatal(Range.NONE, "Failed to read " + file + ": " + e.getMessage());
    }
  }

  private static void scanTopIncludes(Reader stream, String source, Path workingDir, Diagnostics diags, List<String> incs) {
    CodePointCharStream charStream = null;
    try {
      charStream = CharStreams.fromReader(stream, source);
    } catch (IOException e) {
      diags.addFatal(Range.NONE, "Failed to read " + source + ": " + e.getMessage());
      return;
    }
    SqlStreamLexer lexer = new SqlStreamLexer(charStream);
    lexer.removeErrorListeners(); // don't emit to stderr
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    tokens.fill();

    int i = 0;
    while (i < tokens.size()) {
      Token t = tokens.get(i);
      if (t.getType() == Token.EOF) break;

      if (t.getType() == SqlStreamLexer.INCLUDE) {
        Token next = (i + 1 < tokens.size()) ? tokens.get(i + 1) : null;
        if (next == null || next.getType() != SqlStreamLexer.STRING_LIT) {
          diags.addFatal(Range.NONE, "Malformed INCLUDE: expected string literal after INCLUDE");
          return;
        }
        String lit = next.getText();
        String inner = unquoteStringLiteral(lit);
        Path incPath = normalize(workingDir.resolve(inner.replace('\\', '/')));
        incs.add(incPath.toString());
        i += 2;
        if (i < tokens.size() && tokens.get(i).getType() == SqlStreamLexer.SEMI) {
          i++;
        }
        // continue scanning next tokens
        continue;
      }

      // If we hit any other token (first non-include), stop scanning the top include section
      break;
    }
  }

  // small helper to unquote ANTLR STRING_LIT (grammar uses single quotes with '' as escape)
  private static String unquoteStringLiteral(String lit) {
    if (lit == null || lit.length() < 2) return lit;
    char q = lit.charAt(0);
    if (lit.charAt(lit.length() - 1) == q) {
      String inner = lit.substring(1, lit.length() - 1);
      if (q == '\'') {
        // grammar uses '' to escape a single-quote inside string
        return inner.replace("''", "'");
      }
      return inner;
    }
    return lit;
  }

  // resolveFromGraph now has workingDir so we can lazily scan included files
  private static Set<String> resolveFromGraph(LinkedHashMap<String, List<String>> graph, Path workingDir, Diagnostics diags) {
    LinkedHashSet<String> ordered = new LinkedHashSet<>();
    Map<String, State> state = new HashMap<>();
    Deque<String> stack = new ArrayDeque<>();

    for (String r : graph.keySet()) {
      String key = r;
      dfsGraph(key, graph, diags, ordered, state, stack, workingDir);
      if (diags.hasErrors()) break;
    }
    return ordered;
  }

  private static void dfsGraph(String node,
      LinkedHashMap<String, List<String>> graph,
      Diagnostics diags,
      LinkedHashSet<String> ordered,
      Map<String, State> state,
      Deque<String> stack,
      Path workingDir) {

    State st = state.getOrDefault(node, State.UNVISITED);
    if (st == State.DONE || diags.hasErrors())
      return;

    if (st == State.IN_PROGRESS) {
      List<String> cyc = new ArrayList<>();
      boolean cap = false;
      for (String s : stack) {
        if (s.equals(node))
          cap = true;
        if (cap)
          cyc.add(s);
      }
      cyc.add(node);
      diags.addFatal(Range.NONE, "Include cycle detected: " + cycleStringFromStrings(cyc));
      return;
    }

    state.put(node, State.IN_PROGRESS);
    stack.push(node);

    List<String> neighbors = graph.getOrDefault(node, Collections.emptyList());
    for (String nb : neighbors) {
      // if neighbor hasn't been scanned yet and is a file (not IN_MEM), scan it now
      if (!graph.containsKey(nb) && !IN_MEM.equals(nb)) {
        Path nbPath = Path.of(nb);
        if (!Files.exists(nbPath)) {
          diags.addFatal(Range.NONE, "Included file not found: " + nbPath);
          stack.pop();
          return;
        }
        scanTopIncludes(nbPath, workingDir, diags, graph);
        if (diags.hasErrors()) {
          stack.pop();
          return;
        }
      }

      dfsGraph(nb, graph, diags, ordered, state, stack, workingDir);
      if (diags.hasErrors()) {
        stack.pop();
        return;
      }
    }

    stack.pop();
    state.put(node, State.DONE);
    ordered.add(node);
  }

  private static String cycleStringFromStrings(List<String> cyc) {
    return String.join(" -> ",
        cyc.stream().map(s -> Path.of(s).getFileName().toString()).toList());
  }

  private static Path normalize(Path p) {
    return p.toAbsolutePath().normalize();
  }

  private IncludeResolver() {
  }
}