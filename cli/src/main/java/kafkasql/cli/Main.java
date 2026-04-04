package kafkasql.cli;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.*;
import java.util.*;

import kafkasql.lang.compare.DiffKind;
import kafkasql.lang.compare.RuleSet;
import kafkasql.lang.compare.RuleSetLoader;
import kafkasql.linter.LintSettings;
import kafkasql.linter.LintSettingsLoader;
import kafkasql.lang.compare.ScriptDiff;
import kafkasql.lang.compare.ScriptDiffer;
import kafkasql.lang.input.FileInput;
import kafkasql.lang.input.Input;
import kafkasql.lang.input.StringInput;
import kafkasql.lang.printer.AstPrinter;
import kafkasql.lang.printer.Printer;
import kafkasql.lang.syntax.ast.Script;
import kafkasql.pipeline.Pipeline;
import kafkasql.pipeline.PipelineContext;
import kafkasql.pipeline.PipelineResult;
import kafkasql.pipeline.phases.LintPhase;
import kafkasql.pipeline.phases.ParsePhase;
import kafkasql.pipeline.phases.SemanticPhase;
import kafkasql.runtime.diagnostics.Diagnostics;

public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            System.exit(0);
        }

        String verb = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);

        switch (verb) {
            case "repl"                 -> runRepl(rest);
            case "run"                  -> runRun(rest);
            case "diff"                 -> runDiff(rest);
            case "rules"       -> runRules(rest);
            case "help", "-h", "--help" -> { usage(); System.exit(0); }
            default -> {
                err("unknown verb '" + verb + "'");
                usage();
                System.exit(1);
            }
        }
    }

    // =========================================================================
    // Usage
    // =========================================================================

    private static void usage() {
        System.out.println("Usage: kafkasql <verb> [options]");
        System.out.println();
        System.out.println("Verbs:");
        System.out.println("  repl                           Start interactive REPL");
        System.out.println("  run  [options] <file...>       Parse and validate scripts");
        System.out.println("  diff [options] <left> <right>  Compare two scripts");
        System.out.println("  rules [<file>]                 Write default ruleset to a kafkasql.rules.toml file");
        System.out.println("  help                           Show this help");
        System.out.println();
        System.out.println("Run options:");
        System.out.println("  -t, --text <script>     Inline script instead of files");
        System.out.println("  -a, --print-ast         Print AST if parse succeeds");
        System.out.println("  -l, --lint-only         Show only linting diagnostics");
        System.out.println("  -v, --verbose           Enable ANTLR trace output");
        System.out.println("  -n, --no-include        Disable INCLUDE resolution");
        System.out.println("  -w, --working-dir <dir> Base directory for includes (default: .)");
        System.out.println();
        System.out.println("Diff options:");
        System.out.println("      --delta             Output a migration script instead of a diff");
        System.out.println("  -s, --semantic          Run compatibility analysis (severity annotations)");
        System.out.println("  -r, --rules <file>      TOML rules file; absent keys use built-in defaults");
        System.out.println("                          Run 'kafkasql rules' to create one.");
        System.out.println("  -u, --show-unchanged    Include unchanged entries in output");
        System.out.println("      --no-color          Disable ANSI colours");
        System.out.println("  -n, --no-include        Disable INCLUDE resolution");
        System.out.println("  -w, --working-dir <dir> Base directory for includes (default: .)");
        System.out.println();
        System.out.println("Use 'kafkasql <verb> --help' for verb-specific help.");
    }

    private static void usageVerb(String verb) {
        switch (verb) {
            case "run" -> {
                System.out.println("Usage: kafkasql run [options] <file...>");
                System.out.println("       kafkasql run [options] -t <script...>");
                System.out.println("Options:");
                System.out.println("  -t, --text <script>     Inline script instead of files");
                System.out.println("  -a, --print-ast         Print AST if parse succeeds");
                System.out.println("  -l, --lint-only         Show only linting diagnostics");
                System.out.println("  -v, --verbose           Enable ANTLR trace output");
                System.out.println("  -n, --no-include        Disable INCLUDE resolution");
                System.out.println("  -w, --working-dir <dir> Base directory for includes");
            }
            case "diff" -> {
                System.out.println("Usage: kafkasql diff [options] <left.kafka> <right.kafka>");
                System.out.println("Options:");
                System.out.println("      --delta             Output a migration script instead of a diff");
                System.out.println("  -s, --semantic          Run compatibility analysis");
                System.out.println("  -r, --rules <file>      TOML rules file; absent keys use built-in defaults");
                System.out.println("                          Run 'kafkasql rules' to create one.");
                System.out.println("  -u, --show-unchanged    Include unchanged entries");
                System.out.println("      --no-color          Disable ANSI colours");
                System.out.println("  -n, --no-include        Disable INCLUDE resolution");
                System.out.println("  -w, --working-dir <dir> Base directory for includes");
            }
        }
    }

    // =========================================================================
    // rules
    // =========================================================================

    private static final String DEFAULT_RULES_FILENAME = "kafkasql.rules.toml";

    /**
     * Write the default ruleset to a kafkasql.rules.toml file.
     * Usage: kafkasql rules [&lt;file&gt;]
     * Default filename: kafkasql.rules.toml
     */
    private static void runRules(String[] args) throws IOException {
        String filename = DEFAULT_RULES_FILENAME;
        for (String a : args) {
            if (a.equals("-h") || a.equals("--help")) {
                System.out.println("Usage: kafkasql rules [<file>]");
                System.out.println("Writes the built-in ruleset defaults to a TOML file.");
                System.out.println("Default output file: " + DEFAULT_RULES_FILENAME);
                System.out.println("Edit the file, then pass it to diff:");
                System.out.println("  kafkasql diff --rules <file> v1.kafka v2.kafka");
                return;
            }
            if (a.startsWith("-")) { err("unknown option '" + a + "'"); System.exit(1); }
            filename = a;
        }
        Path out = Path.of(filename).toAbsolutePath().normalize();
        String toml = RuleSetLoader.generate(RuleSet.defaults());
        Files.writeString(out, toml);
        System.out.println("Written: " + out);
    }

    // =========================================================================
    // repl
    // =========================================================================

    private static void runRepl(String[] args) throws Exception {
        for (String a : args) {
            if (a.equals("-h") || a.equals("--help")) {
                System.out.println("Usage: kafkasql repl");
                System.out.println("Starts an interactive REPL session.");
                return;
            }
            err("repl takes no arguments (unexpected: '" + a + "')");
            System.exit(1);
        }
        new InteractiveRepl().run();
    }

    // =========================================================================
    // run
    // =========================================================================

    private static void runRun(String[] args) throws Exception {
        String workingDir = null;
        boolean resolveIncludes = true;
        boolean printAst = false;
        boolean lintOnly = false;
        boolean trace = false;
        String inlineText = null;
        List<String> fileArgs = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "-h", "--help"       -> { usageVerb("run"); return; }
                case "-w", "--working-dir" -> {
                    if (++i >= args.length) { err("missing value for " + a); System.exit(1); }
                    workingDir = args[i];
                }
                case "-n", "--no-include" -> resolveIncludes = false;
                case "-a", "--print-ast"  -> printAst = true;
                case "-l", "--lint-only"  -> lintOnly = true;
                case "-v", "--verbose"    -> trace = true;
                case "-t", "--text" -> {
                    if (++i >= args.length) { err("missing script after " + a); System.exit(1); }
                    var sb = new StringBuilder();
                    while (i < args.length) {
                        if (!sb.isEmpty()) sb.append(' ');
                        sb.append(args[i++]);
                    }
                    inlineText = sb.toString();
                }
                default -> {
                    if (a.startsWith("-")) { err("unknown option '" + a + "'"); usageVerb("run"); System.exit(1); }
                    fileArgs.add(a);
                }
            }
        }

        if (inlineText != null && !fileArgs.isEmpty()) {
            err("cannot combine --text with file arguments");
            System.exit(1);
        }
        if (inlineText == null && fileArgs.isEmpty()) {
            err("run: no input — provide file arguments or --text");
            usageVerb("run");
            System.exit(1);
        }

        Path wd = Path.of(workingDir != null ? workingDir : ".").toAbsolutePath().normalize();
        List<Input> inputs = new ArrayList<>();
        if (inlineText != null) {
            inputs.add(new StringInput("<text>", inlineText));
        } else {
            for (String p : fileArgs) {
                Path path = Path.of(p).toAbsolutePath().normalize();
                inputs.add(new FileInput(path.toString(), path));
            }
        }

        // Load lint settings from kafkasql.rules.toml in working dir, if present
        LintSettings lintSettings = LintSettings.defaults();
        Path configFile = wd.resolve(DEFAULT_RULES_FILENAME);
        if (Files.exists(configFile)) {
            try {
                lintSettings = LintSettingsLoader.load(configFile);
            } catch (IllegalArgumentException e) {
                System.err.println("warning: " + DEFAULT_RULES_FILENAME + " lint settings: " + e.getMessage());
            }
        }

        Pipeline pipeline = Pipeline.builder()
            .addPhase(new ParsePhase())
            .addPhase(new SemanticPhase())
            .addPhase(new LintPhase())
            .build();

        PipelineContext context = PipelineContext.builder()
            .inputs(inputs)
            .workingDir(wd)
            .includeResolution(resolveIncludes)
            .verbose(trace)
            .lintSettings(lintSettings)
            .build();

        PipelineResult result = pipeline.execute(context);

        if (!lintOnly && result.diagnostics().hasError()) {
            System.out.println("Compilation failed with errors:");
            printDiags(result.diagnostics(), false);
            System.exit(1);
        }

        if (printAst && result.model().parseResult() != null) {
            for (Script script : result.model().parseResult().scripts()) {
                printAst(script);
            }
        }

        if (lintOnly) {
            printDiags(result.diagnostics(), true);
        } else if (result.diagnostics().hasWarning()) {
            System.out.println("Compilation succeeded with warnings:");
            printDiags(result.diagnostics(), false);
        }

        System.exit(0);
    }

    // =========================================================================
    // diff
    // =========================================================================

    private static void runDiff(String[] args) {
        String workingDir = null;
        boolean resolveIncludes = true;
        boolean delta = false;
        boolean semantic = false;
        String rulesFile = null;
        boolean showUnchanged = false;
        boolean noColor = false;
        List<String> positional = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "-h", "--help"           -> { usageVerb("diff"); return; }
                case "-w", "--working-dir"    -> {
                    if (++i >= args.length) { err("missing value for " + a); System.exit(1); }
                    workingDir = args[i];
                }
                case "-n", "--no-include"     -> resolveIncludes = false;
                case "--delta"                -> delta = true;
                case "-s", "--semantic"       -> semantic = true;
                case "-r", "--rules"          -> {
                    if (++i >= args.length) { err("missing value for " + a); System.exit(1); }
                    rulesFile = args[i];
                }
                case "-u", "--show-unchanged" -> showUnchanged = true;
                case "--no-color"             -> noColor = true;
                default -> {
                    if (a.startsWith("-")) { err("unknown option '" + a + "'"); usageVerb("diff"); System.exit(1); }
                    positional.add(a);
                }
            }
        }

        if (positional.size() != 2) {
            err("diff requires exactly two file arguments: <left> <right>");
            usageVerb("diff");
            System.exit(1);
        }

        String leftArg  = positional.get(0);
        String rightArg = positional.get(1);
        Path wd = Path.of(workingDir != null ? workingDir : ".").toAbsolutePath().normalize();
        Path left  = wd.resolve(leftArg).normalize();
        Path right = wd.resolve(rightArg).normalize();

        if (delta) {
            String script;
            try {
                script = ScriptDiffer.generateDelta(left, right, wd, resolveIncludes);
            } catch (IllegalArgumentException e) {
                System.err.println(e.getMessage());
                System.exit(1);
                return;
            }
            if (script.isEmpty()) {
                System.out.println("-- No changes detected");
            } else {
                System.out.println("-- Delta script: " + leftArg + " \u2192 " + rightArg);
                System.out.println();
                System.out.println(script);
            }
            return;
        }

        // --rules implies semantic enrichment
        if (rulesFile != null) semantic = true;

        ScriptDiff diff;
        try {
            if (rulesFile != null) {
                Path rulesPath = Path.of(rulesFile).toAbsolutePath().normalize();
                RuleSet rules = RuleSetLoader.load(rulesPath);
                diff = ScriptDiffer.diff(left, right, wd, resolveIncludes, rules);
            } else {
                diff = ScriptDiffer.diff(left, right, wd, resolveIncludes, semantic);
            }
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        } catch (java.io.IOException e) {
            System.err.println("error reading rules file: " + e.getMessage());
            System.exit(1);
            return;
        }

        PrintWriter pw = new PrintWriter(System.out, true);
        pw.println("--- " + leftArg);
        pw.println("+++ " + rightArg);
        if (rulesFile != null) pw.println("(compatibility analysis applied — rules: " + rulesFile + ")");
        else if (semantic) pw.println("(compatibility analysis applied)");
        pw.println();

        new DiffPrinter(pw, !noColor, showUnchanged).print(diff);

        long added     = diff.statements().stream().filter(s -> s.kind() == DiffKind.RIGHT_ONLY).count();
        long removed   = diff.statements().stream().filter(s -> s.kind() == DiffKind.LEFT_ONLY).count();
        long modified  = diff.statements().stream().filter(s -> s.kind() == DiffKind.MODIFIED).count();
        long unchanged = diff.statements().stream().filter(s -> s.kind() == DiffKind.UNCHANGED).count();
        pw.println();
        pw.printf("Summary: +%d added  -%d removed  ~%d modified  =%d unchanged%n",
                added, removed, modified, unchanged);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static void printDiags(Diagnostics diags, boolean lintOnly) {
        for (var e : diags.all()) {
            if (e.severity() == kafkasql.runtime.diagnostics.DiagnosticEntry.Severity.INFO) continue;
            if (lintOnly && e.kind() != kafkasql.runtime.diagnostics.DiagnosticKind.LINT) continue;
            System.out.println(" - " + e);
        }
    }

    private static void printAst(Script ast) throws IOException {
        Writer out = new OutputStreamWriter(System.out);
        Printer printer = new AstPrinter(out);
        printer.write(ast);
        out.flush();
    }

    private static void err(String m) {
        System.err.println("error: " + m);
    }
}
