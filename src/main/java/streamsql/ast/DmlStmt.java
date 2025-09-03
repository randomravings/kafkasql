package streamsql.ast;

public sealed interface DmlStmt extends Stmt permits Dml.Read, Dml.Write {

}
