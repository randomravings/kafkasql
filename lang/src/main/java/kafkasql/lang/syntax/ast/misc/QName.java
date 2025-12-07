package kafkasql.lang.syntax.ast.misc;

import java.util.List;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstNode;

public final record QName(
    Range range,
    List<Identifier> parts
) implements AstNode {

    public static final QName ROOT = new QName(Range.NONE, List.of());

    public static QName of(Identifier id) {
        return new QName(id.range(), List.of(id));
    }

    public boolean isRoot() {
        return parts.isEmpty();
    }

    public String context() {
        if (parts.size() <= 1) return "";
        return String.join(".", parts.subList(0, parts.size() - 1).stream()
                .map(Identifier::name).toList());
    }

    public String name() {
        return parts.isEmpty() ? "" : parts.getLast().name();
    }

    public String fullName() {
        return String.join(".", parts.stream().map(Identifier::name).toList());
    }
}