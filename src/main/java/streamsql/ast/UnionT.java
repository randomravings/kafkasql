package streamsql.ast;

import java.util.List;

public final record UnionT(QName qName, List<UnionAlt> types) implements ComplexT { }
