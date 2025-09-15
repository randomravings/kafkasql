package streamsql.ast;

import java.util.List;

public final record StructT(QName qName, List<Field> fields) implements ComplexT {}