package kafkasql.lang.syntax;

import org.antlr.v4.runtime.TokenStream;

import kafkasql.runtime.diagnostics.Diagnostics;

public final record ParserArgs(
    String source,
    TokenStream tokens,
    Diagnostics diags,
    boolean trace
) { }
