package kafkasql.lang.semantic.resolve;

import kafkasql.runtime.*;
import kafkasql.runtime.diagnostics.DiagnosticCode;
import kafkasql.runtime.diagnostics.DiagnosticKind;
import kafkasql.runtime.diagnostics.Diagnostics;
import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.semantic.symbol.SymbolTable;
import kafkasql.lang.syntax.ast.Script;
import kafkasql.lang.syntax.ast.decl.ContextDecl;
import kafkasql.lang.syntax.ast.decl.Decl;
import kafkasql.lang.syntax.ast.decl.DerivedTypeDecl;
import kafkasql.lang.syntax.ast.decl.EnumDecl;
import kafkasql.lang.syntax.ast.decl.EnumSymbolDecl;
import kafkasql.lang.syntax.ast.decl.StreamDecl;
import kafkasql.lang.syntax.ast.decl.StreamMemberDecl;
import kafkasql.lang.syntax.ast.decl.StructDecl;
import kafkasql.lang.syntax.ast.decl.StructFieldDecl;
import kafkasql.lang.syntax.ast.decl.TypeDecl;
import kafkasql.lang.syntax.ast.fragment.DeclFragment;
import kafkasql.lang.syntax.ast.fragment.DroppedNode;
import kafkasql.lang.syntax.ast.misc.QName;
import kafkasql.lang.syntax.ast.AstListNode;
import kafkasql.lang.syntax.ast.stmt.AlterStmt;
import kafkasql.lang.syntax.ast.stmt.CursorStmt;
import kafkasql.lang.syntax.ast.stmt.CreateStmt;
import kafkasql.lang.syntax.ast.stmt.DropStmt;
import kafkasql.lang.syntax.ast.stmt.ReadMode;
import kafkasql.lang.syntax.ast.stmt.ReadStmt;
import kafkasql.lang.syntax.ast.stmt.Stmt;
import kafkasql.lang.syntax.ast.stmt.UseStmt;
import kafkasql.lang.syntax.ast.use.ContextUse;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.time.Instant;

public final class DeclResolver {

    private record CursorRef(
        Name context,
        String cursorName
    ) {}

    private DeclResolver() {}

    public static void collectSymbols(
        Script script,
        SymbolTable symbols,
        ContextScope scope,
        Diagnostics diags
    ) {

        for (Stmt stmt : script.statements()) {
            switch (stmt) {
                case UseStmt s -> resolveUseStmt(s, symbols, scope, diags);
                case CreateStmt c -> resolveCreateStmt(c, symbols, scope, diags);
                case AlterStmt a -> resolveAlterStmt(a, symbols, diags);
                case DropStmt d -> resolveDropStmt(d, symbols, diags);
                case CursorStmt cursor -> resolveCursorStmt(cursor, symbols, scope, diags);
                case ReadStmt read -> resolveReadStmt(read, symbols, scope, diags);
                default -> {
                    // ignore other statements
                }
            }
        }
    }

    private static void resolveUseStmt(
        UseStmt stmt,
        SymbolTable symbols,
        ContextScope scope,
        Diagnostics diags
    ) {
        switch (stmt.target()) {
            case ContextUse uc -> {
                // Check for GLOBAL context (empty QName)
                if (uc.qname().isRoot()) {
                    // Return to global/root context
                    scope.set(Name.ROOT);
                    return;
                }
                
                Name fqn = toName(uc.qname());
                Optional<ContextDecl> ctxDecl = symbols.lookupContext(fqn);

                if (ctxDecl.isEmpty()) {
                    diags.error(
                        uc.range(),
                        DiagnosticKind.SEMANTIC,
                        DiagnosticCode.UNKNOWN_CONTEXT,
                        "Unknown context: " + fqn
                    );
                    // keep current scope unchanged on error
                } else {
                    scope.set(fqn);
                }
            }
        }
    }

    private static void resolveCreateStmt(
        CreateStmt stmt,
        SymbolTable symbols,
        ContextScope scope,
        Diagnostics diags
    ) {
        Optional<Name> canonicalFqn = Optional.empty();
        
        // TYPEs and STREAMs must live inside a context (namespace)
        // Only ContextDecl can be created at global scope
        if (stmt.decl() instanceof TypeDecl || stmt.decl() instanceof StreamDecl) {
            canonicalFqn = requireActiveContext(scope, diags, stmt.decl());
            if (canonicalFqn.isEmpty())
                return;
        } else {
            canonicalFqn = anyName(scope, diags, stmt.decl());
        }

        if (duplicate(canonicalFqn.get(), symbols, diags, stmt.range()))
            return;
        
        symbols.register(canonicalFqn.get(), stmt.decl());
    }

