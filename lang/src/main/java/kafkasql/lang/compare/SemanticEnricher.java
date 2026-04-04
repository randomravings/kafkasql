package kafkasql.lang.compare;

import kafkasql.lang.syntax.ast.decl.*;
import kafkasql.lang.syntax.ast.fragment.DefaultNode;
import kafkasql.lang.syntax.ast.fragment.DeclFragment;
import kafkasql.lang.syntax.ast.fragment.DroppedNode;
import kafkasql.lang.syntax.ast.type.ComplexTypeNode;
import kafkasql.lang.syntax.ast.type.ListTypeNode;
import kafkasql.lang.syntax.ast.type.MapTypeNode;
import kafkasql.lang.syntax.ast.type.PrimitiveTypeNode;

import java.util.List;

/**
 * Optional second pass that annotates a {@link ScriptDiff} with {@link SemanticNote}s
 * describing the <em>meaning</em> of each structural change.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 *   ScriptDiff diff = AstDiff.compare(leftScript, rightScript);
 *
 *   // Default rules (KafkaSQL immutable-log semantics):
 *   SemanticEnricher.enrich(diff);
 *
 *   // Custom rules — e.g. treat type changes as WARNING:
 *   RuleSet lenient = RuleSet.defaults()
 *       .with(RuleKey.STRUCT_FIELD_TYPE_CHANGED, DiffSeverity.WARNING);
 *   SemanticEnricher.enrich(diff, lenient);
 * }</pre>
 *
 * <h2>Design</h2>
 * Enrichment is a pure walk over the already-produced diff tree.  It does not need a
 * {@link kafkasql.lang.semantic.SemanticModel} — all rules are applied from the AST
 * nodes alone.  The severity of each finding is resolved through a
 * {@link RuleSet} instance, making every decision configurable without
 * subclassing or re-implementing the walker.
 *
 * <p>Notes are appended to the mutable {@code List<SemanticNote>} that each diff node
 * carries.  Calling {@code enrich} a second time is idempotent only if the caller
 * clears existing notes first.
 */
public final class SemanticEnricher {

    private SemanticEnricher() {}

    // =========================================================================
    // Entry points
    // =========================================================================

    /** Enrich using the default rule set ({@link RuleSet#defaults()}). */
    public static void enrich(ScriptDiff diff) {
        enrich(diff, RuleSet.defaults());
    }

    /** Enrich using a custom rule set. */
    public static void enrich(ScriptDiff diff, RuleSet rules) {
        for (StmtDiff stmt : diff.statements()) {
            enrichStmt(stmt, rules);
        }
    }

    // =========================================================================
    // Statement-level enrichment
    // =========================================================================

    private static void enrichStmt(StmtDiff stmt, RuleSet rules) {
        switch (stmt) {
            case StmtDiff.CreateDiff  cd -> enrichCreate(cd, rules);
            case StmtDiff.DropDiff    dd -> enrichDrop(dd, rules);
            case StmtDiff.AlterDiff   ad -> enrichAlter(ad, rules);
            default -> { /* USE/READ/WRITE/SHOW/EXPLAIN — no semantic rules yet */ }
        }
    }

    private static void enrichCreate(StmtDiff.CreateDiff diff, RuleSet rules) {
        switch (diff.kind()) {
            case LEFT_ONLY ->
                diff.notes().add(note(rules.severity(RuleKey.DECL_REMOVED), "statement",
                    "Declaration '" + diff.left().decl().name().name() + "' removed"));
            case RIGHT_ONLY ->
                diff.notes().add(note(rules.severity(RuleKey.DECL_ADDED), "statement",
                    "Declaration '" + diff.right().decl().name().name() + "' added"));
            case MODIFIED -> {
                if (diff.declDiff() != null) enrichDeclDiff(diff.declDiff(), diff.notes(), rules);
            }
            case UNCHANGED -> { }
        }
    }

    private static void enrichDrop(StmtDiff.DropDiff diff, RuleSet rules) {
        switch (diff.kind()) {
            case LEFT_ONLY ->
                diff.notes().add(note(rules.severity(RuleKey.DROP_REMOVED), "statement",
                    "DROP statement removed (entity will no longer be dropped)"));
            case RIGHT_ONLY ->
                diff.notes().add(note(rules.severity(RuleKey.DROP_ADDED), "statement",
                    "DROP statement added — entity will be destroyed"));
            default -> { }
        }
    }

