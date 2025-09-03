package streamsql;

import streamsql.ast.*;
import java.util.*;
import java.util.stream.Collectors;

public final class AstPrinter {

  public static String print(List<Stmt> stmts) {
    var sb = new StringBuilder();
    for (int i = 0; i < stmts.size(); i++) {
      printStmt(sb, stmts.get(i), "", i == stmts.size() - 1);
      if (i < stmts.size() - 1) sb.append('\n');
    }
    return sb.toString();
  }

  /* ───────── Tree helpers ───────── */
  private static void branch(StringBuilder sb, String indent, boolean last, String text) {
    sb.append(indent).append(last ? "└─ " : "├─ ").append(text).append('\n');
  }
  private static String kidIndent(String indent, boolean last) {
    return indent + (last ? "   " : "│  ");
  }

  /* ───────── Statement printing ───────── */
  private static void printStmt(StringBuilder sb, Stmt s, String indent, boolean last) {
    if (s instanceof CreateContext c) {
      branch(sb, indent, last, "ContextDecl " + c.qName().fullName());
    } else if (s instanceof UseContext u) {
      branch(sb, indent, last, "UseContext " + u.context().qName().fullName());
    } else if (s instanceof CreateType sc) {
      if (sc.complexType() instanceof Complex.Struct cs) {
        branch(sb, indent, last, "StructDecl " + sc.qName().fullName());
        var ind = kidIndent(indent, last);
        forEach(cs.fields(), (f, i, isLast) -> printField(sb, f, ind, isLast));
      } else if (sc.complexType() instanceof Complex.Enum en) {
        branch(sb, indent, last, "EnumDecl " + sc.qName().fullName());
        var ind = kidIndent(indent, last);
        forEach(en.symbols(), (sym, i, isLast) ->
          branch(sb, ind, isLast, sym.name() + " = " + sym.value()));
      } else if (sc.complexType() instanceof Complex.Union un) {
        branch(sb, indent, last, "UnionDecl " + sc.qName().fullName());
        var ind = kidIndent(indent, last);
        forEach(un.types(), (a, i, isLast) -> {
          branch(sb, ind, isLast, "Alt " + a.name());
          branch(sb, kidIndent(ind, isLast), true, dataTypeToString(a.typ()));
        });
      } else if (sc.complexType() instanceof Complex.Scalar cs) {
        branch(sb, indent, last, "ScalarDecl " + sc.qName().fullName());
        branch(sb, kidIndent(indent, last), true, typeToString(cs.primitive()));
      }
    } else if (s instanceof CreateStream cs) {
      branch(sb, indent, last, "CreateStream " + cs.qName().fullName());
      var ind = kidIndent(indent, last);
      forEach(cs.stream().types(), (a, i, isLast) -> {
        if (a instanceof Stream.InlineType ia) {
          branch(sb, ind, isLast, "TYPE " + ia.alias() + " (inline)");
          var ind2 = kidIndent(ind, isLast);
          forEach(ia.fields(), (f, j, lastField) -> printField(sb, f, ind2, lastField));
        } else {
          var ra = (Stream.ReferenceType) a;
          branch(sb, ind, isLast, "TYPE " + ra.alias() + " = " + ra.ref().qName().fullName());
        }
      });
    } else if (s instanceof Dml.Read rq) {
      branch(sb, indent, last, "Read FROM " + rq.stream);
      var ind = kidIndent(indent, last);
      forEach(rq.blocks, (b, i, isLast) -> {
        branch(sb, ind, isLast, "TYPE " + b.typeName());
        var ind2 = kidIndent(ind, isLast);
        String sel = b.select().stream()
          .map(selItem -> selItem instanceof Dml.Read.Star ? "*" :
               ((Dml.Read.Col) selItem).name().parts().stream().collect(Collectors.joining(".")))
          .collect(Collectors.joining(", "));
        branch(sb, ind2, b.where() == null, "SELECT " + sel);
        if (b.where() != null) {
          branch(sb, ind2, true, "WHERE " + exprToInfix(b.where()));
        }
      });
    } else if (s instanceof Dml.Write w) {
      branch(sb, indent, last, "Write TO " + w.stream + " TYPE " + w.typeName);
      var ind = kidIndent(indent, last);
      // Projection
      String proj = w.projection.stream().map(AstPrinter::pathToString).collect(Collectors.joining(", "));
      branch(sb, ind, false, "PROJECTION " + proj);
      // Values
      var valsInd = kidIndent(ind, false);
      branch(sb, ind, true, "VALUES");
      forEach(w.rows, (row, i, isLastRow) -> {
        String v = row.stream().map(AstPrinter::valToString).collect(Collectors.joining(", "));
        branch(sb, valsInd, isLastRow, "(" + v + ")");
      });
    } else {
      branch(sb, indent, last, s.getClass().getSimpleName());
    }
  }

  /* ───────── Field & type helpers ───────── */
  private static void printField(StringBuilder sb, Complex.StructField f, String indent, boolean last) {
    String name = f.name();
    String opt = f.optional() ? " OPTIONAL" : "";
    String def = (f.defaultJson() != null) ? " DEFAULT " + f.defaultJson() : "";
    branch(sb, indent, last, "Field " + name + ": " + dataTypeToString(f.typ()) + opt + def);
  }

