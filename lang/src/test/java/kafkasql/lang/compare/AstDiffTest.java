package kafkasql.lang.compare;

import kafkasql.lang.syntax.ast.Script;
import kafkasql.lang.syntax.ast.decl.*;
import kafkasql.util.TestHelpers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AstDiff — syntactic comparison tests")
class AstDiffTest {

    // =========================================================================
    // Helper
    // =========================================================================

    private static Script parseScript(String text) {
        var result = TestHelpers.parse(text);
        assertFalse(result.diags().hasError(),
            "Parse errors: " + result.diags().all());
        assertFalse(result.scripts().isEmpty(), "Expected at least one script");
        return result.scripts().getFirst();
    }

    private static ScriptDiff diff(String left, String right) {
        return AstDiff.compare(parseScript(left), parseScript(right));
    }

    // =========================================================================
    // Version pragma
    // =========================================================================

    @Nested
    @DisplayName("Version pragma")
    class VersionPragmaTests {

        @Test
        @DisplayName("Both absent — no version diff")
        void bothAbsent_noVersionDiff() {
            ScriptDiff d = diff(
                "CREATE TYPE Foo AS SCALAR INT32;",
                "CREATE TYPE Foo AS SCALAR INT32;"
            );
            assertTrue(d.version().isEmpty());
        }

        @Test
        @DisplayName("Left has version, right absent — FieldChange (left non-null, right null)")
        void leftHasVersion_rightAbsent() {
            ScriptDiff d = diff(
                "SET VERSION = 1; CREATE TYPE Foo AS SCALAR INT32;",
                "CREATE TYPE Foo AS SCALAR INT32;"
            );
            assertTrue(d.version().isPresent(), "Expected version field change");
            FieldChange fc = d.version().get();
            assertNotNull(fc.left());
            assertNull(fc.right());
        }

        @Test
        @DisplayName("Right has version, left absent — FieldChange (left null, right non-null)")
        void rightHasVersion_leftAbsent() {
            ScriptDiff d = diff(
                "CREATE TYPE Foo AS SCALAR INT32;",
                "SET VERSION = 1; CREATE TYPE Foo AS SCALAR INT32;"
            );
            assertTrue(d.version().isPresent());
            FieldChange fc = d.version().get();
            assertNull(fc.left());
            assertNotNull(fc.right());
        }

        @Test
        @DisplayName("Same version — no version diff")
        void sameVersion_noChange() {
            ScriptDiff d = diff(
                "SET VERSION = 1; CREATE TYPE Foo AS SCALAR INT32;",
                "SET VERSION = 1; CREATE TYPE Foo AS SCALAR INT32;"
            );
            assertTrue(d.version().isEmpty());
        }

        @Test
        @DisplayName("Different versions — MODIFIED FieldChange")
        void differentVersion_modified() {
            ScriptDiff d = diff(
                "SET VERSION = 1; CREATE TYPE Foo AS SCALAR INT32;",
                "SET VERSION = 2; CREATE TYPE Foo AS SCALAR INT32;"
            );
            assertTrue(d.version().isPresent());
            FieldChange fc = d.version().get();
            assertNotNull(fc.left());
            assertNotNull(fc.right());
        }
    }

    // =========================================================================
    // Includes
    // =========================================================================

    @Nested
    @DisplayName("Includes")
    class IncludeTests {

        @Test
        @DisplayName("No includes on either side — empty list")
        void noIncludes() {
            ScriptDiff d = diff(
                "CREATE TYPE Foo AS SCALAR INT32;",
                "CREATE TYPE Foo AS SCALAR INT32;"
            );
            assertTrue(d.includes().isEmpty());
        }

        @Test
        @DisplayName("Same include on both sides — UNCHANGED")
        void sameInclude_unchanged() {
            ScriptDiff d = diff(
                "INCLUDE 'common.kafka'; CREATE TYPE Foo AS SCALAR INT32;",
                "INCLUDE 'common.kafka'; CREATE TYPE Foo AS SCALAR INT32;"
            );
            assertEquals(1, d.includes().size());
            assertEquals(DiffKind.UNCHANGED, d.includes().getFirst().kind());
        }

        @Test
        @DisplayName("Include on left only — LEFT_ONLY")
        void includeLeftOnly() {
            ScriptDiff d = diff(
                "INCLUDE 'common.kafka'; CREATE TYPE Foo AS SCALAR INT32;",
                "CREATE TYPE Foo AS SCALAR INT32;"
            );
            assertEquals(1, d.includes().size());
            MemberDiff<?> inc = d.includes().getFirst();
            assertEquals(DiffKind.LEFT_ONLY, inc.kind());
            assertNotNull(inc.left());
            assertNull(inc.right());
        }

        @Test
        @DisplayName("Include on right only — RIGHT_ONLY")
        void includeRightOnly() {
            ScriptDiff d = diff(
                "CREATE TYPE Foo AS SCALAR INT32;",
                "INCLUDE 'extra.kafka'; CREATE TYPE Foo AS SCALAR INT32;"
            );
            assertEquals(1, d.includes().size());
            MemberDiff<?> inc = d.includes().getFirst();
            assertEquals(DiffKind.RIGHT_ONLY, inc.kind());
            assertNull(inc.left());
            assertNotNull(inc.right());
        }

        @Test
        @DisplayName("Multiple includes — matched by path, order-insensitive")
        void multipleIncludes_orderInsensitive() {
            ScriptDiff d = diff(
                "INCLUDE 'a.kafka'; INCLUDE 'b.kafka'; CREATE TYPE Foo AS SCALAR INT32;",
                "INCLUDE 'b.kafka'; INCLUDE 'a.kafka'; CREATE TYPE Foo AS SCALAR INT32;"
            );
            assertEquals(2, d.includes().size());
            d.includes().forEach(inc ->
                assertEquals(DiffKind.UNCHANGED, inc.kind()));
        }
    }

    // =========================================================================
    // CREATE STRUCT
    // =========================================================================

    @Nested
    @DisplayName("CREATE TYPE … STRUCT")
    class StructTests {

        @Test
        @DisplayName("Identical struct — UNCHANGED")
        void identical_unchanged() {
            ScriptDiff d = diff(
                "CREATE TYPE Person AS STRUCT ( Id INT32, Name STRING );",
                "CREATE TYPE Person AS STRUCT ( Id INT32, Name STRING );"
            );
            StmtDiff.CreateDiff cd = singleCreate(d);
            assertEquals(DiffKind.UNCHANGED, cd.kind());
            assertInstanceOf(DeclDiff.StructDiff.class, cd.declDiff());
            DeclDiff.StructDiff sd = (DeclDiff.StructDiff) cd.declDiff();
            assertEquals(DiffKind.UNCHANGED, sd.kind());
            sd.fields().forEach(f -> assertEquals(DiffKind.UNCHANGED, f.kind()));
        }