    private static void enrichAlter(StmtDiff.AlterDiff diff, RuleSet rules) {
        switch (diff.kind()) {
            case LEFT_ONLY ->
                diff.notes().add(note(rules.severity(RuleKey.ALTER_REMOVED), "statement",
                    "ALTER statement removed"));
            case RIGHT_ONLY ->
                diff.notes().add(note(rules.severity(RuleKey.ALTER_ADDED), "statement",
                    "ALTER statement added"));
            default -> { }
        }
    }

    // =========================================================================
    // Declaration-level enrichment
    // =========================================================================

    private static void enrichDeclDiff(DeclDiff decl, List<SemanticNote> parentNotes, RuleSet rules) {
        switch (decl) {
            case DeclDiff.KindChangeDiff kd ->
                parentNotes.add(note(rules.severity(RuleKey.DECL_KIND_CHANGED), "kind",
                    "Declaration kind changed from " + kindLabel(kd.left())
                    + " to " + kindLabel(kd.right())
                    + " — all existing data is incompatible"));
            case DeclDiff.StructDiff    sd -> enrichStruct(sd, rules);
            case DeclDiff.EnumDiff      ed -> enrichEnum(ed, parentNotes, rules);
            case DeclDiff.UnionDiff     ud -> enrichUnion(ud, rules);
            case DeclDiff.ScalarDiff    sd -> enrichScalar(sd, parentNotes, rules);
            case DeclDiff.DerivedDiff   dd -> enrichDerived(dd, parentNotes, rules);
            case DeclDiff.StreamDiff    sd -> enrichStream(sd, rules);
            case DeclDiff.ContextDiff   cd -> enrichContext(cd, parentNotes, rules);
        }
    }

    // ── Struct ────────────────────────────────────────────────────────────────────────────

    // Package-private: also called from enrichStream for inner struct re-diff.
    static void enrichStruct(DeclDiff.StructDiff diff, RuleSet rules) {
        for (MemberDiff<StructFieldDecl> fd : diff.fields()) {
            enrichFieldDiff(fd, rules);
        }
    }

    private static void enrichFieldDiff(MemberDiff<StructFieldDecl> diff, RuleSet rules) {
        switch (diff.kind()) {
            case LEFT_ONLY -> {
                boolean softDropped = hasFragment(diff.left().fragments(), DroppedNode.class);
                if (softDropped) {
                    diff.notes().add(note(rules.severity(RuleKey.STRUCT_FIELD_SOFT_DROPPED), "field",
                        "Field '" + diff.left().name().name() + "' soft-dropped (backward-compatible)"));
                } else {
                    diff.notes().add(note(rules.severity(RuleKey.STRUCT_FIELD_REMOVED), "field",
                        "Field '" + diff.left().name().name() + "' removed"));
                }
            }
            case RIGHT_ONLY -> {
                boolean hasNullable = diff.right().nullable().isPresent();
                boolean hasDefault  = hasFragment(diff.right().fragments(), DefaultNode.class);
                if (hasNullable || hasDefault) {
                    diff.notes().add(note(rules.severity(RuleKey.STRUCT_FIELD_ADDED_OPTIONAL), "field",
                        "Field '" + diff.right().name().name() + "' added (has nullable or default)"));
                } else {
                    diff.notes().add(note(rules.severity(RuleKey.STRUCT_FIELD_ADDED_REQUIRED), "field",
                        "Required field '" + diff.right().name().name() + "' added without a default value"));
                }
            }
            case MODIFIED -> {
                for (FieldChange change : diff.changes()) {
                    if ("nullable".equals(change.aspect())) {
                        enrichNullabilityChange(change, diff.right().fragments(), diff.notes(), rules);
                    } else {
                        enrichStructFieldChange(change, diff.notes(), rules);
                    }
                }
            }
            case UNCHANGED -> { }
        }
    }

    /**
     * Nullable changes require field-level context to assess correctly:
     * <ul>
     *   <li><b>NOT NULL → NULL</b>: BREAKING — weakening a constraint writers already relied on.</li>
     *   <li><b>NULL → NOT NULL with default</b>: SAFE — existing null records are covered by the default.</li>
     *   <li><b>NULL → NOT NULL without default</b>: BREAKING — existing null values have no fallback.</li>
     * </ul>
     * All severities are resolved through {@code rules}.
     */
    private static void enrichNullabilityChange(
        FieldChange change,
        List<DeclFragment> rightFragments,
        List<SemanticNote> notes,
        RuleSet rules
    ) {
        if (change.left() == null) {
            notes.add(note(rules.severity(RuleKey.STRUCT_FIELD_NULLABLE_ADDED), "nullable",
                "Field made nullable — NOT NULL fields cannot be weakened"));
        } else {
            boolean hasDefault = hasFragment(rightFragments, DefaultNode.class);
            if (hasDefault) {
                notes.add(note(rules.severity(RuleKey.STRUCT_FIELD_NULLABLE_REMOVED_WITH_DEFAULT), "nullable",
                    "Nullable removed — default value covers existing null records"));
            } else {
                notes.add(note(rules.severity(RuleKey.STRUCT_FIELD_NULLABLE_REMOVED_NO_DEFAULT), "nullable",
                    "Nullable removed without a default — existing null values in the log have no fallback"));
            }
        }
    }

