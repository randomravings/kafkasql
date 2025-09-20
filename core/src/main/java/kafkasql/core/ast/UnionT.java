package kafkasql.core.ast;

import java.util.List;

public final record UnionT(Range range, QName qName, List<UnionMember> types, AstOptionalNode<UnionV> defaultvalue) implements ComplexT { }