        @Test
        @DisplayName("Field added — RIGHT_ONLY member")
        void fieldAdded_rightOnly() {
            ScriptDiff d = diff(
                "CREATE TYPE Person AS STRUCT ( Id INT32 );",
                "CREATE TYPE Person AS STRUCT ( Id INT32, Email STRING );"
            );
            StmtDiff.CreateDiff cd = singleCreate(d);
            assertEquals(DiffKind.MODIFIED, cd.kind());
            DeclDiff.StructDiff sd = (DeclDiff.StructDiff) cd.declDiff();
            assertEquals(DiffKind.MODIFIED, sd.kind());

            MemberDiff<StructFieldDecl> emailDiff = findFieldByName(sd.fields(), "Email");
            assertEquals(DiffKind.RIGHT_ONLY, emailDiff.kind());
            assertNull(emailDiff.left());
            assertNotNull(emailDiff.right());
        }

        @Test
        @DisplayName("Field removed — LEFT_ONLY member")
        void fieldRemoved_leftOnly() {
            ScriptDiff d = diff(
                "CREATE TYPE Person AS STRUCT ( Id INT32, Email STRING );",
                "CREATE TYPE Person AS STRUCT ( Id INT32 );"
            );
            StmtDiff.CreateDiff cd = singleCreate(d);
            assertEquals(DiffKind.MODIFIED, cd.kind());
            DeclDiff.StructDiff sd = (DeclDiff.StructDiff) cd.declDiff();

            MemberDiff<StructFieldDecl> emailDiff = findFieldByName(sd.fields(), "Email");
            assertEquals(DiffKind.LEFT_ONLY, emailDiff.kind());
            assertNotNull(emailDiff.left());
            assertNull(emailDiff.right());
        }

        @Test
        @DisplayName("Field type changed — MODIFIED with type FieldChange")
        void fieldTypeChanged_modified() {
            ScriptDiff d = diff(
                "CREATE TYPE T AS STRUCT ( Score INT32 );",
                "CREATE TYPE T AS STRUCT ( Score INT64 );"
            );
            DeclDiff.StructDiff sd = (DeclDiff.StructDiff) singleCreate(d).declDiff();
            MemberDiff<StructFieldDecl> score = findFieldByName(sd.fields(), "Score");
            assertEquals(DiffKind.MODIFIED, score.kind());
            assertTrue(score.changes().stream().anyMatch(c -> "type".equals(c.aspect())),
                "Expected a 'type' FieldChange");
        }

        @Test
        @DisplayName("Field nullable added — MODIFIED with nullable FieldChange")
        void fieldNullableAdded() {
            ScriptDiff d = diff(
                "CREATE TYPE T AS STRUCT ( Nick STRING );",
                "CREATE TYPE T AS STRUCT ( Nick STRING NULL );"
            );
            DeclDiff.StructDiff sd = (DeclDiff.StructDiff) singleCreate(d).declDiff();
            MemberDiff<StructFieldDecl> nick = findFieldByName(sd.fields(), "Nick");
            assertEquals(DiffKind.MODIFIED, nick.kind());
            assertTrue(nick.changes().stream().anyMatch(c -> "nullable".equals(c.aspect())));
        }

        @Test
        @DisplayName("Field nullable removed — MODIFIED with nullable FieldChange")
        void fieldNullableRemoved() {
            ScriptDiff d = diff(
                "CREATE TYPE T AS STRUCT ( Nick STRING NULL );",
                "CREATE TYPE T AS STRUCT ( Nick STRING );"
            );
            DeclDiff.StructDiff sd = (DeclDiff.StructDiff) singleCreate(d).declDiff();
            MemberDiff<StructFieldDecl> nick = findFieldByName(sd.fields(), "Nick");
            assertEquals(DiffKind.MODIFIED, nick.kind());
            assertTrue(nick.changes().stream().anyMatch(c -> "nullable".equals(c.aspect())));
        }

        @Test
        @DisplayName("Struct renamed — left-only + right-only pair")
        void structRenamed_leftAndRightOnly() {
            ScriptDiff d = diff(
                "CREATE TYPE OldName AS STRUCT ( Id INT32 );",
                "CREATE TYPE NewName AS STRUCT ( Id INT32 );"
            );
            List<StmtDiff> stmts = d.statements();
            assertEquals(2, stmts.size());
            long leftOnly  = stmts.stream().filter(s -> s.kind() == DiffKind.LEFT_ONLY).count();
            long rightOnly = stmts.stream().filter(s -> s.kind() == DiffKind.RIGHT_ONLY).count();
            assertEquals(1, leftOnly);
            assertEquals(1, rightOnly);
        }

        @Test
        @DisplayName("Field ordering differs — all UNCHANGED (matching by name)")
        void fieldOrderDiffers_allUnchanged() {
            ScriptDiff d = diff(
                "CREATE TYPE T AS STRUCT ( A INT32, B STRING, C INT64 );",
                "CREATE TYPE T AS STRUCT ( C INT64, A INT32, B STRING );"
            );
            DeclDiff.StructDiff sd = (DeclDiff.StructDiff) singleCreate(d).declDiff();
            assertEquals(DiffKind.UNCHANGED, sd.kind());
        }
    }

    // =========================================================================
    // CREATE ENUM
    // =========================================================================

    @Nested
    @DisplayName("CREATE TYPE … ENUM")
    class EnumTests {

        @Test
        @DisplayName("Identical enum — UNCHANGED")
        void identical_unchanged() {
            ScriptDiff d = diff(
                "CREATE TYPE Status AS ENUM ( Active = 1, Inactive = 2 );",
                "CREATE TYPE Status AS ENUM ( Active = 1, Inactive = 2 );"
            );
            assertEquals(DiffKind.UNCHANGED, singleCreate(d).kind());
        }

        @Test
        @DisplayName("Symbol added — RIGHT_ONLY member")
        void symbolAdded() {
            ScriptDiff d = diff(
                "CREATE TYPE Status AS ENUM ( Active = 1 );",
                "CREATE TYPE Status AS ENUM ( Active = 1, Deleted = 3 );"
            );
            DeclDiff.EnumDiff ed = (DeclDiff.EnumDiff) singleCreate(d).declDiff();
            assertEquals(DiffKind.MODIFIED, ed.kind());
            MemberDiff<EnumSymbolDecl> deleted = findSymbolByName(ed.symbols(), "Deleted");
            assertEquals(DiffKind.RIGHT_ONLY, deleted.kind());
        }

        @Test
        @DisplayName("Symbol removed — LEFT_ONLY member")
        void symbolRemoved() {
            ScriptDiff d = diff(
                "CREATE TYPE Status AS ENUM ( Active = 1, Legacy = 99 );",
                "CREATE TYPE Status AS ENUM ( Active = 1 );"
            );
            DeclDiff.EnumDiff ed = (DeclDiff.EnumDiff) singleCreate(d).declDiff();
            MemberDiff<EnumSymbolDecl> legacy = findSymbolByName(ed.symbols(), "Legacy");
            assertEquals(DiffKind.LEFT_ONLY, legacy.kind());
        }

