package kafkasql.lang.semantic.bind;

import java.util.*;

import kafkasql.runtime.type.AnyType;

public final class TypeEnv {

    private final Map<String, AnyType> vars = new HashMap<>();

    public void define(String name, AnyType type) {
        vars.put(name, type);
    }

    public Optional<AnyType> lookup(String name) {
        AnyType type = vars.get(name);
        return Optional.ofNullable(type);
    }
}