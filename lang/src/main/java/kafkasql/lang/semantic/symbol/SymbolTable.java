package kafkasql.lang.semantic.symbol;

import java.util.*;

import kafkasql.runtime.Name;
import kafkasql.lang.syntax.ast.decl.ContextDecl;
import kafkasql.lang.syntax.ast.decl.Decl;
import kafkasql.lang.syntax.ast.decl.StreamDecl;
import kafkasql.lang.syntax.ast.decl.StructDecl;
import kafkasql.lang.syntax.ast.decl.TypeDecl;

public final class SymbolTable {

    public final Map<Name, Decl> _decl = new HashMap<>();

    public boolean hasKey(Name name) {
        return _decl.containsKey(name);
    }

    public boolean register(Name name, Decl decl) {
        if (hasKey(name))
            return false;
        _decl.put(name, decl);
        return true;
    }

    public Optional<TypeDecl> lookupType(Name name) {
        var v = get(name);
        if (v.isPresent() && v.get() instanceof TypeDecl t)
            return Optional.of(t);
        else
            return Optional.empty();
        
    }

    public Optional<StructDecl> lookupStruct(Name name) {
        var v = lookupType(name);
        if (v.isPresent() && v.get().kind() instanceof StructDecl s)
            return Optional.of(s);
        else
            return Optional.empty();
    }

    public Optional<StreamDecl> lookupStream(Name name) {
        var v = get(name);
        if (v.isPresent() && v.get() instanceof StreamDecl s)
            return Optional.of(s);
        else
            return Optional.empty();
    }

    public Optional<ContextDecl> lookupContext(Name name) {
        var v = get(name);
        if (v.isPresent() && v.get() instanceof ContextDecl c)
            return Optional.of(c);
        else
            return Optional.empty();
    }

    private Optional<Decl> get(Name name) {
        return Optional.ofNullable(_decl.get(name));
    }

    /**
     * Reverse lookup: find the Name for a given Decl.
     * This is useful when we have a Decl and need its fully qualified name.
     */
    public Optional<Name> nameOf(Decl decl) {
        return _decl.entrySet().stream()
            .filter(e -> e.getValue() == decl)
            .map(Map.Entry::getKey)
            .findFirst();
    }
}