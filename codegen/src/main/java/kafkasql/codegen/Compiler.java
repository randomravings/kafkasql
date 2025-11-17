package kafkasql.codegen;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import kafkasql.lang.ast.AlphaT;
import kafkasql.lang.ast.AnyT;
import kafkasql.lang.ast.Ast;
import kafkasql.lang.ast.BinaryT;
import kafkasql.lang.ast.BoolT;
import kafkasql.lang.ast.BytesT;
import kafkasql.lang.ast.CharT;
import kafkasql.lang.ast.ComplexT;
import kafkasql.lang.ast.CompositeT;
import kafkasql.lang.ast.CreateStmt;
import kafkasql.lang.ast.CreateType;
import kafkasql.lang.ast.DateT;
import kafkasql.lang.ast.DecimalT;
import kafkasql.lang.ast.EnumSymbol;
import kafkasql.lang.ast.EnumT;
import kafkasql.lang.ast.Field;
import kafkasql.lang.ast.FixedT;
import kafkasql.lang.ast.Float32T;
import kafkasql.lang.ast.Float64T;
import kafkasql.lang.ast.FractionalT;
import kafkasql.lang.ast.Int16T;
import kafkasql.lang.ast.Int32T;
import kafkasql.lang.ast.Int64T;
import kafkasql.lang.ast.Int8T;
import kafkasql.lang.ast.IntegerT;
import kafkasql.lang.ast.ListT;
import kafkasql.lang.ast.MapT;
import kafkasql.lang.ast.NumberT;
import kafkasql.lang.ast.PrimitiveT;
import kafkasql.lang.ast.QName;
import kafkasql.lang.ast.ReadStmt;
import kafkasql.lang.ast.ScalarT;
import kafkasql.lang.ast.StringT;
import kafkasql.lang.ast.StructT;
import kafkasql.lang.ast.TemporalT;
import kafkasql.lang.ast.TimeT;
import kafkasql.lang.ast.TimestampT;
import kafkasql.lang.ast.TimestampTzT;
import kafkasql.lang.ast.TypeReference;
import kafkasql.lang.ast.UnionT;
import kafkasql.lang.ast.UuidT;
import kafkasql.lang.ast.VoidT;
import kafkasql.lang.ast.WriteStmt;

public class Compiler {
    private static final String INDENT = "    ";

    private Compiler() { }

    public Map<String, String>  compile(Ast ast) {
        var map = new LinkedHashMap<String, String>();
        compileStmts(ast, map);
        return map;
    }

    private void compileStmts(Ast ast, Map<String, String> map) {
        for(var stmt : ast) {
            switch (stmt) {
                case CreateStmt createStmt:
                    compileCreateStmt(createStmt, map);
                    break;
                case ReadStmt readStmt:
                    compileReadStmt(readStmt, map);
                    break;
                case WriteStmt writeStmt:
                    compileWriteStmt(writeStmt, map);
                    break;
                default:
                    // TODO: log?
                    break;
            }
        }
    }

    private static void compileCreateStmt(CreateStmt createStmt, Map<String, String> map) {
        switch (createStmt) {
            case CreateType createType:
                String code = compileType(createType.type());
                map.put(createType.type().qName().fullName(), code);
                break;
            default:
                // TODO: log?
                break;
        }
    }

    private static String compileType(ComplexT complexType) {
        return switch (complexType) {
            case ScalarT scalarType -> compileScalar(scalarType);
            case EnumT enumType -> compileEnum(enumType);
            case StructT structType -> compileStruct(structType);
            case UnionT unionType -> compileUnion(unionType);
        };
    }

    private static String compileScalar(ScalarT scalarType) {
        StringBuilder sb = beginDecl(scalarType.qName(), "record");
        beginScope(sb, 0);
        space(sb, 1);
        endScope(sb, 0);
        newLine(sb, 1);
        return sb.toString();
    }

    private static String compileEnum(EnumT enumType) {
        StringBuilder sb = beginDecl(enumType.qName(), "enum");
        beginScope(sb, 0);
        newLine(sb, 1);

        writeEnumSymbols(sb, enumType.symbols(), 1);


        var type = Integer.class.getSimpleName();
        if (enumType.type().isPresent())
            sb.append(integerType(enumType.type().get()));

        newLine(sb, 1);
        indent(sb, 1);
        sb.append("public final ");
        sb.append(type);
        sb.append(" value;");

        newLine(sb, 1);
        indent(sb, 1);
        sb.append("private ");
        sb.append(enumType.qName().name());
        sb.append("(");
        
        sb.append(type);
        sb.append(" value) {");
        newLine(sb, 1);
        indent(sb, 2);
        sb.append("this.value = value;");
        newLine(sb, 1);
        indent(sb, 1);
        sb.append("}");
        endScope(sb, 0);
        newLine(sb, 1);
        return sb.toString();
    }

    private static void writeEnumSymbols(StringBuilder sb, List<EnumSymbol> symbols, int indentLevel) {
        var size = symbols.size();
        if(size > 0)
            writeEnumSymbol(sb, symbols.get(0), indentLevel);
        for (int i = 1; i < size; i++) {
            newLine(sb, 1);
            indent(sb, indentLevel);
            writeEnumSymbol(sb, symbols.get(i), indentLevel);
        }
    }

    private static void writeEnumSymbol(StringBuilder sb, EnumSymbol symbol, int indentLevel) {
        indent(sb, indentLevel);
        sb.append(symbol.name());
        sb.append("(");
        sb.append(symbol.value());
        sb.append(")");
    }

