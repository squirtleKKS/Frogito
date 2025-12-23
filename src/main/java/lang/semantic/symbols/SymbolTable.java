package lang.semantic.symbols;

import java.util.*;

public final class SymbolTable {

    private final Deque<Map<String, Symbol>> scopes = new ArrayDeque<>();

    public SymbolTable() {
        pushScope();
    }

    public void pushScope() {
        scopes.push(new HashMap<>());
    }

    public void popScope() {
        if (scopes.size() == 1) {
            throw new IllegalStateException("Нельзя удалить глобальную область");
        }
        scopes.pop();
    }

    public boolean declare(Symbol symbol) {
        Map<String, Symbol> current = scopes.peek();
        assert current != null;
        if (current.containsKey(symbol.getName())) {
            return false;
        }
        current.put(symbol.getName(), symbol);
        return true;
    }

    public Symbol resolve(String name) {
        for (Map<String, Symbol> scope : scopes) {
            Symbol sym = scope.get(name);
            if (sym != null) return sym;
        }
        return null;
    }
}

