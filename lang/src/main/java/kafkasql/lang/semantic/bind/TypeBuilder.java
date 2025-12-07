package kafkasql.lang.semantic.bind;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import kafkasql.runtime.diagnostics.Diagnostics;
import kafkasql.lang.semantic.BindingEnv;
import kafkasql.lang.semantic.factory.ComplexTypeFactory;
import kafkasql.lang.semantic.factory.TypeFactory;
import kafkasql.lang.semantic.symbol.SymbolTable;
import kafkasql.lang.syntax.ast.Script;
import kafkasql.lang.syntax.ast.decl.DerivedTypeDecl;
import kafkasql.lang.syntax.ast.decl.EnumDecl;
import kafkasql.lang.syntax.ast.decl.ScalarDecl;
import kafkasql.lang.syntax.ast.decl.StructDecl;
import kafkasql.lang.syntax.ast.decl.StructFieldDecl;
import kafkasql.lang.syntax.ast.decl.TypeDecl;
import kafkasql.lang.syntax.ast.decl.UnionDecl;
import kafkasql.lang.syntax.ast.decl.UnionMemberDecl;
import kafkasql.lang.syntax.ast.stmt.CreateStmt;
import kafkasql.lang.semantic.util.FragmentUtils;
import kafkasql.lang.syntax.ast.stmt.Stmt;
import kafkasql.lang.syntax.ast.type.ComplexTypeNode;
import kafkasql.lang.syntax.ast.type.ListTypeNode;
import kafkasql.lang.syntax.ast.type.MapTypeNode;
import kafkasql.lang.syntax.ast.type.TypeNode;
import kafkasql.runtime.Name;
import kafkasql.runtime.type.*;

/**
 * TypeBuilder
 * 
 * Builds runtime type objects (StructType, EnumType, etc.) from AST declarations.
 * 
 * This phase runs after TypeResolver, which has verified that all type references
 * are valid. TypeBuilder creates the actual runtime type objects and stores them
 * in bindings, so that later phases (DefaultBinder, StatementBinder) can use
 * fully-constructed types instead of TypeReference placeholders.
 * 
 * Key insight: In a SQL-style language, type information is always available from
 * context (field declarations, parameters, etc.), so we can build all runtime types
 * upfront before binding any literal values.
 */
public final class TypeBuilder {

    private TypeBuilder() { }

    /**
     * Build runtime types for all type declarations in the script.
     * 
     * Runtime types are stored in bindings with the TypeDecl as key:
     *   bindings.put(structDecl, runtimeStructType)
     */
    public static void buildTypes(
        Script script,
        SymbolTable symbols,
        BindingEnv bindings,
        Diagnostics diags
    ) {
        TypeBuildVisitor visitor = new TypeBuildVisitor(symbols, bindings, diags);
        
        for (Stmt stmt : script.statements()) {
            if (stmt instanceof CreateStmt ct && ct.decl() instanceof TypeDecl decl) {
                visitor.buildRuntimeType(decl);
            }
        }
    }

    /**
     * Public helper: resolve a TypeNode to a runtime AnyType.
     * 
     * This is used by other binders (e.g., StatementBinder) that need to resolve
     * field types that may reference complex types.
     */
    public static AnyType resolveFieldType(
        TypeNode typeNode,
        SymbolTable symbols,
        BindingEnv bindings,
        Diagnostics diags
    ) {
        TypeBuildVisitor visitor = new TypeBuildVisitor(symbols, bindings, diags);
        return visitor.resolveType(typeNode);
    }

    // ========================================================================
    // VISITOR
    // ========================================================================

    private static final class TypeBuildVisitor {
        
        private final SymbolTable symbols;
        private final BindingEnv bindings;
        private final Diagnostics diags;

        TypeBuildVisitor(SymbolTable symbols, BindingEnv bindings, Diagnostics diags) {
            this.symbols = symbols;
            this.bindings = bindings;
            this.diags = diags;
        }

        /**
         * Build and cache the runtime type for a declaration.
         * Handles circular dependencies by checking if already built.
         */
        public AnyType buildRuntimeType(TypeDecl decl) {
            
            // Check if we've already built this type
            AnyType cached = bindings.getOrNull(decl, AnyType.class);
            if (cached != null) {
                return cached;
            }

            // Get the Name for this declaration from the symbol table
            Name declName = symbols.nameOf(decl).orElseThrow(() -> 
                new IllegalStateException("Type declaration not in symbol table")
            );

            // Build the runtime type
            AnyType runtimeType = switch (decl.kind()) {
                case StructDecl s -> buildStructType(decl, s, declName);
                case EnumDecl e -> ComplexTypeFactory.fromEnumDecl(declName, e, decl.fragments(), diags);
                case ScalarDecl sc -> buildScalarType(decl, sc, declName);
                case UnionDecl u -> buildUnionType(decl, u, declName);
                case DerivedTypeDecl d -> {
                    // For derived types, resolve the target reference
                    Object resolved = bindings.get(d.target());
                    if (resolved instanceof TypeDecl targetDecl) {
                        // Recursively build the target type
                        yield buildRuntimeType(targetDecl);
                    }
                    throw new IllegalStateException("DerivedType target not resolved: " + d.target());
                }
            };

            // Cache it in bindings
            bindings.put(decl, runtimeType);
            
            return runtimeType;
        }

