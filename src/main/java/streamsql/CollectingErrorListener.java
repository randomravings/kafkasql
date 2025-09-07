package streamsql;
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
  private CollectingErrorListener(String source, Diagnostics diagnostics) {
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
    diagnostics.error("Syntax | src: " + source + ", ln: " + line + ", col: " + charPositionInLine + " | Symbol: " + offendingSymbol + " | Message: " + msg);
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
    diagnostics.error("Ambiguity | src: " + source + " - Ambiguity detected between tokens " + startIndex + " and " + stopIndex);
  }

  @Override
  public void reportAttemptingFullContext(
                Parser recognizer,
                DFA dfa,
                int startIndex,
                int stopIndex,
                BitSet conflictingAlts,
                ATNConfigSet configs) {
    diagnostics.error("Full Context | src: " + source + " - Attempting full context between tokens " + startIndex + " and " + stopIndex);
  }

  @Override
  public void reportContextSensitivity(
                Parser recognizer,
                DFA dfa,
                int startIndex,
                int stopIndex,
                int prediction,
                ATNConfigSet configs) {
    diagnostics.error("Context Sensitivity | src: " + source + ": Context sensitivity detected between tokens " + startIndex + " and " + stopIndex);
  }
  public static CollectingErrorListener withDiagnostics(String source, Diagnostics diagnostics) {
    return new CollectingErrorListener(source, diagnostics);
  }
}