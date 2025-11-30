package kafkasql.lang.semantic.resolve;

import kafkasql.runtime.Name;

/**
 * Very small helper for tracking the active context
 * and building fully-qualified names from simple identifiers.
 */
public final class ContextScope {

    private Name _current = Name.ROOT;

    /** Returns true if we're at global scope */
    public boolean isGlobal() {
        return _current.isRoot();
    }

    /** Returns the current context FQN ("" means global) */
    public Name current() {
        return _current;
    }

    /** Reset to global */
    public void resetToGlobal() {
        this._current = Name.ROOT;
    }

    /**
     * Set active context to a fully-qualified, canonical name.
     * Caller guarantees it is valid.
     */
    public void set(Name canonical) {
        this._current = canonical;
    }

    /**
     * Create a child context:
     *   current = "com" + "." + childName
     */
    public Name qualify(String child) {
        return Name.of(_current.fullName(), child);
    }
}