package kafkasql.lang.semantic.bind;

import kafkasql.runtime.type.*;
import kafkasql.lang.diagnostics.*;
import kafkasql.lang.semantic.BindingEnv;
import kafkasql.lang.semantic.symbol.SymbolTable;
import kafkasql.lang.syntax.ast.expr.*;
import kafkasql.lang.syntax.ast.literal.*;
import kafkasql.lang.syntax.ast.misc.Identifier;
import kafkasql.lang.syntax.ast.decl.EnumDecl;
import kafkasql.runtime.Name;

import java.util.Optional;

public final class ExpressionBinder {

    private final TypeEnv env;
    private final SymbolTable symbols;
    private final Diagnostics diags;
    private final BindingEnv bindings;

    public ExpressionBinder(TypeEnv env, SymbolTable symbols, Diagnostics diags, BindingEnv bindings) {
        this.env = env;
        this.symbols = symbols;
        this.diags = diags;
        this.bindings = bindings;
    }

    public AnyType bind(Expr expr) {
        AnyType t = bindExpr(expr);
        bindings.put(expr, t);
        return t;
    }

    private AnyType bindExpr(Expr expr) {
        return switch (expr) {
            case LiteralExpr lit   -> bindLiteralExpr(lit);
            case IdentifierExpr id -> bindIdentifierExpr(id);
            case MemberExpr m      -> bindMemberExpr(m);
            case IndexExpr ix      -> bindIndexExpr(ix);
            case PrefixExpr p      -> bindPrefixExpr(p);
            case PostfixExpr p     -> bindPostfixExpr(p);
            case InfixExpr inf     -> bindInfixExpr(inf);
            case TrifixExpr tri    -> bindTrifixExpr(tri);
            case ParenExpr paren   -> bindParenExpr(paren);
        };
    }

    // ---------------------------------------------------------------------
    // Literal inference (untyped)
    // ---------------------------------------------------------------------

    private AnyType bindLiteralExpr(LiteralExpr lit) {
        LiteralNode node = lit.literal();

        AnyType t = switch (node) {
            case BoolLiteralNode __   -> PrimitiveType.bool();
            case StringLiteralNode __ -> PrimitiveType.string();
            case BytesLiteralNode __  -> PrimitiveType.bytes();
            case NullLiteralNode __   -> VoidType.get();
            case NumberLiteralNode __ -> PrimitiveType.float64();

            case EnumLiteralNode enumLit -> bindEnumLiteral(enumLit);
            
            // These resolve later when typed
            case UnionLiteralNode __  -> VoidType.get();
            case StructLiteralNode __ -> VoidType.get();
            case ListLiteralNode __   -> VoidType.get();
            case MapLiteralNode __    -> VoidType.get();
        };

        bindings.put(lit, t);
        return t;
    }
    
    private AnyType bindEnumLiteral(EnumLiteralNode enumLit) {
        // Look up the enum type
        Name enumName = Name.of(
            enumLit.enumName().context(),
            enumLit.enumName().name()
        );
        
        Optional<kafkasql.lang.syntax.ast.decl.TypeDecl> typeDeclOpt = symbols.lookupType(enumName);
        if (typeDeclOpt.isEmpty()) {
            diags.error(
                enumLit.range(),
                DiagnosticKind.TYPE,
                DiagnosticCode.UNKNOWN_TYPE,
                "Unknown enum type: " + enumName
            );
            return VoidType.get();
        }
        
        if (!(typeDeclOpt.get() instanceof EnumDecl enumDecl)) {
            diags.error(
                enumLit.range(),
                DiagnosticKind.TYPE,
                DiagnosticCode.INVALID_TYPE_REF,
                "Type is not an enum: " + enumName
            );
            return VoidType.get();
        }
        
        // Get the pre-built EnumType from bindings
        Object boundValue = bindings.get(enumDecl);
        
        if (boundValue == null) {
            // Shouldn't happen if TypeBuilder ran
            diags.error(
                enumLit.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.INTERNAL_ERROR,
                "Enum type not built (no binding): " + enumName
            );
            return VoidType.get();
        }
        
        if (!(boundValue instanceof EnumType enumType)) {
            diags.error(
                enumLit.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.INTERNAL_ERROR,
                "Enum binding is not EnumType: " + boundValue.getClass().getSimpleName()
            );
            return VoidType.get();
        }
        
        // Verify the symbol exists
        String symbolName = enumLit.symbol().name();
        boolean found = enumType.symbols().stream()
            .anyMatch(sym -> sym.name().equals(symbolName));
            
        if (!found) {
            diags.error(
                enumLit.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.UNKNOWN_FIELD,
                "Unknown enum symbol: " + symbolName + " in " + enumName
            );
            return VoidType.get();
        }
        
        return enumType;
    }

