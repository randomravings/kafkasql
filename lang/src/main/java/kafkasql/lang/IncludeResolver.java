package kafkasql.lang;

import java.nio.file.*;
import java.util.*;
import java.io.IOException;

import org.antlr.v4.runtime.CommonTokenStream;

import kafkasql.runtime.diagnostics.DiagnosticCode;
import kafkasql.runtime.diagnostics.DiagnosticKind;
import kafkasql.runtime.diagnostics.Diagnostics;
import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.input.FileInput;
import kafkasql.lang.input.Input;
import kafkasql.lang.input.StringInput;
import kafkasql.lang.lex.Lexer;
import kafkasql.lang.lex.LexerArgs;
import kafkasql.lang.syntax.Parser;
import kafkasql.lang.syntax.ParserArgs;
import kafkasql.lang.syntax.ast.misc.Include;

public final class IncludeResolver {

    private enum State {
        UNVISITED,
        IN_PROGRESS,
        DONE
    }

    private record IncludeWithRange(
        Path path,
        Range range
    ) {}

    private final record Node(
        Input input,
        List<IncludeWithRange> includes
    ) implements Comparable<Node> {
        @Override
        public int compareTo(Node o) {
            return this.input.source().compareTo(o.input.source());
        }
    };

    private IncludeResolver() { }

    // ============================================================
    // PUBLIC API
    // ============================================================

    public static List<Input> buildIncludeOrder(
        Collection<Input> inputs,
        Path workingDir,
        Diagnostics diags
    ) {
        LinkedHashMap<String, Node> graph = new LinkedHashMap<>();
        for (Input input : inputs) {
            switch (input) {
                case StringInput sInput ->
                    scanTopIncludes(
                        sInput,
                        workingDir,
                        graph,
                        diags
                    );
                case FileInput fInput ->
                    scanTopIncludes(
                        fInput,
                        Range.NONE,  // Root file, not from an include statement
                        workingDir,
                        graph,
                        diags
                    );
            };
            if (diags.hasError())
                return List.of();
        }
        return resolveFromGraph(graph, diags);
    }

    // ============================================================
    // INCLUDE SCANNING
    // ============================================================

    private static void scanTopIncludes(
        StringInput input,
        Path workingDir,
        LinkedHashMap<String, Node> graph,
        Diagnostics diags
    ) {
        if (graph.containsKey(input.source()))
            return;
        
        List<IncludeWithRange> includes = getIncludes(
            input,
            workingDir,
            diags
        );

        addAndScan(
            input,
            includes,
            workingDir,
            graph,
            diags
        );
    }

    private static void scanTopIncludes(
        FileInput input,
        Range includeRange,
        Path workingDir,
        LinkedHashMap<String, Node> graph,
        Diagnostics diags
    ) {
        if (graph.containsKey(input.source()))
            return;

        String inputText = null;
        try {
            inputText = Files.readString(input.path());
        } catch (java.nio.file.NoSuchFileException e) {
            diags.fatal(
                includeRange,
                DiagnosticKind.INTERNAL,
                DiagnosticCode.INTERNAL_ERROR,
                "Include file not found: " + input.path()
            );
            return;
        } catch (java.nio.file.AccessDeniedException e) {
            diags.fatal(
                includeRange,
                DiagnosticKind.INTERNAL,
                DiagnosticCode.INTERNAL_ERROR,
                "Cannot read include file (access denied): " + input.path()
            );
            return;
        } catch (IOException e) {
            diags.fatal(
                includeRange,
                DiagnosticKind.INTERNAL,
                DiagnosticCode.INTERNAL_ERROR,
                "Cannot read include file: " + e.getMessage()
            );
            return;
        }

        List<IncludeWithRange> includes = getIncludes(
                new StringInput(
                    input.source(),
                    inputText
                ),
                workingDir,
                diags
            );

        addAndScan(
            input,
            includes,
            workingDir,
            graph,
            diags
        );
    }

    private static List<IncludeWithRange> getIncludes(
        StringInput input,
        Path workingDir,
        Diagnostics diags
    ) {
        CommonTokenStream tokenStream = Lexer.tokenize(
            new LexerArgs(
                input,
                diags
            )
        );
        List<Include> includes = Parser.scanTopIncludes(
            new ParserArgs(
                input.source(),
                tokenStream,
                diags,
                false
            )
        );
        List<IncludeWithRange> canonicalIncludes = new ArrayList<>();
        for (Include include : includes) {
            Path file = workingDir
                .resolve(include.path())
                .toAbsolutePath()
                .normalize()
            ;
            canonicalIncludes.add(new IncludeWithRange(file, include.range()));
        }
        return canonicalIncludes;
    }

    public static void addAndScan(
        Input input,
        List<IncludeWithRange> includes,
        Path workingDir,
        LinkedHashMap<String, Node> graph,
        Diagnostics diags
    ) {
        graph.put(
            input.source(),
            new Node(
                input,
                includes
            )
        );
        for (IncludeWithRange includeWithRange : includes) {
            Path includePath = includeWithRange.path();
            FileInput includedInput = new FileInput(
                includePath.toString(),
                includePath
            );
            scanTopIncludes(
                includedInput,
                includeWithRange.range(),
                workingDir,
                graph,
                diags
            );
        }
    }

    // ============================================================
    // GRAPH RESOLUTION
    // ============================================================

    private static List<Input> resolveFromGraph(
        LinkedHashMap<String, Node> graph,
        Diagnostics diags
    ) {
        LinkedHashSet<Node> ordered = new LinkedHashSet<>();
        Map<String, State> state = new HashMap<>();
        Deque<String> stack = new ArrayDeque<>();

        for (String r : graph.keySet()) {
            dfsGraph(r, graph, diags, ordered, state, stack);
            if (diags.hasError()) break;
        }
        return ordered
            .stream()
            .map(n -> n.input())
            .toList()
        ;
    }

    private static void dfsGraph(
        String node,
        LinkedHashMap<String, Node> graph,
        Diagnostics diags,
        LinkedHashSet<Node> ordered,
        Map<String, State> state,
        Deque<String> stack
    ) {
        State st = state.getOrDefault(node, State.UNVISITED);
        if (st == State.DONE || diags.hasError())
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

            diags.fatal(
                Range.NONE,
                DiagnosticKind.INCLUDE,
                DiagnosticCode.INCLUDE_CYCLE,
                "Include cycle detected: " + cycleStringFromPaths(cyc)
            );
            return;
        }

        state.put(node, State.IN_PROGRESS);
        stack.push(node);

        Node n = graph.get(node);
        if (n == null) {
            diags.fatal(
                Range.NONE,
                DiagnosticKind.INTERNAL,
                DiagnosticCode.INTERNAL_ERROR,
                "Cannot resolve includes: missing node for " + node
            );
            stack.pop();
            return;
        }
        List<IncludeWithRange> neighbors = n.includes();
        for (IncludeWithRange nb : neighbors) {
            dfsGraph(nb.path().toString(), graph, diags, ordered, state, stack);
            if (diags.hasError()) {
                stack.pop();
                return;
            }
        }

        stack.pop();
        state.put(node, State.DONE);
        ordered.add(n);
    }

    private static String cycleStringFromPaths(List<String> cyc) {
        return String.join(" -> ", cyc);
    }
}