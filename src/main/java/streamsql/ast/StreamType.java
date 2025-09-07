package streamsql.ast;

import java.util.List;

public sealed interface StreamType
    permits StreamInlineT, StreamReferenceT {
    Identifier alias();
    List<Identifier> distributionKeys();
}
