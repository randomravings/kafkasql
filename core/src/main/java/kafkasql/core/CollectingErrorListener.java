package kafkasql.core;
import java.util.BitSet;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;

public class CollectingErrorListener extends BaseErrorListener {
  private final String source;
  private final Diagnostics diagnostics;

  public CollectingErrorListener(String source, Diagnostics diagnostics) {
    this.source = source;
    this.diagnostics = diagnostics;
  }

  @Override
  public void syntaxError(
                Recognizer<?, ?> recognizer,
                Object offendingSymbol,
                int line,
                int charPositionInLine,
                String msg,
                RecognitionException e) {
    int line1 = Math.max(0, line);   // ANTLR provides 1-based line
    int col1 = Math.max(1, charPositionInLine + 1); // convert to 1-based column

    // best-effort end position: try to size from offendingSymbol, else mark that token only
    int endLine = line1;
    int endCol = col1;
    try {
      if (offendingSymbol != null) {
        String s = offendingSymbol.toString();
        if (s != null && s.length() > 0) {
          endCol = col1 + Math.max(0, s.length() - 1);
        }
      }
    } catch (Throwable ignore) {}

    Range range = new Range(source, new kafkasql.core.ast.Pos(line1, col1), new kafkasql.core.ast.Pos(endLine, endCol));
    diagnostics.addError(range, msg);
  }

  @Override
  public void reportAmbiguity(
                Parser recognizer,
                DFA dfa,
                int startIndex,
                int stopIndex,
                boolean exact,
                BitSet ambigAlts,
                ATNConfigSet configs) {
    String message = "Ambiguity detected between tokens " + startIndex + " and " + stopIndex;
    diagnostics.addError(Range.NONE, message);
  }

  @Override
  public void reportAttemptingFullContext(
                Parser recognizer,
                DFA dfa,
                int startIndex,
                int stopIndex,
                BitSet conflictingAlts,
                ATNConfigSet configs) {
    String message = "Attempting full context between tokens " + startIndex + " and " + stopIndex;
    diagnostics.addError(Range.NONE, message);
  }

  @Override
  public void reportContextSensitivity(
                Parser recognizer,
                DFA dfa,
                int startIndex,
                int stopIndex,
                int prediction,
                ATNConfigSet configs) {
    String message = "Context sensitivity detected between tokens " + startIndex + " and " + stopIndex;
    diagnostics.addError(Range.NONE, message);
  }
}