        /**
         * Build a StructType, recursively building field types.
         */
        private StructType buildStructType(TypeDecl typeDecl, StructDecl decl, Name declName) {
            
            LinkedHashMap<String, StructTypeField> fields = new LinkedHashMap<>();
            
            for (StructFieldDecl f : decl.fields()) {
                AnyType fieldType = resolveType(f.type());
                
                // Don't evaluate defaults here - that's DefaultBinder's job
                // Just build the type structure with no default values
                
                StructTypeField field = new StructTypeField(
                    f.name().name(),
                    fieldType,
                    f.nullable().isPresent(),
                    Optional.empty(),  // No default yet
                    FragmentUtils.extractDoc(f.fragments(), diags)
                );
                
                fields.put(field.name(), field);
            }
            
            // Build temporary struct without constraints for field resolution
            StructType tempStruct = new StructType(
                declName,
                fields,
                List.of(),
                FragmentUtils.extractDoc(typeDecl.fragments(), diags)
            );
            
            // Now bind constraints with full field context
            List<kafkasql.runtime.type.CheckConstraint> constraints = 
                ConstraintBinder.bindStructChecks(
                    typeDecl,
                    decl,
                    tempStruct,
                    symbols,
                    bindings,
                    diags
                );
            
            return new StructType(
                declName,
                fields,
                constraints,
                FragmentUtils.extractDoc(typeDecl.fragments(), diags)
            );
        }
        
        /**
         * Build a ScalarType with CHECK constraint.
         */
        private ScalarType buildScalarType(TypeDecl typeDecl, ScalarDecl decl, Name declName) {
            
            // Get the base primitive type
            PrimitiveType primitiveType = ComplexTypeFactory.fromScalarDecl(
                declName, 
                decl, 
                typeDecl.fragments(), 
                diags
            ).primitive();
            
            // Extract doc and default
            Optional<String> doc = FragmentUtils.extractDoc(typeDecl.fragments(), diags);
            Optional<Object> defaultValue = FragmentUtils.extractDefault(typeDecl.fragments(), diags);
            
            // Bind CHECK constraint
            Optional<kafkasql.runtime.type.CheckConstraint> checkConstraint = 
                ConstraintBinder.bindScalarCheck(
                    typeDecl,
                    decl,
                    primitiveType,
                    symbols,
                    bindings,
                    diags
                );
            
            return new ScalarType(
                declName,
                primitiveType,
                defaultValue,
                checkConstraint,
                doc
            );
        }

        /**
         * Build a UnionType, recursively building member types.
         */
        private UnionType buildUnionType(TypeDecl typeDecl, UnionDecl decl, Name declName) {
            
            LinkedHashMap<String, UnionTypeMember> members = new LinkedHashMap<>();
            
            for (UnionMemberDecl m : decl.members()) {
                AnyType memberType = resolveType(m.type());
                
                UnionTypeMember member = new UnionTypeMember(
                    m.name().name(),
                    memberType,
                    FragmentUtils.extractDoc(m.fragments(), diags)
                );
                
                members.put(member.name(), member);
            }
            
            return new UnionType(
                declName,
                members,
                FragmentUtils.extractDoc(typeDecl.fragments(), diags)
            );
        }

        /**
         * Resolve a TypeNode to a runtime AnyType.
         * 
         * For complex type references, recursively build the referenced type.
         * For composite types (List/Map), recursively resolve element/value types.
         */
        private AnyType resolveType(TypeNode typeNode) {
            
            // Complex type reference - look up and build the runtime type
            if (typeNode instanceof ComplexTypeNode complexNode) {
                Name name = Name.of(
                    complexNode.name().context(),
                    complexNode.name().name()
                );
                
                Optional<TypeDecl> declOpt = symbols.lookupType(name);
                if (declOpt.isPresent()) {
                    // Recursively build this type (handles circular deps via caching)
                    return buildRuntimeType(declOpt.get());
                }
                
                // Shouldn't happen if TypeResolver ran correctly
                diags.error(
                    complexNode.range(),
                    kafkasql.runtime.diagnostics.DiagnosticKind.TYPE,
                    kafkasql.runtime.diagnostics.DiagnosticCode.UNKNOWN_TYPE,
                    "Unknown type: " + name
                );
                return TypeReference.get(complexNode.name().fullName());
            }
            
            // List type - recursively resolve element type
            if (typeNode instanceof ListTypeNode listNode) {
                AnyType elementType = resolveType(listNode.elementType());
                return new ListType(elementType);
            }
            
            // Map type - recursively resolve value type (key must be primitive)
            if (typeNode instanceof MapTypeNode mapNode) {
                AnyType keyType = TypeFactory.fromAst(mapNode.keyType());
                AnyType valueType = resolveType(mapNode.valueType());
                
                if (keyType instanceof PrimitiveType primitiveKey) {
                    return new MapType(primitiveKey, valueType);
                }
                
                // Error: map key must be primitive
                diags.error(
                    mapNode.keyType().range(),
                    kafkasql.runtime.diagnostics.DiagnosticKind.TYPE,
                    kafkasql.runtime.diagnostics.DiagnosticCode.INVALID_TYPE_REF,
                    "Map keys must be primitive types"
                );
                return TypeFactory.fromAst(typeNode);
            }
            
            // Primitive type - use TypeFactory
            return TypeFactory.fromAst(typeNode);
        }
    }
}