    private static void resolveAlterStmt(
        AlterStmt stmt,
        SymbolTable symbols,
        Diagnostics diags
    ) {
        Name target = toName(stmt.target());
        if (!symbols.hasKey(target)) {
            diags.error(
                stmt.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.UNKNOWN_TYPE,
                "Cannot ALTER unknown object: " + target
            );
            return;
        }

        switch (stmt) {
            case AlterStmt.AlterType at -> {
                var typeOpt = symbols.lookupType(target);
                if (typeOpt.isEmpty()) {
                    diags.error(
                        stmt.range(),
                        DiagnosticKind.SEMANTIC,
                        DiagnosticCode.UNKNOWN_TYPE,
                        target + " is not a TYPE"
                    );
                    return;
                }
                applyAlterType(at, typeOpt.get(), target, symbols, diags);
            }
            case AlterStmt.AlterStream as -> {
                if (symbols.lookupStream(target).isEmpty()) {
                    diags.error(
                        stmt.range(),
                        DiagnosticKind.SEMANTIC,
                        DiagnosticCode.UNKNOWN_STREAM,
                        target + " is not a STREAM"
                    );
                }
            }
        }
    }

    private static void applyAlterType(
        AlterStmt.AlterType at,
        TypeDecl existingType,
        Name target,
        SymbolTable symbols,
        Diagnostics diags
    ) {
        switch (at.action()) {
            case AlterStmt.AddField af -> {
                if (!(existingType.kind() instanceof StructDecl struct)) {
                    diags.error(af.range(), DiagnosticKind.SEMANTIC, DiagnosticCode.INVALID_TYPE_REF,
                        "Can only ADD FIELD to STRUCT types");
                    return;
                }
                String newFieldName = af.field().name().name();
                for (StructFieldDecl f : struct.fields()) {
                    if (f.name().name().equals(newFieldName)) {
                        diags.error(af.range(), DiagnosticKind.SEMANTIC, DiagnosticCode.DUPLICATE_DECLARATION,
                            "Field '" + newFieldName + "' already exists");
                        return;
                    }
                }
                AstListNode<StructFieldDecl> newFields = new AstListNode<>(StructFieldDecl.class);
                newFields.addAll(struct.fields());
                newFields.add(af.field());
                StructDecl newStruct = new StructDecl(struct.range(), newFields);
                TypeDecl newTypeDecl = new TypeDecl(
                    existingType.range(), existingType.name(), newStruct, existingType.fragments());
                symbols.replace(target, newTypeDecl);
            }
            case AlterStmt.DropMember dm -> {
                if (!(existingType.kind() instanceof StructDecl struct)) {
                    diags.error(dm.range(), DiagnosticKind.SEMANTIC, DiagnosticCode.INVALID_TYPE_REF,
                        "Can only DROP member from STRUCT types");
                    return;
                }
                String dropFieldName = dm.name().name();
                AstListNode<StructFieldDecl> newFields = new AstListNode<>(StructFieldDecl.class);
                boolean found = false;
                for (StructFieldDecl f : struct.fields()) {
                    if (f.name().name().equals(dropFieldName)) {
                        if (f.fragments().stream().anyMatch(frag -> frag instanceof DroppedNode)) {
                            diags.error(dm.range(), DiagnosticKind.SEMANTIC, DiagnosticCode.INVALID_TYPE_REF,
                                "Field '" + dropFieldName + "' is already dropped");
                            return;
                        }
                        AstListNode<DeclFragment> newFragments = new AstListNode<>(DeclFragment.class);
                        newFragments.addAll(f.fragments());
                        newFragments.add(new DroppedNode(f.range()));
                        newFields.add(new StructFieldDecl(
                            f.range(), f.name(), f.type(), f.nullable(), newFragments));
                        found = true;
                    } else {
                        newFields.add(f);
                    }
                }
                if (!found) {
                    diags.error(dm.range(), DiagnosticKind.SEMANTIC, DiagnosticCode.UNKNOWN_MEMBER,
                        "Unknown field: " + dropFieldName);
                    return;
                }
                StructDecl newStruct = new StructDecl(struct.range(), newFields);
                TypeDecl newTypeDecl = new TypeDecl(
                    existingType.range(), existingType.name(), newStruct, existingType.fragments());
                symbols.replace(target, newTypeDecl);
            }
            case AlterStmt.AddSymbol as -> {
                if (!(existingType.kind() instanceof EnumDecl enumDecl)) {
                    diags.error(as.range(), DiagnosticKind.SEMANTIC, DiagnosticCode.INVALID_TYPE_REF,
                        "Can only ADD SYMBOL to ENUM types");
                    return;
                }
                String newSymbolName = as.symbol().name().name();
                for (EnumSymbolDecl s : enumDecl.symbols()) {
                    if (s.name().name().equals(newSymbolName)) {
                        diags.error(as.range(), DiagnosticKind.SEMANTIC, DiagnosticCode.DUPLICATE_DECLARATION,
                            "Symbol '" + newSymbolName + "' already exists in enum");
                        return;
                    }
                }
                AstListNode<EnumSymbolDecl> newSymbols = new AstListNode<>(EnumSymbolDecl.class);
                newSymbols.addAll(enumDecl.symbols());
                newSymbols.add(as.symbol());
                EnumDecl newEnum = new EnumDecl(enumDecl.range(), enumDecl.type(), newSymbols);
                TypeDecl newTypeDecl = new TypeDecl(
                    existingType.range(), existingType.name(), newEnum, existingType.fragments());
                symbols.replace(target, newTypeDecl);
            }
        }
    }

