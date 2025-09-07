package streamsql.ast;

public sealed interface PathSeg permits PathFieldSeg, PathIndexSeg, PathKeySeg {}
