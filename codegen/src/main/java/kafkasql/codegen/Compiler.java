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
        // Generate code for each declaration in the symbol table
        for (Map.Entry<Name, kafkasql.lang.syntax.ast.decl.Decl> entry : symbols._decl.entrySet()) {
            Name declName = entry.getKey();
            kafkasql.lang.syntax.ast.decl.Decl decl = entry.getValue();
            
            if (decl instanceof kafkasql.lang.syntax.ast.decl.TypeDecl typeDecl) {
                // Get the bound runtime type
                Object boundType = model.bindings().get(typeDecl);
                
                if (boundType instanceof ScalarType scalarType) {
                    generateScalar(declName, scalarType);
                } else if (boundType instanceof EnumType enumType) {
                    generateEnum(declName, enumType);
                } else if (boundType instanceof StructType structType) {
                    generateStruct(declName, structType);
                }
                // TODO: UnionType
            } else if (decl instanceof kafkasql.lang.syntax.ast.decl.StreamDecl streamDecl) {
                generateStream(declName, streamDecl);
            }
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
        sb.append("import kafkasql.runtime.value.RecordValue;\n");
        if (needsImport(type.primitive())) {
            sb.append("import java.math.BigDecimal;\n");
            sb.append("import java.time.*;\n");
            sb.append("import java.util.UUID;\n");
        }
        sb.append("\n");
        
        // Documentation
        type.doc().ifPresent(doc -> {
            sb.append("/**\n");
            sb.append(" * ").append(doc).append("\n");
            sb.append(" */\n");
        });
        
        // Type alias as record wrapper with self-referential generic
        String typeName = name.name();
        sb.append("public record ").append(typeName).append("(").append(javaType).append(" value) implements RecordValue<").append(typeName).append("> {\n");
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
        
        // Imports
        sb.append("import kafkasql.runtime.value.RecordValue;\n\n");
        
        // Documentation
        type.doc().ifPresent(doc -> {
            sb.append("/**\n");
            sb.append(" * ").append(doc).append("\n");
            sb.append(" */\n");
        });
        
        // Enum declaration with self-referential generic
        String enumName = name.name();
        sb.append("public enum ").append(enumName).append(" implements RecordValue<").append(enumName).append("> {");
        
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
        sb.append("import kafkasql.runtime.value.RecordValue;\n");
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
        String recordName = name.name();
        sb.append("public record ").append(recordName).append("(\n");
        
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
        
        sb.append("\n) implements RecordValue<").append(recordName).append("> {\n}\n");
        
        generatedFiles.put(className, sb.toString());
    }
    
    // ========================================================================
    // Stream Generation
    // ========================================================================
    
    private void generateStream(Name name, kafkasql.lang.syntax.ast.decl.StreamDecl streamDecl) {
        String className = toClassName(name);
        
        StringBuilder sb = new StringBuilder();
        
        // Package
        if (!name.context().isEmpty()) {
            sb.append("package ").append(toPackage(name)).append(";\n\n");
        }
        
        // Imports
        sb.append("import kafkasql.runtime.stream.CompiledStream;\n");
        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.time.*;\n");
        sb.append("import java.util.*;\n\n");
        
        // Collect all permitted types for sealed interface
        StringBuilder permitsClause = new StringBuilder();
        boolean firstPermit = true;
        for (kafkasql.lang.syntax.ast.decl.StreamMemberDecl member : streamDecl.streamTypes()) {
            if (!firstPermit) {
                permitsClause.append(", ");
            }
            firstPermit = false;
            permitsClause.append(name.name()).append(".").append(member.name().name());
        }
        
        // Stream as sealed interface with CompiledStream
        String streamName = name.name();
        sb.append("/**\n");
        sb.append(" * Stream: ").append(streamName).append("\n");
        sb.append(" * Sealed interface for all message types in this stream.\n");
        sb.append(" */\n");
        sb.append("public sealed interface ").append(streamName).append(" extends CompiledStream<").append(streamName).append(">");
        sb.append(" permits ").append(permitsClause).append(" {\n\n");
        
        // Generate static factory methods for reader/writer
        generateStreamFactoryMethods(sb, streamName, streamDecl);
        
        // Generate each member type
        for (kafkasql.lang.syntax.ast.decl.StreamMemberDecl member : streamDecl.streamTypes()) {
            kafkasql.lang.syntax.ast.decl.TypeDecl memberTypeDecl = member.memberDecl();
            String memberName = memberTypeDecl.name().name();
            
            // For inline structs in streams, work directly with the AST
            if (memberTypeDecl.kind() instanceof kafkasql.lang.syntax.ast.decl.StructDecl structDecl) {
                // Generate inline struct as static nested record implementing the interface
                sb.append(INDENT).append("/**\n");
                sb.append(INDENT).append(" * Stream member: ").append(memberName).append("\n");
                sb.append(INDENT).append(" */\n");
                sb.append(INDENT).append("record ").append(memberName).append("(\n");
                
                boolean first = true;
                for (kafkasql.lang.syntax.ast.decl.StructFieldDecl field : structDecl.fields()) {
                    if (!first) {
                        sb.append(",\n");
                    }
                    first = false;
                    
                    String fieldName = field.name().name();
                    
                    // Map the AST type node to Java type
                    String javaType = mapAstTypeToJava(field.type(), field.nullable().isPresent());
                    sb.append(INDENT).append(INDENT).append(javaType).append(" ").append(fieldName);
                }
                
                sb.append("\n").append(INDENT).append(") implements ").append(name.name()).append(" {}\n\n");
            } else {
                // For external type references, check if we have a binding
                Object boundType = model.bindings().get(memberTypeDecl);
                if (boundType != null) {
                    // For external type references, generate a wrapper record that delegates
                    String referencedTypeFqn = null;
                    if (boundType instanceof ScalarType st) {
                        referencedTypeFqn = st.fqn().fullName().replace('.', '.');
                    } else if (boundType instanceof EnumType et) {
                        referencedTypeFqn = et.fqn().fullName().replace('.', '.');
                    } else if (boundType instanceof StructType st) {
                        referencedTypeFqn = st.fqn().fullName().replace('.', '.');
                    }
                    
                    if (referencedTypeFqn != null) {
                        sb.append(INDENT).append("/**\n");
                        sb.append(INDENT).append(" * Wrapper for external type: ").append(referencedTypeFqn).append("\n");
                        sb.append(INDENT).append(" */\n");
                        sb.append(INDENT).append("record ").append(memberName).append("(\n");
                        sb.append(INDENT).append(INDENT).append(referencedTypeFqn).append(" value\n");
                        sb.append(INDENT).append(") implements ").append(name.name()).append(" {}\n\n");
                    }
                }
            }
        }
        
        // Close the sealed interface
        sb.append("}\n");
        
        generatedFiles.put(className, sb.toString());
    }
    
    private void generateStreamFactoryMethods(StringBuilder sb, String streamName, kafkasql.lang.syntax.ast.decl.StreamDecl streamDecl) {
        // Static method to create a Kafka-backed reader
        sb.append(INDENT).append("/**\n");
        sb.append(INDENT).append(" * Creates a Kafka-backed stream reader for ").append(streamName).append(".\n");
        sb.append(INDENT).append(" *\n");
        sb.append(INDENT).append(" * <p>The topic name is implicitly derived from the stream name.\n");
        sb.append(INDENT).append(" * The caller is responsible for creating and configuring the consumer,\n");
        sb.append(INDENT).append(" * including byte[] deserializers, consumer group, subscriptions, and other properties.\n");
        sb.append(INDENT).append(" * The consumer is NOT owned by this reader - the caller must manage its lifecycle.\n");
        sb.append(INDENT).append(" *\n");
        sb.append(INDENT).append(" * @param consumer Pre-configured Kafka consumer with byte[] key/value deserializers\n");
        sb.append(INDENT).append(" * @return A StreamReader instance backed by Kafka\n");
        sb.append(INDENT).append(" */\n");
        sb.append(INDENT).append("static kafkasql.runtime.stream.StreamReader<").append(streamName).append("> reader(\n");
        sb.append(INDENT).append(INDENT).append("org.apache.kafka.clients.consumer.KafkaConsumer<byte[], byte[]> consumer\n");
        sb.append(INDENT).append(") {\n");
        sb.append(INDENT).append(INDENT).append("return kafkasql.io.kafka.KafkaStream.reader(\n");
        sb.append(INDENT).append(INDENT).append(INDENT).append(streamName).append(".class,\n");
        sb.append(INDENT).append(INDENT).append(INDENT).append("consumer\n");
        sb.append(INDENT).append(INDENT).append(");\n");
        sb.append(INDENT).append("}\n\n");
        
        // Static method to create a Kafka-backed writer
        sb.append(INDENT).append("/**\n");
        sb.append(INDENT).append(" * Creates a Kafka-backed stream writer for ").append(streamName).append(".\n");
        sb.append(INDENT).append(" *\n");
        sb.append(INDENT).append(" * <p>The topic name is implicitly derived from the stream name.\n");
        sb.append(INDENT).append(" * The caller is responsible for creating and configuring the producer,\n");
        sb.append(INDENT).append(" * including byte[] serializers, acks policy, and other properties.\n");
        sb.append(INDENT).append(" * The producer is NOT owned by this writer - the caller must manage its lifecycle.\n");
        sb.append(INDENT).append(" *\n");
        sb.append(INDENT).append(" * @param producer Pre-configured Kafka producer with byte[] key/value serializers\n");
        sb.append(INDENT).append(" * @return A StreamWriter instance backed by Kafka\n");
        sb.append(INDENT).append(" */\n");
        sb.append(INDENT).append("static kafkasql.runtime.stream.StreamWriter<").append(streamName).append("> writer(\n");
        sb.append(INDENT).append(INDENT).append("org.apache.kafka.clients.producer.KafkaProducer<byte[], byte[]> producer\n");
        sb.append(INDENT).append(") {\n");
        sb.append(INDENT).append(INDENT).append("return kafkasql.io.kafka.KafkaStream.writer(\n");
        sb.append(INDENT).append(INDENT).append(INDENT).append(streamName).append(".class,\n");
        sb.append(INDENT).append(INDENT).append(INDENT).append("producer\n");
        sb.append(INDENT).append(INDENT).append(");\n");
        sb.append(INDENT).append("}\n\n");
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
    
    private String mapAstTypeToJava(kafkasql.lang.syntax.ast.type.TypeNode typeNode, boolean nullable) {
        if (typeNode instanceof kafkasql.lang.syntax.ast.type.PrimitiveTypeNode primNode) {
            return mapPrimitiveKindFromRuntime(primNode.kind());
        } else if (typeNode instanceof kafkasql.lang.syntax.ast.type.ComplexTypeNode complexNode) {
            // This is a reference to another type (enum, struct, scalar)
            // Convert QName to package.ClassName format
            var qname = complexNode.name();
            var parts = qname.parts();
            if (parts.isEmpty()) {
                return "Object";
            }
            
            // Build FQN: convert context parts to package, last part is class name
            StringBuilder fqn = new StringBuilder();
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) {
                    fqn.append(".");
                }
                fqn.append(parts.get(i).name());
            }
            return fqn.toString();
        } else if (typeNode instanceof kafkasql.lang.syntax.ast.type.ListTypeNode listNode) {
            String itemType = mapAstTypeToJava(listNode.elementType(), false);
            return "List<" + itemType + ">";
        } else if (typeNode instanceof kafkasql.lang.syntax.ast.type.MapTypeNode mapNode) {
            String keyType = mapAstTypeToJava(mapNode.keyType(), false);
            String valueType = mapAstTypeToJava(mapNode.valueType(), false);
            return "Map<" + keyType + ", " + valueType + ">";
        }
        
        return "Object";
    }
    
    private String mapPrimitiveKindFromRuntime(kafkasql.runtime.type.PrimitiveKind kind) {
        return switch (kind) {
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
