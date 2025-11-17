package kafkasql.lang;

import kafkasql.lang.ast.ComplexT;
import kafkasql.lang.ast.StreamT;
import kafkasql.lang.ast.ReadStmt;
import kafkasql.lang.ast.WriteStmt;

public final class AstCompare {
    public static boolean compare(StreamT source, StreamT target) {
        return true;
    }
    public static boolean compare(ComplexT source, ComplexT target) {
        return true;
    }
    public static boolean resolve(ReadStmt query, StreamT stream) {
        return true;
    }
    public static boolean resolve(WriteStmt query, StreamT stream) {
        return true;
    }
}
