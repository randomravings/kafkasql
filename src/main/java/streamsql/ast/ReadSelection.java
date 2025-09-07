package streamsql.ast;

import java.util.Optional;

public record ReadSelection(Identifier alias, Projection projection, Optional<WhereClause> where) {}
