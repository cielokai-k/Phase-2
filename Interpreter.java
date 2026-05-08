import java.util.ArrayList;
import java.util.List;

public class Interpreter {
    private SymTable symTable;

    public Interpreter(SymTable symTable) {
        this.symTable = symTable;
    }

    public void execute(ASTNode root) {
        evaluateNode(root);
    }

    private Object evaluateNode(ASTNode node) {
        if (node == null) return null;

        if (node instanceof NonTerminalNode) {
            NonTerminalNode nt = (NonTerminalNode) node;
            List<ASTNode> children = nt.children;
            String nodeName = nt.name.trim().toUpperCase();

            switch (nodeName) {
                // =========================================================
                // 1. PROGRAM TRAVERSAL
                // =========================================================
                case "<PROGRAM'>":
                case "PROGRAM":
                case "<PROGRAM_RECOVERED>":
                case "STATEMENT_LIST":
                case "OPT_INHIBIT":       // <--- Add this exact line!
                    for (ASTNode child : children) evaluateNode(child);
                    return null;
                case "SUBROUTINE_LIST":
                    for (ASTNode child : children) evaluateNode(child);
                    return null;

                // =========================================================
                // 2. I/O STATEMENTS
                // =========================================================
                case "IO_STMT":
                    String ioType = extractLexeme(children.get(0)).toLowerCase();
                    if (ioType.equals("express") || ioType.contains("express")) {
                        Object val = evaluateNode(children.get(2));
                        System.out.println(String.valueOf(val).replace("thought_lit:", "").trim());
                    } else if (ioType.equals("sense") || ioType.contains("sense")) {
                        String targetVar = extractLexeme(children.get(2));
                        java.util.Scanner sc = new java.util.Scanner(System.in);
                        if (sc.hasNextInt()) symTable.setValue(targetVar, sc.nextInt());
                        else if (sc.hasNextFloat() || sc.hasNextDouble()) symTable.setValue(targetVar, sc.nextFloat());
                        else symTable.setValue(targetVar, sc.nextLine());
                    }
                    return null;

                // =========================================================
                // 3. VARIABLES & MEMORY
                // =========================================================
                case "CONST_DECL":
                    // AST: [INSTINCT] [DATA_TYPE] [CONST_LIST] [SEMICOLON]
                    String constType = extractLexeme(children.get(1));
                    for (ASTNode c : children) {
                        if (c instanceof NonTerminalNode && ((NonTerminalNode)c).name.trim().equals("CONST_LIST")) {
                            evaluateDeclaration(constType, c, true); // ALWAYS true for instinct
                        }
                    }
                    return null;

                case "ID_DECL":
                    String declDataType = extractLexeme(children.get(0));
                    boolean isInstinct = false;
                    
                    for (ASTNode child : children) {
                        if (extractLexeme(child).toLowerCase().equals("instinct")) isInstinct = true;
                    }
                    
                    for (ASTNode child : children) {
                        if (child instanceof NonTerminalNode && ((NonTerminalNode)child).name.trim().equals("ID_LIST")) {
                            evaluateDeclaration(declDataType, child, isInstinct); // Passes the flag!
                        }
                    }
                    return null;

                case "ASSIGN_STMT":
                    String varName = extractLexeme(children.get(0));
                    String assignOp = extractLexeme(children.get(1)).toUpperCase(); // Grabs the =, +=, -=, etc.
                    Object exprValue = evaluateNode(children.get(2));
                    
                    if (!symTable.contains(varName)) {
                        throw new RuntimeException("Semantic Error: '" + varName + "' not declared.");
                    }
                    
                    if (symTable.isInstinct(varName) && symTable.getValue(varName) != null) {
                        throw new RuntimeException("Semantic Error: Cannot rewrite instinct (constant) variable '" + varName + "'.");
                    }
                    
                    // --- COMPOUND ASSIGNMENT MATH ---
                    // If it's not a standard '=', process the math before saving!
                    if (assignOp.equals("+=") || assignOp.equals("PLUS_ASSIGN")) {
                        exprValue = performMath(symTable.getValue(varName), exprValue, "+");
                    } else if (assignOp.equals("-=") || assignOp.equals("MINUS_ASSIGN")) {
                        exprValue = performMath(symTable.getValue(varName), exprValue, "-");
                    } else if (assignOp.equals("*=") || assignOp.equals("MUL_ASSIGN") || assignOp.equals("STAR_ASSIGN")) {
                        exprValue = performMath(symTable.getValue(varName), exprValue, "*");
                    } else if (assignOp.equals("/=") || assignOp.equals("DIV_ASSIGN") || assignOp.equals("SLASH_ASSIGN")) {
                        exprValue = performMath(symTable.getValue(varName), exprValue, "/");
                    } else if (assignOp.equals("%=") || assignOp.equals("MOD_ASSIGN")) {
                        exprValue = performMath(symTable.getValue(varName), exprValue, "%");
                    }
                    
                    symTable.setValue(varName, exprValue);
                    return null;

                case "CLUSTER_DECL":
                    String clusterType = extractLexeme(children.get(0));
                    evaluateCluster(clusterType, children.get(2));
                    return null;

                case "VARIABLE_ACCESS":
                    // 1. ARRAY ACCESS: (4+ children: [LEFT_SIDE] [ "[" ] [EXPR] [ "]" ])
                    if (children.size() >= 4) {
                        // Recursively evaluate the left side. 
                        // If it's matrix[row][col], this cleanly evaluates matrix[row] first!
                        Object arrayObj = evaluateNode(children.get(0)); 
                        
                        // Evaluate the index number
                        Object indexObj = evaluateNode(children.get(2));
                        int index = (int) Double.parseDouble(indexObj.toString());
                        
                        if (!(arrayObj instanceof java.util.List)) {
                            throw new RuntimeException("Runtime Error: Target is not a cluster.");
                        }
                        
                        java.util.List<?> list = (java.util.List<?>) arrayObj;
                        return list.get(index);
                    }
                    
                    // 2. BASE CASE: It's just a standard variable (e.g., 'matrix' or 'row')
                    String accessName = extractLexeme(children.get(0));
                    Object storedValue = symTable.getValue(accessName);
                    
                    if (storedValue == null) {
                        throw new RuntimeException("Runtime Error: Variable '" + accessName + "' has no value or hasn't been declared.");
                    }
                    return storedValue;

                // =========================================================
                // 4. MATH & EXPRESSIONS
                // =========================================================
                case "ADD_EXPR":
                case "MULT_EXPR":
                case "POW_EXPR":
                    if (children.size() == 1 || isEpsilon(children.get(1))) return evaluateNode(children.get(0));
                    Object leftMath = evaluateNode(children.get(0));
                    String mathOp = extractLexeme(children.get(1));
                    Object rightMath = evaluateNode(children.get(2));
                    return performMath(leftMath, rightMath, mathOp);

                case "POSTFIX_EXPR":
                    if (children.size() == 1) return evaluateNode(children.get(0));
                    String postVar = extractLexeme(children.get(0));
                    String postOp = extractLexeme(children.get(1)).toUpperCase();
                    Object currentVal = symTable.getValue(postVar);
                    if (currentVal == null) return null;
                    double numVal = Double.parseDouble(currentVal.toString());
                    if (postOp.equals("++") || postOp.equals("INCREMENT")) numVal++;
                    if (postOp.equals("--") || postOp.equals("DECREMENT")) numVal--;
                    if (currentVal instanceof Integer) symTable.setValue(postVar, (int) numVal);
                    else symTable.setValue(postVar, (float) numVal);
                    return currentVal;

                case "UNARY_EXPR":
                    if (children.size() == 1) return evaluateNode(children.get(0));
                    String unOp = extractLexeme(children.get(0)).toUpperCase();
                    Object unVal = evaluateNode(children.get(1));
                    
                    // Add logical NOT support! (e.g., !true)
                    if (unOp.equals("!") || unOp.equals("NOT")) return !isTruthy(unVal);
                    
                    try {
                        double dVal = Double.parseDouble(unVal.toString());
                        if (unOp.equals("-") || unOp.equals("MINUS")) dVal = -dVal;
                        if (unVal instanceof Integer) return (int) dVal;
                        return (float) dVal;
                    } catch (Exception e) { return unVal; }

                // =========================================================
                // 5. RELATIONAL & LOGICAL
                // =========================================================
                case "REL_EXPR":
                case "REL_EQUAL":
                    if (children.size() == 1) return evaluateNode(children.get(0));
                    Object leftRel = evaluateNode(children.get(0));
                    String relOp = extractLexeme(children.get(1));
                    Object rightRel = evaluateNode(children.get(2));
                    return performRelationalCheck(leftRel, rightRel, relOp);

                case "LOGIC_OR":
                case "LOGIC_XOR":
                case "LOGIC_AND":
                    if (children.size() == 1 || isEpsilon(children.get(1))) return evaluateNode(children.get(0));
                    
                    // Left-recursive format: [LEFT_EXPR] [OPERATOR] [RIGHT_EXPR]
                    boolean leftBool = isTruthy(evaluateNode(children.get(0)));
                    String logicOp = extractLexeme(children.get(1)).toUpperCase();
                    boolean rightBool = isTruthy(evaluateNode(children.get(2)));
                    
                    if (logicOp.equals("||") || logicOp.equals("OR_OP") || logicOp.equals("OR")) return leftBool || rightBool;
                    if (logicOp.equals("&&") || logicOp.equals("AND_OP") || logicOp.equals("AND")) return leftBool && rightBool;
                    if (logicOp.equals("^") || logicOp.equals("XOR_OP") || logicOp.equals("XOR")) return leftBool ^ rightBool;
                    
                    return leftBool;

                // =========================================================
                // 6. CONTROL FLOW (Loops, Switches, Conditionals)
                // =========================================================
                case "CONDITIONAL_STMT":
                    // stimulate (if) and inhibit (else)
                    Object condition = evaluateNode(children.get(2));
                    if (isTruthy(condition)) evaluateNode(children.get(5));
                    else {
                        if (children.size() > 7 && children.get(7) instanceof NonTerminalNode) {
                            if (!((NonTerminalNode)children.get(7)).children.isEmpty()) {
                                evaluateNode(children.get(7)); // Execute inhibit block
                            }
                        }
                    }
                    return null;

                case "SWITCH_STMT":
                    // evaluate (switch)
                    Object targetValue = evaluateNode(children.get(2));
                    boolean matchFound = evaluateCases(targetValue, children.get(5)); // Evaluates path (cases)
                    
                    if (!matchFound && children.size() > 6) {
                        ASTNode optBase = children.get(6);
                        if (!isEpsilon(optBase) && optBase instanceof NonTerminalNode) {
                            evaluateNode(((NonTerminalNode)optBase).children.get(2)); // Execute base (default)
                        }
                    }
                    return null;

                case "LOOP_STMT":
                    String loopKeyword = extractLexeme(children.get(0)).toLowerCase();
                    try {
                        // 1. THE CYCLE LOOP (While)
                        if (loopKeyword.equals("cycle") || loopKeyword.contains("cycle")) {
                            while (isTruthy(evaluateNode(children.get(2)))) {
                                try { evaluateNode(children.get(5)); }
                                catch (RuntimeException innerE) {
                                    if (innerE.getMessage().equals("CEREBRA_FLOW")) continue; // flow = continue
                                    else throw innerE;
                                }
                            }
                        }
                        // 2. THE ECHO LOOP (For)
                        else if (loopKeyword.equals("echo") || loopKeyword.contains("echo")) {
                            evaluateNode(children.get(2)); // Initialization
                            while (isTruthy(evaluateNode(children.get(3)))) { // Condition
                                try { evaluateNode(children.get(8)); } // Block
                                catch (RuntimeException innerE) {
                                    if (!innerE.getMessage().equals("CEREBRA_FLOW")) throw innerE;
                                }
                                evaluateNode(children.get(5)); // Increment/Decrement
                            }
                        }
                        // 3. THE REACT LOOP (Do-While)
                        else if (loopKeyword.equals("react") || loopKeyword.contains("react")) {
                            // Dynamically grab the block and the condition so it survives parser changes
                            ASTNode reactBody = null;
                            ASTNode reactCondition = null;
                            for (ASTNode c : children) {
                                if (c instanceof NonTerminalNode) {
                                    String nName = ((NonTerminalNode)c).name.trim().toUpperCase();
                                    if (nName.contains("LIST") || nName.equals("STATEMENT")) reactBody = c;
                                    if (nName.equals("EXPR")) reactCondition = c;
                                }
                            }
                            
                            if (reactBody != null && reactCondition != null) {
                                do {
                                    try { evaluateNode(reactBody); } 
                                    catch (RuntimeException innerE) {
                                        if (!innerE.getMessage().equals("CEREBRA_FLOW")) throw innerE;
                                    }
                                } while (isTruthy(evaluateNode(reactCondition)));
                            }
                        }
                    } catch (RuntimeException outerE) {
                        if (!outerE.getMessage().equals("CEREBRA_DORMANT")) throw outerE; // dormant = break
                    }
                    return null;
                // =========================================================
                // 7. SUBROUTINES
                // =========================================================
                case "SUBROUTINE":
                    String funcName = extractLexeme(children.get(2));
                    symTable.addLexeme("FUNC_" + funcName);
                    symTable.setDataType("FUNC_" + funcName, "subroutine");
                    symTable.setValue("FUNC_" + funcName, node);
                    return null;

                case "SUBROUTINE_CALL":
                    String callName = extractLexeme(children.get(0));
                    Object savedFunc = symTable.getValue("FUNC_" + callName);
                    if (savedFunc == null) {
                        throw new RuntimeException("Subroutine '" + callName + "' not found.");
                    }
                    NonTerminalNode fn = (NonTerminalNode) savedFunc;
                    List<Object> args = new ArrayList<>();
                    if (children.size() > 2) extractArguments(children.get(2), args);
                    mapParameters(fn.children.get(4), args);
                    try {
                        evaluateNode(fn.children.get(7));
                    } catch (RuntimeException e) {
                        if (e.getMessage().startsWith("CEREBRA_RETURN:")) {
                            String retStr = e.getMessage().substring(15);
                            try {
                                if (retStr.contains(".")) return Float.parseFloat(retStr);
                                return Integer.parseInt(retStr);
                            } catch (Exception numE) { return retStr; }
                        } else throw e;
                    }
                    return null;

                case "STATEMENT":
                    if (children.isEmpty()) return null;
                    
                    ASTNode firstChild = children.get(0);
                    
                    // 1. Check if the statement is a raw keyword (dormant, flow, recall)
                    if (firstChild instanceof TerminalNode) {
                        String cmd = ((TerminalNode) firstChild).token.lexeme.toLowerCase();
                        
                        if (cmd.equals("dormant")) {
                            throw new RuntimeException("CEREBRA_DORMANT"); // Elevator UP for Break!
                        }
                        if (cmd.equals("flow")) {
                            throw new RuntimeException("CEREBRA_FLOW");    // Elevator UP for Continue!
                        }
                        if (cmd.equals("recall") || cmd.contains("recall")) {
                            Object retVal = evaluateNode(children.get(1));
                            throw new RuntimeException("CEREBRA_RETURN:" + retVal); // Elevator UP for Return!
                        }
                    }
                    
                    // 2. For EVERYTHING else (math, assignments, subroutines), just evaluate it!
                    return evaluateNode(firstChild);

                case "BUILTIN_CALL":
                    String builtInName = extractLexeme(children.get(0)).toLowerCase();
                    if (builtInName.equals("transcribe") || builtInName.contains("transcribe")) {
                        Object innerVal = evaluateNode(children.get(2));
                        return String.valueOf(innerVal).replace("thought_lit:", "").trim();
                    } else if (builtInName.equals("length") || builtInName.contains("length")) {
                        Object innerVal = evaluateNode(children.get(2));
                        return innerVal.toString().length();
                    }
                    return null;

                default:
                    for (ASTNode child : children) {
                        if (!isEpsilon(child)) return evaluateNode(child);
                    }
                    return null;
            }
        } else {
            return extractTerminalValue(node);
        }
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private boolean isEpsilon(ASTNode node) {
        if (node instanceof TerminalNode) return ((TerminalNode)node).token.displayToken().contains("EPSILON");
        return false;
    }

    private boolean isTruthy(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Integer) return ((Integer) value) != 0;
        return value != null;
    }