    private static void resolveDropStmt(
        DropStmt stmt,
        SymbolTable symbols,
        Diagnostics diags
    ) {
        Name target = toName(stmt.target());
        if (!symbols.hasKey(target)) {
            diags.error(
                stmt.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.UNKNOWN_TYPE,
                "Cannot DROP unknown object: " + target
            );
            return;
        }

        boolean hasError = false;

        switch (stmt) {
            case DropStmt.DropContext dc -> {
                if (symbols.lookupContext(target).isEmpty()) {
                    diags.error(dc.range(), DiagnosticKind.SEMANTIC, DiagnosticCode.UNKNOWN_CONTEXT,
                        target + " is not a CONTEXT");
                    hasError = true;
                } else {
                    List<Name> children = findChildren(symbols, target);
                    if (!children.isEmpty()) {
                        diags.error(dc.range(), DiagnosticKind.SEMANTIC, DiagnosticCode.DEPENDENCY_EXISTS,
                            "Cannot DROP CONTEXT " + target + ": contains " + children.size() + " object(s)");
                        hasError = true;
                    }
                }
            }
            case DropStmt.DropType dt -> {
                if (symbols.lookupType(target).isEmpty()) {
                    diags.error(dt.range(), DiagnosticKind.SEMANTIC, DiagnosticCode.UNKNOWN_TYPE,
                        target + " is not a TYPE");
                    hasError = true;
                } else {
                    List<Name> dependents = findTypeDependents(symbols, target);
                    if (!dependents.isEmpty()) {
                        diags.error(dt.range(), DiagnosticKind.SEMANTIC, DiagnosticCode.DEPENDENCY_EXISTS,
                            "Cannot DROP TYPE " + target + ": referenced by " + dependents.getFirst());
                        hasError = true;
                    }
                }
            }
            case DropStmt.DropStream ds -> {
                if (symbols.lookupStream(target).isEmpty()) {
                    diags.error(ds.range(), DiagnosticKind.SEMANTIC, DiagnosticCode.UNKNOWN_STREAM,
                        target + " is not a STREAM");
                    hasError = true;
                }
            }
        }

        if (!hasError) {
            symbols._decl.remove(target);
        }
    }

