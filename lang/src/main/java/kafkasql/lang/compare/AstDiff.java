package kafkasql.lang.compare;

import kafkasql.lang.syntax.ast.AstListNode;
import kafkasql.lang.syntax.ast.AstNode;
import kafkasql.lang.syntax.ast.Script;
import kafkasql.lang.syntax.ast.constExpr.*;
import kafkasql.lang.syntax.ast.decl.*;
import kafkasql.lang.syntax.ast.expr.*;
import kafkasql.lang.syntax.ast.fragment.*;
import kafkasql.lang.syntax.ast.literal.*;
import kafkasql.lang.syntax.ast.misc.Identifier;
import kafkasql.lang.syntax.ast.misc.Include;
import kafkasql.lang.syntax.ast.misc.VersionPragma;
import kafkasql.lang.syntax.ast.stmt.*;
import kafkasql.lang.syntax.ast.type.*;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * Produces a {@link ScriptDiff} by performing a <em>syntactic</em> comparison of two
 * {@link Script}s (or statement lists).
 *
 * <h2>Matching strategy</h2>
 * <ul>
 *   <li>{@link CreateStmt} — matched <em>semantically</em> by declaration kind + name,
 *       so reordering is transparent.</li>
 *   <li>All other statements — matched <em>positionally</em> within each statement
 *       type, which preserves order sensitivity for executable statements.</li>
 * </ul>
 *
 * <h2>Comparison depth</h2>
 * All comparisons are purely syntactic — no symbol resolution required.  The
 * resulting {@link ScriptDiff} can then be enriched with semantic severity
 * information via {@link SemanticEnricher#enrich(ScriptDiff)}.
 */
public final class AstDiff {

    private AstDiff() {}

    // =========================================================================
    // Entry points
    // =========================================================================

    /** Compare two complete scripts — includes version pragma and includes. */
    public static ScriptDiff compare(Script left, Script right) {
        Optional<FieldChange> versionDiff = diffVersion(left.version(), right.version());
        List<MemberDiff<Include>> includeDiffs = diffIncludes(left.includes(), right.includes());
        List<StmtDiff> stmtDiffs = diffStatements(left.statements(), right.statements());
        return new ScriptDiff(versionDiff, includeDiffs, List.copyOf(stmtDiffs));
    }

    /** Compare two statement lists directly (useful for unit tests). */
    public static ScriptDiff compare(AstListNode<Stmt> left, AstListNode<Stmt> right) {
        List<StmtDiff> stmtDiffs = diffStatements(left, right);
        return new ScriptDiff(Optional.empty(), List.of(), List.copyOf(stmtDiffs));
    }
    // =========================================================================
    // Version pragma + include diff
    // =========================================================================

    private static Optional<FieldChange> diffVersion(
        Optional<VersionPragma> left,
        Optional<VersionPragma> right
    ) {
        if (left.isEmpty() && right.isEmpty()) return Optional.empty();
        if (left.isEmpty()) return Optional.of(new FieldChange("version", null, right.get()));
        if (right.isEmpty()) return Optional.of(new FieldChange("version", left.get(), null));
        int lv = left.get().version(), rv = right.get().version();
        if (lv == rv) return Optional.empty();
        return Optional.of(new FieldChange("version", left.get(), right.get()));
    }

    private static List<MemberDiff<Include>> diffIncludes(
        List<Include> left,
        List<Include> right
    ) {
        // Match includes by path (order-insensitive within a script)
        Map<String, Include> leftMap  = new LinkedHashMap<>();
        Map<String, Include> rightMap = new LinkedHashMap<>();
        for (Include i : left)  leftMap.put(i.path(), i);
        for (Include i : right) rightMap.put(i.path(), i);

        Set<String> allPaths = new LinkedHashSet<>(leftMap.keySet());
        allPaths.addAll(rightMap.keySet());

        List<MemberDiff<Include>> diffs = new ArrayList<>();
        for (String path : allPaths) {
            Include l = leftMap.get(path);
            Include r = rightMap.get(path);
            if      (l == null) diffs.add(MemberDiff.rightOnly(r));
            else if (r == null) diffs.add(MemberDiff.leftOnly(l));
            else                diffs.add(MemberDiff.unchanged(l, r));
        }
        return diffs;
    }

    // =========================================================================
    // Statement diff
    // =========================================================================

    private static List<StmtDiff> diffStatements(AstListNode<Stmt> left, AstListNode<Stmt> right) {
        List<StmtDiff> diffs = new ArrayList<>();

        // --- 1. Partition CREATE vs everything else ---
        Map<String, CreateStmt> leftCreates  = new LinkedHashMap<>();
        Map<String, CreateStmt> rightCreates = new LinkedHashMap<>();
        List<Stmt> leftOthers  = new ArrayList<>();
        List<Stmt> rightOthers = new ArrayList<>();

        partition(left,  leftCreates,  leftOthers);
        partition(right, rightCreates, rightOthers);

        // --- 2. Match CREATE statements by key (left order, then right-only) ---
        Set<String> allKeys = new LinkedHashSet<>(leftCreates.keySet());
        allKeys.addAll(rightCreates.keySet());
        for (String key : allKeys) {
            diffs.add(diffCreate(leftCreates.get(key), rightCreates.get(key)));
        }

        // --- 3. Match other statements positionally within each type ---
        matchPositional(leftOthers, rightOthers, diffs);

        return diffs;
    }

    // =========================================================================
    // Partitioning + positional matching
    // =========================================================================

    private static void partition(
        AstListNode<Stmt> stmts,
        Map<String, CreateStmt> creates,
        List<Stmt> others
    ) {
        for (Stmt s : stmts) {
            if (s instanceof CreateStmt c) creates.put(createKey(c), c);
            else others.add(s);
        }
    }

    private static String createKey(CreateStmt c) {
        String kind = switch (c.decl()) {
            case TypeDecl    __ -> "TYPE";
            case StreamDecl  __ -> "STREAM";
            case ContextDecl __ -> "CONTEXT";
        };
        return kind + ":" + c.decl().name().name();
    }

    private static void matchPositional(List<Stmt> left, List<Stmt> right, List<StmtDiff> diffs) {
        // Group by runtime class, then zip within each class preserving order.
        Map<Class<?>, Deque<Stmt>> leftByClass  = groupByClass(left);
        Map<Class<?>, Deque<Stmt>> rightByClass = groupByClass(right);

        Set<Class<?>> allClasses = new LinkedHashSet<>(leftByClass.keySet());
        allClasses.addAll(rightByClass.keySet());

        for (Class<?> cls : allClasses) {
            Deque<Stmt> lq = leftByClass.getOrDefault(cls,  new ArrayDeque<>());
            Deque<Stmt> rq = rightByClass.getOrDefault(cls, new ArrayDeque<>());
            while (!lq.isEmpty() || !rq.isEmpty()) {
                Stmt l = lq.isEmpty() ? null : lq.poll();
                Stmt r = rq.isEmpty() ? null : rq.poll();
                diffs.add(diffGenericStmt(l, r));
            }
        }
    }

    private static Map<Class<?>, Deque<Stmt>> groupByClass(List<Stmt> stmts) {
        Map<Class<?>, Deque<Stmt>> map = new LinkedHashMap<>();
        for (Stmt s : stmts) map.computeIfAbsent(s.getClass(), __ -> new ArrayDeque<>()).add(s);
        return map;
    }

    // =========================================================================
    // CREATE statement diff
    // =========================================================================

    private static StmtDiff.CreateDiff diffCreate(CreateStmt left, CreateStmt right) {
        if (left  == null) return StmtDiff.createRightOnly(right);
        if (right == null) return StmtDiff.createLeftOnly(left);

        DeclDiff dd = diffDecl(left.decl(), right.decl());
        return new StmtDiff.CreateDiff(dd.kind(), left, right, dd, new ArrayList<>());
    }

    // =========================================================================
    // Declaration diff
    // =========================================================================

    private static DeclDiff diffDecl(Decl left, Decl right) {
        if (left instanceof TypeDecl    lt && right instanceof TypeDecl    rt) return diffTypeDecl(lt, rt);
        if (left instanceof StreamDecl  ls && right instanceof StreamDecl  rs) return diffStreamDecl(ls, rs);
        if (left instanceof ContextDecl lc && right instanceof ContextDecl rc) return diffContextDecl(lc, rc);
        throw new IllegalStateException("Declaration class mismatch: " + left.getClass() + " vs " + right.getClass());
    }

    static DeclDiff diffTypeDecl(TypeDecl left, TypeDecl right) {
        TypeKindDecl lk = left.kind(), rk = right.kind();

        // Kind itself changed (e.g. STRUCT → ENUM) — incomparable
        if (!lk.getClass().equals(rk.getClass())) {
            return new DeclDiff.KindChangeDiff(lk, rk);
        }

        if (lk instanceof StructDecl      ls && rk instanceof StructDecl      rs) return diffStruct(ls, rs);
        if (lk instanceof EnumDecl        le && rk instanceof EnumDecl        re) return diffEnum(le, re, left, right);
        if (lk instanceof UnionDecl       lu && rk instanceof UnionDecl       ru) return diffUnion(lu, ru);
        if (lk instanceof ScalarDecl      ls && rk instanceof ScalarDecl      rs) return diffScalar(ls, rs, left, right);
        if (lk instanceof DerivedTypeDecl ld && rk instanceof DerivedTypeDecl rd) return diffDerived(ld, rd);
        throw new IllegalStateException("Unhandled kind combination: " + lk.getClass());
    }

    // ── Struct ────────────────────────────────────────────────────────────────

    private static DeclDiff.StructDiff diffStruct(StructDecl left, StructDecl right) {
        List<MemberDiff<StructFieldDecl>> fieldDiffs = matchAndDiff(
            left.fields(), right.fields(),
            f -> f.name().name(),
            AstDiff::diffField
        );
        DiffKind kind = anyChanged(fieldDiffs) ? DiffKind.MODIFIED : DiffKind.UNCHANGED;
        return new DeclDiff.StructDiff(kind, List.copyOf(fieldDiffs));
    }

    private static MemberDiff<StructFieldDecl> diffField(StructFieldDecl left, StructFieldDecl right) {
        List<FieldChange> changes = new ArrayList<>();

        if (!typeNodesEqual(left.type(), right.type())) {
            changes.add(new FieldChange("type", left.type(), right.type()));
        }

        boolean leftNullable  = left.nullable().isPresent();
        boolean rightNullable = right.nullable().isPresent();
        if (leftNullable != rightNullable) {
            changes.add(new FieldChange("nullable",
                leftNullable  ? left.nullable().get()  : null,
                rightNullable ? right.nullable().get() : null));
        }

        compareFragmentsByType(left.fragments(), right.fragments(), changes);

        return changes.isEmpty()
            ? MemberDiff.unchanged(left, right)
            : MemberDiff.modified(left, right, changes);
    }

    // ── Enum ──────────────────────────────────────────────────────────────────

    private static DeclDiff.EnumDiff diffEnum(
        EnumDecl left, EnumDecl right,
        TypeDecl leftDecl, TypeDecl rightDecl
    ) {
        // Base type change
        List<FieldChange> baseChanges = new ArrayList<>();
        boolean leftHasBase  = left.type().isPresent();
        boolean rightHasBase = right.type().isPresent();
        if (leftHasBase != rightHasBase
                || (leftHasBase && !typeNodesEqual(left.type().get(), right.type().get()))) {
            baseChanges.add(new FieldChange("base-type",
                leftHasBase  ? left.type().get()  : null,
                rightHasBase ? right.type().get() : null));
        }

        List<MemberDiff<EnumSymbolDecl>> symbolDiffs = matchAndDiff(
            left.symbols(), right.symbols(),
            s -> s.name().name(),
            AstDiff::diffEnumSymbol
        );

        boolean changed = !baseChanges.isEmpty() || anyChanged(symbolDiffs);
        return new DeclDiff.EnumDiff(
            changed ? DiffKind.MODIFIED : DiffKind.UNCHANGED,
            List.copyOf(baseChanges),
            List.copyOf(symbolDiffs)
        );
    }

    private static MemberDiff<EnumSymbolDecl> diffEnumSymbol(EnumSymbolDecl left, EnumSymbolDecl right) {
        List<FieldChange> changes = new ArrayList<>();

        if (!constExprEqual(left.value(), right.value())) {
            changes.add(new FieldChange("value", left, right));
        }
        compareFragmentsByType(left.fragments(), right.fragments(), changes);

        return changes.isEmpty()
            ? MemberDiff.unchanged(left, right)
            : MemberDiff.modified(left, right, changes);
    }

    // ── Union ─────────────────────────────────────────────────────────────────

    private static DeclDiff.UnionDiff diffUnion(UnionDecl left, UnionDecl right) {
        List<MemberDiff<UnionMemberDecl>> memberDiffs = matchAndDiff(
            left.members(), right.members(),
            m -> m.name().name(),
            AstDiff::diffUnionMember
        );
        DiffKind kind = anyChanged(memberDiffs) ? DiffKind.MODIFIED : DiffKind.UNCHANGED;
        return new DeclDiff.UnionDiff(kind, List.copyOf(memberDiffs));
    }

    private static MemberDiff<UnionMemberDecl> diffUnionMember(UnionMemberDecl left, UnionMemberDecl right) {
        List<FieldChange> changes = new ArrayList<>();

        if (!typeNodesEqual(left.type(), right.type())) {
            changes.add(new FieldChange("type", left.type(), right.type()));
        }
        compareFragmentsByType(left.fragments(), right.fragments(), changes);

        return changes.isEmpty()
            ? MemberDiff.unchanged(left, right)
            : MemberDiff.modified(left, right, changes);
    }

    // ── Scalar ────────────────────────────────────────────────────────────────

    private static DeclDiff.ScalarDiff diffScalar(
        ScalarDecl left, ScalarDecl right,
        TypeDecl leftDecl, TypeDecl rightDecl
    ) {
        List<FieldChange> changes = new ArrayList<>();

        if (!typeNodesEqual(left.type(), right.type())) {
            changes.add(new FieldChange("type", left.type(), right.type()));
        }
        // Fragments live on the TypeDecl wrapper for scalars (DEFAULT, CHECK, DOC)
        compareFragmentsByType(leftDecl.fragments(), rightDecl.fragments(), changes);

        DiffKind kind = changes.isEmpty() ? DiffKind.UNCHANGED : DiffKind.MODIFIED;
        return new DeclDiff.ScalarDiff(kind, List.copyOf(changes));
    }

    // ── Derived ───────────────────────────────────────────────────────────────

    private static DeclDiff.DerivedDiff diffDerived(DerivedTypeDecl left, DerivedTypeDecl right) {
        List<FieldChange> changes = new ArrayList<>();
        if (!typeNodesEqual(left.target(), right.target())) {
            changes.add(new FieldChange("target", left.target(), right.target()));
        }
        DiffKind kind = changes.isEmpty() ? DiffKind.UNCHANGED : DiffKind.MODIFIED;
        return new DeclDiff.DerivedDiff(kind, List.copyOf(changes));
    }

    // ── Stream ────────────────────────────────────────────────────────────────

    private static DeclDiff.StreamDiff diffStreamDecl(StreamDecl left, StreamDecl right) {
        List<MemberDiff<StreamMemberDecl>> memberDiffs = matchAndDiff(
            left.streamTypes(), right.streamTypes(),
            m -> m.name().name(),
            AstDiff::diffStreamMember
        );
        DiffKind kind = anyChanged(memberDiffs) ? DiffKind.MODIFIED : DiffKind.UNCHANGED;
        return new DeclDiff.StreamDiff(kind, List.copyOf(memberDiffs));
    }

    private static MemberDiff<StreamMemberDecl> diffStreamMember(StreamMemberDecl left, StreamMemberDecl right) {
        // Compare the inline TypeDecl.  Report a single "type-decl" FieldChange
        // pointing to the TypeDecl nodes; detailed member diffs are in the nested diff.
        DeclDiff inner = diffTypeDecl(left.memberDecl(), right.memberDecl());
        if (inner.kind() == DiffKind.UNCHANGED) {
            return MemberDiff.unchanged(left, right);
        }
        List<FieldChange> changes = List.of(
            new FieldChange("type-decl", left.memberDecl(), right.memberDecl())
        );
        return MemberDiff.modified(left, right, changes);
    }

    // ── Context ───────────────────────────────────────────────────────────────

    private static DeclDiff.ContextDiff diffContextDecl(ContextDecl left, ContextDecl right) {
        List<FieldChange> changes = new ArrayList<>();
        compareFragmentsByType(left.fragments(), right.fragments(), changes);
        DiffKind kind = changes.isEmpty() ? DiffKind.UNCHANGED : DiffKind.MODIFIED;
        return new DeclDiff.ContextDiff(kind, List.copyOf(changes));
    }

    // =========================================================================
    // Generic (non-CREATE) statement diff — shallow structural equality
    // =========================================================================

    private static StmtDiff diffGenericStmt(Stmt left, Stmt right) {
        Stmt rep = left != null ? left : right;

        if (rep instanceof AlterStmt) {
            return diffAlter((AlterStmt) left, (AlterStmt) right);
        }
        if (rep instanceof DropStmt) {
            return diffDrop((DropStmt) left, (DropStmt) right);
        }
        if (rep instanceof UseStmt) {
            return diffUse((UseStmt) left, (UseStmt) right);
        }
        if (rep instanceof ReadStmt) {
            return diffRead((ReadStmt) left, (ReadStmt) right);
        }
        if (rep instanceof WriteStmt) {
            return diffWrite((WriteStmt) left, (WriteStmt) right);
        }
        if (rep instanceof ShowStmt) {
            return diffShow((ShowStmt) left, (ShowStmt) right);
        }
        if (rep instanceof ExplainStmt) {
            return diffExplain((ExplainStmt) left, (ExplainStmt) right);
        }
        throw new IllegalStateException("Unhandled Stmt type: " + rep.getClass());
    }

    private static StmtDiff.AlterDiff diffAlter(AlterStmt left, AlterStmt right) {
        if (left  == null) return new StmtDiff.AlterDiff(DiffKind.RIGHT_ONLY, null,  right, new ArrayList<>());
        if (right == null) return new StmtDiff.AlterDiff(DiffKind.LEFT_ONLY,  left,  null,  new ArrayList<>());
        boolean sameTarget = left.target().fullName().equals(right.target().fullName());
        boolean sameClass  = left.getClass().equals(right.getClass());
        DiffKind kind = (sameTarget && sameClass) ? DiffKind.UNCHANGED : DiffKind.MODIFIED;
        return new StmtDiff.AlterDiff(kind, left, right, new ArrayList<>());
    }

    private static StmtDiff.DropDiff diffDrop(DropStmt left, DropStmt right) {
        if (left  == null) return new StmtDiff.DropDiff(DiffKind.RIGHT_ONLY, null,  right, new ArrayList<>());
        if (right == null) return new StmtDiff.DropDiff(DiffKind.LEFT_ONLY,  left,  null,  new ArrayList<>());
        boolean sameTarget = left.target().fullName().equals(right.target().fullName());
        boolean sameClass  = left.getClass().equals(right.getClass());
        DiffKind kind = (sameTarget && sameClass) ? DiffKind.UNCHANGED : DiffKind.MODIFIED;
        return new StmtDiff.DropDiff(kind, left, right, new ArrayList<>());
    }

    private static StmtDiff.UseDiff diffUse(UseStmt left, UseStmt right) {
        if (left  == null) return new StmtDiff.UseDiff(DiffKind.RIGHT_ONLY, null,  right, new ArrayList<>());
        if (right == null) return new StmtDiff.UseDiff(DiffKind.LEFT_ONLY,  left,  null,  new ArrayList<>());
        DiffKind kind = DiffKind.UNCHANGED; // USE stmts are positionally matched; treat as changed if targets differ
        return new StmtDiff.UseDiff(kind, left, right, new ArrayList<>());
    }

    private static StmtDiff.ReadDiff diffRead(ReadStmt left, ReadStmt right) {
        if (left  == null) return new StmtDiff.ReadDiff(DiffKind.RIGHT_ONLY, null, right, new ArrayList<>());
        if (right == null) return new StmtDiff.ReadDiff(DiffKind.LEFT_ONLY, left,  null,  new ArrayList<>());
        boolean same = left.stream().fullName().equals(right.stream().fullName());
        return new StmtDiff.ReadDiff(same ? DiffKind.UNCHANGED : DiffKind.MODIFIED, left, right, new ArrayList<>());
    }

    private static StmtDiff.WriteDiff diffWrite(WriteStmt left, WriteStmt right) {
        if (left  == null) return new StmtDiff.WriteDiff(DiffKind.RIGHT_ONLY, null, right, new ArrayList<>());
        if (right == null) return new StmtDiff.WriteDiff(DiffKind.LEFT_ONLY, left,  null, new ArrayList<>());
        boolean same = left.stream().fullName().equals(right.stream().fullName());
        return new StmtDiff.WriteDiff(same ? DiffKind.UNCHANGED : DiffKind.MODIFIED, left, right, new ArrayList<>());
    }

    private static StmtDiff.ShowDiff diffShow(ShowStmt left, ShowStmt right) {
        if (left  == null) return new StmtDiff.ShowDiff(DiffKind.RIGHT_ONLY, null, right, new ArrayList<>());
        if (right == null) return new StmtDiff.ShowDiff(DiffKind.LEFT_ONLY, left,  null, new ArrayList<>());
        java.util.Optional<String> leftFilter  = left  instanceof ShowContextualStmt l ? l.filter() : java.util.Optional.empty();
        java.util.Optional<String> rightFilter = right instanceof ShowContextualStmt r ? r.filter() : java.util.Optional.empty();
        boolean same = left.target() == right.target()
            && left.getClass().equals(right.getClass())
            && java.util.Objects.equals(leftFilter, rightFilter);
        return new StmtDiff.ShowDiff(same ? DiffKind.UNCHANGED : DiffKind.MODIFIED, left, right, new ArrayList<>());
    }

    private static StmtDiff.ExplainDiff diffExplain(ExplainStmt left, ExplainStmt right) {
        if (left  == null) return new StmtDiff.ExplainDiff(DiffKind.RIGHT_ONLY, null, right, new ArrayList<>());
        if (right == null) return new StmtDiff.ExplainDiff(DiffKind.LEFT_ONLY, left,  null, new ArrayList<>());
        boolean same = left.target().fullName().equals(right.target().fullName());
        return new StmtDiff.ExplainDiff(same ? DiffKind.UNCHANGED : DiffKind.MODIFIED, left, right, new ArrayList<>());
    }

    // =========================================================================
    // Fragment comparison
    // =========================================================================

    private static void compareFragmentsByType(
        List<DeclFragment> left,
        List<DeclFragment> right,
        List<FieldChange> changes
    ) {
        compareFragmentPair(left, right, DocNode.class,       "doc",        changes);
        compareFragmentPair(left, right, DefaultNode.class,   "default",    changes);
        compareFragmentPair(left, right, CheckNode.class,     "check",      changes);
        compareFragmentPair(left, right, DroppedNode.class,   "dropped",    changes);
        compareFragmentPair(left, right, DistributeDecl.class,"distribute", changes);
        compareFragmentPair(left, right, TimestampDecl.class, "timestamp",  changes);
        compareNamedConstraints(left, right, changes);
    }

    private static <T extends DeclFragment> void compareFragmentPair(
        List<DeclFragment> left,
        List<DeclFragment> right,
        Class<T> cls,
        String aspect,
        List<FieldChange> changes
    ) {
        T l = findFragment(left,  cls);
        T r = findFragment(right, cls);
        if (l == null && r == null) return;
        if (l == null || r == null || !fragmentsEqual(l, r)) {
            changes.add(new FieldChange(aspect, l, r));
        }
    }

    private static void compareNamedConstraints(
        List<DeclFragment> left,
        List<DeclFragment> right,
        List<FieldChange> changes
    ) {
        Map<String, ConstraintNode> leftMap  = indexConstraints(left);
        Map<String, ConstraintNode> rightMap = indexConstraints(right);
        Set<String> allNames = new LinkedHashSet<>(leftMap.keySet());
        allNames.addAll(rightMap.keySet());
        for (String name : allNames) {
            ConstraintNode l = leftMap.get(name);
            ConstraintNode r = rightMap.get(name);
            if (l == null || r == null || !fragmentsEqual(l.fragment(), r.fragment())) {
                changes.add(new FieldChange("constraint:" + name, l, r));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends DeclFragment> T findFragment(List<DeclFragment> frags, Class<T> cls) {
        for (DeclFragment f : frags) {
            if (cls.isInstance(f)) return (T) f;
        }
        return null;
    }

    private static Map<String, ConstraintNode> indexConstraints(List<DeclFragment> frags) {
        Map<String, ConstraintNode> map = new LinkedHashMap<>();
        for (DeclFragment f : frags) {
            if (f instanceof ConstraintNode cn) map.put(cn.name().name(), cn);
        }
        return map;
    }

    private static boolean fragmentsEqual(DeclFragment left, DeclFragment right) {
        if (left == null && right == null) return true;
        if (left == null || right == null) return false;
        if (!left.getClass().equals(right.getClass())) return false;
        return switch (left) {
            case DocNode       l -> right instanceof DocNode       r && l.comment().equals(r.comment());
            case DefaultNode   l -> right instanceof DefaultNode   r && literalEqual(l.value(), r.value());
            case CheckNode     l -> right instanceof CheckNode     r && exprStructurallyEqual(l.expr(), r.expr());
            case DroppedNode   l -> true;
            case DistributeDecl l -> right instanceof DistributeDecl r && identifierListEqual(l.keys(), r.keys());
            case TimestampDecl l -> right instanceof TimestampDecl r && l.field().name().equals(r.field().name());
            case ConstraintNode l -> right instanceof ConstraintNode r
                && l.name().name().equals(r.name().name())
                && fragmentsEqual(l.fragment(), r.fragment());
        };
    }

    // =========================================================================
    // TypeNode equality
    // =========================================================================

    static boolean typeNodesEqual(TypeNode left, TypeNode right) {
        if (left == null && right == null) return true;
        if (left == null || right == null) return false;
        if (!left.getClass().equals(right.getClass())) return false;
        return switch (left) {
            case PrimitiveTypeNode l -> right instanceof PrimitiveTypeNode r
                && l.kind() == r.kind()
                && Objects.equals(l.hasLength()   ? l.length()    : null, r.hasLength()   ? r.length()    : null)
                && Objects.equals(l.hasPrecision() ? l.precision() : null, r.hasPrecision() ? r.precision() : null)
                && Objects.equals(l.hasScale()     ? l.scale()     : null, r.hasScale()     ? r.scale()     : null);
            case ComplexTypeNode   l -> right instanceof ComplexTypeNode   r
                && l.name().fullName().equals(r.name().fullName());
            case ListTypeNode      l -> right instanceof ListTypeNode      r
                && typeNodesEqual(l.elementType(), r.elementType());
            case MapTypeNode       l -> right instanceof MapTypeNode       r
                && typeNodesEqual(l.keyType(),  r.keyType())
                && typeNodesEqual(l.valueType(), r.valueType());
        };
    }

    // =========================================================================
    // LiteralNode equality
    // =========================================================================

    private static boolean literalEqual(LiteralNode left, LiteralNode right) {
        if (left == null && right == null) return true;
        if (left == null || right == null) return false;
        if (!left.getClass().equals(right.getClass())) return false;
        return switch (left) {
            case BoolLiteralNode   l -> right instanceof BoolLiteralNode   r && Objects.equals(l.value(), r.value());
            case StringLiteralNode l -> right instanceof StringLiteralNode r && l.value().equals(r.value());
            case NumberLiteralNode l -> right instanceof NumberLiteralNode r && l.text().equals(r.text());
            case BytesLiteralNode  l -> right instanceof BytesLiteralNode  r && l.text().equals(r.text());
            case NullLiteralNode   l -> true;
            case EnumLiteralNode   l -> right instanceof EnumLiteralNode   r
                && l.enumName().fullName().equals(r.enumName().fullName())
                && l.symbol().name().equals(r.symbol().name());
            case UnionLiteralNode  l -> right instanceof UnionLiteralNode  r
                && l.unionName().fullName().equals(r.unionName().fullName())
                && l.memberName().name().equals(r.memberName().name())
                && literalEqual(l.value(), r.value());
            // Complex composite literals: compared element-by-element
            case StructLiteralNode l -> right instanceof StructLiteralNode r
                && l.fields().size() == r.fields().size()
                && IntStream.range(0, l.fields().size()).allMatch(i -> {
                    var lf = l.fields().get(i);
                    var rf = r.fields().get(i);
                    return lf.name().name().equals(rf.name().name()) && literalEqual(lf.value(), rf.value());
                });
            case ListLiteralNode   l -> right instanceof ListLiteralNode r
                && l.elements().size() == r.elements().size()
                && IntStream.range(0, l.elements().size()).allMatch(i ->
                    literalEqual(l.elements().get(i), r.elements().get(i)));
            case MapLiteralNode    l -> right instanceof MapLiteralNode r
                && l.entries().size() == r.entries().size()
                && IntStream.range(0, l.entries().size()).allMatch(i -> {
                    var le = l.entries().get(i);
                    var re = r.entries().get(i);
                    return literalEqual(le.key(), re.key()) && literalEqual(le.value(), re.value());
                });
        };
    }

    // =========================================================================
    // ConstExpr equality (enum symbol values)
    // =========================================================================

    private static boolean constExprEqual(ConstExpr left, ConstExpr right) {
        if (left == null && right == null) return true;
        if (left == null || right == null) return false;
        if (!left.getClass().equals(right.getClass())) return false;
        return switch (left) {
            case ConstLiteralExpr   l -> right instanceof ConstLiteralExpr   r && l.text().equals(r.text());
            case ConstSymbolRefExpr l -> right instanceof ConstSymbolRefExpr r && l.name().name().equals(r.name().name());
            case ConstBinaryExpr    l -> right instanceof ConstBinaryExpr    r
                && l.op() == r.op()
                && constExprEqual(l.left(), r.left())
                && constExprEqual(l.right(), r.right());
            case ConstParenExpr     l -> right instanceof ConstParenExpr     r && constExprEqual(l.inner(), r.inner());
        };
    }

    // =========================================================================
    // Expr structural equality (used for CHECK constraints)
    // =========================================================================

    private static boolean exprStructurallyEqual(Expr left, Expr right) {
        if (left == null && right == null) return true;
        if (left == null || right == null) return false;
        if (!left.getClass().equals(right.getClass())) return false;
        return switch (left) {
            case IdentifierExpr l -> right instanceof IdentifierExpr r && l.name().name().equals(r.name().name());
            case LiteralExpr    l -> right instanceof LiteralExpr    r && literalEqual(l.literal(), r.literal());
            case PrefixExpr     l -> right instanceof PrefixExpr     r && l.op() == r.op()
                && exprStructurallyEqual(l.expr(), r.expr());
            case PostfixExpr    l -> right instanceof PostfixExpr    r && l.op() == r.op()
                && exprStructurallyEqual(l.expr(), r.expr());
            case InfixExpr      l -> right instanceof InfixExpr      r && l.op() == r.op()
                && exprStructurallyEqual(l.left(),  r.left())
                && exprStructurallyEqual(l.right(), r.right());
            case TrifixExpr     l -> right instanceof TrifixExpr     r && l.op() == r.op()
                && exprStructurallyEqual(l.left(),   r.left())
                && exprStructurallyEqual(l.middle(), r.middle())
                && exprStructurallyEqual(l.right(),  r.right());
            case ParenExpr      l -> right instanceof ParenExpr      r && exprStructurallyEqual(l.inner(),  r.inner());
            case MemberExpr     l -> right instanceof MemberExpr     r
                && l.name().name().equals(r.name().name())
                && exprStructurallyEqual(l.target(), r.target());
            case IndexExpr      l -> right instanceof IndexExpr      r
                && exprStructurallyEqual(l.target(), r.target())
                && exprStructurallyEqual(l.index(),  r.index());
        };
    }

    // =========================================================================
    // Generic matching helper
    // =========================================================================

    private static <T extends AstNode> List<MemberDiff<T>> matchAndDiff(
        List<T> leftList,
        List<T> rightList,
        Function<T, String> nameOf,
        BiFunction<T, T, MemberDiff<T>> differ
    ) {
        Map<String, T> leftMap  = index(leftList,  nameOf);
        Map<String, T> rightMap = index(rightList, nameOf);

        Set<String> allNames = new LinkedHashSet<>(leftMap.keySet());
        allNames.addAll(rightMap.keySet());

        List<MemberDiff<T>> diffs = new ArrayList<>();
        for (String name : allNames) {
            T l = leftMap.get(name);
            T r = rightMap.get(name);
            if      (l == null) diffs.add(MemberDiff.rightOnly(r));
            else if (r == null) diffs.add(MemberDiff.leftOnly(l));
            else                diffs.add(differ.apply(l, r));
        }
        return diffs;
    }

    private static <T> Map<String, T> index(List<T> items, Function<T, String> keyFn) {
        Map<String, T> map = new LinkedHashMap<>();
        for (T item : items) map.put(keyFn.apply(item), item);
        return map;
    }

    private static <T extends AstNode> boolean anyChanged(List<MemberDiff<T>> diffs) {
        return diffs.stream().anyMatch(d -> d.kind() != DiffKind.UNCHANGED);
    }

    private static boolean identifierListEqual(List<Identifier> left, List<Identifier> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            if (!left.get(i).name().equals(right.get(i).name())) return false;
        }
        return true;
    }
}
