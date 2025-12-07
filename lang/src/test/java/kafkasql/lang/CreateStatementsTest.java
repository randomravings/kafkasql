package kafkasql.lang;

import org.junit.jupiter.api.*;

import kafkasql.runtime.type.PrimitiveKind;
import kafkasql.lang.syntax.ast.constExpr.ConstExpr;
import kafkasql.lang.syntax.ast.constExpr.ConstLiteralExpr;
import kafkasql.lang.syntax.ast.decl.ContextDecl;
import kafkasql.lang.syntax.ast.decl.DerivedTypeDecl;
import kafkasql.lang.syntax.ast.decl.EnumDecl;
import kafkasql.lang.syntax.ast.decl.EnumSymbolDecl;
import kafkasql.lang.syntax.ast.decl.ScalarDecl;
import kafkasql.lang.syntax.ast.decl.StreamDecl;
import kafkasql.lang.syntax.ast.decl.StreamMemberDecl;
import kafkasql.lang.syntax.ast.decl.StructDecl;
import kafkasql.lang.syntax.ast.decl.StructFieldDecl;
import kafkasql.lang.syntax.ast.decl.TypeDecl;
import kafkasql.lang.syntax.ast.decl.UnionDecl;
import kafkasql.lang.syntax.ast.decl.UnionMemberDecl;
import kafkasql.lang.syntax.ast.fragment.CheckNode;
import kafkasql.lang.syntax.ast.fragment.DefaultNode;
import kafkasql.lang.syntax.ast.literal.EnumLiteralNode;
import kafkasql.lang.syntax.ast.literal.LiteralNode;
import kafkasql.lang.syntax.ast.literal.NullLiteralNode;
import kafkasql.lang.syntax.ast.literal.NumberLiteralNode;
import kafkasql.lang.syntax.ast.stmt.CreateStmt;
import kafkasql.lang.syntax.ast.stmt.UseStmt;
import kafkasql.lang.syntax.ast.type.ComplexTypeNode;
import kafkasql.lang.syntax.ast.type.ListTypeNode;
import kafkasql.lang.syntax.ast.type.MapTypeNode;
import kafkasql.lang.syntax.ast.type.PrimitiveTypeNode;
import kafkasql.lang.syntax.ast.type.TypeNode;
import kafkasql.lang.syntax.ast.use.ContextUse;
import kafkasql.util.TestHelpers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CREATE statements – AST universe reborn")
public class CreateStatementsTest {

    @Test
    void createContextSimple() {
        var stmts = TestHelpers.parseAssert("CREATE CONTEXT foo;");
        CreateStmt cstmt = TestHelpers.only(stmts, CreateStmt.class);

        TestHelpers.assertDecl(
            ContextDecl.class,
            cstmt.decl(),
            "foo"
        );
    }

    @Test
    void createScalarPrimitive() {
        var stmts = TestHelpers.parseAssert("CREATE TYPE MyInt AS SCALAR INT32;");
        CreateStmt cstmt = TestHelpers.only(stmts, CreateStmt.class);

        TypeDecl typeDecl = TestHelpers.assertDecl(
            TypeDecl.class,
            cstmt.decl(),
            "MyInt"
        );
        ScalarDecl scalar = TestHelpers.assertTypeDecl(
            ScalarDecl.class,
            typeDecl.kind()
        );
        assertInstanceOf(PrimitiveTypeNode.class, scalar.type());
        assertEquals(0, typeDecl.fragments().size());
    }

    @Test
    void createScalarWithDefaultAndCheck() {
        var stmts = TestHelpers.parseAssert("""
            CREATE TYPE PosInt AS SCALAR INT32
                DEFAULT 1
                CHECK ( value > 0 );
            """);
        CreateStmt cstmt = TestHelpers.only(stmts, CreateStmt.class);
        TypeDecl typeDecl = TestHelpers.assertDecl(
            TypeDecl.class,
            cstmt.decl(),
            "PosInt"
        );
        ScalarDecl scalar = TestHelpers.assertTypeDecl(
            ScalarDecl.class,
            typeDecl.kind()
        );
        TestHelpers.assertPrimitive(
            scalar.type(),
            PrimitiveKind.INT32,
            null,
            null,
            null
        );
        assertEquals(2, typeDecl.fragments().size());
        // default literal
        DefaultNode defaultNode = TestHelpers.assertSingleFragmentOf(typeDecl.fragments(), DefaultNode.class);
        LiteralNode def = defaultNode.value();
        assertInstanceOf(NumberLiteralNode.class, def);
        assertEquals("1", ((NumberLiteralNode) def).text());

        // check
        TestHelpers.assertSingleFragmentOf(typeDecl.fragments(), CheckNode.class);
    }

