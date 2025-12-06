package kafkasql.cli;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.*;
import java.util.*;

import kafkasql.lang.IncludeResolver;
import kafkasql.lang.KafkaSqlArgs;
import kafkasql.lang.KafkaSqlParser;
import kafkasql.lang.input.FileInput;
import kafkasql.lang.input.Input;
import kafkasql.lang.input.StringInput;
import kafkasql.lang.printer.AstPrinter;
import kafkasql.lang.printer.Printer;
import kafkasql.lang.semantic.SemanticModel;
import kafkasql.lang.diagnostics.Diagnostics;
import kafkasql.lang.ParseResult;
import kafkasql.lang.syntax.ast.Script;

public class Main {

    private static void usage() {
        System.out.println("Usage:");
        System.out.println("  kafkasql                                    (interactive mode)");
        System.out.println("  kafkasql [-a] [-n] (-f <f1.kafka>[,<f2.kafka>...] ...)");
        System.out.println("  kafkasql [-a] -t <script...>");
        System.out.println("Options:");
        System.out.println("  -i, --interactive   Interactive REPL mode (default if no files)");
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
        boolean interactive = false;
        String inlineText = null;
        List<String> fileArgs = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "-i", "--interactive" -> interactive = true;
                case "-w", "--working-dir" -> {
                    if (++i >= args.length) {
                        err("missing value for --working-dir/-w");
                        return;
                    }
                    workingDir = args[i].trim();
                }
                case "-f", "--files" -> {
                    if (++i >= args.length) {
                        err("missing value for --files/-f");
                        return;
                    }
                    for (String f : args[i].split(",")) {
                        if (!f.isBlank())
                            fileArgs.add(f.trim());
                    }
                }
                case "-n", "--no-include" -> resolveIncludes = false;
                case "-a", "--print-ast" -> printAst = true;
                case "-v", "--verbose" -> trace = true;
                case "-t", "--text" -> {
                    if (inlineText != null) {
                        err("--text/-t specified multiple times");
                        return;
                    }
                    if (++i >= args.length) {
                        err("missing inline script after --text/-t");
                        return;
                    }
                    var sb = new StringBuilder();
                    while (i < args.length) {
                        if (sb.length() > 0)
                            sb.append(' ');
                        sb.append(args[i++]);
                    }
                    inlineText = sb.toString();
                    break;
                }
                case "-h", "--help" -> {
                    usage();
                    return;
                }
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
        
        // If no files or text provided, and not explicitly requested non-interactive, start REPL
        if (!interactive && inlineText == null && fileArgs.isEmpty()) {
            interactive = true;
        }
        
        // Interactive mode
        if (interactive) {
            if (inlineText != null || !fileArgs.isEmpty()) {
                err("cannot use --interactive with --files or --text");
                usage();
                return;
            }
            InteractiveRepl repl = new InteractiveRepl();
            repl.run();
            return;
        }

        Path wd = Path.of(workingDir != null ? workingDir : ".").toAbsolutePath().normalize();
        KafkaSqlArgs parseArgs = new KafkaSqlArgs(wd, resolveIncludes, trace);
        List<Input> fs = new ArrayList<>();

        if (inlineText != null) {
            var source = new StringInput ("<text>", inlineText);
            fs.add(source);
        } else if (!fileArgs.isEmpty()) {
            for (String p : fileArgs) {
                Path path = Path.of(p).toAbsolutePath().normalize();
                FileInput source = new FileInput(
                    path.toString(),
                    path
                );
                fs.add(source);
            }
        } else {
            System.err.println("error: no input (use --files or --text)");
            usage();
            System.exit(1);
        }

        if(resolveIncludes) {
            Diagnostics diags = new Diagnostics();
            fs = IncludeResolver.buildIncludeOrder(fs, wd, diags);
            if (diags.hasError()) {
                System.out.println("Include resolution failed with errors:");
                printDiags(diags);
                System.exit(1);
            }
        }

        ParseResult parseResult =  KafkaSqlParser.parse(fs, parseArgs);

        if (parseResult.diags().hasError()) {
            System.out.println("Parsing failed with errors:");
            printDiags(parseResult.diags());
            System.exit(1);
        }

        if (printAst) {
            for (Script script : parseResult.scripts()) {
                printAst(script);
            }
        }

        SemanticModel model = KafkaSqlParser.bind(parseResult);

        if (model.hasErrors()) {
            System.out.println("Binding failed with errors:");
            printDiags(model.diags());
            System.exit(1);
        }

        System.exit(0);
    }

    private static void printDiags(Diagnostics diags) {
        for (var e : diags.all()) {
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