        @Test
        @DisplayName("Symbol value changed — MODIFIED with value FieldChange")
        void symbolValueChanged() {
            ScriptDiff d = diff(
                "CREATE TYPE Status AS ENUM ( Active = 1 );",
                "CREATE TYPE Status AS ENUM ( Active = 2 );"
            );
            DeclDiff.EnumDiff ed = (DeclDiff.EnumDiff) singleCreate(d).declDiff();
            MemberDiff<EnumSymbolDecl> active = findSymbolByName(ed.symbols(), "Active");
            assertEquals(DiffKind.MODIFIED, active.kind());
            assertTrue(active.changes().stream().anyMatch(c -> "value".equals(c.aspect())));
        }

        @Test
        @DisplayName("Kind changed STRUCT → ENUM — KindChangeDiff")
        void kindChanged_structToEnum() {
            ScriptDiff d = diff(
                "CREATE TYPE T AS STRUCT ( Id INT32 );",
                "CREATE TYPE T AS ENUM ( A = 1 );"
            );
            StmtDiff.CreateDiff cd = singleCreate(d);
            assertEquals(DiffKind.MODIFIED, cd.kind());
            assertInstanceOf(DeclDiff.KindChangeDiff.class, cd.declDiff());
        }
    }

    // =========================================================================
    // CREATE UNION
    // =========================================================================

    @Nested
    @DisplayName("CREATE TYPE … UNION")
    class UnionTests {

        @Test
        @DisplayName("Identical union — UNCHANGED")
        void identical_unchanged() {
            ScriptDiff d = diff(
                "CREATE TYPE Val AS UNION ( I INT32, S STRING );",
                "CREATE TYPE Val AS UNION ( I INT32, S STRING );"
            );
            assertEquals(DiffKind.UNCHANGED, singleCreate(d).kind());
        }

        @Test
        @DisplayName("Member added — RIGHT_ONLY")
        void memberAdded() {
            ScriptDiff d = diff(
                "CREATE TYPE Val AS UNION ( I INT32 );",
                "CREATE TYPE Val AS UNION ( I INT32, F FLOAT64 );"
            );
            DeclDiff.UnionDiff ud = (DeclDiff.UnionDiff) singleCreate(d).declDiff();
            assertEquals(DiffKind.MODIFIED, ud.kind());
            MemberDiff<UnionMemberDecl> fDiff = ud.members().stream()
                .filter(m -> "F".equals(nameOf(m))).findFirst().orElseThrow();
            assertEquals(DiffKind.RIGHT_ONLY, fDiff.kind());
        }

        @Test
        @DisplayName("Member removed — LEFT_ONLY")
        void memberRemoved() {
            ScriptDiff d = diff(
                "CREATE TYPE Val AS UNION ( I INT32, S STRING );",
                "CREATE TYPE Val AS UNION ( I INT32 );"
            );
            DeclDiff.UnionDiff ud = (DeclDiff.UnionDiff) singleCreate(d).declDiff();
            MemberDiff<UnionMemberDecl> sDiff = ud.members().stream()
                .filter(m -> "S".equals(nameOf(m))).findFirst().orElseThrow();
            assertEquals(DiffKind.LEFT_ONLY, sDiff.kind());
        }

        @Test
        @DisplayName("Member type changed — MODIFIED with type FieldChange")
        void memberTypeChanged() {
            ScriptDiff d = diff(
                "CREATE TYPE Val AS UNION ( N INT32 );",
                "CREATE TYPE Val AS UNION ( N INT64 );"
            );
            DeclDiff.UnionDiff ud = (DeclDiff.UnionDiff) singleCreate(d).declDiff();
            MemberDiff<UnionMemberDecl> nDiff = ud.members().getFirst();
            assertEquals(DiffKind.MODIFIED, nDiff.kind());
            assertTrue(nDiff.changes().stream().anyMatch(c -> "type".equals(c.aspect())));
        }
    }

    // =========================================================================
    // CREATE SCALAR
    // =========================================================================

    @Nested
    @DisplayName("CREATE TYPE … SCALAR")
    class ScalarTests {

        @Test
        @DisplayName("Identical scalar — UNCHANGED")
        void identical_unchanged() {
            ScriptDiff d = diff(
                "CREATE TYPE MyInt AS SCALAR INT32;",
                "CREATE TYPE MyInt AS SCALAR INT32;"
            );
            assertEquals(DiffKind.UNCHANGED, singleCreate(d).kind());
        }

        @Test
        @DisplayName("Base type changed — MODIFIED with type FieldChange")
        void baseTypeChanged() {
            ScriptDiff d = diff(
                "CREATE TYPE MyInt AS SCALAR INT32;",
                "CREATE TYPE MyInt AS SCALAR INT64;"
            );
            StmtDiff.CreateDiff cd = singleCreate(d);
            assertEquals(DiffKind.MODIFIED, cd.kind());
            DeclDiff.ScalarDiff sd = (DeclDiff.ScalarDiff) cd.declDiff();
            assertTrue(sd.changes().stream().anyMatch(c -> "type".equals(c.aspect())));
        }

        @Test
        @DisplayName("Default added — MODIFIED with default FieldChange")
        void defaultAdded() {
            ScriptDiff d = diff(
                "CREATE TYPE PosInt AS SCALAR INT32;",
                "CREATE TYPE PosInt AS SCALAR INT32 DEFAULT 0;"
            );
            DeclDiff.ScalarDiff sd = (DeclDiff.ScalarDiff) singleCreate(d).declDiff();
            assertEquals(DiffKind.MODIFIED, sd.kind());
            assertTrue(sd.changes().stream().anyMatch(c -> "default".equals(c.aspect())));
        }

        @Test
        @DisplayName("Check added — MODIFIED with check FieldChange")
        void checkAdded() {
            ScriptDiff d = diff(
                "CREATE TYPE PosInt AS SCALAR INT32;",
                "CREATE TYPE PosInt AS SCALAR INT32 CHECK ( value > 0 );"
            );
            DeclDiff.ScalarDiff sd = (DeclDiff.ScalarDiff) singleCreate(d).declDiff();
            assertTrue(sd.changes().stream().anyMatch(c -> "check".equals(c.aspect())));
        }
    }

    // =========================================================================
    // CREATE STREAM
    // =========================================================================

    @Nested
    @DisplayName("CREATE STREAM")
    class StreamTests {

        @Test
        @DisplayName("Identical stream — UNCHANGED")
        void identical_unchanged() {
            ScriptDiff d = diff(
                "CREATE STREAM Events ( TYPE Payload AS STRUCT ( Id INT32 ) );",
                "CREATE STREAM Events ( TYPE Payload AS STRUCT ( Id INT32 ) );"
            );
            assertEquals(DiffKind.UNCHANGED, singleCreate(d).kind());
        }

        @Test
        @DisplayName("Member type added — RIGHT_ONLY")
        void memberAdded() {
            ScriptDiff d = diff(
                "CREATE STREAM Events ( TYPE A AS STRUCT ( Id INT32 ) );",
                "CREATE STREAM Events ( TYPE A AS STRUCT ( Id INT32 ), TYPE B AS STRUCT ( Name STRING ) );"
            );
            DeclDiff.StreamDiff sd = (DeclDiff.StreamDiff) singleCreate(d).declDiff();
            assertEquals(DiffKind.MODIFIED, sd.kind());
            MemberDiff<StreamMemberDecl> bDiff = sd.members().stream()
                .filter(m -> "B".equals(nameOfStream(m))).findFirst().orElseThrow();
            assertEquals(DiffKind.RIGHT_ONLY, bDiff.kind());
        }