  private static String dataTypeToString(DataType t) {
    if (t instanceof PrimitiveType p) return typeToString(p);
    if (t instanceof Composite.List l) return "LIST<" + dataTypeToString(l.item()) + ">";
    if (t instanceof Composite.Map m)  return "MAP<" + typeToString(m.key()) + ", " + dataTypeToString(m.value()) + ">";
    if (t instanceof TypeRef r) return "REF " + r.qName().fullName();
    return "<unknown-type>";
  }

  private static String typeToString(PrimitiveType p) {
    return switch (p) {
      case Primitive.Bool __ -> "BOOL";
      case Primitive.Int8 __ -> "INT8";
      case Primitive.UInt8 __ -> "UINT8";
      case Primitive.Int16 __ -> "INT16";
      case Primitive.UInt16 __ -> "UINT16";
      case Primitive.Int32 __ -> "INT32";
      case Primitive.UInt32 __ -> "UINT32";
      case Primitive.Int64 __ -> "INT64";
      case Primitive.UInt64 __ -> "UINT64";
      case Primitive.Single __ -> "SINGLE";
      case Primitive.Double __ -> "DOUBLE";
      case Primitive.String __ -> "STRING";
      case Primitive.FString f -> "FSTRING(" + f.size() + ")";
      case Primitive.Bytes __ -> "BYTES";
      case Primitive.FBytes f -> "FBYTES(" + f.size() + ")";
      case Primitive.Uuid __ -> "UUID";
      case Primitive.Date __ -> "DATE";
      case Primitive.Time t -> "TIME(" + t.precision() + ")";
      case Primitive.Timestamp t -> "TIMESTAMP(" + t.precision() + ")";
      case Primitive.TimestampTz t -> "TIMESTAMP_TZ(" + t.precision() + ")";
      case Primitive.Decimal d -> "DECIMAL(" + d.precision() + "," + d.scale() + ")";
    };
  }

  private static String pathToString(Dml.Write.Path p) {
    var b = new StringBuilder(p.head());
    for (var seg : p.segments()) {
      if (seg instanceof Dml.Write.FieldSeg f) b.append(".").append(f.name());
      else if (seg instanceof Dml.Write.IndexSeg i) b.append("[").append(i.index()).append("]");
      else if (seg instanceof Dml.Write.KeySeg k) b.append("[\"").append(k.key().replace("\"","\\\"")).append("\"]");
    }
    return b.toString();
  }

  private static String valToString(Dml.Write.ValLit v) {
    if (v instanceof Dml.Write.VStr s) return "'" + s.value().replace("'", "''") + "'";
    if (v instanceof Dml.Write.VNum n) {
      // Preserve integers without .0 when possible
      double d = n.value();
      if (Math.floor(d) == d) return Long.toString((long)d);
      return Double.toString(d);
    }
    if (v instanceof Dml.Write.VBool b) return Boolean.toString(b.value());
    if (v instanceof Dml.Write.VEnum e) return e.symbol();
    return "NULL";
  }

  /* ───────── Expression pretty-print (infix) ───────── */

  // Precedence: NOT > AND > OR; comparisons in the middle
  private static int prec(Expr e) {
    if (e instanceof Not) return 3;
    if (e instanceof Binary b) {
      return switch (b.op()) {
        case EQ, NEQ, LT, LTE, GT, GTE, IS_NULL, IS_NOT_NULL -> 2;
        case AND -> 1;
        case OR  -> 0;
      };
    }
    return 4; // literals/idents
  }

  private static String exprToInfix(Expr e) {
    if (e instanceof Not n) {
      String inner = exprToInfix(n.expr());
      return "NOT " + (prec(n.expr()) < prec(n) ? "(" + inner + ")" : inner);
    }
    if (e instanceof Binary b) {
      String op = switch (b.op()) {
        case EQ -> "="; case NEQ -> "!="; case LT -> "<"; case LTE -> "<=";
        case GT -> ">"; case GTE -> ">="; case AND -> "AND"; case OR -> "OR";
        case IS_NULL -> "IS NULL"; case IS_NOT_NULL -> "IS NOT NULL";
      };
      String L = exprToInfix(b.left());
      String R = exprToInfix(b.right());
      if (b.op() == BinOp.IS_NULL || b.op() == BinOp.IS_NOT_NULL) {
        // Right is just NULL in our encoding; print as unary postfix
        String left = prec(b.left()) < prec(b) ? "(" + L + ")" : L;
        return left + " " + op;
      } else {
        String left  = prec(b.left())  < prec(b) ? "(" + L + ")" : L;
        String right = prec(b.right()) < prec(b) ? "(" + R + ")" : R;
        return left + " " + op + " " + right;
      }
    }
    if (e instanceof Literal.Str s)  return "'" + s.value().replace("'", "''") + "'";
    if (e instanceof Literal.Num n)  return stripNum(n.value());
    if (e instanceof Literal.Bool b) return Boolean.toString(b.value());
    if (e instanceof Ident id)  return String.join(".", id.parts());
    return "NULL";
  }

  private static String stripNum(double d){
    if (Math.floor(d) == d) return Long.toString((long)d);
    return Double.toString(d);
  }

  /* ───────── Small util ───────── */
  private interface ForEach<T> { void each(T t, int index, boolean last); }
  private static <T> void forEach(List<T> xs, ForEach<T> f) {
    for (int i = 0; i < xs.size(); i++) f.each(xs.get(i), i, i == xs.size() - 1);
  }
}