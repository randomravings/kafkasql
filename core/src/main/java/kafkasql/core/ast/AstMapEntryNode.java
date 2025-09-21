package kafkasql.core.ast;

import kafkasql.core.Range;

public final record AstMapEntryNode<K extends AstNode, V extends AstNode>(Range range, K key, V value) implements AstNode {}