        @Test
        @DisplayName("Member type removed — LEFT_ONLY")
        void memberRemoved() {
            ScriptDiff d = diff(
                "CREATE STREAM Events ( TYPE A AS STRUCT ( Id INT32 ), TYPE B AS STRUCT ( Name STRING ) );",
                "CREATE STREAM Events ( TYPE A AS STRUCT ( Id INT32 ) );"
            );
            DeclDiff.StreamDiff sd = (DeclDiff.StreamDiff) singleCreate(d).declDiff();
            MemberDiff<StreamMemberDecl> bDiff = sd.members().stream()
                .filter(m -> "B".equals(nameOfStream(m))).findFirst().orElseThrow();
            assertEquals(DiffKind.LEFT_ONLY, bDiff.kind());
        }

        @Test
        @DisplayName("Member field added inside stream member — MODIFIED")
        void innerFieldChanged() {
            ScriptDiff d = diff(
                "CREATE STREAM Events ( TYPE Payload AS STRUCT ( Id INT32 ) );",
                "CREATE STREAM Events ( TYPE Payload AS STRUCT ( Id INT32, Extra STRING ) );"
            );
            DeclDiff.StreamDiff sd = (DeclDiff.StreamDiff) singleCreate(d).declDiff();
            assertEquals(DiffKind.MODIFIED, sd.kind());
            MemberDiff<StreamMemberDecl> payload = sd.members().getFirst();
            assertEquals(DiffKind.MODIFIED, payload.kind());
        }
    }

    // =========================================================================
    // CREATE CONTEXT
    // =========================================================================

    @Nested
    @DisplayName("CREATE CONTEXT")
    class ContextTests {

        @Test
        @DisplayName("Identical context — UNCHANGED")
        void identical_unchanged() {
            ScriptDiff d = diff(
                "CREATE CONTEXT company;",
                "CREATE CONTEXT company;"
            );
            assertEquals(DiffKind.UNCHANGED, singleCreate(d).kind());
        }

        @Test
        @DisplayName("Context in left only — LEFT_ONLY")
        void leftOnly() {
            ScriptDiff d = diff(
                "CREATE CONTEXT company;",
                "SHOW CONTEXTS;"
            );
            assertTrue(d.statements().stream()
                .anyMatch(s -> s.kind() == DiffKind.LEFT_ONLY),
                "Expected a LEFT_ONLY statement for 'company'");
        }

        @Test
        @DisplayName("Context in right only — RIGHT_ONLY")
        void rightOnly() {
            ScriptDiff d = diff(
                "SHOW CONTEXTS;",
                "CREATE CONTEXT company;"
            );
            assertTrue(d.statements().stream()
                .anyMatch(s -> s.kind() == DiffKind.RIGHT_ONLY),
                "Expected a RIGHT_ONLY statement for 'company'");
        }
    }

    // =========================================================================
    // Non-CREATE statements (positional matching)
    // =========================================================================

    @Nested
    @DisplayName("Non-CREATE statements")
    class NonCreateTests {

        @Test
        @DisplayName("Identical USE — UNCHANGED")
        void use_identical_unchanged() {
            ScriptDiff d = diff(
                "CREATE CONTEXT c; USE CONTEXT c;",
                "CREATE CONTEXT c; USE CONTEXT c;"
            );
            StmtDiff.UseDiff ud = d.statements().stream()
                .filter(s -> s instanceof StmtDiff.UseDiff)
                .map(s -> (StmtDiff.UseDiff) s)
                .findFirst().orElseThrow();
            assertEquals(DiffKind.UNCHANGED, ud.kind());
        }

        @Test
        @DisplayName("USE on left only — LEFT_ONLY")
        void use_leftOnly() {
            ScriptDiff d = diff(
                "CREATE CONTEXT c; USE CONTEXT c;",
                "CREATE CONTEXT c;"
            );
            StmtDiff.UseDiff ud = d.statements().stream()
                .filter(s -> s instanceof StmtDiff.UseDiff)
                .map(s -> (StmtDiff.UseDiff) s)
                .findFirst().orElseThrow();
            assertEquals(DiffKind.LEFT_ONLY, ud.kind());
        }

        @Test
        @DisplayName("USE on right only — RIGHT_ONLY")
        void use_rightOnly() {
            ScriptDiff d = diff(
                "CREATE CONTEXT c;",
                "CREATE CONTEXT c; USE CONTEXT c;"
            );
            StmtDiff.UseDiff ud = d.statements().stream()
                .filter(s -> s instanceof StmtDiff.UseDiff)
                .map(s -> (StmtDiff.UseDiff) s)
                .findFirst().orElseThrow();
            assertEquals(DiffKind.RIGHT_ONLY, ud.kind());
        }

        @Test
        @DisplayName("DROP on left only — LEFT_ONLY")
        void drop_leftOnly() {
            ScriptDiff d = diff(
                "CREATE TYPE Foo AS SCALAR INT32; DROP TYPE Foo;",
                "CREATE TYPE Foo AS SCALAR INT32;"
            );
            StmtDiff.DropDiff dd = d.statements().stream()
                .filter(s -> s instanceof StmtDiff.DropDiff)
                .map(s -> (StmtDiff.DropDiff) s)
                .findFirst().orElseThrow();
            assertEquals(DiffKind.LEFT_ONLY, dd.kind());
        }

        @Test
        @DisplayName("DROP on right only — RIGHT_ONLY")
        void drop_rightOnly() {
            ScriptDiff d = diff(
                "CREATE TYPE Foo AS SCALAR INT32;",
                "CREATE TYPE Foo AS SCALAR INT32; DROP TYPE Foo;"
            );
            StmtDiff.DropDiff dd = d.statements().stream()
                .filter(s -> s instanceof StmtDiff.DropDiff)
                .map(s -> (StmtDiff.DropDiff) s)
                .findFirst().orElseThrow();
            assertEquals(DiffKind.RIGHT_ONLY, dd.kind());
        }

        @Test
        @DisplayName("Identical DROP — UNCHANGED")
        void drop_identical_unchanged() {
            ScriptDiff d = diff(
                "CREATE TYPE Foo AS SCALAR INT32; DROP TYPE Foo;",
                "CREATE TYPE Foo AS SCALAR INT32; DROP TYPE Foo;"
            );
            StmtDiff.DropDiff dd = d.statements().stream()
                .filter(s -> s instanceof StmtDiff.DropDiff)
                .map(s -> (StmtDiff.DropDiff) s)
                .findFirst().orElseThrow();
            assertEquals(DiffKind.UNCHANGED, dd.kind());
        }
    }

    // =========================================================================
    // Statement reordering (CREATE matched by key)
    // =========================================================================

    @Nested
    @DisplayName("CREATE statement reordering")
    class ReorderingTests {

