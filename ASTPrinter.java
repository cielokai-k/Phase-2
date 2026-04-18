public class ASTPrinter {
    private int indentLevel = 0;

    // --- Formatting Helpers ---
    private void printIndent() {
        for (int i = 0; i < indentLevel; i++) {
            System.out.print("  ");
        }
    }

    private void println(String text) {
        printIndent();
        System.out.println(text);
    }

    // --- The Main Recursive Printer ---
    public void print(AST.Node node) {
        if (node == null) return;

        // 1. PROGRAM ROOT
        if (node instanceof AST.Program) {
            AST.Program p = (AST.Program) node;
            println("CEREBRA PROGRAM");
            indentLevel++;
            
            if (!p.subroutines.isEmpty()) {
                println("SUBROUTINES:");
                indentLevel++;
                for (AST.Subroutine sub : p.subroutines) print(sub);
                indentLevel--;
            }
            
            println("ACTIVATE_BLOCK:");
            indentLevel++;
            for (AST.Stmt stmt : p.activateBody) print(stmt);
            indentLevel--;
            
            indentLevel--;
        } 
        
        // 2. STATEMENTS
        else if (node instanceof AST.AssignStmt) {
            AST.AssignStmt a = (AST.AssignStmt) node;
            println("ASSIGN_STMT [" + a.operator.lexeme + "]");
            indentLevel++;
            print(a.target);
            print(a.value);
            indentLevel--;
        }
        else if (node instanceof AST.DeclarationStmt) {
            AST.DeclarationStmt d = (AST.DeclarationStmt) node;
            String prefix = d.isInstinct ? "INSTINCT DECLARATION" : "DECLARATION";
            println(prefix + " [" + d.dataType.lexeme + "]");
            indentLevel++;
            for (AST.VariableDeclaration var : d.variables) {
                println("VAR [" + var.name.lexeme + "]");
                if (var.initializer != null) {
                    indentLevel++;
                    print(var.initializer);
                    indentLevel--;
                }
            }
            indentLevel--;
        }
        else if (node instanceof AST.IoStmt) {
            AST.IoStmt io = (AST.IoStmt) node;
            println("IO_STMT [" + io.action.lexeme + "]");
            indentLevel++;
            print(io.target);
            indentLevel--;
        }
        else if (node instanceof AST.ExprStmt) {
            AST.ExprStmt es = (AST.ExprStmt) node;
            println("EXPR_STMT");
            indentLevel++;
            print(es.expression);
            indentLevel--;
        }
        
        // 3. EXPRESSIONS
        else if (node instanceof AST.BinaryExpr) {
            AST.BinaryExpr b = (AST.BinaryExpr) node;
            println("BINARY_EXPR [" + b.operator.lexeme + "]");
            indentLevel++;
            print(b.left);
            print(b.right);
            indentLevel--;
        }
        else if (node instanceof AST.UnaryExpr) {
            AST.UnaryExpr u = (AST.UnaryExpr) node;
            String position = u.isPostfix ? "POSTFIX" : "PREFIX";
            println("UNARY_EXPR " + position + " [" + u.operator.lexeme + "]");
            indentLevel++;
            print(u.operand);
            indentLevel--;
        }
        else if (node instanceof AST.VariableAccessExpr) {
            AST.VariableAccessExpr v = (AST.VariableAccessExpr) node;
            println("VARIABLE [" + v.name.lexeme + "]");
            if (!v.indices.isEmpty()) {
                indentLevel++;
                println("INDICES:");
                for (AST.Expr index : v.indices) print(index);
                indentLevel--;
            }
        }
        else if (node instanceof AST.LiteralExpr) {
            AST.LiteralExpr l = (AST.LiteralExpr) node;
            println("LITERAL [" + l.value.type + ": " + l.value.lexeme + "]");
        }
        else if (node instanceof AST.CallExpr) {
            AST.CallExpr c = (AST.CallExpr) node;
            println("CALL [" + c.callee.lexeme + "]");
            indentLevel++;
            for (AST.Expr arg : c.arguments) print(arg);
            indentLevel--;
        }
        else if (node instanceof AST.ClusterDeclStmt) {
            AST.ClusterDeclStmt c = (AST.ClusterDeclStmt) node;
            println("CLUSTER DECLARATION [" + c.dataType.lexeme + "]");
            indentLevel++;
            for (AST.ClusterItem item : c.clusters) {
                String dims = "[" + item.size1.lexeme + "]";
                if (item.size2 != null) dims += "[" + item.size2.lexeme + "]";
                println("ARRAY [" + item.name.lexeme + "] " + dims);
                
                if (item.init1D != null) {
                    indentLevel++;
                    println("INITIALIZER (1D):");
                    indentLevel++;
                    for(AST.Expr e : item.init1D) print(e);
                    indentLevel -= 2;
                } else if (item.init2D != null) {
                    indentLevel++;
                    println("INITIALIZER (2D):");
                    indentLevel++;
                    for(java.util.List<AST.Expr> row : item.init2D) {
                        println("ROW:");
                        indentLevel++;
                        for(AST.Expr e : row) print(e);
                        indentLevel--;
                    }
                    indentLevel -= 2;
                }
            }
            indentLevel--;
        }
        // --- SUBROUTINES ---
        else if (node instanceof AST.Subroutine) {
            AST.Subroutine sub = (AST.Subroutine) node;
            println("SUBROUTINE [" + sub.name.lexeme + "] returns [" + sub.returnType.lexeme + "]");
            indentLevel++;

            // 1. Print Parameters
            if (sub.params.isEmpty()) {
                println("PARAMS: (none)");
            } else {
                println("PARAMS:");
                indentLevel++;
                for (AST.Param p : sub.params) {
                    String clusterPrefix = p.isCluster ? "CLUSTER " : "";
                    println("PARAM [" + p.name.lexeme + "] : " + clusterPrefix + p.dataType.lexeme);
                }
                indentLevel--;
            }

            // 2. Print the Function Body
            println("BODY:");
            indentLevel++;
            for (AST.Stmt stmt : sub.body) {
                print(stmt); // Recursively print the statements inside the function!
            }
            indentLevel--;

            indentLevel--;
        }
        // --- CONTROL FLOW ---
        else if (node instanceof AST.FlowControlStmt) {
            AST.FlowControlStmt f = (AST.FlowControlStmt) node;
            println("FLOW_CONTROL [" + f.keyword.lexeme + "]");
            // If it's a 'recall' (return) statement, print what it's returning
            if (f.returnValue != null) {
                indentLevel++;
                print(f.returnValue);
                indentLevel--;
            }
        }
        else if (node instanceof AST.StimulateStmt) {
            AST.StimulateStmt s = (AST.StimulateStmt) node;
            println("STIMULATE (IF):");
            indentLevel++;
            
            println("CONDITION:");
            indentLevel++; print(s.condition); indentLevel--;
            
            println("BODY:");
            indentLevel++; 
            for(AST.Stmt stmt : s.stimulateBody) print(stmt); 
            indentLevel--;
            
            if (s.inhibitBody != null) {
                println("INHIBIT (ELSE):");
                indentLevel++; 
                for(AST.Stmt stmt : s.inhibitBody) print(stmt); 
                indentLevel--;
            }
            indentLevel--;
        }
        else if (node instanceof AST.CycleStmt) {
            AST.CycleStmt c = (AST.CycleStmt) node;
            println("CYCLE (WHILE):");
            indentLevel++;
            
            println("CONDITION:");
            indentLevel++; print(c.condition); indentLevel--;
            
            println("BODY:");
            indentLevel++; 
            for(AST.Stmt stmt : c.body) print(stmt); 
            indentLevel--;
            
            indentLevel--;
        }
        else if (node instanceof AST.ReactStmt) {
            AST.ReactStmt r = (AST.ReactStmt) node;
            println("REACT (DO-WHILE):");
            indentLevel++;
            
            println("BODY:");
            indentLevel++; 
            for(AST.Stmt stmt : r.body) print(stmt); 
            indentLevel--;
            
            println("CONDITION:");
            indentLevel++; print(r.condition); indentLevel--;
            
            indentLevel--;
        }
        else if (node instanceof AST.EchoStmt) {
            AST.EchoStmt e = (AST.EchoStmt) node;
            println("ECHO (FOR):");
            indentLevel++;
            
            println("INIT:");
            indentLevel++; print(e.init); indentLevel--;
            
            println("CONDITION:");
            indentLevel++; print(e.condition); indentLevel--;
            
            println("UPDATE:");
            indentLevel++; print(e.update); indentLevel--;
            
            println("BODY:");
            indentLevel++; 
            for(AST.Stmt stmt : e.body) print(stmt); 
            indentLevel--;
            
            indentLevel--;
        }
        else if (node instanceof AST.EvaluateStmt) {
            AST.EvaluateStmt ev = (AST.EvaluateStmt) node;
            println("EVALUATE (SWITCH):");
            indentLevel++;
            
            println("CONDITION:");
            indentLevel++; print(ev.condition); indentLevel--;
            
            for (AST.PathCase pc : ev.paths) {
                println("PATH:");
                indentLevel++;
                
                println("MATCH:");
                indentLevel++; print(pc.literal); indentLevel--;
                
                println("BODY:");
                indentLevel++; 
                for(AST.Stmt stmt : pc.body) print(stmt); 
                indentLevel--;
                
                if (pc.hasDormant) println("--> [DORMANT / BREAK]");
                indentLevel--;
            }
            
            if (ev.baseCase != null) {
                println("BASE (DEFAULT):");
                indentLevel++; 
                for(AST.Stmt stmt : ev.baseCase) print(stmt); 
                indentLevel--;
            }
            indentLevel--;
        }
        // Fallback for nodes not yet added to the printer
        else {
            println("--> [Unprinted Node Type: " + node.getClass().getSimpleName() + "]");
        }
    }
}