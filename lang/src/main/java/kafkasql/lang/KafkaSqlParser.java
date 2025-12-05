package kafkasql.lang;

import java.nio.file.Files;
import java.util.List;

import kafkasql.lang.lex.Lexer;
import kafkasql.lang.diagnostics.DiagnosticCode;
import kafkasql.lang.diagnostics.DiagnosticKind;
import kafkasql.lang.diagnostics.Diagnostics;
import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.input.*;
import kafkasql.lang.semantic.SemanticModel;
import kafkasql.lang.syntax.*;
import kafkasql.lang.syntax.ast.*;
import kafkasql.lang.semantic.SemanticBinder;

/**
 * KafkaSqlParser
 *
 * Thin façade for:
 *  - parsing (text or files) -> ParseResult
 *  - semantic binding         -> SemanticModel
 *
 * For most “I just want to run a script” cases, use the convenience
 * compileText / compileFiles helpers.
 */
public final class KafkaSqlParser {

    private KafkaSqlParser() {}

    // =============================================================
    // HIGH-LEVEL CONVENIENCE API
    // =============================================================

    /**
     * Parse + bind a single in-memory script in one shot.
     *
     * If resolveIncludes == true, includes are resolved relative to workspaceRoot.
     * Diagnostics (parse + semantic) are available via model.diagnostics().
     */
    public static SemanticModel compile(
        List<Input> sources,
        KafkaSqlArgs args
    ) {
        ParseResult pr = parse(sources, args);
        return bind(pr);
    }

    public static SemanticModel bind(ParseResult parseResult) {
        return SemanticBinder.bind(parseResult.scripts(), parseResult.diags());
    }

    // =============================================================
    // PARSE FILES
    // =============================================================

    public static ParseResult parse(List<Input> sources, KafkaSqlArgs args) {
        Diagnostics diags = new Diagnostics();
        AstListNode<Script> allStmts = new AstListNode<>(Script.class);

        for (Input source : sources) {
            StringInput input = switch (source) {
                case FileInput fInput -> {
                    try {
                        String content = Files.readString(fInput.path());
                        yield new StringInput(
                            fInput.source(),
                            content
                        );
                    } catch (Exception e) {
                        diags.fatal(
                            Range.NONE,
                            DiagnosticKind.INTERNAL,
                            DiagnosticCode.INTERNAL_ERROR,
                            "Cannot read file: " + fInput.path() + ": " + e.getMessage()
                        );
                        yield null;
                    }
                }
                case StringInput sInput -> sInput;
            };
            if (diags.hasError()) break;
            
            Script script = parseScript(input, args, diags);
            if (!diags.hasError())
                allStmts.add(script);
        }

        return new ParseResult(allStmts, diags);
    }

    // =============================================================
    // CORE PARSE LOGIC
    // =============================================================

    private static Script parseScript(
        StringInput input,
        KafkaSqlArgs args,
        Diagnostics diags
    ) {
        var lexerArgs = new kafkasql.lang.lex.LexerArgs(
            input,
            diags
        );
        var tokens = Lexer.tokenize(lexerArgs);
        var parserArgs = new ParserArgs(
            input.source(),
            tokens,
            diags,
            args.trace()
        );
        return Parser.buildTree(parserArgs);
    }
}