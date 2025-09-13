package streamsql.ast;

public final record Segment(Accessor head, Accessor tail) implements Accessor { }