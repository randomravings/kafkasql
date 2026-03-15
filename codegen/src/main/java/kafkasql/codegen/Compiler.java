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
        sb.append("import kafkasql.runtime.Record;\n");
        sb.append("import kafkasql.io.codec.Encoder;\n");
        sb.append("import kafkasql.io.codec.Decoder;\n");
        if (needsImport(type.primitive())) {
            sb.append("import java.math.BigDecimal;\n");
            sb.append("import java.time.*;\n");
            sb.append("import java.util.UUID;\n");
        }
        sb.append("import java.io.*;\n");
        sb.append("\n");
        
        // Documentation
        type.doc().ifPresent(doc -> {
            sb.append("/**\n");
            sb.append(" * ").append(doc).append("\n");
            sb.append(" */\n");
        });
        
        // Type alias as record wrapper
        String typeName = name.name();
        sb.append("public record ").append(typeName).append("(").append(javaType).append(" value) implements Record {\n");
        
        // writeTo
        sb.append(INDENT).append("public void writeTo(OutputStream out) throws Exception {\n");
        sb.append(INDENT).append(INDENT);
        emitFieldWrite(sb, "value", type.primitive(), false);
        sb.append(INDENT).append("}\n\n");
        
        // readFrom
        sb.append(INDENT).append("public static ").append(typeName).append(" readFrom(InputStream in) throws Exception {\n");
        sb.append(INDENT).append(INDENT).append("return new ").append(typeName).append("(\n");
        sb.append(INDENT).append(INDENT).append(INDENT);
        emitFieldRead(sb, type.primitive(), false);
        sb.append("\n");
        sb.append(INDENT).append(INDENT).append(");\n");
        sb.append(INDENT).append("}\n");
        
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
        sb.append("import kafkasql.runtime.Record;\n");
        sb.append("import kafkasql.io.codec.Encoder;\n");
        sb.append("import kafkasql.io.codec.Decoder;\n");
        sb.append("import java.io.*;\n\n");
        
        // Documentation
        type.doc().ifPresent(doc -> {
            sb.append("/**\n");
            sb.append(" * ").append(doc).append("\n");
            sb.append(" */\n");
        });
        
        // Enum declaration
        String enumName = name.name();
        sb.append("public enum ").append(enumName).append(" implements Record {");
        
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
        sb.append(INDENT).append("}\n\n");
        
        // writeTo
        sb.append(INDENT).append("public void writeTo(OutputStream out) throws Exception {\n");
        sb.append(INDENT).append(INDENT).append("Encoder.writeInt32(out, this.value);\n");
        sb.append(INDENT).append("}\n\n");
        
        // readFrom
        sb.append(INDENT).append("public static ").append(enumName).append(" readFrom(InputStream in) throws Exception {\n");
        sb.append(INDENT).append(INDENT).append("int v = Decoder.decodeInt32(in);\n");
        sb.append(INDENT).append(INDENT).append("for (").append(enumName).append(" e : values()) {\n");
        sb.append(INDENT).append(INDENT).append(INDENT).append("if (e.value == v) return e;\n");
        sb.append(INDENT).append(INDENT).append("}\n");
        sb.append(INDENT).append(INDENT).append("throw new IllegalArgumentException(\"Unknown enum value: \" + v);\n");
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
        sb.append("import kafkasql.runtime.Record;\n");
        sb.append("import kafkasql.io.codec.Encoder;\n");
        sb.append("import kafkasql.io.codec.Decoder;\n");
        sb.append("import java.io.*;\n");
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
        
        sb.append("\n) implements Record {\n");
        
        // writeTo
        sb.append(INDENT).append("public void writeTo(OutputStream out) throws Exception {\n");
        for (var entry : type.fields().entrySet()) {
            String fieldName = entry.getKey();
            StructTypeField field = entry.getValue();
            sb.append(INDENT).append(INDENT);
            emitStructFieldWrite(sb, fieldName, field.type(), field.nullable());
        }
        sb.append(INDENT).append("}\n\n");
        
        // readFrom
        sb.append(INDENT).append("public static ").append(recordName).append(" readFrom(InputStream in) throws Exception {\n");
        sb.append(INDENT).append(INDENT).append("return new ").append(recordName).append("(\n");
        first = true;
        for (var entry : type.fields().entrySet()) {
            StructTypeField field = entry.getValue();
            if (!first) {
                sb.append(",\n");
            }
            first = false;
            sb.append(INDENT).append(INDENT).append(INDENT);
            emitStructFieldRead(sb, field.type(), field.nullable());
        }
        sb.append("\n");
        sb.append(INDENT).append(INDENT).append(");\n");
        sb.append(INDENT).append("}\n");
        
        sb.append("}\n");
        
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
        sb.append("import kafkasql.runtime.Record;\n");
        sb.append("import kafkasql.io.codec.Encoder;\n");
        sb.append("import kafkasql.io.codec.Decoder;\n");
        sb.append("import java.io.*;\n");
        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.time.*;\n");
        sb.append("import java.util.*;\n\n");
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
        sb.append("public sealed interface ").append(streamName).append(" extends Record");
        sb.append(" permits ").append(permitsClause).append(" {\n\n");
        
        // Abstract writeTo method
        sb.append(INDENT).append("void writeTo(OutputStream out) throws Exception;\n\n");
        
        // Static readFrom dispatch
        sb.append(INDENT).append("static ").append(streamName).append(" readFrom(InputStream in) throws Exception {\n");
        sb.append(INDENT).append(INDENT).append("int memberIndex = Decoder.decodeVarInt32(in);\n");
        sb.append(INDENT).append(INDENT).append("return switch (memberIndex) {\n");
        int memberIdx = 0;
        for (kafkasql.lang.syntax.ast.decl.StreamMemberDecl m : streamDecl.streamTypes()) {
            String mName = m.name().name();
            sb.append(INDENT).append(INDENT).append(INDENT).append("case ").append(memberIdx).append(" -> ").append(mName).append(".readFrom(in);\n");
            memberIdx++;
        }
        sb.append(INDENT).append(INDENT).append(INDENT).append("default -> throw new IllegalArgumentException(\"Unknown member index: \" + memberIndex);\n");
        sb.append(INDENT).append(INDENT).append("};\n");
        sb.append(INDENT).append("}\n\n");
        
        // Generate static factory methods for reader/writer
        generateStreamFactoryMethods(sb, streamName, streamDecl);
        
        // Generate each member type
        int memberIndex = 0;
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
                
                sb.append("\n").append(INDENT).append(") implements ").append(name.name()).append(" {\n");
                
                // writeTo
                sb.append(INDENT).append(INDENT).append("@Override\n");
                sb.append(INDENT).append(INDENT).append("public void writeTo(OutputStream out) throws Exception {\n");
                sb.append(INDENT).append(INDENT).append(INDENT).append("Encoder.writeVarInt32(out, ").append(memberIndex).append(");\n");
                for (kafkasql.lang.syntax.ast.decl.StructFieldDecl f : structDecl.fields()) {
                    sb.append(INDENT).append(INDENT).append(INDENT);
                    emitAstFieldWrite(sb, f.name().name(), f.type(), f.nullable().isPresent());
                }
                sb.append(INDENT).append(INDENT).append("}\n\n");
                
                // readFrom
                sb.append(INDENT).append(INDENT).append("public static ").append(memberName).append(" readFrom(InputStream in) throws Exception {\n");
                sb.append(INDENT).append(INDENT).append(INDENT).append("return new ").append(memberName).append("(\n");
                boolean firstRead = true;
                for (kafkasql.lang.syntax.ast.decl.StructFieldDecl f : structDecl.fields()) {
                    if (!firstRead) {
                        sb.append(",\n");
                    }
                    firstRead = false;
                    sb.append(INDENT).append(INDENT).append(INDENT).append(INDENT);
                    emitAstFieldRead(sb, f.type(), f.nullable().isPresent());
                }
                sb.append("\n");
                sb.append(INDENT).append(INDENT).append(INDENT).append(");\n");
                sb.append(INDENT).append(INDENT).append("}\n");
                
                sb.append(INDENT).append("}\n\n");
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
                        sb.append(INDENT).append(") implements ").append(name.name()).append(" {\n");
                        
                        // writeTo
                        sb.append(INDENT).append(INDENT).append("@Override\n");
                        sb.append(INDENT).append(INDENT).append("public void writeTo(OutputStream out) throws Exception {\n");
                        sb.append(INDENT).append(INDENT).append(INDENT).append("Encoder.writeVarInt32(out, ").append(memberIndex).append(");\n");
                        sb.append(INDENT).append(INDENT).append(INDENT).append("value.writeTo(out);\n");
                        sb.append(INDENT).append(INDENT).append("}\n\n");
                        
                        // readFrom
                        sb.append(INDENT).append(INDENT).append("public static ").append(memberName).append(" readFrom(InputStream in) throws Exception {\n");
                        sb.append(INDENT).append(INDENT).append(INDENT).append("return new ").append(memberName).append("(").append(referencedTypeFqn).append(".readFrom(in));\n");
                        sb.append(INDENT).append(INDENT).append("}\n");
                        
                        sb.append(INDENT).append("}\n\n");
                    }
                }
            }
            memberIndex++;
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
        sb.append(INDENT).append(INDENT).append("return new kafkasql.io.ReadStream<>(\n");
        sb.append(INDENT).append(INDENT).append(INDENT).append("\"").append(streamName).append("\",\n");
        sb.append(INDENT).append(INDENT).append(INDENT).append("consumer,\n");
        sb.append(INDENT).append(INDENT).append(INDENT).append("bytes -> ").append(streamName).append(".readFrom(new java.io.ByteArrayInputStream(bytes))\n");
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
        sb.append(INDENT).append(INDENT).append("return new kafkasql.io.WriteStream<>(\n");
        sb.append(INDENT).append(INDENT).append(INDENT).append("\"").append(streamName).append("\",\n");
        sb.append(INDENT).append(INDENT).append(INDENT).append("producer,\n");
        sb.append(INDENT).append(INDENT).append(INDENT).append("msg -> { var baos = new java.io.ByteArrayOutputStream(); msg.writeTo(baos); return baos.toByteArray(); }\n");
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
    
    // ========================================================================
    // Serde Emit Helpers
    // ========================================================================
    
    /**
     * Emits an Encoder call for a primitive field: Encoder.writeXxx(out, accessor);
     */
    private void emitFieldWrite(StringBuilder sb, String accessor, PrimitiveType type, boolean nullable) {
        if (nullable) {
            sb.append("if (").append(accessor).append(" == null) { Encoder.writeBool(out, false); } else { Encoder.writeBool(out, true); ");
            emitPrimitiveWrite(sb, accessor, type);
            sb.append(" }\n");
        } else {
            emitPrimitiveWrite(sb, accessor, type);
            sb.append("\n");
        }
    }
    
    private void emitPrimitiveWrite(StringBuilder sb, String accessor, PrimitiveType type) {
        switch (type.kind()) {
            case BOOLEAN -> sb.append("Encoder.writeBool(out, ").append(accessor).append(");");
            case INT8 -> sb.append("Encoder.writeInt8(out, ").append(accessor).append(");");
            case INT16 -> sb.append("Encoder.writeInt16(out, ").append(accessor).append(");");
            case INT32 -> sb.append("Encoder.writeInt32(out, ").append(accessor).append(");");
            case INT64 -> sb.append("Encoder.writeInt64(out, ").append(accessor).append(");");
            case FLOAT32 -> sb.append("Encoder.writeFloat32(out, ").append(accessor).append(");");
            case FLOAT64 -> sb.append("Encoder.writeFloat64(out, ").append(accessor).append(");");
            case STRING -> sb.append("Encoder.writeString(out, ").append(accessor).append(");");
            case BYTES -> sb.append("Encoder.writeBytes(out, ").append(accessor).append(");");
            case UUID -> sb.append("Encoder.writeUUID(out, ").append(accessor).append(");");
            case DECIMAL -> sb.append("Encoder.writeDecimal(out, ").append(accessor).append(");");
            case DATE -> sb.append("Encoder.writeInt64(out, ").append(accessor).append(".toEpochDay());");
            case TIME -> sb.append("Encoder.writeInt64(out, ").append(accessor).append(".toNanoOfDay());");
            case TIMESTAMP -> {
                sb.append("Encoder.writeInt64(out, ").append(accessor).append(".toEpochSecond(java.time.ZoneOffset.UTC)); ");
                sb.append("Encoder.writeInt32(out, ").append(accessor).append(".getNano());");
            }
            case TIMESTAMP_TZ -> {
                sb.append("Encoder.writeInt64(out, ").append(accessor).append(".toEpochSecond()); ");
                sb.append("Encoder.writeInt32(out, ").append(accessor).append(".getNano()); ");
                sb.append("Encoder.writeString(out, ").append(accessor).append(".getZone().getId());");
            }
        }
    }
    
    /**
     * Emits a Decoder call for a primitive field: Decoder.decodeXxx(in)
     */
    private void emitFieldRead(StringBuilder sb, PrimitiveType type, boolean nullable) {
        if (nullable) {
            sb.append("Decoder.decodeBoolean(in) ? ");
            emitPrimitiveRead(sb, type);
            sb.append(" : null");
        } else {
            emitPrimitiveRead(sb, type);
        }
    }
    
    private void emitPrimitiveRead(StringBuilder sb, PrimitiveType type) {
        switch (type.kind()) {
            case BOOLEAN -> sb.append("Decoder.decodeBoolean(in)");
            case INT8 -> sb.append("Decoder.decodeInt8(in)");
            case INT16 -> sb.append("Decoder.decodeInt16(in)");
            case INT32 -> sb.append("Decoder.decodeInt32(in)");
            case INT64 -> sb.append("Decoder.decodeInt64(in)");
            case FLOAT32 -> sb.append("Decoder.decodeFloat32(in)");
            case FLOAT64 -> sb.append("Decoder.decodeFloat64(in)");
            case STRING -> sb.append("Decoder.decodeString(in)");
            case BYTES -> sb.append("Decoder.decodeBytes(in)");
            case UUID -> sb.append("Decoder.decodeUUID(in)");
            case DECIMAL -> sb.append("new java.math.BigDecimal(new java.math.BigInteger(Decoder.decodeBytes(in)))");
            case DATE -> sb.append("java.time.LocalDate.ofEpochDay(Decoder.decodeInt64(in))");
            case TIME -> sb.append("java.time.LocalTime.ofNanoOfDay(Decoder.decodeInt64(in))");
            case TIMESTAMP -> sb.append("java.time.LocalDateTime.ofEpochSecond(Decoder.decodeInt64(in), Decoder.decodeInt32(in), java.time.ZoneOffset.UTC)");
            case TIMESTAMP_TZ -> sb.append("java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochSecond(Decoder.decodeInt64(in), Decoder.decodeInt32(in)), java.time.ZoneId.of(Decoder.decodeString(in)))");
        }
    }
    
    /**
     * Emits a write call for a struct field (any AnyType).
     */
    private void emitStructFieldWrite(StringBuilder sb, String fieldName, AnyType type, boolean nullable) {
        switch (type) {
            case PrimitiveType pt -> emitFieldWrite(sb, fieldName, pt, nullable);
            case ScalarType st -> emitComplexFieldWrite(sb, fieldName, nullable);
            case EnumType et -> emitComplexFieldWrite(sb, fieldName, nullable);
            case StructType st -> emitComplexFieldWrite(sb, fieldName, nullable);
            case UnionType ut -> emitComplexFieldWrite(sb, fieldName, nullable);
            default -> sb.append("// TODO: unsupported type for ").append(fieldName).append("\n");
        }
    }
    
    private void emitComplexFieldWrite(StringBuilder sb, String fieldName, boolean nullable) {
        if (nullable) {
            sb.append("if (").append(fieldName).append(" == null) { Encoder.writeBool(out, false); } else { Encoder.writeBool(out, true); ").append(fieldName).append(".writeTo(out); }\n");
        } else {
            sb.append(fieldName).append(".writeTo(out);\n");
        }
    }
    
    /**
     * Emits a read expression for a struct field (any AnyType).
     */
    private void emitStructFieldRead(StringBuilder sb, AnyType type, boolean nullable) {
        switch (type) {
            case PrimitiveType pt -> emitFieldRead(sb, pt, nullable);
            case ScalarType st -> emitComplexFieldRead(sb, st.fqn().toString(), nullable);
            case EnumType et -> emitComplexFieldRead(sb, et.fqn().toString(), nullable);
            case StructType st -> emitComplexFieldRead(sb, st.fqn().toString(), nullable);
            case UnionType ut -> emitComplexFieldRead(sb, ut.fqn().toString(), nullable);
            default -> sb.append("null /* TODO: unsupported type */");
        }
    }
    
    private void emitComplexFieldRead(StringBuilder sb, String typeFqn, boolean nullable) {
        if (nullable) {
            sb.append("Decoder.decodeBoolean(in) ? ").append(typeFqn).append(".readFrom(in) : null");
        } else {
            sb.append(typeFqn).append(".readFrom(in)");
        }
    }
    
    // ========================================================================
    // AST-based Serde Emit Helpers (for stream member inline records)
    // ========================================================================
    
    private void emitAstFieldWrite(StringBuilder sb, String fieldName, kafkasql.lang.syntax.ast.type.TypeNode typeNode, boolean nullable) {
        if (typeNode instanceof kafkasql.lang.syntax.ast.type.PrimitiveTypeNode primNode) {
            emitPrimitiveKindWrite(sb, fieldName, primNode.kind(), nullable);
        } else if (typeNode instanceof kafkasql.lang.syntax.ast.type.ComplexTypeNode) {
            emitComplexFieldWrite(sb, fieldName, nullable);
        } else {
            sb.append("// TODO: unsupported AST type for ").append(fieldName).append("\n");
        }
    }
    
    private void emitAstFieldRead(StringBuilder sb, kafkasql.lang.syntax.ast.type.TypeNode typeNode, boolean nullable) {
        if (typeNode instanceof kafkasql.lang.syntax.ast.type.PrimitiveTypeNode primNode) {
            emitPrimitiveKindRead(sb, primNode.kind(), nullable);
        } else if (typeNode instanceof kafkasql.lang.syntax.ast.type.ComplexTypeNode complexNode) {
            String typeName = mapAstTypeToJava(complexNode, false);
            emitComplexFieldRead(sb, typeName, nullable);
        } else {
            sb.append("null /* TODO: unsupported AST type */");
        }
    }
    
    private void emitPrimitiveKindWrite(StringBuilder sb, String accessor, PrimitiveKind kind, boolean nullable) {
        if (nullable) {
            sb.append("if (").append(accessor).append(" == null) { Encoder.writeBool(out, false); } else { Encoder.writeBool(out, true); ");
            emitPrimitiveKindWriteCall(sb, accessor, kind);
            sb.append(" }\n");
        } else {
            emitPrimitiveKindWriteCall(sb, accessor, kind);
            sb.append("\n");
        }
    }
    
    private void emitPrimitiveKindWriteCall(StringBuilder sb, String accessor, PrimitiveKind kind) {
        switch (kind) {
            case BOOLEAN -> sb.append("Encoder.writeBool(out, ").append(accessor).append(");");
            case INT8 -> sb.append("Encoder.writeInt8(out, ").append(accessor).append(");");
            case INT16 -> sb.append("Encoder.writeInt16(out, ").append(accessor).append(");");
            case INT32 -> sb.append("Encoder.writeInt32(out, ").append(accessor).append(");");
            case INT64 -> sb.append("Encoder.writeInt64(out, ").append(accessor).append(");");
            case FLOAT32 -> sb.append("Encoder.writeFloat32(out, ").append(accessor).append(");");
            case FLOAT64 -> sb.append("Encoder.writeFloat64(out, ").append(accessor).append(");");
            case STRING -> sb.append("Encoder.writeString(out, ").append(accessor).append(");");
            case BYTES -> sb.append("Encoder.writeBytes(out, ").append(accessor).append(");");
            case UUID -> sb.append("Encoder.writeUUID(out, ").append(accessor).append(");");
            case DECIMAL -> sb.append("Encoder.writeDecimal(out, ").append(accessor).append(");");
            case DATE -> sb.append("Encoder.writeInt64(out, ").append(accessor).append(".toEpochDay());");
            case TIME -> sb.append("Encoder.writeInt64(out, ").append(accessor).append(".toNanoOfDay());");
            case TIMESTAMP -> {
                sb.append("Encoder.writeInt64(out, ").append(accessor).append(".toEpochSecond(java.time.ZoneOffset.UTC)); ");
                sb.append("Encoder.writeInt32(out, ").append(accessor).append(".getNano());");
            }
            case TIMESTAMP_TZ -> {
                sb.append("Encoder.writeInt64(out, ").append(accessor).append(".toEpochSecond()); ");
                sb.append("Encoder.writeInt32(out, ").append(accessor).append(".getNano()); ");
                sb.append("Encoder.writeString(out, ").append(accessor).append(".getZone().getId());");
            }
        }
    }
    
    private void emitPrimitiveKindRead(StringBuilder sb, PrimitiveKind kind, boolean nullable) {
        if (nullable) {
            sb.append("Decoder.decodeBoolean(in) ? ");
            emitPrimitiveKindReadCall(sb, kind);
            sb.append(" : null");
        } else {
            emitPrimitiveKindReadCall(sb, kind);
        }
    }
    
    private void emitPrimitiveKindReadCall(StringBuilder sb, PrimitiveKind kind) {
        switch (kind) {
            case BOOLEAN -> sb.append("Decoder.decodeBoolean(in)");
            case INT8 -> sb.append("Decoder.decodeInt8(in)");
            case INT16 -> sb.append("Decoder.decodeInt16(in)");
            case INT32 -> sb.append("Decoder.decodeInt32(in)");
            case INT64 -> sb.append("Decoder.decodeInt64(in)");
            case FLOAT32 -> sb.append("Decoder.decodeFloat32(in)");
            case FLOAT64 -> sb.append("Decoder.decodeFloat64(in)");
            case STRING -> sb.append("Decoder.decodeString(in)");
            case BYTES -> sb.append("Decoder.decodeBytes(in)");
            case UUID -> sb.append("Decoder.decodeUUID(in)");
            case DECIMAL -> sb.append("new java.math.BigDecimal(new java.math.BigInteger(Decoder.decodeBytes(in)))");
            case DATE -> sb.append("java.time.LocalDate.ofEpochDay(Decoder.decodeInt64(in))");
            case TIME -> sb.append("java.time.LocalTime.ofNanoOfDay(Decoder.decodeInt64(in))");
            case TIMESTAMP -> sb.append("java.time.LocalDateTime.ofEpochSecond(Decoder.decodeInt64(in), Decoder.decodeInt32(in), java.time.ZoneOffset.UTC)");
            case TIMESTAMP_TZ -> sb.append("java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochSecond(Decoder.decodeInt64(in), Decoder.decodeInt32(in)), java.time.ZoneId.of(Decoder.decodeString(in)))");
        }
    }
}
