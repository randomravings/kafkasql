package kafkasql.codegen;

import java.util.LinkedHashMap;
import java.util.Map;

import kafkasql.lang.semantic.SemanticModel;
import kafkasql.lang.semantic.symbol.SymbolTable;
import kafkasql.runtime.Name;
import kafkasql.runtime.type.*;

/**
 * Compiler - Generates Java source code from KafkaSQL type definitions.
 * 
 * Takes a SemanticModel with bound types and generates:
 * - Scalar types → type aliases / wrapper classes
 * - Enum types → Java enums
 * - Struct types → Java records
 * - Union types → sealed interfaces (TODO)
 */
public class Compiler {
    private static final String INDENT = "    ";
    
    private final SemanticModel model;
    private final SymbolTable symbols;
    private final Map<String, String> generatedFiles;
    
    public Compiler(SemanticModel model) {
        this.model = model;
        this.symbols = model.symbols();
        this.generatedFiles = new LinkedHashMap<>();
    }
    
    /**
     * Compile all type definitions to Java source files.
     * Returns a map of qualified class name → source code.
     */
    public Map<String, String> compile() {
        // Generate code for each type in the symbol table
        for (Map.Entry<Name, kafkasql.lang.syntax.ast.decl.Decl> entry : symbols._decl.entrySet()) {
            Name typeName = entry.getKey();
            Object typeDecl = entry.getValue();
            
            // Skip non-type declarations
            if (!(typeDecl instanceof kafkasql.lang.syntax.ast.decl.TypeDecl)) {
                continue;
            }
            
            // Get the bound runtime type
            Object boundType = model.bindings().get(typeDecl);
            
            if (boundType instanceof ScalarType scalarType) {
                generateScalar(typeName, scalarType);
            } else if (boundType instanceof EnumType enumType) {
                generateEnum(typeName, enumType);
            } else if (boundType instanceof StructType structType) {
                generateStruct(typeName, structType);
            }
            // TODO: UnionType
        }
        
        return generatedFiles;
    }
    
    // ========================================================================
    // Scalar Generation
    // ========================================================================
    
    private void generateScalar(Name name, ScalarType type) {
        String className = toClassName(name);
        String javaType = mapPrimitiveType(type.primitive());
        
        StringBuilder sb = new StringBuilder();
        
        // Package
        if (!name.context().isEmpty()) {
            sb.append("package ").append(toPackage(name)).append(";\n\n");
        }
        
        // Imports
        if (needsImport(type.primitive())) {
            sb.append("import java.math.BigDecimal;\n");
            sb.append("import java.time.*;\n");
            sb.append("import java.util.UUID;\n\n");
        }
        
        // Documentation
        type.doc().ifPresent(doc -> {
            sb.append("/**\n");
            sb.append(" * ").append(doc).append("\n");
            sb.append(" */\n");
        });
        
        // Type alias as record wrapper
        sb.append("public record ").append(name.name()).append("(").append(javaType).append(" value) {\n");
        sb.append("}\n");
        
        generatedFiles.put(className, sb.toString());
    }
    
    // ========================================================================
    // Enum Generation
    // ========================================================================
    
    private void generateEnum(Name name, EnumType type) {
        String className = toClassName(name);
        
        StringBuilder sb = new StringBuilder();
        
        // Package
        if (!name.context().isEmpty()) {
            sb.append("package ").append(toPackage(name)).append(";\n\n");
        }
        
        // Documentation
        type.doc().ifPresent(doc -> {
            sb.append("/**\n");
            sb.append(" * ").append(doc).append("\n");
            sb.append(" */\n");
        });
        
        // Enum declaration
        sb.append("public enum ").append(name.name()).append(" {\n");
        
        // Enum constants
        boolean first = true;
        for (EnumTypeSymbol symbol : type.symbols()) {
            if (!first) {
                sb.append(",\n");
            }
            first = false;
            
            // Symbol documentation
            symbol.doc().ifPresent(doc -> {
                sb.append(INDENT).append("/** ").append(doc).append(" */\n");
            });
            
            sb.append(INDENT).append(symbol.name()).append("(").append(symbol.value()).append(")");
        }
        sb.append(";\n\n");
        
        // Value field and constructor
        sb.append(INDENT).append("private final int value;\n\n");
        sb.append(INDENT).append(name.name()).append("(int value) {\n");
        sb.append(INDENT).append(INDENT).append("this.value = value;\n");
        sb.append(INDENT).append("}\n\n");
        
        // Getter
        sb.append(INDENT).append("public int getValue() {\n");
        sb.append(INDENT).append(INDENT).append("return value;\n");
        sb.append(INDENT).append("}\n");
        
        sb.append("}\n");
        
        generatedFiles.put(className, sb.toString());
    }
    
