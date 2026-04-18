public class ASTToDot {
    private int nodeCounter = 0;
    private StringBuilder dot;

    public String generateDot(AST.Node root) {
        dot = new StringBuilder();
        nodeCounter = 0;
        
        dot.append("digraph AST {\n");
        dot.append("  node [shape=box, style=filled, fillcolor=lightblue, fontname=\"Helvetica\"];\n");
        dot.append("  edge [color=darkgray];\n\n");
        
        traverse(root);
        
        dot.append("}\n");
        return dot.toString();
    }

    private String traverse(AST.Node node) {
        if (node == null) return null;
        String id = "node" + (nodeCounter++);
        
        // --- PROGRAM ROOT ---
        if (node instanceof AST.Program) {
            AST.Program p = (AST.Program) node;
            dot.append("  ").append(id).append(" [label=\"Program\", fillcolor=lightgreen];\n");
            
            for (AST.Subroutine sub : p.subroutines) {
                String childId = traverse(sub);
                if (childId != null) dot.append("  ").append(id).append(" -> ").append(childId).append(";\n");
            }
            for (AST.Stmt stmt : p.activateBody) {
                String childId = traverse(stmt);
                if (childId != null) dot.append("  ").append(id).append(" -> ").append(childId).append(";\n");
            }
        }
        
        // --- SUBROUTINES ---
        else if (node instanceof AST.Subroutine) {
            AST.Subroutine sub = (AST.Subroutine) node;
            dot.append("  ").append(id).append(" [label=\"Subroutine: ").append(sub.name.lexeme).append("\\nReturns: ").append(sub.returnType.lexeme).append("\", fillcolor=plum, shape=folder];\n");
            
            for (AST.Param param : sub.params) {
                String paramId = "node" + (nodeCounter++);
                String prefix = param.isCluster ? "Cluster " : "";
                dot.append("  ").append(paramId).append(" [label=\"Param: ").append(param.name.lexeme).append("\\n(").append(prefix).append(param.dataType.lexeme).append(")\", shape=ellipse, fillcolor=thistle];\n");
                dot.append("  ").append(id).append(" -> ").append(paramId).append(" [style=dotted, label=\"param\"];\n");
            }
            
            for (AST.Stmt stmt : sub.body) {
                String childId = traverse(stmt);
                if (childId != null) dot.append("  ").append(id).append(" -> ").append(childId).append(";\n");
            }
        }

        // --- DECLARATIONS & VARIABLES ---
        else if (node instanceof AST.DeclarationStmt) {
            AST.DeclarationStmt d = (AST.DeclarationStmt) node;
            String prefix = d.isInstinct ? "Instinct Decl:\\n" : "Decl:\\n";
            dot.append("  ").append(id).append(" [label=\"").append(prefix).append(d.dataType.lexeme).append("\", fillcolor=lightyellow];\n");
            for (AST.VariableDeclaration var : d.variables) {
                String childId = traverse(var);
                if (childId != null) dot.append("  ").append(id).append(" -> ").append(childId).append(";\n");
            }
        }
        else if (node instanceof AST.VariableDeclaration) {
            AST.VariableDeclaration v = (AST.VariableDeclaration) node;
            dot.append("  ").append(id).append(" [label=\"Var: ").append(v.name.lexeme).append("\"];\n");
            if (v.initializer != null) {
                String childId = traverse(v.initializer);
                if (childId != null) dot.append("  ").append(id).append(" -> ").append(childId).append(" [label=\"=\"];\n");
            }
        }
        else if (node instanceof AST.ClusterDeclStmt) {
            AST.ClusterDeclStmt c = (AST.ClusterDeclStmt) node;
            dot.append("  ").append(id).append(" [label=\"Cluster Decl\\n").append(c.dataType.lexeme).append("\", fillcolor=lightyellow, shape=folder];\n");
            for (AST.ClusterItem item : c.clusters) {
                String childId = traverse(item);
                if (childId != null) dot.append("  ").append(id).append(" -> ").append(childId).append(";\n");
            }
        }
        else if (node instanceof AST.ClusterItem) {
            AST.ClusterItem item = (AST.ClusterItem) node;
            String dims = "[" + item.size1.lexeme + "]";
            if (item.size2 != null) dims += "[" + item.size2.lexeme + "]";
            dot.append("  ").append(id).append(" [label=\"Array: ").append(item.name.lexeme).append("\\n").append(dims).append("\", shape=tab];\n");
            
            if (item.init1D != null) {
                for (AST.Expr e : item.init1D) {
                    String childId = traverse(e);
                    if (childId != null) dot.append("  ").append(id).append(" -> ").append(childId).append(" [style=dashed];\n");
                }
            } else if (item.init2D != null) {
                for (java.util.List<AST.Expr> row : item.init2D) {
                    for (AST.Expr e : row) {
                        String childId = traverse(e);
                        if (childId != null) dot.append("  ").append(id).append(" -> ").append(childId).append(" [style=dashed];\n");
                    }
                }
            }
        }
        
        // --- STATEMENTS & I/O ---
        else if (node instanceof AST.AssignStmt) {
            AST.AssignStmt a = (AST.AssignStmt) node;
            dot.append("  ").append(id).append(" [label=\"Assign\\n").append(a.operator.lexeme).append("\", fillcolor=moccasin];\n");
            String leftId = traverse(a.target);
            String rightId = traverse(a.value);
            if (leftId != null) dot.append("  ").append(id).append(" -> ").append(leftId).append(";\n");
            if (rightId != null) dot.append("  ").append(id).append(" -> ").append(rightId).append(";\n");
        }
        else if (node instanceof AST.IoStmt) {
            AST.IoStmt io = (AST.IoStmt) node;
            dot.append("  ").append(id).append(" [label=\"I/O: ").append(io.action.lexeme).append("\", fillcolor=lightpink, shape=invhouse];\n");
            String childId = traverse(io.target);
            if (childId != null) dot.append("  ").append(id).append(" -> ").append(childId).append(";\n");
        }
        else if (node instanceof AST.ExprStmt) {
            AST.ExprStmt es = (AST.ExprStmt) node;
            dot.append("  ").append(id).append(" [label=\"ExprStmt\"];\n");
            String childId = traverse(es.expression);
            if (childId != null) dot.append("  ").append(id).append(" -> ").append(childId).append(";\n");
        }

        // --- CONTROL FLOW ---
        else if (node instanceof AST.StimulateStmt) {
            AST.StimulateStmt s = (AST.StimulateStmt) node;
            dot.append("  ").append(id).append(" [label=\"STIMULATE\", fillcolor=coral, shape=diamond];\n");
            
            String condId = traverse(s.condition);
            if (condId != null) dot.append("  ").append(id).append(" -> ").append(condId).append(" [label=\"Cond\"];\n");
            
            for (AST.Stmt stmt : s.stimulateBody) {
                String childId = traverse(stmt);
                if (childId != null) dot.append("  ").append(id).append(" -> ").append(childId).append(" [label=\"True\", color=green4, fontcolor=green4];\n");
            }
            
            if (s.inhibitBody != null) {
                for (AST.Stmt stmt : s.inhibitBody) {
                    String childId = traverse(stmt);
                    if (childId != null) dot.append("  ").append(id).append(" -> ").append(childId).append(" [label=\"False (Inhibit)\", color=red, fontcolor=red];\n");
                }
            }
        }
        else if (node instanceof AST.CycleStmt) {
            AST.CycleStmt c = (AST.CycleStmt) node;
            dot.append("  ").append(id).append(" [label=\"CYCLE\", fillcolor=coral, shape=diamond];\n");
            String condId = traverse(c.condition);
            if (condId != null) dot.append("  ").append(id).append(" -> ").append(condId).append(" [label=\"Cond\"];\n");
            for (AST.Stmt stmt : c.body) {
                String childId = traverse(stmt);
                if (childId != null) dot.append("  ").append(id).append(" -> ").append(childId).append(" [label=\"Loop\"];\n");
            }
        }
        else if (node instanceof AST.ReactStmt) {
            AST.ReactStmt r = (AST.ReactStmt) node;
            dot.append("  ").append(id).append(" [label=\"REACT\", fillcolor=coral, shape=diamond];\n");
            for (AST.Stmt stmt : r.body) {
                String childId = traverse(stmt);
                if (childId != null) dot.append("  ").append(id).append(" -> ").append(childId).append(" [label=\"Do\"];\n");
            }
            String condId = traverse(r.condition);
            if (condId != null) dot.append("  ").append(id).append(" -> ").append(condId).append(" [label=\"While Cond\"];\n");
        }
        else if (node instanceof AST.EchoStmt) {
            AST.EchoStmt e = (AST.EchoStmt) node;
            dot.append("  ").append(id).append(" [label=\"ECHO\", fillcolor=coral, shape=diamond];\n");
            String initId = traverse(e.init);
            if (initId != null) dot.append("  ").append(id).append(" -> ").append(initId).append(" [label=\"Init\"];\n");
            String condId = traverse(e.condition);
            if (condId != null) dot.append("  ").append(id).append(" -> ").append(condId).append(" [label=\"Cond\"];\n");
            String upId = traverse(e.update);
            if (upId != null) dot.append("  ").append(id).append(" -> ").append(upId).append(" [label=\"Update\"];\n");
            for (AST.Stmt stmt : e.body) {
                String childId = traverse(stmt);
                if (childId != null) dot.append("  ").append(id).append(" -> ").append(childId).append(" [label=\"Body\"];\n");
            }
        }
        else if (node instanceof AST.EvaluateStmt) {
            AST.EvaluateStmt ev = (AST.EvaluateStmt) node;
            dot.append("  ").append(id).append(" [label=\"EVALUATE\", fillcolor=coral, shape=diamond];\n");
            String condId = traverse(ev.condition);
            if (condId != null) dot.append("  ").append(id).append(" -> ").append(condId).append(" [label=\"Target\"];\n");
            
            for (AST.PathCase pc : ev.paths) {
                String matchId = traverse(pc.literal);
                if (matchId != null) {
                    dot.append("  ").append(id).append(" -> ").append(matchId).append(" [label=\"Path Match\"];\n");
                    for (AST.Stmt stmt : pc.body) {
                        String childId = traverse(stmt);
                        if (childId != null) dot.append("  ").append(matchId).append(" -> ").append(childId).append(";\n");
                    }
                    if (pc.hasDormant) {
                        String breakId = "node" + (nodeCounter++);
                        dot.append("  ").append(breakId).append(" [label=\"DORMANT\", shape=octagon, fillcolor=salmon];\n");
                        dot.append("  ").append(matchId).append(" -> ").append(breakId).append(";\n");
                    }
                }
            }
            if (ev.baseCase != null) {
                String baseId = "node" + (nodeCounter++);
                dot.append("  ").append(baseId).append(" [label=\"BASE\", shape=invhouse, fillcolor=khaki];\n");
                dot.append("  ").append(id).append(" -> ").append(baseId).append(" [label=\"Default\"];\n");
                for (AST.Stmt stmt : ev.baseCase) {
                    String childId = traverse(stmt);
                    if (childId != null) dot.append("  ").append(baseId).append(" -> ").append(childId).append(";\n");
                }
            }
        }
        else if (node instanceof AST.FlowControlStmt) {
            AST.FlowControlStmt f = (AST.FlowControlStmt) node;
            dot.append("  ").append(id).append(" [label=\"").append(f.keyword.lexeme.toUpperCase()).append("\", shape=octagon, fillcolor=salmon];\n");
            if (f.returnValue != null) {
                String childId = traverse(f.returnValue);
                if (childId != null) dot.append("  ").append(id).append(" -> ").append(childId).append(" [label=\"Returns\"];\n");
            }
        }

        // --- EXPRESSIONS ---
        else if (node instanceof AST.BinaryExpr) {
            AST.BinaryExpr b = (AST.BinaryExpr) node;
            dot.append("  ").append(id).append(" [label=\"").append(b.operator.lexeme).append("\", fillcolor=orange];\n");
            String leftId = traverse(b.left);
            String rightId = traverse(b.right);
            if (leftId != null) dot.append("  ").append(id).append(" -> ").append(leftId).append(";\n");
            if (rightId != null) dot.append("  ").append(id).append(" -> ").append(rightId).append(";\n");
        }
        else if (node instanceof AST.UnaryExpr) {
            AST.UnaryExpr u = (AST.UnaryExpr) node;
            String pos = u.isPostfix ? " (Postfix)" : " (Prefix)";
            dot.append("  ").append(id).append(" [label=\"").append(u.operator.lexeme).append(pos).append("\", fillcolor=orange];\n");
            String childId = traverse(u.operand);
            if (childId != null) dot.append("  ").append(id).append(" -> ").append(childId).append(";\n");
        }
        else if (node instanceof AST.VariableAccessExpr) {
            AST.VariableAccessExpr v = (AST.VariableAccessExpr) node;
            dot.append("  ").append(id).append(" [label=\"").append(v.name.lexeme).append("\", shape=ellipse];\n");
            for (AST.Expr index : v.indices) {
                String childId = traverse(index);
                if (childId != null) dot.append("  ").append(id).append(" -> ").append(childId).append(" [label=\"Index\"];\n");
            }
        }
        else if (node instanceof AST.LiteralExpr) {
            AST.LiteralExpr l = (AST.LiteralExpr) node;
            dot.append("  ").append(id).append(" [label=\"").append(l.value.lexeme).append("\", shape=ellipse, fillcolor=lightgrey];\n");
        }
        else if (node instanceof AST.CallExpr) {
            AST.CallExpr c = (AST.CallExpr) node;
            dot.append("  ").append(id).append(" [label=\"Call: ").append(c.callee.lexeme).append("()\", fillcolor=plum, shape=cds];\n");
            for (AST.Expr arg : c.arguments) {
                String childId = traverse(arg);
                if (childId != null) dot.append("  ").append(id).append(" -> ").append(childId).append(" [label=\"arg\"];\n");
            }
        }
        
        // --- FALLBACK ---
        else {
            dot.append("  ").append(id).append(" [label=\"").append(node.getClass().getSimpleName()).append("\", color=red];\n");
        }
        
        return id;
    }
}