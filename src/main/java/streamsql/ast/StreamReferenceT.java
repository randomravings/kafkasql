package streamsql.ast;

import java.util.List;

public final record StreamReferenceT(TypeRef ref, Identifier alias, List<Identifier> distributionKeys) implements StreamType {}
