package kafkasql.lang.input;

public sealed interface Input
    permits StringInput, FileInput {
    String source();
}
