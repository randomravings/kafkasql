package kafkasql.core.ast;

import kafkasql.core.Range;

public record Context(Range range, QName qName) implements AstNode {
    public static final Context ROOT = new Context(Range.NONE, QName.ROOT);
}
