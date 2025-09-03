package streamsql.ast;

import java.util.List;

public record Ident(List<String> parts) implements Expr {}