    @Test
    void createEnumWithSymbols() {
        var stmts = TestHelpers.parseAssert("""
            CREATE TYPE Color AS ENUM (
                Red = 1,
                Green = 2,
                Blue = 3
            );
            """);
        CreateStmt cstmt = TestHelpers.only(stmts, CreateStmt.class);
        TypeDecl typeDecl = TestHelpers.assertDecl(
            TypeDecl.class,
            cstmt.decl(),
            "Color"
        );
        EnumDecl en = TestHelpers.assertTypeDecl(
            EnumDecl.class,
            typeDecl.kind()
        );

        List<EnumSymbolDecl> symbols = en.symbols();
        assertEquals(3, symbols.size());
        assertEquals("Red", symbols.get(0).name().name());
        assertEquals("Green", symbols.get(1).name().name());
        assertEquals("Blue", symbols.get(2).name().name());

        ConstExpr val = symbols.get(1).value();
        assertInstanceOf(ConstLiteralExpr.class, val);
        assertEquals("2", ((ConstLiteralExpr) val).text());
    }

    @Test
    void createEnumWithBaseTypeAndDefault() {
        var stmts = TestHelpers.parseAssert("""
            CREATE TYPE Status AS ENUM (
                Pending = 1,
                Active = 2,
                Disabled = 3
            )
            DEFAULT Status::Active;
            """);
        CreateStmt cstmt = TestHelpers.only(stmts, CreateStmt.class);
        TypeDecl typeDecl = TestHelpers.assertDecl(
            TypeDecl.class,
            cstmt.decl(),
            "Status"
        );
        TestHelpers.assertTypeDecl(
            EnumDecl.class,
            typeDecl.kind()
        );
        DefaultNode defaultNode = TestHelpers.assertSingleFragmentOf(
            typeDecl.fragments(),
            DefaultNode.class
        );
        EnumLiteralNode def = (EnumLiteralNode)defaultNode.value();
        assertEquals("Status", def.enumName().fullName());
        assertEquals("Active", def.symbol().name());
    }

    @Test
    void createStructVariousFieldTypes() {
        var stmts = TestHelpers.parseAssert("""
            CREATE TYPE Person AS STRUCT (
                Id INT64,
                Name STRING,
                Nick STRING(16) NULL,
                Score DECIMAL(10,2),
                Tags LIST<STRING>,
                Attrs MAP<STRING, INT32>,
                Friend com.example.User NULL
            );
            """);
        CreateStmt cstmt = TestHelpers.only(stmts, CreateStmt.class);
        TypeDecl typeDecl = TestHelpers.assertDecl(
            TypeDecl.class,
            cstmt.decl(),
            "Person"
        );
        StructDecl st = TestHelpers.assertTypeDecl(
            StructDecl.class,
            typeDecl.kind()
        );

        List<StructFieldDecl> fields = st.fields();
        assertEquals(7, fields.size());

        assertEquals("Id", fields.get(0).name().name());
        assertInstanceOf(PrimitiveTypeNode.class, fields.get(0).type());
        assertEquals(PrimitiveKind.INT64, ((PrimitiveTypeNode)fields.get(0).type()).kind());
        assertFalse(fields.get(0).nullable().isPresent());

        assertEquals("Name", fields.get(1).name().name());
        assertInstanceOf(PrimitiveTypeNode.class, fields.get(1).type());
        assertEquals(PrimitiveKind.STRING, ((PrimitiveTypeNode)fields.get(1).type()).kind());
        assertEquals(false, ((PrimitiveTypeNode)fields.get(1).type()).hasLength());

        assertEquals("Nick", fields.get(2).name().name());
        assertInstanceOf(PrimitiveTypeNode.class, fields.get(2).type());
        assertEquals(PrimitiveKind.STRING, ((PrimitiveTypeNode)fields.get(2).type()).kind());
        assertEquals(true, ((PrimitiveTypeNode)fields.get(2).type()).hasLength());
        assertEquals(16, ((PrimitiveTypeNode)fields.get(2).type()).length());
        assertTrue(fields.get(2).nullable().isPresent());
        assertInstanceOf(NullLiteralNode.class, fields.get(2).nullable().get());

        assertEquals("Score", fields.get(3).name().name());
        assertInstanceOf(PrimitiveTypeNode.class, fields.get(3).type());
        assertEquals(PrimitiveKind.DECIMAL, ((PrimitiveTypeNode)fields.get(3).type()).kind());
        assertEquals(true, ((PrimitiveTypeNode)fields.get(3).type()).hasPrecision());
        assertEquals(10, ((PrimitiveTypeNode)fields.get(3).type()).precision());
        assertEquals(2, ((PrimitiveTypeNode)fields.get(3).type()).scale());

        assertEquals("Tags", fields.get(4).name().name());
        assertInstanceOf(ListTypeNode.class, fields.get(4).type());

        assertEquals("Attrs", fields.get(5).name().name());
        assertInstanceOf(MapTypeNode.class, fields.get(5).type());

        assertEquals("Friend", fields.get(6).name().name());
        assertInstanceOf(ComplexTypeNode.class, fields.get(6).type());
        assertTrue(fields.get(6).nullable().isPresent());
    }