    private static String compileStruct(StructT structType) {
        StringBuilder sb = beginDecl(structType.qName(), "class");
        beginScope(sb, 0);
        writeFields(sb, structType.fieldList(), 1);

        newLine(sb, 1);
        endScope(sb, 0);
        newLine(sb, 1);
        return sb.toString();
    }

    private static void writeFields(StringBuilder sb, List<Field> fields, int indentLevel) {
        for (var field : fields) {
            newLine(sb, 1);
            indent(sb, indentLevel);
            writeField(sb, field, indentLevel);
        }
    }

    private static void writeField(StringBuilder sb, Field field, int indentLevel) {
        indent(sb, indentLevel);
        sb.append("private ");
        sb.append(optional(field.type(), field.nullable().isPresent()));
        sb.append(" _");
        sb.append(field.name());
        sb.append(";");
    }

    private static String compileUnion(UnionT unionType) {
        StringBuilder sb = beginDecl(unionType.qName(), "class");
        beginScope(sb, 0);
        space(sb, 1);
        endScope(sb, 0);
        newLine(sb, 1);
        return sb.toString();
    }

    private static void compileReadStmt(ReadStmt readStmt, Map<String, String> map) {
        
    }

    private static void compileWriteStmt(WriteStmt writeStmt, Map<String, String> map) {
    }

    private static StringBuilder beginDecl(QName name, String type) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ");
        sb.append(name.context());
        sb.append(";\n\n");
        sb.append("public final ");
        sb.append(type);
        sb.append(" ");
        sb.append(name.name());
        sb.append(" {\n");
        return sb;
    }

    private static String beginScope(StringBuilder sb, int indentLevel) {
        indent(sb, indentLevel);
        sb.append("{");
        return sb.toString();
    }

    private static String endScope(StringBuilder sb, int indentLevel) {
        indent(sb, indentLevel);
        sb.append("}");
        return sb.toString();
    }

    private static void space(StringBuilder sb, int count) {
        for (int i = 0; i < count; i++) {
            sb.append(" ");
        }
    }

    private static void indent(StringBuilder sb, int indentLevel) {
        for (int i = 0; i < indentLevel; i++) {
            sb.append(INDENT);
        }
    }

    private static void newLine(StringBuilder sb, int count) {
        for (int i = 0; i < count; i++) {
            sb.append("\n");
        }
    }

    private static String optional(AnyT type, boolean optional) {
        var dataType = dataType(type);
        if(optional)
            return Optional.class.getSimpleName() + "<" + dataType + ">";
        else
            return dataType;
    }

    private static String dataType(AnyT type) {
        return switch (type) {
            case PrimitiveT primitiveType -> primitiveType(primitiveType);
            case CompositeT compositeType -> compositeType(compositeType);
            case ComplexT complexType -> complexType.qName().fullName();
            case TypeReference typeReference -> typeReference.qName().fullName();
            case VoidT voidType -> Void.class.getSimpleName();
        };
    }

    private static String primitiveType(PrimitiveT primitiveType) {
        return switch (primitiveType) {
            case BoolT boolType -> Boolean.class.getSimpleName();
            case AlphaT alphaType -> alphaType(alphaType);
            case BinaryT binaryType -> binaryType(binaryType);
            case NumberT numberType -> numberType(numberType);
            case TemporalT temporalType -> temporalType(temporalType);
        };
    }

    private static String alphaType(AlphaT alphaType) {
        return switch (alphaType) {
            case StringT stringType -> String.class.getSimpleName();
            case CharT charType -> String.class.getSimpleName();
            case UuidT uuidType -> UUID.class.getSimpleName();
        };
    }

    private static String binaryType(BinaryT binaryType) {
        return switch (binaryType) {
            case BytesT byteType -> Byte[].class.getSimpleName();
            case FixedT fixedType -> Byte[].class.getSimpleName();
        };
    }
    
    private static String numberType(NumberT numberType) {
        return switch (numberType) {
            case IntegerT integerType -> integerType(integerType);
            case FractionalT fractionalType -> fractionalType(fractionalType);
        };
    }

    private static String integerType(IntegerT integerType) {
        return switch (integerType) {
            case Int8T int8Type -> Byte.class.getSimpleName();
            case Int16T int16Type -> Short.class.getSimpleName();
            case Int32T int32Type -> Integer.class.getSimpleName();
            case Int64T int64Type -> Long.class.getSimpleName();
        };
    }

    private static String fractionalType(FractionalT numericType) {
        return switch (numericType) {
            case Float32T float32Type -> Float.class.getSimpleName();
            case Float64T float64Type -> Double.class.getSimpleName();
            case DecimalT decimalType -> BigDecimal.class.getSimpleName();
        };
    }

    private static String temporalType(TemporalT temporalType) {
        return switch (temporalType) {
            case DateT dateType -> LocalDate.class.getSimpleName();
            case TimeT timeType -> LocalTime.class.getSimpleName();
            case TimestampT dateTimeType -> LocalDateTime.class.getSimpleName();
            case TimestampTzT timestampType -> ZonedDateTime.class.getSimpleName();
        };
    }

    private static String compositeType(CompositeT compositeType) {
        return switch (compositeType) {
            case ListT listType -> List.class.getSimpleName() + "<" + dataType(listType.item()) + ">";
            case MapT mapType -> Map.class.getSimpleName() + "<" + dataType(mapType.key()) + ", " + dataType(mapType.value()) + ">";
        };
    }
}