        @Test
        @DisplayName("CREATE statements in different order — all UNCHANGED")
        void createStatements_reordered_allUnchanged() {
            ScriptDiff d = diff(
                "CREATE TYPE A AS SCALAR INT32; CREATE TYPE B AS SCALAR INT64;",
                "CREATE TYPE B AS SCALAR INT64; CREATE TYPE A AS SCALAR INT32;"
            );
            assertEquals(2, d.statements().size());
            d.statements().forEach(s ->
                assertEquals(DiffKind.UNCHANGED, s.kind(),
                    "Expected UNCHANGED but got " + s.kind()));
        }

        @Test
        @DisplayName("One CREATE removed, one added — LEFT_ONLY + RIGHT_ONLY")
        void oneRemovedOneAdded() {
            ScriptDiff d = diff(
                "CREATE TYPE A AS SCALAR INT32; CREATE TYPE B AS SCALAR INT64;",
                "CREATE TYPE A AS SCALAR INT32; CREATE TYPE C AS SCALAR FLOAT32;"
            );
            long leftOnly  = d.statements().stream().filter(s -> s.kind() == DiffKind.LEFT_ONLY).count();
            long rightOnly = d.statements().stream().filter(s -> s.kind() == DiffKind.RIGHT_ONLY).count();
            assertEquals(1, leftOnly,  "Expected one LEFT_ONLY (B removed)");
            assertEquals(1, rightOnly, "Expected one RIGHT_ONLY (C added)");
        }
    }

    // =========================================================================
    // SemanticEnricher
    // =========================================================================

    @Nested
    @DisplayName("SemanticEnricher")
    class SemanticEnricherTests {

        @Test
        @DisplayName("Declaration removed — BREAKING note on CreateDiff")
        void declarationRemoved_breakingNote() {
            ScriptDiff d = diff(
                "CREATE TYPE Foo AS SCALAR INT32;",
                "SHOW TYPES;"
            );
            SemanticEnricher.enrich(d);
            StmtDiff.CreateDiff cd = d.statements().stream()
                .filter(s -> s instanceof StmtDiff.CreateDiff)
                .map(s -> (StmtDiff.CreateDiff) s)
                .findFirst().orElseThrow();
            assertEquals(DiffKind.LEFT_ONLY, cd.kind());
            assertTrue(cd.notes().stream().anyMatch(n -> n.severity() == DiffSeverity.BREAKING),
                "Expected BREAKING note for removed declaration");
        }

        @Test
        @DisplayName("Declaration added — SAFE note on CreateDiff")
        void declarationAdded_safeNote() {
            ScriptDiff d = diff(
                "SHOW TYPES;",
                "CREATE TYPE Foo AS SCALAR INT32;"
            );
            SemanticEnricher.enrich(d);
            StmtDiff.CreateDiff cd = d.statements().stream()
                .filter(s -> s instanceof StmtDiff.CreateDiff)
                .map(s -> (StmtDiff.CreateDiff) s)
                .findFirst().orElseThrow();
            assertEquals(DiffKind.RIGHT_ONLY, cd.kind());
            assertTrue(cd.notes().stream().anyMatch(n -> n.severity() == DiffSeverity.SAFE),
                "Expected SAFE note for added declaration");
        }

        @Test
        @DisplayName("Field removed without DROPPED marker — BREAKING note")
        void fieldRemoved_breakingNote() {
            ScriptDiff d = diff(
                "CREATE TYPE T AS STRUCT ( Id INT32, Email STRING );",
                "CREATE TYPE T AS STRUCT ( Id INT32 );"
            );
            SemanticEnricher.enrich(d);
            DeclDiff.StructDiff sd = (DeclDiff.StructDiff) singleCreate(d).declDiff();
            MemberDiff<StructFieldDecl> emailDiff = findFieldByName(sd.fields(), "Email");
            assertTrue(emailDiff.notes().stream().anyMatch(n -> n.severity() == DiffSeverity.BREAKING),
                "Expected BREAKING note for removed field");
        }

        @Test
        @DisplayName("Required field added without default — WARNING note")
        void requiredFieldAdded_warningNote() {
            ScriptDiff d = diff(
                "CREATE TYPE T AS STRUCT ( Id INT32 );",
                "CREATE TYPE T AS STRUCT ( Id INT32, Name STRING );"
            );
            SemanticEnricher.enrich(d);
            DeclDiff.StructDiff sd = (DeclDiff.StructDiff) singleCreate(d).declDiff();
            MemberDiff<StructFieldDecl> nameDiff = findFieldByName(sd.fields(), "Name");
            assertTrue(nameDiff.notes().stream().anyMatch(n -> n.severity() == DiffSeverity.WARNING),
                "Expected WARNING note for required field added without default");
        }

        @Test
        @DisplayName("Nullable field added — SAFE note")
        void nullableFieldAdded_safeNote() {
            ScriptDiff d = diff(
                "CREATE TYPE T AS STRUCT ( Id INT32 );",
                "CREATE TYPE T AS STRUCT ( Id INT32, Nick STRING NULL );"
            );
            SemanticEnricher.enrich(d);
            DeclDiff.StructDiff sd = (DeclDiff.StructDiff) singleCreate(d).declDiff();
            MemberDiff<StructFieldDecl> nickDiff = findFieldByName(sd.fields(), "Nick");
            assertTrue(nickDiff.notes().stream().anyMatch(n -> n.severity() == DiffSeverity.SAFE),
                "Expected SAFE note for nullable field added");
        }

        @Test
        @DisplayName("Nullable removed without default — BREAKING note")
        void nullableRemovedNoDefault_breakingNote() {
            ScriptDiff d = diff(
                "CREATE TYPE T AS STRUCT ( Nick STRING NULL );",
                "CREATE TYPE T AS STRUCT ( Nick STRING );"
            );
            SemanticEnricher.enrich(d);
            DeclDiff.StructDiff sd = (DeclDiff.StructDiff) singleCreate(d).declDiff();
            MemberDiff<StructFieldDecl> nickDiff = findFieldByName(sd.fields(), "Nick");
            assertTrue(nickDiff.notes().stream().anyMatch(n ->
                n.severity() == DiffSeverity.BREAKING && "nullable".equals(n.aspect())));
        }

        @Test
        @DisplayName("Nullable removed with default present — SAFE note")
        void nullableRemovedWithDefault_safeNote() {
            ScriptDiff d = diff(
                "CREATE TYPE T AS STRUCT ( Nick STRING NULL );",
                "CREATE TYPE T AS STRUCT ( Nick STRING DEFAULT 'anon' );"
            );
            SemanticEnricher.enrich(d);
            DeclDiff.StructDiff sd = (DeclDiff.StructDiff) singleCreate(d).declDiff();
            MemberDiff<StructFieldDecl> nickDiff = findFieldByName(sd.fields(), "Nick");
            assertTrue(nickDiff.notes().stream().anyMatch(n ->
                n.severity() == DiffSeverity.SAFE && "nullable".equals(n.aspect())));
        }