    /** Handles metadata aspects for struct field MODIFIED changes. */
    private static void enrichStructFieldChange(FieldChange change, List<SemanticNote> notes, RuleSet rules) {
        switch (change.aspect()) {
            case "type" -> {
                String leftDesc  = describeType(change.left());
                String rightDesc = describeType(change.right());
                notes.add(note(rules.severity(RuleKey.STRUCT_FIELD_TYPE_CHANGED), "type",
                    "Type changed: " + leftDesc + " → " + rightDesc + " — member type is immutable"));
            }
            case "nullable" ->
                // Should be handled by enrichNullabilityChange; this is a conservative fallback.
                notes.add(note(rules.severity(RuleKey.STRUCT_FIELD_NULLABLE_ADDED), "nullable",
                    "Nullability changed"));
            case "default" -> {
                if (change.left() == null) {
                    notes.add(note(rules.severity(RuleKey.STRUCT_FIELD_DEFAULT_ADDED), "default",
                        "Default value added"));
                } else if (change.right() == null) {
                    notes.add(note(rules.severity(RuleKey.STRUCT_FIELD_DEFAULT_REMOVED), "default",
                        "Default value removed"));
                } else {
                    notes.add(note(rules.severity(RuleKey.STRUCT_FIELD_DEFAULT_CHANGED), "default",
                        "Default value changed"));
                }
            }
            case "check" -> {
                if (change.left() == null) {
                    notes.add(note(rules.severity(RuleKey.STRUCT_FIELD_CHECK_ADDED), "check",
                        "CHECK constraint added — new writes must satisfy it"));
                } else if (change.right() == null) {
                    notes.add(note(rules.severity(RuleKey.STRUCT_FIELD_CHECK_REMOVED), "check",
                        "CHECK constraint removed"));
                } else {
                    notes.add(note(rules.severity(RuleKey.STRUCT_FIELD_CHECK_CHANGED), "check",
                        "CHECK constraint changed"));
                }
            }
            case "dropped" -> {
                if (change.left() == null) {
                    notes.add(note(rules.severity(RuleKey.STRUCT_FIELD_DROPPED_ADDED), "dropped",
                        "Field marked as DROPPED (soft-deprecated)"));
                } else {
                    notes.add(note(rules.severity(RuleKey.STRUCT_FIELD_DROPPED_REMOVED), "dropped",
                        "DROPPED marker removed — field is writable again"));
                }
            }
            case "doc" ->
                notes.add(note(rules.severity(RuleKey.STRUCT_FIELD_DOC_CHANGED), "doc",
                    "Documentation comment changed"));
            case "distribute" ->
                notes.add(note(rules.severity(RuleKey.STRUCT_FIELD_DISTRIBUTE_CHANGED), "distribute",
                    "Distribution keys changed"));
            case "timestamp" ->
                notes.add(note(rules.severity(RuleKey.STRUCT_FIELD_TIMESTAMP_CHANGED), "timestamp",
                    "Timestamp field changed"));
            default -> {
                if (change.aspect().startsWith("constraint:")) {
                    String name = change.aspect().substring("constraint:".length());
                    if (change.left() == null) {
                        notes.add(note(rules.severity(RuleKey.STRUCT_FIELD_CONSTRAINT_ADDED),
                            change.aspect(), "Constraint '" + name + "' added"));
                    } else if (change.right() == null) {
                        notes.add(note(rules.severity(RuleKey.STRUCT_FIELD_CONSTRAINT_REMOVED),
                            change.aspect(), "Constraint '" + name + "' removed"));
                    } else {
                        notes.add(note(rules.severity(RuleKey.STRUCT_FIELD_CONSTRAINT_CHANGED),
                            change.aspect(), "Constraint '" + name + "' changed"));
                    }
                } else {
                    notes.add(note(DiffSeverity.INFO, change.aspect(),
                        "'" + change.aspect() + "' changed"));
                }
            }
        }
    }

