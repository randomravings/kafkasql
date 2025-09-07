package streamsql.ast;

import java.util.List;

public final record ProjectionList(List<Path> fields) implements Projection {}