    // ========================================================================
    // Struct Generation
    // ========================================================================
    
    private void generateStruct(Name name, StructType type) {
        String className = toClassName(name);
        
        StringBuilder sb = new StringBuilder();
        
        // Package
        if (!name.context().isEmpty()) {
            sb.append("package ").append(toPackage(name)).append(";\n\n");
        }
        
        // Imports
        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.time.*;\n");
        sb.append("import java.util.*;\n\n");
        
        // Documentation
        type.doc().ifPresent(doc -> {
            sb.append("/**\n");
            sb.append(" * ").append(doc).append("\n");
            sb.append(" */\n");
        });
        
        // Record declaration
        sb.append("public record ").append(name.name()).append("(\n");
        
        // Fields
        boolean first = true;
        for (var entry : type.fields().entrySet()) {
            String fieldName = entry.getKey();
            StructTypeField field = entry.getValue();
            
            if (!first) {
                sb.append(",\n");
            }
            first = false;
            
            // Field documentation
            field.doc().ifPresent(doc -> {
                sb.append(INDENT).append("/** ").append(doc).append(" */\n");
            });
            
            String javaType = mapFieldType(field.type(), field.nullable());
            sb.append(INDENT).append(javaType).append(" ").append(fieldName);
        }
        
        sb.append("\n) {\n}\n");
        
        generatedFiles.put(className, sb.toString());
    }
    
    // ========================================================================
    // Helper Methods
    // ========================================================================
    
    private String toClassName(Name name) {
        if (!name.context().isEmpty()) {
            return name.context().replace('.', '/') + "/" + name.name();
        }
        return name.name();
    }
    
    private String toPackage(Name name) {
        return name.context();
    }
    
    private boolean needsImport(PrimitiveType type) {
        return switch (type.kind()) {
            case DECIMAL, DATE, TIME, TIMESTAMP, TIMESTAMP_TZ, UUID -> true;
            default -> false;
        };
    }
    
    private String mapPrimitiveType(PrimitiveType type) {
        return switch (type.kind()) {
            case BOOLEAN -> "boolean";
            case INT8 -> "byte";
            case INT16 -> "short";
            case INT32 -> "int";
            case INT64 -> "long";
            case FLOAT32 -> "float";
            case FLOAT64 -> "double";
            case STRING -> "String";
            case BYTES -> "byte[]";
            case UUID -> "UUID";
            case DATE -> "LocalDate";
            case TIME -> "LocalTime";
            case TIMESTAMP -> "LocalDateTime";
            case TIMESTAMP_TZ -> "ZonedDateTime";
            case DECIMAL -> "BigDecimal";
        };
    }
    
    private String mapFieldType(AnyType type, boolean nullable) {
        String baseType = switch (type) {
            case PrimitiveType pt -> mapPrimitiveType(pt);
            case ScalarType st -> st.fqn().toString().replace('.', '.');
            case EnumType et -> et.fqn().toString().replace('.', '.');
            case StructType st -> st.fqn().toString().replace('.', '.');
            case ListType lt -> "List<" + mapFieldType(lt.item(), false) + ">";
            case MapType mt -> "Map<" + mapFieldType(mt.key(), false) + ", " + 
                                        mapFieldType(mt.value(), false) + ">";
            case UnionType ut -> ut.fqn().toString().replace('.', '.');
            default -> "Object";
        };
        
        // Make nullable for reference types
        if (nullable && isReferenceType(type)) {
            return baseType; // Already nullable in Java
        }
        
        return baseType;
    }
    
    private boolean isReferenceType(AnyType type) {
        return !(type instanceof PrimitiveType pt && isPrimitivePrimitive(pt));
    }
    
    private boolean isPrimitivePrimitive(PrimitiveType type) {
        return switch (type.kind()) {
            case BOOLEAN, INT8, INT16, INT32, INT64, FLOAT32, FLOAT64 -> true;
            default -> false;
        };
    }
}