    private static void resolveCursorStmt(
        CursorStmt stmt,
        SymbolTable symbols,
        ContextScope scope,
        Diagnostics diags
    ) {
        if (scope.isGlobal()) {
            diags.error(
                stmt.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.INVALID_TYPE_REF,
                "Cursor must be created inside a context. " +
                "Use CREATE CONTEXT <name>; and USE CONTEXT <name>; first."
            );
            return;
        }
        Name cursorContext = scope.current();

        switch (stmt) {
            case CursorStmt.CreateCursor create -> {
                LinkedHashMap<Name, CursorStmt.ResetPolicy> resolvedStreams =
                    resolveCursorStreamBindings(create.streams(), cursorContext, symbols, diags);
                if (symbols.lookupCursor(cursorContext, create.cursorName()).isPresent()) {
                    diags.error(
                        create.range(),
                        DiagnosticKind.SEMANTIC,
                        DiagnosticCode.DUPLICATE_DECLARATION,
                        "Cursor already exists: '" + create.cursorName() + "'"
                    );
                    return;
                }
                symbols.registerCursor(
                    cursorContext,
                    create.cursorName(),
                    resolvedStreams
                );
            }
            case CursorStmt.AlterCursorAdd add -> {
                var existingOpt = symbols.lookupCursor(cursorContext, add.cursorName());
                if (existingOpt.isEmpty()) {
                    diags.error(
                        add.range(),
                        DiagnosticKind.SEMANTIC,
                        DiagnosticCode.UNKNOWN_TYPE,
                        "Unknown cursor: '" + add.cursorName() + "'"
                    );
                    return;
                }
                Name streamName = resolveCursorStream(add.stream(), cursorContext, symbols, diags);
                if (streamName == null) {
                    return;
                }
                LinkedHashMap<Name, CursorStmt.ResetPolicy> updated =
                    new LinkedHashMap<>(existingOpt.get().streamPolicies());
                updated.put(streamName, add.resetPolicy().orElse(CursorStmt.ResetPolicy.LATEST));
                symbols.upsertCursor(
                    cursorContext,
                    add.cursorName(),
                    updated
                );
            }
            case CursorStmt.AlterCursorRemove remove -> {
                var existingOpt = symbols.lookupCursor(cursorContext, remove.cursorName());
                if (existingOpt.isEmpty()) {
                    diags.error(
                        remove.range(),
                        DiagnosticKind.SEMANTIC,
                        DiagnosticCode.UNKNOWN_TYPE,
                        "Unknown cursor: '" + remove.cursorName() + "'"
                    );
                    return;
                }
                Name streamName = resolveCursorStream(remove.stream(), cursorContext, symbols, diags);
                if (streamName == null) {
                    return;
                }
                LinkedHashMap<Name, CursorStmt.ResetPolicy> updated =
                    new LinkedHashMap<>(existingOpt.get().streamPolicies());
                if (updated.remove(streamName) == null) {
                    diags.error(
                        remove.range(),
                        DiagnosticKind.SEMANTIC,
                        DiagnosticCode.UNKNOWN_STREAM,
                        "Cursor '" + remove.cursorName() + "' does not include stream '" + streamName.fullName() + "'"
                    );
                    return;
                }
                symbols.upsertCursor(cursorContext, remove.cursorName(), updated);
            }
            case CursorStmt.AlterCursorResetStream alter -> {
                var existingOpt = symbols.lookupCursor(cursorContext, alter.cursorName());
                if (existingOpt.isEmpty()) {
                    diags.error(
                        alter.range(),
                        DiagnosticKind.SEMANTIC,
                        DiagnosticCode.UNKNOWN_TYPE,
                        "Unknown cursor: '" + alter.cursorName() + "'"
                    );
                    return;
                }
                Name streamName = resolveCursorStream(alter.stream(), cursorContext, symbols, diags);
                if (streamName == null) {
                    return;
                }
                LinkedHashMap<Name, CursorStmt.ResetPolicy> updated =
                    new LinkedHashMap<>(existingOpt.get().streamPolicies());
                if (!updated.containsKey(streamName)) {
                    diags.error(
                        alter.range(),
                        DiagnosticKind.SEMANTIC,
                        DiagnosticCode.UNKNOWN_STREAM,
                        "Cursor '" + alter.cursorName() + "' does not include stream '" + streamName.fullName() + "'"
                    );
                    return;
                }
                updated.put(streamName, alter.resetPolicy());
                symbols.upsertCursor(cursorContext, alter.cursorName(), updated);
            }
            case CursorStmt.AlterCursorSeekStream seek -> {
                var existingOpt = symbols.lookupCursor(cursorContext, seek.cursorName());
                if (existingOpt.isEmpty()) {
                    diags.error(
                        seek.range(),
                        DiagnosticKind.SEMANTIC,
                        DiagnosticCode.UNKNOWN_TYPE,
                        "Unknown cursor: '" + seek.cursorName() + "'"
                    );
                    return;
                }
                Name streamName = resolveCursorStream(seek.stream(), cursorContext, symbols, diags);
                if (streamName == null) {
                    return;
                }
                if (!existingOpt.get().streamPolicies().containsKey(streamName)) {
                    diags.error(
                        seek.range(),
                        DiagnosticKind.SEMANTIC,
                        DiagnosticCode.UNKNOWN_STREAM,
                        "Cursor '" + seek.cursorName() + "' does not include stream '" + streamName.fullName() + "'"
                    );
                    return;
                }
                LinkedHashSet<Integer> seenPartitions = new LinkedHashSet<>();
                for (CursorStmt.PartitionSeek p : seek.seeks()) {
                    if (!seenPartitions.add(p.partition())) {
                        diags.error(
                            p.range(),
                            DiagnosticKind.SEMANTIC,
                            DiagnosticCode.DUPLICATE_DECLARATION,
                            "Duplicate partition in cursor seek list: " + p.partition()
                        );
                    }
                    if (p.target() instanceof CursorStmt.SeekTarget.Timestamp ts) {
                        try {
                            Instant.parse(ts.timestamp());
                        } catch (Exception ex) {
                            diags.error(
                                p.range(),
                                DiagnosticKind.SEMANTIC,
                                DiagnosticCode.INVALID_LITERAL,
                                "Invalid seek timestamp literal: '" + ts.timestamp() + "'"
                            );
                        }
                    }
                    if (p.target() instanceof CursorStmt.SeekTarget.Offset off && off.offset() < 0) {
                        diags.error(
                            p.range(),
                            DiagnosticKind.SEMANTIC,
                            DiagnosticCode.INVALID_LITERAL,
                            "Offset must be >= 0 in cursor seek list"
                        );
                    }
                }
            }
            case CursorStmt.DropCursor drop -> {
                if (symbols.lookupCursor(cursorContext, drop.cursorName()).isEmpty()) {
                    diags.error(
                        drop.range(),
                        DiagnosticKind.SEMANTIC,
                        DiagnosticCode.UNKNOWN_TYPE,
                        "Unknown cursor: '" + drop.cursorName() + "'"
                    );
                    return;
                }
                symbols.removeCursor(cursorContext, drop.cursorName());
            }
        }
    }

