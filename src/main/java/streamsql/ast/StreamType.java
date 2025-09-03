package streamsql.ast;

import java.util.List;

public sealed interface StreamType
    permits Stream.Log, Stream.Compact {
        public sealed interface Definition
            permits Stream.InlineType, Stream.ReferenceType {
            String alias();
        }
    QName qName();
    List<Definition> types();
}