    // Aggressively strips "ID:" and brackets to guarantee names always match perfectly
    private String extractLexeme(ASTNode node) {
        if (node instanceof TerminalNode) {
            String text = ((TerminalNode) node).token.lexeme;
            return text.replace("ID:", "").replaceAll("[\\[\\]]", "").trim();
        } else if (node instanceof NonTerminalNode) {
            for (ASTNode child : ((NonTerminalNode) node).children) {
                if (!isEpsilon(child)) return extractLexeme(child);
            }
        }
        return "";
    }

    private Object extractTerminalValue(ASTNode node) {
        if (node instanceof TerminalNode) {
            String text = ((TerminalNode) node).token.lexeme;
            if (text.startsWith("\"") && text.endsWith("\"")) text = text.substring(1, text.length() - 1);
            if (text.equals("true")) return true;
            if (text.equals("false")) return false;
            if (text.contains("thought_lit")) return text.replace("thought_lit:", "").trim();
            try {
                if (text.contains(".")) return Float.parseFloat(text);
                return Integer.parseInt(text);
            } catch (Exception e) {
                return text.replaceAll("[\\[\\]]", "").replace("ID:", "").trim();
            }
        }
        return null;
    }

    // The Invincible Sweeper (Now supports Constants!)
    private void evaluateDeclaration(String dataType, ASTNode node, boolean isInstinct) {
        if (node instanceof TerminalNode) return;
        NonTerminalNode nt = (NonTerminalNode) node;
        String name = nt.name.toUpperCase().trim();
        
        // Target variable assignments
        if (name.equals("ID_INIT") || name.equals("ID_ITEM") || name.equals("CONST_INIT")) {
            String varName = extractLexeme(nt.children.get(0));
            symTable.addLexeme(varName);
            symTable.setDataType(varName, dataType);
            
            // Lock it if it's an instinct!
            if (isInstinct) symTable.markAsInstinct(varName);
            
            if (nt.children.size() > 2) {
                Object val = evaluateNode(nt.children.get(2));
                symTable.setValue(varName, val);
            }
        } else {
            // Dig down through any wrappers, safely passing the isInstinct flag
            for (ASTNode child : nt.children) {
                if (!isEpsilon(child)) evaluateDeclaration(dataType, child, isInstinct); // <- 3 arguments!
            }
        }
    }

// Handles both 1D and 2D Arrays
    private void evaluateCluster(String dataType, ASTNode listNode) {
        if (listNode instanceof NonTerminalNode) {
            NonTerminalNode nt = (NonTerminalNode) listNode;
            String name = nt.name.toUpperCase().trim();
            
            if (name.contains("LIST") || name.equals("CLUSTER_ITEM")) {
                for (ASTNode child : nt.children) evaluateCluster(dataType, child);
            } 
            // Handles CLUSTER_1D and CLUSTER_2D
            else if (name.equals("CLUSTER_1D") || name.equals("CLUSTER_2D")) {
                String arrName = extractLexeme(nt.children.get(0));
                symTable.addLexeme(arrName);
                symTable.setDataType(arrName, "cluster " + dataType);
                java.util.List<Object> arrayData = new java.util.ArrayList<>();
                
                // Find the Initialization block
                for (ASTNode child : nt.children) {
                    if (child instanceof NonTerminalNode) {
                        String childName = ((NonTerminalNode)child).name.toUpperCase().trim();
                        if (childName.equals("1D_INIT") || childName.equals("2D_INIT")) {
                            extractClusterValues(((NonTerminalNode)child).children.get(1), arrayData);
                        }
                    }
                }
                symTable.setValue(arrName, arrayData);
            }
        }
    }

// Recursively builds flat arrays OR nested matrices
    // Unwinds left-recursive lists: [LIST] [COMMA] [ITEM]
    private void extractClusterValues(ASTNode node, java.util.List<Object> arrayData) {
        if (node instanceof NonTerminalNode) {
            NonTerminalNode nt = (NonTerminalNode) node;
            String name = nt.name.toUpperCase().trim();
            
            if (name.contains("LIST")) {
                if (nt.children.isEmpty() || isEpsilon(nt.children.get(0))) return;
                
                // Left-Recursive Case: [LIST] [COMMA] [ITEM] (3 children)
                if (nt.children.size() >= 3) {
                    extractClusterValues(nt.children.get(0), arrayData); // Walk down the left side first
                    processClusterItem(nt.children.get(2), arrayData);   // Then process the right item
                } 
                // Base Case: [ITEM] (1 child)
                else if (nt.children.size() == 1) {
                    processClusterItem(nt.children.get(0), arrayData);
                }
            }
        }
    }

