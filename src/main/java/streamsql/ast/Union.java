package streamsql.ast;

import java.util.List;

public final record Union(QName qName, List<UnionAlt> types) implements ComplexType {}
