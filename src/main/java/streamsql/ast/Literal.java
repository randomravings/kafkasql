package streamsql.ast;

public final class Literal {

    public enum Null implements Expr { INSTANCE }

    public record Str(String value) implements Expr {}

    public record Num(double value) implements Expr {}

    public record Bool(boolean value) implements Expr {}
}
