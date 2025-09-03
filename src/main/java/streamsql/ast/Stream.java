package streamsql.ast;

import java.util.List;

public final class Stream {
    public final record Log(QName qName, List<StreamType.Definition> types) implements StreamType {}
    public final record Compact(QName qName, List<StreamType.Definition> types) implements StreamType {}
    public final record InlineType(List<Complex.StructField> fields, String alias) implements StreamType.Definition {}
    public final record ReferenceType(TypeRef ref, String alias) implements StreamType.Definition {}
}