        @Test
        @DisplayName("NOT NULL field made nullable — BREAKING note")
        void notNullMadeNullable_breakingNote() {
            ScriptDiff d = diff(
                "CREATE TYPE T AS STRUCT ( Nick STRING );",
                "CREATE TYPE T AS STRUCT ( Nick STRING NULL );"
            );
            SemanticEnricher.enrich(d);
            DeclDiff.StructDiff sd = (DeclDiff.StructDiff) singleCreate(d).declDiff();
            MemberDiff<StructFieldDecl> nickDiff = findFieldByName(sd.fields(), "Nick");
            assertTrue(nickDiff.notes().stream().anyMatch(n ->
                n.severity() == DiffSeverity.BREAKING && "nullable".equals(n.aspect())));
        }

        @Test
        @DisplayName("Enum symbol removed — BREAKING note")
        void enumSymbolRemoved_breakingNote() {
            ScriptDiff d = diff(
                "CREATE TYPE Status AS ENUM ( Active = 1, Legacy = 99 );",
                "CREATE TYPE Status AS ENUM ( Active = 1 );"
            );
            SemanticEnricher.enrich(d);
            DeclDiff.EnumDiff ed = (DeclDiff.EnumDiff) singleCreate(d).declDiff();
            MemberDiff<EnumSymbolDecl> legacy = findSymbolByName(ed.symbols(), "Legacy");
            assertTrue(legacy.notes().stream().anyMatch(n -> n.severity() == DiffSeverity.BREAKING));
        }

        @Test
        @DisplayName("Enum symbol added — WARNING note")
        void enumSymbolAdded_warningNote() {
            ScriptDiff d = diff(
                "CREATE TYPE Status AS ENUM ( Active = 1 );",
                "CREATE TYPE Status AS ENUM ( Active = 1, New = 5 );"
            );
            SemanticEnricher.enrich(d);
            DeclDiff.EnumDiff ed = (DeclDiff.EnumDiff) singleCreate(d).declDiff();
            MemberDiff<EnumSymbolDecl> newSym = findSymbolByName(ed.symbols(), "New");
            assertTrue(newSym.notes().stream().anyMatch(n -> n.severity() == DiffSeverity.WARNING));
        }

        @Test
        @DisplayName("Enum symbol value changed — BREAKING note")
        void enumSymbolValueChanged_breakingNote() {
            ScriptDiff d = diff(
                "CREATE TYPE Status AS ENUM ( Active = 1 );",
                "CREATE TYPE Status AS ENUM ( Active = 2 );"
            );
            SemanticEnricher.enrich(d);
            DeclDiff.EnumDiff ed = (DeclDiff.EnumDiff) singleCreate(d).declDiff();
            MemberDiff<EnumSymbolDecl> active = findSymbolByName(ed.symbols(), "Active");
            assertTrue(active.notes().stream().anyMatch(n -> n.severity() == DiffSeverity.BREAKING));
        }

        @Test
        @DisplayName("Union member removed — BREAKING note")
        void unionMemberRemoved_breakingNote() {
            ScriptDiff d = diff(
                "CREATE TYPE Val AS UNION ( I INT32, S STRING );",
                "CREATE TYPE Val AS UNION ( I INT32 );"
            );
            SemanticEnricher.enrich(d);
            DeclDiff.UnionDiff ud = (DeclDiff.UnionDiff) singleCreate(d).declDiff();
            MemberDiff<UnionMemberDecl> sDiff = ud.members().stream()
                .filter(m -> "S".equals(nameOf(m))).findFirst().orElseThrow();
            assertTrue(sDiff.notes().stream().anyMatch(n -> n.severity() == DiffSeverity.BREAKING));
        }

        @Test
        @DisplayName("Union member added — WARNING note")
        void unionMemberAdded_warningNote() {
            ScriptDiff d = diff(
                "CREATE TYPE Val AS UNION ( I INT32 );",
                "CREATE TYPE Val AS UNION ( I INT32, S STRING );"
            );
            SemanticEnricher.enrich(d);
            DeclDiff.UnionDiff ud = (DeclDiff.UnionDiff) singleCreate(d).declDiff();
            MemberDiff<UnionMemberDecl> sDiff = ud.members().stream()
                .filter(m -> "S".equals(nameOf(m))).findFirst().orElseThrow();
            assertTrue(sDiff.notes().stream().anyMatch(n -> n.severity() == DiffSeverity.WARNING));
        }

        @Test
        @DisplayName("DROP added in right — BREAKING note")
        void dropAdded_breakingNote() {
            ScriptDiff d = diff(
                "CREATE TYPE Foo AS SCALAR INT32;",
                "CREATE TYPE Foo AS SCALAR INT32; DROP TYPE Foo;"
            );
            SemanticEnricher.enrich(d);
            StmtDiff.DropDiff dd = d.statements().stream()
                .filter(s -> s instanceof StmtDiff.DropDiff)
                .map(s -> (StmtDiff.DropDiff) s)
                .findFirst().orElseThrow();
            assertTrue(dd.notes().stream().anyMatch(n -> n.severity() == DiffSeverity.BREAKING));
        }

        // ── Type-change classification ─────────────────────────────────────

        @Test
        @DisplayName("Integer widening INT32→INT64 — BREAKING (member type is immutable)")
        void integerWidening_breakingNote() {
            ScriptDiff d = diff(
                "CREATE TYPE T AS STRUCT ( Id INT32 );",
                "CREATE TYPE T AS STRUCT ( Id INT64 );"
            );
            SemanticEnricher.enrich(d);
            DeclDiff.StructDiff sd = (DeclDiff.StructDiff) singleCreate(d).declDiff();
            assertTrue(findFieldByName(sd.fields(), "Id").notes().stream()
                .anyMatch(n -> n.severity() == DiffSeverity.BREAKING && "type".equals(n.aspect())));
        }

        @Test
        @DisplayName("Integer narrowing INT64→INT32 — BREAKING note")
        void integerNarrowing_breakingNote() {
            ScriptDiff d = diff(
                "CREATE TYPE T AS STRUCT ( Id INT64 );",
                "CREATE TYPE T AS STRUCT ( Id INT32 );"
            );
            SemanticEnricher.enrich(d);
            DeclDiff.StructDiff sd = (DeclDiff.StructDiff) singleCreate(d).declDiff();
            assertTrue(findFieldByName(sd.fields(), "Id").notes().stream()
                .anyMatch(n -> n.severity() == DiffSeverity.BREAKING && "type".equals(n.aspect())));
        }

        @Test
        @DisplayName("STRING length widening STRING(5)→STRING(10) — BREAKING (member type is immutable)")
        void stringWidening_breakingNote() {
            ScriptDiff d = diff(
                "CREATE TYPE T AS STRUCT ( Code STRING(5) );",
                "CREATE TYPE T AS STRUCT ( Code STRING(10) );"
            );
            SemanticEnricher.enrich(d);
            DeclDiff.StructDiff sd = (DeclDiff.StructDiff) singleCreate(d).declDiff();
            assertTrue(findFieldByName(sd.fields(), "Code").notes().stream()
                .anyMatch(n -> n.severity() == DiffSeverity.BREAKING && "type".equals(n.aspect())));
        }

