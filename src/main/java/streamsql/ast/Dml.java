package streamsql.ast;

import java.util.List;

public final class Dml {
    private Dml() { }
    public final static class Read implements DmlStmt {
        public sealed interface SelectItem permits Star, Col {}
        public record Star() implements SelectItem {}
        public record Col(Ident name) implements SelectItem {}
        public record TypeBlock(String typeName, List<SelectItem> select, Expr where) {}

        public final String stream;
        public final List<TypeBlock> blocks;
        public Read(String stream, List<TypeBlock> blocks){ this.stream=stream; this.blocks=blocks; }
    }
    
    public final static class Write implements DmlStmt {
        public sealed interface PathSeg permits FieldSeg, IndexSeg, KeySeg {}
        public record FieldSeg(String name) implements PathSeg {}
        public record IndexSeg(int index) implements PathSeg {}
        public record KeySeg(String key) implements PathSeg {}

        public sealed interface ValLit permits VStr, VNum, VBool, VNull, VEnum {}
        public record VStr(String value) implements ValLit {}
        public record VNum(double value) implements ValLit {}
        public record VBool(boolean value) implements ValLit {}
        public enum VNull implements ValLit { INSTANCE }
        public record VEnum(String symbol) implements ValLit {}

        public record Path(String head, List<PathSeg> segments) {}

        public final String stream;
        public final String typeName;
        public final List<Path> projection;
        public final List<List<ValLit>> rows;
        public Write(String stream, String typeName, List<Path> projection, List<List<ValLit>> rows){
            this.stream=stream; this.typeName=typeName; this.projection=projection; this.rows=rows;
        }
    }
}