    @Test
    void nestedCompositeTypes() {
        var stmts = TestHelpers.parseAssert("""
            CREATE TYPE A AS STRUCT (
                Data LIST<MAP<STRING, LIST<INT32>>>
            );
            """);
        CreateStmt cstmt = TestHelpers.only(stmts, CreateStmt.class);
        TypeDecl typeDecl = TestHelpers.assertDecl(
            TypeDecl.class,
            cstmt.decl(),
            "A"
        );
        StructDecl st = TestHelpers.assertTypeDecl(
            StructDecl.class,
            typeDecl.kind()
        );

        assertEquals(1, st.fields().size());

        StructFieldDecl dataField = st.fields().getFirst();
        assertEquals("Data", dataField.name().name());
        TypeNode t = dataField.type();
        assertInstanceOf(ListTypeNode.class, t);

        // LIST< MAP<STRING, LIST<INT32>> >
        TypeNode mapInner = ((ListTypeNode) t).elementType();
        assertInstanceOf(MapTypeNode.class, mapInner);

        MapTypeNode mapType = (MapTypeNode) mapInner;

        assertInstanceOf(PrimitiveTypeNode.class, mapType.keyType());
        assertEquals(PrimitiveKind.STRING, ((PrimitiveTypeNode)mapType.keyType()).kind());
        assertEquals(false, ((PrimitiveTypeNode)mapType.keyType()).hasLength());

        TypeNode valueType = mapType.valueType();
        assertInstanceOf(ListTypeNode.class, valueType);

        TypeNode intListElement = ((ListTypeNode) valueType).elementType();
        assertInstanceOf(PrimitiveTypeNode.class, intListElement);
        assertEquals(PrimitiveKind.INT32, ((PrimitiveTypeNode)intListElement).kind());
    }

    @Test
    void createUnionAlts() {
        var stmts = TestHelpers.parseAssert("""
            CREATE TYPE Value AS UNION (
                I INT32,
                S STRING,
                Ref com.example.Other
            );
            """);
        CreateStmt cstmt = TestHelpers.only(stmts, CreateStmt.class);
        TypeDecl typeDecl = TestHelpers.assertDecl(
            TypeDecl.class,
            cstmt.decl(),
            "Value"
        );
        UnionDecl un = TestHelpers.assertTypeDecl(
            UnionDecl.class,
            typeDecl.kind()
        );

        List<UnionMemberDecl> members = un.members();
        assertEquals(3, members.size());

        assertEquals("I", members.get(0).name().name());
        assertInstanceOf(PrimitiveTypeNode.class, members.get(0).type());
        assertEquals(PrimitiveKind.INT32, ((PrimitiveTypeNode)members.get(0).type()).kind());

        assertEquals("S", members.get(1).name().name());
        assertInstanceOf(PrimitiveTypeNode.class, members.get(1).type());
        assertEquals(PrimitiveKind.STRING, ((PrimitiveTypeNode)members.get(1).type()).kind());
        assertEquals(false, ((PrimitiveTypeNode)members.get(1).type()).hasLength());

        assertEquals("Ref", members.get(2).name().name());
        assertInstanceOf(ComplexTypeNode.class, members.get(2).type());
    }

    @Test
    void createStreamLogWithInlineAndRef() {
        var stmts = TestHelpers.parseAssert("""
            CREATE STREAM Events (
              TYPE Base AS STRUCT ( Id INT32, Kind STRING ),
              TYPE Payload AS com.example.Payload
            );
            """);
        CreateStmt cs = TestHelpers.only(stmts, CreateStmt.class);
        StreamDecl decl = TestHelpers.assertDecl(
            StreamDecl.class,
            cs.decl(),
            "Events"
        );

        List<StreamMemberDecl> members = decl.streamTypes();
        assertEquals(2, members.size());

        TypeDecl inlineDecl = TestHelpers.assertDecl(
            TypeDecl.class,
            members.get(0).memberDecl(),
            "Base"
        );
        StructDecl inline = TestHelpers.assertTypeDecl(
            StructDecl.class,
            inlineDecl.kind()
        );
        assertEquals(2, inline.fields().size());

        TypeDecl refDecl = TestHelpers.assertDecl(
            TypeDecl.class,
            members.get(1).memberDecl(),
            "Payload"
        );
        DerivedTypeDecl ref = TestHelpers.assertTypeDecl(
            DerivedTypeDecl.class,
            refDecl.kind()
        );
        assertEquals("com.example.Payload", ref.target().name().fullName());
    }

