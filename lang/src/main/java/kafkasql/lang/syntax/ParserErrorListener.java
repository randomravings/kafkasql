package kafkasql.lang.syntax;

import java.util.BitSet;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;

import kafkasql.runtime.diagnostics.DiagnosticCode;
import kafkasql.runtime.diagnostics.DiagnosticKind;
import kafkasql.runtime.diagnostics.Diagnostics;
import kafkasql.runtime.diagnostics.Pos;
import kafkasql.runtime.diagnostics.Range;

public final class ParserErrorListener extends BaseErrorListener {

    private final String source;
    private final Diagnostics diagnostics;

    public ParserErrorListener(String source, Diagnostics diagnostics) {
        this.source = source;
        this.diagnostics = diagnostics;
    }

    @Override
    public void syntaxError(
        Recognizer<?, ?> recognizer,
        Object offendingSymbol,
        int line,
        int charPosInLine,
        String msg,
        RecognitionException e
    ) {
        int lnb = line;
        int chb = charPosInLine;
        int lne = lnb;
        int che = chb + 1;

        try {
            if (offendingSymbol instanceof Token tok) {
                String txt = tok.getText();
                if (txt != null && !txt.isEmpty())
                    che = chb + txt.length();
            }
        } catch (Throwable ignore) {}

        Range r = new Range(source, new Pos(lnb, chb), new Pos(lne, che));

        diagnostics.error(
            r,
            DiagnosticKind.PARSER,
            DiagnosticCode.SYNTAX_ERROR,
            msg
        );
    }

    // ------------------------------------------------------------------------
    // Ambiguity reporting
    // 
    // These methods report ANTLR's adaptive parsing events. They indicate when
    // the parser had to use full context lookahead or detected ambiguity.
    // 
    // These are logged as INFO level (not WARNING) so they don't:
    // 1. Clutter the UI with internal parser behavior
    // 2. Supersede real errors (like semantic validation errors)
    // 
    // They're still captured in diagnostics for debugging grammar issues.
    // To see them: filter diagnostics by INFO severity level.
    // ------------------------------------------------------------------------

    @Override
    public void reportAmbiguity(
        Parser recognizer,
        DFA dfa,
        int startIndex,
        int stopIndex,
        boolean exact,
        BitSet ambigAlts,
        ATNConfigSet configs
    ) {
        diagnostics.info(
            Range.NONE,
            DiagnosticKind.PARSER,
            DiagnosticCode.AMBIGUITY,
            "Ambiguity between tokens " + startIndex + " and " + stopIndex +
                " (alts=" + ambigAlts + ", rule=" + ruleName(recognizer, dfa) + ")"
        );
    }

    @Override
    public void reportAttemptingFullContext(
        Parser recognizer,
        DFA dfa,
        int startIndex,
        int stopIndex,
        BitSet conflictingAlts,
        ATNConfigSet configs
    ) {
        diagnostics.info(
            Range.NONE,
            DiagnosticKind.PARSER,
            DiagnosticCode.AMBIGUITY,
            "Attempting full context between tokens " + startIndex + " and " + stopIndex +
                " (conflicts=" + conflictingAlts + ", rule=" + ruleName(recognizer, dfa) + ")"
        );
    }

    @Override
    public void reportContextSensitivity(
        Parser recognizer,
        DFA dfa,
        int startIndex,
        int stopIndex,
        int prediction,
        ATNConfigSet configs
    ) {
        diagnostics.info(
            Range.NONE,
            DiagnosticKind.PARSER,
            DiagnosticCode.CONTEXT_SENSITIVITY,
            "Context sensitivity between tokens " + startIndex + " and " + stopIndex +
                " (prediction=" + prediction + ", rule=" + ruleName(recognizer, dfa) + ")"
        );
    }

    // ------------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------------

    private static String ruleName(
        Parser p,
        DFA dfa
    ) {
        try {
            int decision = dfa.decision;
            int ruleIndex = p.getATN().decisionToState.get(decision).ruleIndex;
            String[] names = p.getRuleNames();
            return (ruleIndex >= 0 && ruleIndex < names.length)
                ? names[ruleIndex]
                : "<unknown>";
        } catch (Throwable ex) {
            return "<unknown>";
        }
    }
}