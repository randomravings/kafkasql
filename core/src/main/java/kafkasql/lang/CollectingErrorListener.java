package kafkasql.lang;
import java.util.BitSet;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;

import kafkasql.lang.ast.Range;

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
    int lnb = line;
    int chb = charPositionInLine;
    int lne = lnb;
    int che = chb;
    try {
      if (offendingSymbol != null) {
        String s = offendingSymbol.toString();
        if (s != null && s.length() > 0) {
          che = lnb + Math.max(0, s.length());
        }
      }
    } catch (Throwable ignore) {}

    Range range = new Range(source, new Pos(lnb, chb), new Pos(lne, che));
    diagnostics.error(range, msg);
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
    diagnostics.fatal(Range.NONE, message);
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
    diagnostics.fatal(Range.NONE, message);
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
    diagnostics.fatal(Range.NONE, message);
  }
}