    private static Name resolveCursorStream(
        QName stream,
        Name cursorContext,
        SymbolTable symbols,
        Diagnostics diags
    ) {
        Name streamName = toName(stream);
        if (streamName.context().isEmpty() && !cursorContext.isRoot()) {
            streamName = Name.of(cursorContext.fullName(), streamName.name());
        }
        if (symbols.lookupStream(streamName).isEmpty()) {
            diags.error(
                stream.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.UNKNOWN_STREAM,
                "Unknown stream in cursor mapping: " + streamName.fullName()
            );
            return null;
        }
        if (!Name.of(streamName.context()).equals(cursorContext)) {
            diags.error(
                stream.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.INVALID_CONTEXT_SCOPE,
                "Cursor context '" + cursorContext.fullName() + "' cannot include stream from context '"
                    + streamName.context() + "'."
            );
            return null;
        }
        return streamName;
    }

    private static LinkedHashMap<Name, CursorStmt.ResetPolicy> resolveCursorStreamBindings(
        List<CursorStmt.StreamBinding> streams,
        Name cursorContext,
        SymbolTable symbols,
        Diagnostics diags
    ) {
        LinkedHashMap<Name, CursorStmt.ResetPolicy> resolved = new LinkedHashMap<>();
        for (CursorStmt.StreamBinding binding : streams) {
            Name streamName = resolveCursorStream(binding.stream(), cursorContext, symbols, diags);
            if (streamName == null) {
                continue;
            }
            resolved.put(streamName, binding.resetPolicy().orElse(CursorStmt.ResetPolicy.LATEST));
        }
        return resolved;
    }

    private static void resolveReadStmt(
        ReadStmt stmt,
        SymbolTable symbols,
        ContextScope scope,
        Diagnostics diags
    ) {
        if (stmt.mode().isEmpty()) return;
        if (!(stmt.mode().get() instanceof ReadMode.FromCursor cursorMode)) return;

        Name stream = toName(stmt.stream());
        if (stream.context().isEmpty() && !scope.isGlobal()) {
            stream = Name.of(scope.current().fullName(), stream.name());
        }

        Optional<CursorRef> cursorRef = parseCursorRef(cursorMode.cursorName(), stmt.range(), diags);
        if (cursorRef.isEmpty()) {
            return;
        }

        var cursorOpt = symbols.lookupCursor(cursorRef.get().context(), cursorRef.get().cursorName());
        if (cursorOpt.isEmpty()) {
            diags.error(
                stmt.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.UNKNOWN_TYPE,
                "Unknown cursor: '" + cursorMode.cursorName() + "'"
            );
            return;
        }

        if (symbols.lookupStream(stream).isPresent() && !cursorOpt.get().streamPolicies().containsKey(stream)) {
            diags.error(
                stmt.stream().range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.UNKNOWN_STREAM,
                "Cursor '" + cursorMode.cursorName() + "' is not assigned to stream '" + stream.fullName() + "'"
            );
        }
    }

