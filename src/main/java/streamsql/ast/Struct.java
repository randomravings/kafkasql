package streamsql.ast;

import java.util.List;

public final record Struct(QName qName, List<Field> fields) implements ComplexType {}