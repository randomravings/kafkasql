package kafkasql.core.ast;

import java.util.List;
import java.util.stream.Collectors;

public final record Ast(Range range, List<Stmt> statements) implements AstNode {
    public static Ast EMPTY = new Ast(Range.NONE, List.of());
    public Ast merge(Ast other) {
        if (this.statements.isEmpty()) return other;
        if (other.statements.isEmpty()) return this;
        var newRange = new Range(this.range.start(), other.range.end());
        var newStmts = List.<Stmt>of();
        newStmts = newStmts.stream().collect(Collectors.toList());
        newStmts.addAll(this.statements);
        newStmts.addAll(other.statements);
        return new Ast(newRange, newStmts);
    }
}
