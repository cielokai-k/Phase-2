import java.util.List;

public class AST {

    // --- BASE CLASSES ---
    public static abstract class Node {}
    public static abstract class Stmt extends Node {}
    public static abstract class Expr extends Node {}

    // --- HIGH-LEVEL STRUCTURES ---
    public static class Program extends Node {
        public final List<Subroutine> subroutines;
        public final List<Stmt> activateBody;

        public Program(List<Subroutine> subroutines, List<Stmt> activateBody) {
            this.subroutines = subroutines;
            this.activateBody = activateBody;
        }
    }

    // =========================================================================
    // SUBROUTINES & PARAMETERS
    // =========================================================================

    public static class Subroutine extends Node {
        public final Token returnType; 
        public final Token name;
        public final List<Param> params;
        public final List<Stmt> body;

        public Subroutine(Token returnType, Token name, List<Param> params, List<Stmt> body) {
            this.returnType = returnType;
            this.name = name;
            this.params = params;
            this.body = body;
        }
    }

    public static class Param extends Node {
        public final Token dataType;
        public final Token name;
        public final boolean isCluster; // True if it's an array/cluster parameter

        public Param(Token dataType, Token name, boolean isCluster) {
            this.dataType = dataType;
            this.name = name;
            this.isCluster = isCluster;
        }
    }

    // =========================================================================
    // STATEMENTS
    // =========================================================================

    public static class DeclarationStmt extends Stmt {
        public final Token dataType;
        public final List<VariableDeclaration> variables;
        public final boolean isInstinct; // True if constant

        public DeclarationStmt(Token dataType, List<VariableDeclaration> variables, boolean isInstinct) {
            this.dataType = dataType;
            this.variables = variables;
            this.isInstinct = isInstinct;
        }
    }

    public static class VariableDeclaration extends Node {
        public final Token name;
        public final Expr initializer; // Can be null if just 'pulse x;'

        public VariableDeclaration(Token name, Expr initializer) {
            this.name = name;
            this.initializer = initializer;
        }
    }

    public static class AssignStmt extends Stmt {
        public final VariableAccessExpr target;
        public final Token operator; // =, +=, -=, etc.
        public final Expr value;

        public AssignStmt(VariableAccessExpr target, Token operator, Expr value) {
            this.target = target;
            this.operator = operator;
            this.value = value;
        }
    }

    public static class StimulateStmt extends Stmt {
        public final Expr condition;
        public final List<Stmt> stimulateBody;
        public final List<Stmt> inhibitBody; // Can be null

        public StimulateStmt(Expr condition, List<Stmt> stimulateBody, List<Stmt> inhibitBody) {
            this.condition = condition;
            this.stimulateBody = stimulateBody;
            this.inhibitBody = inhibitBody;
        }
    }

    public static class CycleStmt extends Stmt {
        public final Expr condition;
        public final List<Stmt> body;

        public CycleStmt(Expr condition, List<Stmt> body) {
            this.condition = condition;
            this.body = body;
        }
    }

    public static class ReactStmt extends Stmt {
        public final List<Stmt> body;
        public final Expr condition;

        public ReactStmt(List<Stmt> body, Expr condition) {
            this.body = body;
            this.condition = condition;
        }
    }

    public static class EchoStmt extends Stmt {
        public final Stmt init;       // The initialization (e.g., pulse i = 0;)
        public final Expr condition;  // The condition (e.g., i < 10;)
        public final Stmt update;     // The update step (e.g., i++)
        public final List<Stmt> body;

        public EchoStmt(Stmt init, Expr condition, Stmt update, List<Stmt> body) {
            this.init = init;
            this.condition = condition;
            this.update = update;
            this.body = body;
        }
    }

    public static class EvaluateStmt extends Stmt {
        public final Expr condition;
        public final List<PathCase> paths;
        public final List<Stmt> baseCase; // Can be null

        public EvaluateStmt(Expr condition, List<PathCase> paths, List<Stmt> baseCase) {
            this.condition = condition;
            this.paths = paths;
            this.baseCase = baseCase;
        }
    }

    public static class PathCase extends Node {
        public final Expr literal; 
        public final List<Stmt> body;
        public final boolean hasDormant; 

        public PathCase(Expr literal, List<Stmt> body, boolean hasDormant) {
            this.literal = literal;
            this.body = body;
            this.hasDormant = hasDormant;
        }
    }

    public static class IoStmt extends Stmt {
        public final Token action; // SENSE or EXPRESS
        public final Expr target; 

        public IoStmt(Token action, Expr target) {
            this.action = action;
            this.target = target;
        }
    }

    public static class FlowControlStmt extends Stmt {
        public final Token keyword; // FLOW or DORMANT or RECALL
        public final Expr returnValue; // Only used for RECALL

        public FlowControlStmt(Token keyword, Expr returnValue) {
            this.keyword = keyword;
            this.returnValue = returnValue;
        }
    }

    // Wraps an expression so it can sit in a statement list (e.g., function calls like "myFunc();")
    public static class ExprStmt extends Stmt {
        public final Expr expression;

        public ExprStmt(Expr expression) {
            this.expression = expression;
        }
    }

    // =========================================================================
    // EXPRESSIONS
    // =========================================================================

    public static class BinaryExpr extends Expr {
        public final Expr left;
        public final Token operator;
        public final Expr right;

        public BinaryExpr(Expr left, Token operator, Expr right) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }
    }

    public static class UnaryExpr extends Expr {
        public final Token operator;
        public final Expr operand;
        public final boolean isPostfix;

        public UnaryExpr(Token operator, Expr operand, boolean isPostfix) {
            this.operator = operator;
            this.operand = operand;
            this.isPostfix = isPostfix;
        }
    }

    public static class LiteralExpr extends Expr {
        public final Token value;

        public LiteralExpr(Token value) {
            this.value = value;
        }
    }

    public static class VariableAccessExpr extends Expr {
        public final Token name;
        public final List<Expr> indices; // For arrays

        public VariableAccessExpr(Token name, List<Expr> indices) {
            this.name = name;
            this.indices = indices;
        }
    }

    public static class CallExpr extends Expr {
        public final Token callee; // Function name, or LENGTH/TRANSCRIBE token
        public final List<Expr> arguments;

        public CallExpr(Token callee, List<Expr> arguments) {
            this.callee = callee;
            this.arguments = arguments;
        }
    }

    public static class ClusterDeclStmt extends Stmt {
        public final Token dataType;
        public final List<ClusterItem> clusters;

        public ClusterDeclStmt(Token dataType, List<ClusterItem> clusters) {
            this.dataType = dataType;
            this.clusters = clusters;
        }
    }

    public static class ClusterItem extends Node {
        public final Token name;
        public final Token size1;
        public final Token size2; // Can be null if it's just a 1D array
        public final List<Expr> init1D; // Can be null
        public final List<List<Expr>> init2D; // Can be null

        public ClusterItem(Token name, Token size1, Token size2, List<Expr> init1D, List<List<Expr>> init2D) {
            this.name = name;
            this.size1 = size1;
            this.size2 = size2;
            this.init1D = init1D;
            this.init2D = init2D;
        }
    }
    
    
}

    