        @Test
        @DisplayName("STRING length narrowing STRING(10)→STRING(5) — BREAKING note")
        void stringNarrowing_breakingNote() {
            ScriptDiff d = diff(
                "CREATE TYPE T AS STRUCT ( Code STRING(10) );",
                "CREATE TYPE T AS STRUCT ( Code STRING(5) );"
            );
            SemanticEnricher.enrich(d);
            DeclDiff.StructDiff sd = (DeclDiff.StructDiff) singleCreate(d).declDiff();
            assertTrue(findFieldByName(sd.fields(), "Code").notes().stream()
                .anyMatch(n -> n.severity() == DiffSeverity.BREAKING && "type".equals(n.aspect())));
        }

        @Test
        @DisplayName("STRING constrained to unlimited STRING(5)→STRING — BREAKING (member type is immutable)")
        void stringConstraintRemoved_breakingNote() {
            ScriptDiff d = diff(
                "CREATE TYPE T AS STRUCT ( Code STRING(5) );",
                "CREATE TYPE T AS STRUCT ( Code STRING );"
            );
            SemanticEnricher.enrich(d);
            DeclDiff.StructDiff sd = (DeclDiff.StructDiff) singleCreate(d).declDiff();
            assertTrue(findFieldByName(sd.fields(), "Code").notes().stream()
                .anyMatch(n -> n.severity() == DiffSeverity.BREAKING && "type".equals(n.aspect())));
        }

        @Test
        @DisplayName("STRING unlimited to constrained STRING→STRING(5) — BREAKING note")
        void stringConstraintAdded_breakingNote() {
            ScriptDiff d = diff(
                "CREATE TYPE T AS STRUCT ( Code STRING );",
                "CREATE TYPE T AS STRUCT ( Code STRING(5) );"
            );
            SemanticEnricher.enrich(d);
            DeclDiff.StructDiff sd = (DeclDiff.StructDiff) singleCreate(d).declDiff();
            assertTrue(findFieldByName(sd.fields(), "Code").notes().stream()
                .anyMatch(n -> n.severity() == DiffSeverity.BREAKING && "type".equals(n.aspect())));
        }

        @Test
        @DisplayName("DECIMAL same scale, more precision — BREAKING (member type is immutable)")
        void decimalPrecisionWidened_breakingNote() {
            ScriptDiff d = diff(
                "CREATE TYPE T AS STRUCT ( Price DECIMAL(18,2) );",
                "CREATE TYPE T AS STRUCT ( Price DECIMAL(20,2) );"
            );
            SemanticEnricher.enrich(d);
            DeclDiff.StructDiff sd = (DeclDiff.StructDiff) singleCreate(d).declDiff();
            assertTrue(findFieldByName(sd.fields(), "Price").notes().stream()
                .anyMatch(n -> n.severity() == DiffSeverity.BREAKING && "type".equals(n.aspect())));
        }

        @Test
        @DisplayName("DECIMAL scale changed — BREAKING note")
        void decimalScaleChanged_breakingNote() {
            ScriptDiff d = diff(
                "CREATE TYPE T AS STRUCT ( Price DECIMAL(18,2) );",
                "CREATE TYPE T AS STRUCT ( Price DECIMAL(18,4) );"
            );
            SemanticEnricher.enrich(d);
            DeclDiff.StructDiff sd = (DeclDiff.StructDiff) singleCreate(d).declDiff();
            assertTrue(findFieldByName(sd.fields(), "Price").notes().stream()
                .anyMatch(n -> n.severity() == DiffSeverity.BREAKING && "type".equals(n.aspect())));
        }

        @Test
        @DisplayName("TIMESTAMP precision increased — BREAKING (member type is immutable)")
        void timestampPrecisionIncreased_breakingNote() {
            ScriptDiff d = diff(
                "CREATE TYPE T AS STRUCT ( Ts TIMESTAMP(0) );",
                "CREATE TYPE T AS STRUCT ( Ts TIMESTAMP(3) );"
            );
            SemanticEnricher.enrich(d);
            DeclDiff.StructDiff sd = (DeclDiff.StructDiff) singleCreate(d).declDiff();
            assertTrue(findFieldByName(sd.fields(), "Ts").notes().stream()
                .anyMatch(n -> n.severity() == DiffSeverity.BREAKING && "type".equals(n.aspect())));
        }

        @Test
        @DisplayName("TIMESTAMP precision decreased — BREAKING (member type is immutable)")
        void timestampPrecisionDecreased_breakingNote() {
            ScriptDiff d = diff(
                "CREATE TYPE T AS STRUCT ( Ts TIMESTAMP(3) );",
                "CREATE TYPE T AS STRUCT ( Ts TIMESTAMP(0) );"
            );
            SemanticEnricher.enrich(d);
            DeclDiff.StructDiff sd = (DeclDiff.StructDiff) singleCreate(d).declDiff();
            assertTrue(findFieldByName(sd.fields(), "Ts").notes().stream()
                .anyMatch(n -> n.severity() == DiffSeverity.BREAKING && "type".equals(n.aspect())));
        }

        @Test
        @DisplayName("Cross-kind type change INT32→STRING — BREAKING note")
        void crossKindTypeChange_breakingNote() {
            ScriptDiff d = diff(
                "CREATE TYPE T AS STRUCT ( Code INT32 );",
                "CREATE TYPE T AS STRUCT ( Code STRING );"
            );
            SemanticEnricher.enrich(d);
            DeclDiff.StructDiff sd = (DeclDiff.StructDiff) singleCreate(d).declDiff();
            assertTrue(findFieldByName(sd.fields(), "Code").notes().stream()
                .anyMatch(n -> n.severity() == DiffSeverity.BREAKING && "type".equals(n.aspect())));
        }

        // ── Kind change ───────────────────────────────────────────────────

        @Test
        @DisplayName("Declaration kind changed STRUCT→ENUM — BREAKING note on CreateDiff")
        void kindChanged_breakingNoteOnParent() {
            ScriptDiff d = diff(
                "CREATE TYPE Foo AS STRUCT ( Id INT32 );",
                "CREATE TYPE Foo AS ENUM ( A = 1 );"
            );
            SemanticEnricher.enrich(d);
            StmtDiff.CreateDiff cd = singleCreate(d);
            assertTrue(cd.notes().stream().anyMatch(n ->
                n.severity() == DiffSeverity.BREAKING && "kind".equals(n.aspect())),
                "Expected BREAKING 'kind' note on parent CreateDiff");
        }

        // ── Scalar enrichment ─────────────────────────────────────────────

        @Test
        @DisplayName("Scalar type change — note on parent CreateDiff")
        void scalarTypeChange_noteOnParent() {
            ScriptDiff d = diff(
                "CREATE TYPE Money AS SCALAR DECIMAL(18,2);",
                "CREATE TYPE Money AS SCALAR DECIMAL(20,2);"
            );
            SemanticEnricher.enrich(d);
            StmtDiff.CreateDiff cd = singleCreate(d);
            assertTrue(cd.notes().stream().anyMatch(n -> "type".equals(n.aspect())),
                "Expected type note on parent CreateDiff for scalar change");
        }