    private static Optional<CursorRef> parseCursorRef(
        String rawCursorRef,
        Range range,
        Diagnostics diags
    ) {
        int split = rawCursorRef.lastIndexOf('.');
        if (split <= 0 || split == rawCursorRef.length() - 1) {
            diags.error(
                range,
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.INVALID_TYPE_REF,
                "FROM CURSOR requires fully-qualified cursor reference '<context>.<cursor>'"
            );
            return Optional.empty();
        }

        String contextPart = rawCursorRef.substring(0, split);
        String cursorName = rawCursorRef.substring(split + 1);
        return Optional.of(new CursorRef(Name.of(contextPart), cursorName));
    }

    // =======================================================================
    // Dependency helpers
    // =======================================================================

    /**
     * Find all symbols that are children of the given context.
     */
    private static List<Name> findChildren(SymbolTable symbols, Name context) {
        String prefix = context.fullName() + ".";
        return symbols._decl.keySet().stream()
            .filter(n -> n.fullName().toLowerCase().startsWith(prefix.toLowerCase()))
            .toList();
    }

    /**
     * Find all symbols that reference the given type.
     * Checks stream member derived types and struct field type references.
     */
    private static List<Name> findTypeDependents(SymbolTable symbols, Name typeName) {
        return symbols._decl.entrySet().stream()
            .filter(e -> referencesType(e.getValue(), typeName))
            .map(Map.Entry::getKey)
            .toList();
    }

    private static boolean referencesType(Decl decl, Name typeName) {
        if (decl instanceof StreamDecl sd) {
            for (StreamMemberDecl member : sd.streamTypes()) {
                if (member.memberDecl().kind() instanceof DerivedTypeDecl dt) {
                    if (toName(dt.target().name()).equals(typeName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // =======================================================================
    // Helpers
    // =======================================================================
    
    /**
     * Construct canonical name for declarations that require an active context.
     * Returns empty if context is global (error reported).
     * Used for TYPE and STREAM declarations.
     * 
     * @param scope current context scope
     * @param diags diagnostics
     * @param decl declaration (TypeDecl or StreamDecl)
     * @return fully-qualified name
     */
    private static Optional<Name> requireActiveContext(ContextScope scope, Diagnostics diags, Decl decl) {
        if (scope.isGlobal()) {
            String declType = switch (decl) {
                case TypeDecl t -> "TYPE";
                case StreamDecl s -> "STREAM";
                default -> "declaration";
            };
            diags.error(
                decl.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.INVALID_TYPE_REF,
                declType + " must be created inside a context. " +
                "Use CREATE CONTEXT <name>; and USE CONTEXT <name>; first."
            );
            return Optional.empty();
        }
        return anyName(scope, diags, decl);
    }

    /**
     * Construct canonical context name from simple identifier
     * Returns empty if name is empty (error reported)
     *
     * @param scope current context scope
     * @param diags diagnostics
     * @param decl declaration
     * @return fully-qualified context name
     */
    private static Optional<Name> anyName(ContextScope scope, Diagnostics diags, Decl decl) {
        if (decl.name().name().isEmpty()) {
            diags.error(
                decl.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.INVALID_TYPE_REF,
                "Name cannot be empty."
            );
            return Optional.empty();
        }

        Name name = scope.qualify(decl.name().name());

        if (name.isRoot()) {
            diags.error(
                decl.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.INVALID_CONTEXT_SCOPE,
                "Cannot create context with empty name."
            );
            return Optional.empty();
        }

        return Optional.of(name);
    }

    private static boolean duplicate(
        Name name,
        SymbolTable symbols,
        Diagnostics diags,
        Range range
    ) {
        if (symbols.hasKey(name)) {
            diags.error(
                range,
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.DUPLICATE_DECLARATION,
                "Unknown declaration: " + name
            );
            return true;
        }
        return false;
    }

    private static Name toName(QName q) {
        return Name.of(q.context(), q.name());
    }
}
