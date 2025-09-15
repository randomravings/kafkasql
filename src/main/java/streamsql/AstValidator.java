package streamsql;

import java.util.*;
import streamsql.ast.*;
import streamsql.validation.StreamValidator;

public final class AstValidator {
  private AstValidator() {}

  public static void validate(List<Stmt> stmts, Catalog catalog) {
    Map<String, StructT> structs = new HashMap<>();

    for (Stmt s : stmts) {
      if (s instanceof CreateType ct && ct.type() instanceof StructT st) {
        structs.put(st.qName().fullName(), st);
      }
    }

    for (Stmt stmt : stmts) {
      switch (stmt) {
        case CreateStream cs:
          StreamValidator.validate(cs, catalog);
          break;
        default:
          break;
      }
    }
  }
}