    // ---------------------------------------------------------------------
    // Identifiers
    // ---------------------------------------------------------------------

    private AnyType bindIdentifierExpr(IdentifierExpr idExpr) {
        Identifier name = idExpr.name();
        Optional<AnyType> t = env.lookup(name.name());

        if (t.isEmpty()) {
            diags.error(
                idExpr.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.UNKNOWN_FIELD,
                "Unknown identifier: " + name.name()
            );
            bindings.put(idExpr, VoidType.get());
            return VoidType.get();
        }

        AnyType resolved = t.get();
        bindings.put(idExpr, resolved);
        return resolved;
    }

    // ---------------------------------------------------------------------
    // Member access: struct.field
    // ---------------------------------------------------------------------

    private AnyType bindMemberExpr(MemberExpr m) {
        AnyType targetType = bindExpr(m.target());

        if (!(targetType instanceof StructType struct)) {
            diags.error(
                m.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.INVALID_OPERATOR,
                "Cannot access member '" + m.name().name() +
                    "' on non-struct type: " + debugType(targetType)
            );
            bindings.put(m, VoidType.get());
            return VoidType.get();
        }

        StructTypeField field = struct.fields().get(m.name().name());
        if (field == null) {
            diags.error(
                m.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.UNKNOWN_FIELD,
                "Unknown field '" + m.name().name() +
                    "' on struct " + struct.fqn().toString()
            );
            bindings.put(m, VoidType.get());
            return VoidType.get();
        }

        AnyType fieldType = field.type();
        bindings.put(m, fieldType);
        return fieldType;
    }

    // ---------------------------------------------------------------------
    // Index access
    // ---------------------------------------------------------------------

    private AnyType bindIndexExpr(IndexExpr idx) {
        AnyType target = bindExpr(idx.target());
        AnyType index  = bindExpr(idx.index());

        if (target instanceof ListType listType) {

            // Special case: numeric literals must be valid INT32 for list indexing
            if (index instanceof PrimitiveType ip && ip.isNumericKind()) {
                // Check if the index expression is a literal and validate it's INT32-compatible
                if (idx.index() instanceof LiteralExpr litExpr && 
                    litExpr.literal() instanceof NumberLiteralNode numLit) {
                    
                    try {
                        // Try to parse as integer and check if it fits in INT32
                        String text = numLit.text();
                        // Remove any underscores (allowed in number literals)
                        text = text.replace("_", "");
                        
                        // Check if it's a whole number
                        if (text.contains(".") || text.toLowerCase().contains("e")) {
                            diags.error(
                                idx.index().range(),
                                DiagnosticKind.SEMANTIC,
                                DiagnosticCode.TYPE_MISMATCH,
                                "List index must be a whole number (INT32), got: " + text
                            );
                        } else {
                            long value = Long.parseLong(text);
                            if (value < 0) {
                                diags.error(
                                    idx.index().range(),
                                    DiagnosticKind.SEMANTIC,
                                    DiagnosticCode.TYPE_MISMATCH,
                                    "List index cannot be negative: " + value
                                );
                            } else if (value > Integer.MAX_VALUE) {
                                diags.error(
                                    idx.index().range(),
                                    DiagnosticKind.SEMANTIC,
                                    DiagnosticCode.TYPE_MISMATCH,
                                    "List index out of INT32 range: " + value
                                );
                            }
                        }
                    } catch (NumberFormatException e) {
                        diags.error(
                            idx.index().range(),
                            DiagnosticKind.SEMANTIC,
                            DiagnosticCode.TYPE_MISMATCH,
                            "Invalid numeric literal for list index"
                        );
                    }
                }
                // Accept as valid index
                bindings.put(idx, listType.item());
                return listType.item();
            }
            
            if (!(index instanceof PrimitiveType ip) || !ip.isIntegerKind()) {
                diags.error(
                    idx.range(),
                    DiagnosticKind.SEMANTIC,
                    DiagnosticCode.TYPE_MISMATCH,
                    "List index must be integer, got: " + debugType(index)
                );
            }
            bindings.put(idx, listType.item());
            return listType.item();
        }

        if (target instanceof MapType mapType) {

            if (!isPrimitiveAssignable(index, mapType.key())) {
                diags.error(
                    idx.range(),
                    DiagnosticKind.SEMANTIC,
                    DiagnosticCode.TYPE_MISMATCH,
                    "Map key mismatch: expected " + debugType(mapType.key()) +
                        ", got " + debugType(index)
                );
            }

            bindings.put(idx, mapType.value());
            return mapType.value();
        }

        diags.error(
            idx.range(),
            DiagnosticKind.SEMANTIC,
            DiagnosticCode.INVALID_OPERATOR,
            "Indexing only supported on LIST and MAP types, got: " + debugType(target)
        );
        bindings.put(idx, VoidType.get());
        return VoidType.get();
    }