    // ── Enum ──────────────────────────────────────────────────────────────────────────────

    private static void enrichEnum(DeclDiff.EnumDiff diff, List<SemanticNote> parentNotes, RuleSet rules) {
        for (FieldChange change : diff.baseChanges()) {
            if ("type".equals(change.aspect())) {
                String from = describeType(change.left());
                String to   = describeType(change.right());
                parentNotes.add(note(rules.severity(RuleKey.ENUM_BASE_TYPE_CHANGED), "type",
                    "Enum underlying type changed: " + from + " → " + to
                    + " — changes wire encoding width, breaking existing readers"));
            }
        }

        for (MemberDiff<EnumSymbolDecl> sd : diff.symbols()) {
            switch (sd.kind()) {
                case LEFT_ONLY ->
                    sd.notes().add(note(rules.severity(RuleKey.ENUM_SYMBOL_REMOVED), "symbol",
                        "Enum symbol '" + sd.left().name().name() + "' removed"));
                case RIGHT_ONLY ->
                    sd.notes().add(note(rules.severity(RuleKey.ENUM_SYMBOL_ADDED), "symbol",
                        "Enum symbol '" + sd.right().name().name() + "' added — consumers may not handle it"));
                case MODIFIED -> {
                    for (FieldChange change : sd.changes()) {
                        if ("value".equals(change.aspect())) {
                            sd.notes().add(note(rules.severity(RuleKey.ENUM_SYMBOL_VALUE_CHANGED), "value",
                                "Enum symbol '" + sd.left().name().name() + "' value changed"));
                        } else if ("doc".equals(change.aspect())) {
                            sd.notes().add(note(rules.severity(RuleKey.ENUM_SYMBOL_DOC_CHANGED), "doc",
                                "Documentation comment changed"));
                        }
                        // other aspects not currently produced for enum symbols
                    }
                }
                case UNCHANGED -> { }
            }
        }
    }

    // ── Union ─────────────────────────────────────────────────────────────────────────────

    private static void enrichUnion(DeclDiff.UnionDiff diff, RuleSet rules) {
        for (MemberDiff<UnionMemberDecl> md : diff.members()) {
            switch (md.kind()) {
                case LEFT_ONLY ->
                    md.notes().add(note(rules.severity(RuleKey.UNION_MEMBER_REMOVED), "member",
                        "Union member '" + md.left().name().name() + "' removed"));
                case RIGHT_ONLY ->
                    md.notes().add(note(rules.severity(RuleKey.UNION_MEMBER_ADDED), "member",
                        "Union member '" + md.right().name().name() + "' added — consumers may not handle it"));
                case MODIFIED -> {
                    for (FieldChange change : md.changes()) {
                        if ("type".equals(change.aspect())) {
                            String from = describeType(change.left());
                            String to   = describeType(change.right());
                            md.notes().add(note(rules.severity(RuleKey.UNION_MEMBER_TYPE_CHANGED), "type",
                                "Union member '" + md.left().name().name() + "' type changed: "
                                + from + " → " + to + " — member type is immutable"));
                        } else if ("doc".equals(change.aspect())) {
                            md.notes().add(note(rules.severity(RuleKey.UNION_MEMBER_DOC_CHANGED), "doc",
                                "Documentation comment changed"));
                        }
                        // other aspects not currently produced for union members
                    }
                }
                case UNCHANGED -> { }
            }
        }
    }

    // ── Scalar ─────────────────────────────────────────────────────────────────────────────

    private static void enrichScalar(DeclDiff.ScalarDiff diff, List<SemanticNote> parentNotes, RuleSet rules) {
        for (FieldChange change : diff.changes()) {
            if ("type".equals(change.aspect())) {
                String left  = describeType(change.left());
                String right = describeType(change.right());
                parentNotes.add(note(rules.severity(RuleKey.SCALAR_TYPE_CHANGED), "type",
                    "Scalar underlying type changed: " + left + " → " + right));
            } else if ("doc".equals(change.aspect())) {
                parentNotes.add(note(rules.severity(RuleKey.SCALAR_DOC_CHANGED), "doc",
                    "Documentation comment changed"));
            } else {
                parentNotes.add(note(DiffSeverity.INFO, change.aspect(),
                    "'" + change.aspect() + "' changed"));
            }
        }
    }

    // ── Derived ─────────────────────────────────────────────────────────────────────────────

