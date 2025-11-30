package kafkasql.lang.lex;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

public final class Lexer {

    private Lexer() { }

    public static CommonTokenStream tokenize(
        LexerArgs args
    ) {
        SqlStreamLexer lexer = new SqlStreamLexer(
            CharStreams.fromString(
                args.input().content(),
                args.input().source()
            )
        );

        LexerErrorListener errorListener = new LexerErrorListener(
            args.input().source(),
            args.diags()
        );

        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);

        return new CommonTokenStream(lexer);
    }
}
