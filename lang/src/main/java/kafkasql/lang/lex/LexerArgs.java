package kafkasql.lang.lex;

import kafkasql.runtime.diagnostics.Diagnostics;
import kafkasql.lang.input.StringInput;

public final record LexerArgs(
    StringInput input,
    Diagnostics diags
) { }
