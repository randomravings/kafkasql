package streamsql.ast;

import java.util.List;

public sealed interface DataStream
    permits StreamLog, StreamCompact {
    QName qName();
    List<StreamType> types();
}