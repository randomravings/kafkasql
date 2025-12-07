package kafkasql.lang.lex;

import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.BaseErrorListener;

import kafkasql.runtime.diagnostics.DiagnosticCode;
import kafkasql.runtime.diagnostics.DiagnosticKind;
import kafkasql.runtime.diagnostics.Diagnostics;
import kafkasql.runtime.diagnostics.Pos;
import kafkasql.runtime.diagnostics.Range;

public class LexerErrorListener extends BaseErrorListener {
    private final String source;
    private final Diagnostics diags;

    public LexerErrorListener(
        String source,
        Diagnostics diags
    ) {
        this.source = source;
        this.diags = diags;
    }

    @Override
    public void syntaxError(
        Recognizer<?, ?> recognizer,
        Object offendingSymbol,
        int line,
        int charPositionInLine,
        String msg,
        RecognitionException e
    ) {
        int lnb = line;
        int chb = charPositionInLine;
        int lne = lnb;
        int che = chb;

        try {
            if (offendingSymbol != null) {
                String s = offendingSymbol.toString();
                if (s != null && !s.isEmpty()) {
                    che = chb + s.length();
                }
            }
        } catch (Throwable ignore) {}

        Range range = new Range(source, new Pos(lnb, chb), new Pos(lne, che));
        diags.error(
            range,
            DiagnosticKind.LEXER,
            DiagnosticCode.SYNTAX_ERROR,
            msg
        );
    }
}
