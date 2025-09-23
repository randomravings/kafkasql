package kafkasql.core.ast;

import java.util.List;

import kafkasql.core.Range;

public final record UnionT(Range range, QName qName, List<UnionMember> types, AstOptionalNode<UnionV> defaultvalue, AstOptionalNode<Doc> doc) implements ComplexT { }