        // ── Derived enrichment ────────────────────────────────────────────

        @Test
        @DisplayName("Derived type base changed — BREAKING note on parent CreateDiff")
        void derivedBaseChanged_breakingNoteOnParent() {
            ScriptDiff d = diff(
                "CREATE TYPE Alias AS com.Foo;",
                "CREATE TYPE Alias AS com.Bar;"
            );
            SemanticEnricher.enrich(d);
            StmtDiff.CreateDiff cd = singleCreate(d);
            assertTrue(cd.notes().stream().anyMatch(n ->
                n.severity() == DiffSeverity.BREAKING && "target".equals(n.aspect())),
                "Expected BREAKING 'target' note for derived type base change");
        }

        // ── describeType detail ───────────────────────────────────────────

        @Test
        @DisplayName("Type change message includes parameter detail e.g. DECIMAL(18,2)")
        void typeChangeMessage_includesParams() {
            ScriptDiff d = diff(
                "CREATE TYPE T AS STRUCT ( Price DECIMAL(18,2) );",
                "CREATE TYPE T AS STRUCT ( Price DECIMAL(20,2) );"
            );
            SemanticEnricher.enrich(d);
            DeclDiff.StructDiff sd = (DeclDiff.StructDiff) singleCreate(d).declDiff();
            SemanticNote note = findFieldByName(sd.fields(), "Price").notes().stream()
                .filter(n -> "type".equals(n.aspect())).findFirst().orElseThrow();
            assertTrue(note.message().contains("18") && note.message().contains("20"),
                "Expected precision values in type change message, got: " + note.message());
        }
    }

    // =========================================================================
    // ScriptDiff.flatten()
    // =========================================================================

    @Nested
    @DisplayName("ScriptDiff.flatten()")
    class FlattenTests {

        @Test
        @DisplayName("UNCHANGED entries omitted by default")
        void unchangedOmittedByDefault() {
            ScriptDiff d = diff(
                "CREATE TYPE Foo AS SCALAR INT32;",
                "CREATE TYPE Foo AS SCALAR INT32;"
            );
            List<DiffEntry> entries = d.flatten();
            assertTrue(entries.isEmpty(),
                "Expected no entries for fully identical scripts, got: " + entries.size());
        }

        @Test
        @DisplayName("UNCHANGED entries included when flag is true")
        void unchangedIncludedWhenRequested() {
            ScriptDiff d = diff(
                "CREATE TYPE Foo AS SCALAR INT32;",
                "CREATE TYPE Foo AS SCALAR INT32;"
            );
            List<DiffEntry> entries = d.flatten(true);
            assertFalse(entries.isEmpty(),
                "Expected at least one entry when includeUnchanged=true");
            assertTrue(entries.stream().allMatch(e -> e.kind() == DiffKind.UNCHANGED));
        }

        @Test
        @DisplayName("LEFT_ONLY statement produces a DiffEntry")
        void leftOnlyStatement_producesEntry() {
            ScriptDiff d = diff(
                "CREATE TYPE Foo AS SCALAR INT32;",
                "SHOW TYPES;"
            );
            List<DiffEntry> entries = d.flatten();
            assertTrue(entries.stream().anyMatch(e ->
                e.kind() == DiffKind.LEFT_ONLY && e.leftRange() != null),
                "Expected a LEFT_ONLY DiffEntry with non-null leftRange");
        }

        @Test
        @DisplayName("RIGHT_ONLY statement produces a DiffEntry")
        void rightOnlyStatement_producesEntry() {
            ScriptDiff d = diff(
                "SHOW TYPES;",
                "CREATE TYPE Foo AS SCALAR INT32;"
            );
            List<DiffEntry> entries = d.flatten();
            assertTrue(entries.stream().anyMatch(e -> e.kind() == DiffKind.RIGHT_ONLY),
                "Expected a RIGHT_ONLY DiffEntry");
        }

        @Test
        @DisplayName("Version change produces a DiffEntry at the head of the list")
        void versionChange_producesFirstEntry() {
            ScriptDiff d = diff(
                "SET VERSION = 1; CREATE TYPE Foo AS SCALAR INT32;",
                "SET VERSION = 2; CREATE TYPE Foo AS SCALAR INT32;"
            );
            List<DiffEntry> entries = d.flatten();
            assertFalse(entries.isEmpty());
            DiffEntry first = entries.getFirst();
            assertEquals("version", first.aspect());
        }

        @Test
        @DisplayName("Include added — produces RIGHT_ONLY DiffEntry")
        void includeAdded_entry() {
            ScriptDiff d = diff(
                "CREATE TYPE Foo AS SCALAR INT32;",
                "INCLUDE 'extra.kafka'; CREATE TYPE Foo AS SCALAR INT32;"
            );
            List<DiffEntry> entries = d.flatten();
            assertTrue(entries.stream().anyMatch(e ->
                "include".equals(e.aspect()) && e.kind() == DiffKind.RIGHT_ONLY));
        }

        @Test
        @DisplayName("Modified struct field produces multiple DiffEntries (one per FieldChange)")
        void modifiedField_multipleEntries() {
            ScriptDiff d = diff(
                "CREATE TYPE T AS STRUCT ( Score INT32, Nick STRING );",
                "CREATE TYPE T AS STRUCT ( Score INT64, Nick STRING NULL );"
            );
            List<DiffEntry> entries = d.flatten();
            assertTrue(entries.size() >= 2,
                "Expected at least 2 entries for two modified fields, got: " + entries.size());
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static StmtDiff.CreateDiff singleCreate(ScriptDiff d) {
        List<StmtDiff> creates = d.statements().stream()
            .filter(s -> s instanceof StmtDiff.CreateDiff)
            .toList();
        assertEquals(1, creates.size(), "Expected exactly 1 CREATE diff");
        return (StmtDiff.CreateDiff) creates.getFirst();
    }

    private static MemberDiff<StructFieldDecl> findFieldByName(
        List<MemberDiff<StructFieldDecl>> fields, String name
    ) {
        return fields.stream()
            .filter(f -> name.equals(f.left() != null
                ? f.left().name().name()
                : f.right().name().name()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No field named: " + name));
    }

    private static MemberDiff<EnumSymbolDecl> findSymbolByName(
        List<MemberDiff<EnumSymbolDecl>> symbols, String name
    ) {
        return symbols.stream()
            .filter(s -> name.equals(s.left() != null
                ? s.left().name().name()
                : s.right().name().name()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No symbol named: " + name));
    }

    /** Name of a union member diff (from whichever side exists). */
    private static String nameOf(MemberDiff<UnionMemberDecl> md) {
        return md.left() != null ? md.left().name().name() : md.right().name().name();
    }

    /** Name of a stream member diff (from whichever side exists). */
    private static String nameOfStream(MemberDiff<StreamMemberDecl> md) {
        return md.left() != null ? md.left().name().name() : md.right().name().name();
    }
}
