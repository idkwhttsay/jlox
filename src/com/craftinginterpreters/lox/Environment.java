package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.Map;

class Environment {
    final Environment enclosing;
    private static final Object UNINITIALIZED = new Object();
    private final Map<String, Object> values = new HashMap<>();

    Environment() {
        this.enclosing = null;
    }

    Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    Object get(Token name) {
        if(values.containsKey(name.lexeme)) {
            Object value = values.get(name.lexeme);
            if(value == UNINITIALIZED) {
                throw new RuntimeError(name, "Accessing uninitialized variable " + name.lexeme + ".");
            }
            return value;
        }

        if(enclosing != null) return enclosing.get(name);

        throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "''.");
    }

    void assign(Token name, Object value) {
        if (values.containsKey(name.lexeme)) {
            values.put(name.lexeme, value);
            return;
        }

        if(enclosing != null) {
            enclosing.assign(name, value);
            return;
        }

        throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "''.");
    }

    void define(String name, Object value) {
        values.put(name, value);
    }

    void defineUninitialized(String name) {
        values.put(name, UNINITIALIZED);
    }
}
