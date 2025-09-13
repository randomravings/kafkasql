package streamsql.ast;

import java.util.List;

public final record ProjectionList(List<Accessor> fields) implements Projection {}
