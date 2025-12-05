package kafkasql.lang.semantic.resolve;

import java.util.Optional;

import kafkasql.runtime.*;
import kafkasql.lang.diagnostics.DiagnosticCode;
import kafkasql.lang.diagnostics.DiagnosticKind;
import kafkasql.lang.diagnostics.Diagnostics;
import kafkasql.lang.semantic.BindingEnv;
import kafkasql.lang.semantic.symbol.SymbolTable;
import kafkasql.lang.syntax.ast.Script;
import kafkasql.lang.syntax.ast.decl.DerivedTypeDecl;
import kafkasql.lang.syntax.ast.decl.EnumDecl;
import kafkasql.lang.syntax.ast.decl.ScalarDecl;
import kafkasql.lang.syntax.ast.decl.StreamDecl;
import kafkasql.lang.syntax.ast.decl.StreamMemberDecl;
import kafkasql.lang.syntax.ast.decl.StructDecl;
import kafkasql.lang.syntax.ast.decl.StructFieldDecl;
import kafkasql.lang.syntax.ast.decl.TypeDecl;
import kafkasql.lang.syntax.ast.decl.TypeMemberDecl;
import kafkasql.lang.syntax.ast.fragment.DistributeDecl;
import kafkasql.lang.syntax.ast.fragment.TimestampDecl;
import kafkasql.lang.syntax.ast.decl.UnionDecl;
import kafkasql.lang.syntax.ast.decl.UnionMemberDecl;
import kafkasql.lang.syntax.ast.misc.QName;
import kafkasql.lang.syntax.ast.stmt.CreateStmt;
import kafkasql.lang.syntax.ast.stmt.Stmt;
import kafkasql.lang.syntax.ast.type.ComplexTypeNode;
import kafkasql.lang.syntax.ast.type.CompositeTypeNode;
import kafkasql.lang.syntax.ast.type.ListTypeNode;
import kafkasql.lang.syntax.ast.type.MapTypeNode;
import kafkasql.lang.syntax.ast.type.PrimitiveTypeNode;
import kafkasql.lang.syntax.ast.type.TypeNode;

public final class TypeResolver {

    private TypeResolver() {}

    public static void resolve(
        Script script,
        SymbolTable symbols,
        BindingEnv bindings,
        Diagnostics diags
    ) {
        ResolverVisitor visitor = new ResolverVisitor(symbols, diags, bindings);

        for (Stmt stmt : script.statements()) {
            switch (stmt) {
                case CreateStmt ct -> {
                    switch (ct.decl()) {
                        case TypeDecl t -> visitor.visitTypeDecl(t);
                        case StreamDecl s -> visitor.visitStreamDecl(s);
                        default -> { }
                    }
                }
                default -> { }
            }
        }
    }

    // ======================================================================
    // VISITOR
    // ======================================================================

    private static final class ResolverVisitor {

        private final SymbolTable symbols;
        private final Diagnostics diags;
        private final BindingEnv bindings;

        ResolverVisitor(SymbolTable symbols, Diagnostics diags, BindingEnv bindings) {
            this.symbols = symbols;
            this.diags = diags;
            this.bindings = bindings;
        }

        // ==============================================================
        // PUBLIC ENTRY POINTS
        // ==============================================================

        public void visitTypeDecl(TypeDecl decl) {
            if (bindings.containsKey(decl)) {
                return; // already resolving or resolved
            }

            bindings.put(decl, Boolean.FALSE); // mark resolving

            switch (decl.kind()) {
                case StructDecl s -> resolveStruct(s);
                case ScalarDecl s -> resolveScalar(s);
                case EnumDecl e   -> resolveEnum(e);
                case UnionDecl u  -> resolveUnion(u);
                case DerivedTypeDecl d -> resolveDerivedType(d);
                default -> throw new IllegalStateException("Unexpected type kind: " + decl.kind());
            }

            bindings.put(decl, Boolean.TRUE); // mark resolved
        }

        public void visitStreamDecl(StreamDecl decl) {
            // Validate that stream-level fragments don't contain DISTRIBUTE or TIMESTAMP
            for (var frag : decl.fragments()) {
                if (frag instanceof DistributeDecl dist) {
                    diags.error(
                        dist.range(),
                        DiagnosticKind.SEMANTIC,
                        DiagnosticCode.INVALID_FRAGMENT_LOCATION,
                        "DISTRIBUTE BY is not allowed at stream level. It must be specified on individual TYPE declarations."
                    );
                } else if (frag instanceof TimestampDecl ts) {
                    diags.error(
                        ts.range(),
                        DiagnosticKind.SEMANTIC,
                        DiagnosticCode.INVALID_FRAGMENT_LOCATION,
                        "TIMESTAMP BY is not allowed at stream level. It must be specified on individual TYPE declarations."
                    );
                }
            }
            
            for (StreamMemberDecl m : decl.streamTypes()) {
                visitTypeDecl(m.memberDecl());
            }
        }

        // ==============================================================    
        // TYPE DECLARATION RESOLUTION
        // ==============================================================

        private void resolveStruct(StructDecl s) {
            for (StructFieldDecl f : s.fields()) {
                resolveFieldType(f);
            }
        }

        private void resolveScalar(ScalarDecl s) {
            resolveTypeNode(s.type());
        }

        private void resolveEnum(EnumDecl e) {
            // symbols resolved later through literal binding
        }

        private void resolveUnion(UnionDecl u) {
            for (UnionMemberDecl m : u.members()) {
                resolveTypeNode(m.type());
            }
        }

        private void resolveDerivedType(DerivedTypeDecl d) {
            // Resolve the target type reference
            resolveTypeReference(d.target());
        }

        // ==============================================================
        // TYPE NODE RESOLUTION
        // ==============================================================

        private void resolveFieldType(TypeMemberDecl m) {
            TypeNode t = switch (m) {
                case StructFieldDecl sf -> sf.type();
                case UnionMemberDecl um -> um.type();
                default -> throw new IllegalStateException("Unexpected member: " + m);
            };
            resolveTypeNode(t);
        }

        private void resolveTypeNode(TypeNode node) {
            switch (node) {
                case ComplexTypeNode ref -> resolveTypeReference(ref);
                case PrimitiveTypeNode p -> {}
                case CompositeTypeNode c -> resolveTypeNode(c);
            }
        }

        private void resolveTypeNode(CompositeTypeNode node) {
            switch (node) {
                case ListTypeNode list ->
                    resolveTypeNode(list.elementType());

                case MapTypeNode map -> {
                    resolveTypeNode(map.valueType());
                }
            }
        }

        private void resolveTypeReference(ComplexTypeNode ref) {

            Name name = toName(ref.name());
            Optional<TypeDecl> decl = symbols.lookupType(name);

            if (decl.isEmpty()) {
                diags.error(
                    ref.range(),
                    DiagnosticKind.SEMANTIC,
                    DiagnosticCode.UNKNOWN_TYPE,
                    "Unknown type reference: " + name
                );
                return;
            }

            bindings.put(ref, decl.get());

            // Ensure referenced decl is also resolved
            visitTypeDecl(decl.get());
        }

        private static Name toName(QName q) {
            return Name.of(q.context(), q.name());
        }
    }
}