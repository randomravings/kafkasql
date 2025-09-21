package kafkasql.core.ast;

import kafkasql.core.Range;

public final record StructT(Range range, QName qName, AstListNode<Field> fieldList) implements ComplexT {}