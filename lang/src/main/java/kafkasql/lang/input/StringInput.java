package kafkasql.lang.input;

public final record StringInput(
    String source,
    String content
) implements Input { }