    // Helper to separate nested arrays from standard numbers
    private void processClusterItem(ASTNode itemNode, java.util.List<Object> arrayData) {
        // If the item is a nested array (1D_INIT)
        if (itemNode instanceof NonTerminalNode && ((NonTerminalNode)itemNode).name.trim().equals("1D_INIT")) {
            java.util.List<Object> subList = new java.util.ArrayList<>();
            // Extract the inner list (child 1 is between the curly braces)
            extractClusterValues(((NonTerminalNode)itemNode).children.get(1), subList);
            arrayData.add(subList);
        } else {
            // It's a normal number expression, just evaluate and add it!
            arrayData.add(evaluateNode(itemNode));
        }
    }

    private boolean evaluateCases(Object targetValue, ASTNode node) {
        if (node instanceof NonTerminalNode) {
            NonTerminalNode nt = (NonTerminalNode) node;
            String name = nt.name.toUpperCase().trim();
            if (name.equals("CASE_LIST") || name.equals("CASE_TAIL")) {
                for (ASTNode child : nt.children) if (evaluateCases(targetValue, child)) return true;
            } else if (name.equals("CASE_ITEM")) {
                Object caseVal = evaluateNode(nt.children.get(1));
                if (String.valueOf(targetValue).equals(String.valueOf(caseVal))) {
                    try { evaluateNode(nt.children.get(3)); } 
                    catch (RuntimeException e) {
                        if (e.getMessage().equals("CEREBRA_DORMANT")) return true;
                        else throw e;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private Object performMath(Object left, Object right, String op) {
        if (left == null || right == null) return null;
        String lStr = left.toString();
        String rStr = right.toString();
        String opClean = op.toUpperCase().trim();
        
        if ((opClean.equals("+") || opClean.equals("PLUS")) && (left instanceof String || right instanceof String)) {
            return lStr + " " + rStr;
        }
        
        try {
            double lVal = Double.parseDouble(lStr);
            double rVal = Double.parseDouble(rStr);
            double result = 0;
            switch (opClean) {
                case "+": case "PLUS": result = lVal + rVal; break;
                case "-": case "MINUS": result = lVal - rVal; break;
                case "*": case "STAR": result = lVal * rVal; break;
                case "/": case "SLASH": 
                    if (rVal == 0) throw new RuntimeException("Division by zero."); 
                    result = lVal / rVal; break;
                case "%": case "MOD": case "PERCENT": result = lVal % rVal; break;
                case "**": case "EXPONENT": result = Math.pow(lVal, rVal); break;
            }
            if (left instanceof Integer && right instanceof Integer) return (int) result;
            return (float) result;
        } catch (NumberFormatException e) {
            throw new RuntimeException("Semantic Type Error: Math requires numeric types.");
        }
    }

    private Boolean performRelationalCheck(Object left, Object right, String op) {
        if (left == null || right == null) return false;
        String opClean = op.toUpperCase().trim();
        if (opClean.equals("==") || opClean.equals("EQUAL_EQUAL") || opClean.equals("EQUAL")) return left.equals(right);
        if (opClean.equals("!=") || opClean.equals("NOT_EQUAL")) return !left.equals(right);
        try {
            double lVal = Double.parseDouble(left.toString());
            double rVal = Double.parseDouble(right.toString());
            switch (opClean) {
                case ">": case "GREATER": return lVal > rVal;
                case "<": case "LESS": return lVal < rVal;
                case ">=": case "GREATER_EQUAL": return lVal >= rVal;
                case "<=": case "LESS_EQUAL": return lVal <= rVal;
                default: return false;
            }
        } catch (NumberFormatException e) {
            throw new RuntimeException("Semantic Error: Cannot compare > or < on non-numeric types.");
        }
    }

    private void extractArguments(ASTNode node, List<Object> args) {
        if (node instanceof NonTerminalNode) {
            NonTerminalNode nt = (NonTerminalNode) node;
            if (nt.name.equals("EXPR")) args.add(evaluateNode(nt));
            else for (ASTNode child : nt.children) extractArguments(child, args);
        }
    }

    private void mapParameters(ASTNode paramsNode, List<Object> args) {
        List<String> paramNames = new ArrayList<>();
        extractParamNames(paramsNode, paramNames);
        for (int i = 0; i < Math.min(paramNames.size(), args.size()); i++) {
            String pName = paramNames.get(i);
            symTable.addLexeme(pName);
            symTable.setValue(pName, args.get(i));
        }
    }

    private void extractParamNames(ASTNode node, List<String> params) {
        if (node instanceof NonTerminalNode) {
            NonTerminalNode nt = (NonTerminalNode) node;
            if (nt.name.equals("PARAM_ITEM")) params.add(extractLexeme(nt.children.get(1)));
            else for (ASTNode child : nt.children) extractParamNames(child, params);
        }
    }
}