    // ---------------------------------------------------------------------
    // Prefix (!, -)
    // ---------------------------------------------------------------------

    private AnyType bindPrefixExpr(PrefixExpr p) {
        AnyType innerType = bindExpr(p.expr());

        return switch (p.op()) {

            case NOT -> {
                if (!(innerType instanceof PrimitiveType pt) || !pt.isBooleanKind()) {
                    diags.error(
                        p.range(),
                        DiagnosticKind.SEMANTIC,
                        DiagnosticCode.TYPE_MISMATCH,
                        "NOT requires BOOLEAN operand, got: " + debugType(innerType)
                    );
                }
                bindings.put(p, PrimitiveType.bool());
                yield PrimitiveType.bool();
            }

            case NEG -> {
                if (!(innerType instanceof PrimitiveType pt) || 
                        pt.isIntegerKind() || pt.isNumericKind()) {
                    diags.error(
                        p.range(),
                        DiagnosticKind.SEMANTIC,
                        DiagnosticCode.TYPE_MISMATCH,
                        "Unary '-' requires numeric operand, got: " + debugType(innerType)
                    );
                }
                bindings.put(p, innerType);
                yield innerType;
            }
        };
    }

    // ---------------------------------------------------------------------
    // Postfix (IS NULL / IS NOT NULL)
    // ---------------------------------------------------------------------

    private AnyType bindPostfixExpr(PostfixExpr p) {
        bindExpr(p.expr());

        return switch (p.op()) {
            case IS_NULL, IS_NOT_NULL -> {
                bindings.put(p, PrimitiveType.bool());
                yield PrimitiveType.bool();
            }
        };
    }

    // ---------------------------------------------------------------------
    // Parens
    // ---------------------------------------------------------------------

    private AnyType bindParenExpr(ParenExpr p) {
        AnyType t = bindExpr(p.inner());
        bindings.put(p, t);
        return t;
    }

    // ---------------------------------------------------------------------
    // Infix binary ops
    // ---------------------------------------------------------------------

