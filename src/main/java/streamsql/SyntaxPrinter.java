package streamsql;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import streamsql.ast.BoolT;
import streamsql.ast.BytesT;
import streamsql.ast.AnyT;
import streamsql.ast.DateT;
import streamsql.ast.DecimalT;
import streamsql.ast.FixedT;
import streamsql.ast.CharT;
import streamsql.ast.Float32T;
import streamsql.ast.Float64T;
import streamsql.ast.Int16T;
import streamsql.ast.Int32T;
import streamsql.ast.Int64T;
import streamsql.ast.Int8T;
import streamsql.ast.ListT;
import streamsql.ast.MapT;
import streamsql.ast.PrimitiveType;
import streamsql.ast.Stmt;
import streamsql.ast.StringT;
import streamsql.ast.TimeT;
import streamsql.ast.TimestampT;
import streamsql.ast.TimestampTzT;
import streamsql.ast.TypeRef;
import streamsql.ast.UuidT;

public final class SyntaxPrinter extends Printer {
    protected SyntaxPrinter(Writer out) {
        super(out);
        //TODO Auto-generated constructor stub
    }

  public void write(List<Stmt> stmts) throws IOException {
  }

  // Helpers for DataType, Path, Value, Expr
  private static String dataTypeToString(AnyT t) {
    if (t instanceof PrimitiveType p)
      return typeToString(p);
    if (t instanceof ListT l)
      return "LIST<" + dataTypeToString(l.item()) + ">";
    if (t instanceof MapT m)
      return "MAP<" + typeToString(m.key()) + ", " + dataTypeToString(m.value()) + ">";
    if (t instanceof TypeRef r)
      return "REF " + r.qName().fullName();
    return "<unknown-type>";
  }

  private static String typeToString(PrimitiveType p) {
    return switch (p) {
      case BoolT __ -> "BOOL";
      case Int8T __ -> "INT8";
      case Int16T __ -> "INT16";
      case Int32T __ -> "INT32";
      case Int64T __ -> "INT64";
      case Float32T __ -> "FLOAT32";
      case Float64T __ -> "FLOAT64";
      case StringT __ -> "STRING";
      case CharT f -> "CHAR(" + f.size() + ")";
      case BytesT __ -> "BYTES";
      case FixedT f -> "FIXED(" + f.size() + ")";
      case UuidT __ -> "UUID";
      case DateT __ -> "DATE";
      case TimeT t -> "TIME(" + t.precision() + ")";
      case TimestampT t -> "TIMESTAMP(" + t.precision() + ")";
      case TimestampTzT t -> "TIMESTAMP_TZ(" + t.precision() + ")";
      case DecimalT d -> "DECIMAL(" + d.precision() + "," + d.scale() + ")";
    };
  }
}
