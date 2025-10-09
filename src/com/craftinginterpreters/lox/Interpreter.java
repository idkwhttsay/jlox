package com.craftinginterpreters.lox;

import java.util.List;

class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
    private static class BreakException extends RuntimeError {
        BreakException(Token token, String message) {
            super(token, message);
        }
    }
    
    private Environment environment = new Environment();

    void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }

    Object interpretExpression(Expr expression) {
        try {
            return evaluate(expression);
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
            return null;
        }
    }

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    void executeBlock(List<Stmt> statements, Environment environment) {
        Environment previous = this.environment;
        try {
            this.environment = environment;

            for(Stmt statement : statements) {
                execute(statement);
            }
        } finally {
            this.environment = previous;
        }
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if(isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if(stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }

        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        if(stmt.initializer != null) {
            Object value = evaluate(stmt.initializer);
            environment.define(stmt.name.lexeme, value);
        } else {
            environment.defineUninitialized(stmt.name.lexeme);
        }

        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        try {
            while(isTruthy(evaluate(stmt.condition))) {
                execute(stmt.body);
            }
        } catch (BreakException ex) { }

        return null;
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);
        environment.assign(expr.name, value);
        return value;
    }

    @Override
    public Object visitBreakExpr(Expr.Break expr) {
        throw new BreakException(expr.token, "Break expression out of while/for loop enclosing.");
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);

        if(expr.operator.type == TokenType.OR) {
            if(isTruthy(left)) return left;
        } else {
            if(!isTruthy(left)) return left;
        }

        return evaluate(expr.right);
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        switch(expr.operator.type) {
            case BANG:
                return !isTruthy(right);
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return - (double)right;
            default: return null;
        }
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return environment.get(expr.name);
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case GREATER:
                checkComparisonOperand(expr.operator, left, right);
                return compareValues(left, right) > 0;
            case GREATER_EQUAL:
                checkComparisonOperand(expr.operator, left, right);
                return compareValues(left, right) >= 0;
            case LESS:
                checkComparisonOperand(expr.operator, left, right);
                return compareValues(left, right) < 0;
            case LESS_EQUAL:
                checkComparisonOperand(expr.operator, left, right);
                return compareValues(left, right) <= 0;
            case BANG_EQUAL:
                checkComparisonOperand(expr.operator, left, right);
                return compareValues(left, right) != 0;
            case EQUAL_EQUAL:
                checkComparisonOperand(expr.operator, left, right);
                return compareValues(left, right) == 0;
            case MINUS:
                checkNumberOperand(expr.operator, left, right);
                return (double)left - (double)right;
            case PLUS:
                if(left instanceof Double && right instanceof Double) {
                    return (double)left + (double)right;
                }

                if(left instanceof String || right instanceof String) {
                    return stringify(left) + stringify(right);
                }

                throw new RuntimeError(expr.operator, "Operands must be numbers or strings. String AND number are allowed.");
            case SLASH:
                checkNumberOperand(expr.operator, left, right);
                checkDivisionByZero(expr.operator, left, right);
                return (double)left / (double)right;
            case STAR:
                checkNumberOperand(expr.operator, left, right);
                return (double)left * (double)right;
            default: return null;
        }
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if(operand instanceof Double) return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    private void checkNumberOperand(Token operator, Object left, Object right) {
        if(left instanceof Double && right instanceof Double) return;
        throw new RuntimeError(operator, "Operands must be a numbers.");
    }

    private void checkDivisionByZero(Token operator, Object left, Object right) {
        if((double)right != 0.0) return;
        throw new RuntimeError(operator, "Division by zero is illegal.");
    }

    private void checkComparisonOperand(Token operator, Object left, Object right) {
        if (left == null || right == null) return; // nil can be compared with anything
        if (left.getClass() == right.getClass()) return; // same types always comparable
        
        // try to parse string as number and compare it
        if ((left instanceof Double && right instanceof String) || (left instanceof String && right instanceof Double)) return;
        
        if (left instanceof Boolean || right instanceof Boolean) return;
        
        throw new RuntimeError(operator, 
            "Cannot compare " + getTypeName(left) + " and " + getTypeName(right) + ".");
    }
    
    private String getTypeName(Object object) {
        if (object == null) return "nil";
        if (object instanceof Double) return "number";
        if (object instanceof String) return "string";
        if (object instanceof Boolean) return "boolean";
        return object.getClass().getSimpleName().toLowerCase();
    }
    
    private int compareValues(Object left, Object right) {
        if (left == null && right == null) return 0;    // nil == nil
        if (left == null) return -1;                    // nil < anything
        if (right == null) return 1;                    // anyhting > nil
        
        // same type comparisons
        if (left instanceof Double && right instanceof Double) return Double.compare((Double)left, (Double)right);
        if (left instanceof String && right instanceof String) return ((String)left).compareTo((String)right);
        if (left instanceof Boolean && right instanceof Boolean) return Boolean.compare((Boolean)left, (Boolean)right);
        
        // mixed type comparisons
        if (left instanceof Double && right instanceof String) {
            try {
                return Double.compare((Double)left, Double.parseDouble((String)right));
            } catch (NumberFormatException e) { // number < string (according to ordering)
                return -1;
            }
        }
        if (left instanceof String && right instanceof Double) {
            try {
                return Double.compare(Double.parseDouble((String)left), (Double)right);
            } catch (NumberFormatException e) { // number < string (according to ordering)
                return 1;
            }
        }
        
        return Integer.compare(getTypeOrder(left), getTypeOrder(right));
    }
    
    private int getTypeOrder(Object object) { // nil < boolean < number < string
        if (object == null) return 0;               // nil
        if (object instanceof Boolean) return 1;    // boolean
        if (object instanceof Double) return 2;     // number
        if (object instanceof String) return 3;     // string
        return 4; // unknown types come last
    }

    String stringify(Object object) {
        if(object == null) return "nil";

        if(object instanceof Double) {
            String text = object.toString();
            if(text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }

            return text;
        }

        return object.toString();
    }

    private boolean isTruthy(Object object) {
        if(object == null) return false;
        if(object instanceof Boolean) return (boolean) object;
        return true;
    }
}