    @Test
    void createStreamCompactMultipleInline() {
        var stmts = TestHelpers.parseAssert("""
            CREATE STREAM Session (
                TYPE StartRec AS STRUCT ( UserId INT64, Start TIMESTAMP(3) ),
                TYPE EndRec AS STRUCT ( UserId INT64, End TIMESTAMP(3) ),
                TYPE Extra AS com.example.Extra
            );
            """);
        CreateStmt cs = TestHelpers.only(stmts, CreateStmt.class);
        StreamDecl sd = TestHelpers.assertDecl(
            StreamDecl.class,
            cs.decl(),
            "Session"
        );


        List<StreamMemberDecl> members = sd.streamTypes();
        assertEquals(3, members.size());

        TypeDecl startRecDecl = TestHelpers.assertDecl(
            TypeDecl.class,
            members.get(0).memberDecl(),
            "StartRec"
        );
        StructDecl startRec = TestHelpers.assertTypeDecl(
            StructDecl.class,
            startRecDecl.kind()
        );
        assertEquals(2, startRec.fields().size());

        TypeDecl endRecDecl = TestHelpers.assertDecl(
            TypeDecl.class,
            members.get(1).memberDecl(),
            "EndRec"
        );
        StructDecl endRec = TestHelpers.assertTypeDecl(
            StructDecl.class,
            endRecDecl.kind()
        );
        assertEquals(2, endRec.fields().size());

        TypeDecl extraDecl = TestHelpers.assertDecl(
            TypeDecl.class,
            members.get(2).memberDecl(),
            "Extra"
        );
        DerivedTypeDecl extra = TestHelpers.assertTypeDecl(
            DerivedTypeDecl.class,
            extraDecl.kind()
        );
        assertEquals("com.example.Extra", extra.target().name().fullName());
    }

    @Test
    void chainedContextThenCreateStructUsesContext_syntactic() {
        var stmts = TestHelpers.parseAssert("""
            CREATE CONTEXT company;
            USE CONTEXT company;
            CREATE CONTEXT finance;
            USE CONTEXT finance;
            CREATE TYPE Account AS STRUCT ( Id INT32 );
            """);

        assertEquals(5, stmts.size());

        CreateStmt ctx0 = TestHelpers.at(stmts, 0, CreateStmt.class);
        UseStmt     use0 = TestHelpers.at(stmts, 1, UseStmt.class);
        CreateStmt ctx1 = TestHelpers.at(stmts, 2, CreateStmt.class);
        UseStmt     use1 = TestHelpers.at(stmts, 3, UseStmt.class);
        CreateStmt     ct   = TestHelpers.at(stmts, 4, CreateStmt.class);

        TestHelpers.assertDecl(
            ContextDecl.class,
            ctx0.decl(),
            "company"
        );
        assertEquals("company", ((ContextUse) use0.target()).qname().fullName());
        TestHelpers.assertDecl(
            ContextDecl.class,
            ctx1.decl(),
            "finance"
        );
        assertEquals("finance", ((ContextUse)use1.target()).qname().fullName());

        TypeDecl st = TestHelpers.assertDecl(
            TypeDecl.class,
            ct.decl(),
            "Account"
        );
        StructDecl struct = TestHelpers.assertTypeDecl(
            StructDecl.class,
            st.kind()
        );
        assertEquals(1, struct.fields().size());
        assertEquals("Id", struct.fields().getFirst().name().name());
    }

    @Nested
    @DisplayName("Lenient parsing")
    class Lenient {

        @Test
        void duplicateEnumValuesStillParse() {
            var stmts = TestHelpers.parseAssert("CREATE TYPE Dups AS ENUM ( A = 1, B = 1 );");
            CreateStmt cstmt = TestHelpers.only(stmts, CreateStmt.class);
            TypeDecl typeDecl = TestHelpers.assertDecl(
                TypeDecl.class,
                cstmt.decl(),
                "Dups"
            );
            EnumDecl en = TestHelpers.assertTypeDecl(
                EnumDecl.class,
                typeDecl.kind()
            );

            assertEquals(2, en.symbols().size());
        }
    }

    @Nested
    @DisplayName("Case insensitivity")
    class CaseInsensitivity {

        @Test
        void enumSymbolsCaseInsensitive() {
            // Enum symbols should match case-insensitively
            var stmts = TestHelpers.parseAssert("""
                CREATE TYPE Status AS ENUM (
                    Active = 1,
                    Inactive = 2
                )
                DEFAULT Status::active;
                """);
            CreateStmt cstmt = TestHelpers.only(stmts, CreateStmt.class);
            TypeDecl typeDecl = TestHelpers.assertDecl(
                TypeDecl.class,
                cstmt.decl(),
                "Status"
            );
            // Should parse successfully - 'active' matches 'Active' case-insensitively
            assertNotNull(typeDecl);
        }
    }
}