    private static void enrichDerived(DeclDiff.DerivedDiff diff, List<SemanticNote> parentNotes, RuleSet rules) {
        for (FieldChange change : diff.changes()) {
            if ("target".equals(change.aspect())) {
                String from = describeType(change.left());
                String to   = describeType(change.right());
                parentNotes.add(note(rules.severity(RuleKey.DERIVED_BASE_CHANGED), "target",
                    "Derived type base changed: " + from + " → " + to));
            } else if ("doc".equals(change.aspect())) {
                parentNotes.add(note(rules.severity(RuleKey.DERIVED_DOC_CHANGED), "doc",
                    "Documentation comment changed"));
            } else {
                parentNotes.add(note(DiffSeverity.INFO, change.aspect(),
                    "'" + change.aspect() + "' changed"));
            }
        }
    }

    // ── Stream ─────────────────────────────────────────────────────────────────────────────

    private static void enrichStream(DeclDiff.StreamDiff diff, RuleSet rules) {
        for (MemberDiff<StreamMemberDecl> md : diff.members()) {
            switch (md.kind()) {
                case LEFT_ONLY ->
                    md.notes().add(note(rules.severity(RuleKey.STREAM_MEMBER_REMOVED), "member",
                        "Stream message type '" + md.left().name().name() + "' removed"));
                case RIGHT_ONLY ->
                    md.notes().add(note(rules.severity(RuleKey.STREAM_MEMBER_ADDED), "member",
                        "New stream message type '" + md.right().name().name() + "' added"));
                case MODIFIED -> {
                    TypeDecl leftDecl  = md.left().memberDecl();
                    TypeDecl rightDecl = md.right().memberDecl();
                    DeclDiff inner = AstDiff.diffTypeDecl(leftDecl, rightDecl);
                    if (inner instanceof DeclDiff.KindChangeDiff) {
                        md.notes().add(note(rules.severity(RuleKey.STREAM_MEMBER_KIND_CHANGED), "member",
                            "Stream member '" + md.left().name().name() + "' type kind changed — all data is incompatible"));
                    } else if (inner instanceof DeclDiff.StructDiff sd && !sd.fields().isEmpty()) {
                        enrichStruct(sd, rules);
                        md.notes().add(note(rules.severity(RuleKey.STREAM_MEMBER_STRUCT_CHANGED), "member",
                            "Stream message type '" + md.left().name().name() + "' struct changed — see field diff"));
                    } else {
                        md.notes().add(note(rules.severity(RuleKey.STREAM_MEMBER_CHANGED), "member",
                            "Stream message type '" + md.left().name().name() + "' changed"));
                    }
                }
                case UNCHANGED -> { }
            }
        }
    }

    // ── Context ────────────────────────────────────────────────────────────────────────────

    private static void enrichContext(DeclDiff.ContextDiff diff, List<SemanticNote> parentNotes, RuleSet rules) {
        for (FieldChange change : diff.changes()) {
            if ("doc".equals(change.aspect())) {
                parentNotes.add(note(rules.severity(RuleKey.CONTEXT_DOC_CHANGED), "doc",
                    "Documentation comment changed"));
            } else {
                parentNotes.add(note(rules.severity(RuleKey.CONTEXT_CHANGED), change.aspect(),
                    "Context '" + change.aspect() + "' changed"));
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static <T extends DeclFragment> boolean hasFragment(
        List<DeclFragment> frags, Class<T> cls
    ) {
        return frags.stream().anyMatch(cls::isInstance);
    }

    private static SemanticNote note(DiffSeverity severity, String aspect, String message) {
        return new SemanticNote(severity, aspect, message);
    }

    private static String describeType(Object node) {
        if (node instanceof PrimitiveTypeNode p) {
            String base = p.kind().name();
            if (p.hasLength())                    return base + "(" + p.length() + ")";
            if (p.hasPrecision() && p.hasScale()) return base + "(" + p.precision() + "," + p.scale() + ")";
            if (p.hasPrecision())                 return base + "(" + p.precision() + ")";
            return base;
        }
        if (node instanceof ComplexTypeNode c) return c.name().fullName();
        if (node instanceof ListTypeNode)     return "LIST<...>";
        if (node instanceof MapTypeNode)      return "MAP<...>";
        if (node == null) return "(none)";
        return node.getClass().getSimpleName();
    }

    private static String kindLabel(TypeKindDecl decl) {
        return switch (decl) {
            case StructDecl      ignored -> "STRUCT";
            case EnumDecl        ignored -> "ENUM";
            case UnionDecl       ignored -> "UNION";
            case ScalarDecl      ignored -> "SCALAR";
            case DerivedTypeDecl ignored -> "DERIVED";
        };
    }
}