    private AnyType bindInfixExpr(InfixExpr inf) {
        AnyType left  = bindExpr(inf.left());
        AnyType right = bindExpr(inf.right());

        return switch (inf.op()) {

            // Boolean logic
            case AND, OR, XOR -> {
                if (!(left instanceof PrimitiveType l) || !(right instanceof PrimitiveType r) ||
                    !l.isBooleanKind() || !r.isBooleanKind()) {
                    diags.error(
                        inf.range(),
                        DiagnosticKind.SEMANTIC,
                        DiagnosticCode.TYPE_MISMATCH,
                        inf.op() + " requires BOOLEAN operands, got: " +
                            debugType(left) + " and " + debugType(right)
                    );
                }
                bindings.put(inf, PrimitiveType.bool());
                yield PrimitiveType.bool();
            }

            // Comparisons
            case EQ, NEQ, LT, LTE, GT, GTE -> {
                if (!areComparable(left, right)) {
                    diags.error(
                        inf.range(),
                        DiagnosticKind.SEMANTIC,
                        DiagnosticCode.TYPE_MISMATCH,
                        "Operands not comparable for " + inf.op() +
                            ": " + debugType(left) + " vs " + debugType(right)
                    );
                }
                bindings.put(inf, PrimitiveType.bool());
                yield PrimitiveType.bool();
            }

            // Arithmetic
            case ADD, SUB, MUL, DIV, MOD -> {
                if (!(isNumeric(left) && isNumeric(right))) {
                    diags.error(
                        inf.range(),
                        DiagnosticKind.SEMANTIC,
                        DiagnosticCode.TYPE_MISMATCH,
                        "Arithmetic operator requires numeric operands, got: " +
                            debugType(left) + " and " + debugType(right)
                    );
                }
                AnyType result = numericResult(left, right);
                bindings.put(inf, result);
                yield result;
            }

            // Bitwise
            case BITAND, BITOR, SHL, SHR -> {
                if (!(left instanceof PrimitiveType l && right instanceof PrimitiveType r &&
                        l.isIntegerKind() && r.isIntegerKind())) {
                    diags.error(
                        inf.range(),
                        DiagnosticKind.SEMANTIC,
                        DiagnosticCode.TYPE_MISMATCH,
                        "Bitwise/shifts require integer operands, got: " +
                            debugType(left) + " and " + debugType(right)
                    );
                }
                bindings.put(inf, left);
                yield left;
            }

            // IN operator
            case IN -> {
                if (!(right instanceof ListType)) {
                    diags.error(
                        inf.range(),
                        DiagnosticKind.SEMANTIC,
                        DiagnosticCode.TYPE_MISMATCH,
                        "Right operand of IN must be a LIST, got: " + debugType(right)
                    );
                }
                bindings.put(inf, PrimitiveType.bool());
                yield PrimitiveType.bool();
            }
        };
    }

    // ---------------------------------------------------------------------
    // Ternary (BETWEEN)
    // ---------------------------------------------------------------------

    private AnyType bindTrifixExpr(TrifixExpr tri) {
        AnyType a = bindExpr(tri.left());
        AnyType b = bindExpr(tri.middle());
        AnyType c = bindExpr(tri.right());

        return switch (tri.op()) {

            case BETWEEN -> {
                if (!(areComparable(a, b) && areComparable(a, c))) {
                    diags.error(
                        tri.range(),
                        DiagnosticKind.SEMANTIC,
                        DiagnosticCode.TYPE_MISMATCH,
                        "BETWEEN requires comparable operands, got: " +
                            debugType(a) + ", " +
                            debugType(b) + ", " +
                            debugType(c)
                    );
                }
                bindings.put(tri, PrimitiveType.bool());
                yield PrimitiveType.bool();
            }
        };
    }

    // ---------------------------------------------------------------------
    // Helper functions
    // ---------------------------------------------------------------------

    private boolean isNumeric(AnyType t) {
        return t instanceof PrimitiveType pt &&
            (pt.isIntegerKind() || pt.isNumericKind());
    }

    private boolean areComparable(AnyType a, AnyType b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (isNumeric(a) && isNumeric(b)) return true;
        if (a.getClass().equals(b.getClass())) return true;
        return false;
    }

    private AnyType numericResult(AnyType l, AnyType r) {
        if (l instanceof PrimitiveType pt && pt.isNumericKind()) return l;
        if (r instanceof PrimitiveType pt && pt.isNumericKind()) return r;
        return l;
    }

    private boolean isPrimitiveAssignable(AnyType from, PrimitiveType to) {
        if (!(from instanceof PrimitiveType fp))
            return false;
        if (fp.getClass().equals(to.getClass()))
            return true;
        if (fp instanceof PrimitiveType f && to instanceof PrimitiveType t &&
            f.isIntegerKind() && t.isIntegerKind()){
            return true;
        }
        return false;
    }

    private static String debugType(AnyType t) {
        if (t == null) return "<null>";
        if (t instanceof ComplexType ct) return ct.fqn().toString();
        return t.getClass().getSimpleName();
    }
}