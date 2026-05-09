
import java.util.ArrayList;
import java.util.List;

public class Interpreter {

    private SymTable symTable;
    private int currentLine = 1; // Track current line number for error reporting

    public Interpreter(SymTable symTable) {
        this.symTable = symTable;
    }

    public void execute(ASTNode root) {
        evaluateNode(root);
    }

    private Object evaluateNode(ASTNode node) {
        if (node == null) {
            return null;
        }

        // Update line number from node if available
        updateLineNumber(node);

        try {
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
                    case "OPT_INHIBIT":
                        for (ASTNode child : children) {
                            evaluateNode(child);
                        }
                        return null;
                    case "SUBROUTINE_LIST":
                        for (ASTNode child : children) {
                            evaluateNode(child);
                        }
                        return null;

                    // =========================================================
                    // 2. I/O STATEMENTS
                    // =========================================================
                    case "IO_STMT":
                        return handleIOStatement(children);

                    // =========================================================
                    // 3. VARIABLES & MEMORY
                    // =========================================================
                    case "CONST_DECL":
                        return handleConstDeclaration(children);

                    case "ID_DECL":
                        return handleVariableDeclaration(children);

                    case "ASSIGN_STMT":
                        return handleAssignment(children);

                    case "CLUSTER_DECL":
                        String clusterType = extractLexeme(children.get(0));
                        evaluateCluster(clusterType, children.get(2));
                        return null;

                    case "VARIABLE_ACCESS":
                        return handleVariableAccess(children);

                    // =========================================================
                    // 4. MATH & EXPRESSIONS
                    // =========================================================
                    // FIX 1: Explicit EXPR case so errors inside expressions always
                    // propagate up as InterpreterException instead of being re-wrapped
                    // or silently swallowed by the outer catch in default traversal.
                    case "EXPR":
                        for (ASTNode child : children) {
                            if (!isEpsilon(child)) {
                                return evaluateNode(child);
                            }
                        }
                        return null;

                    case "ADD_EXPR":
                    case "MULT_EXPR":
                    case "POW_EXPR":
                        return handleMathExpression(children);

                    case "POSTFIX_EXPR":
                        return handlePostfixExpression(children);

                    case "UNARY_EXPR":
                        return handleUnaryExpression(children);

                    // =========================================================
                    // 5. RELATIONAL & LOGICAL
                    // =========================================================
                    case "REL_EXPR":
                    case "REL_EQUAL":
                        return handleRelationalExpression(children);

                    case "LOGIC_OR":
                    case "LOGIC_XOR":
                    case "LOGIC_AND":
                        return handleLogicalExpression(children);

                    // =========================================================
                    // 6. CONTROL FLOW (Loops, Switches, Conditionals)
                    // =========================================================
                    case "CONDITIONAL_STMT":
                        return handleConditional(children);

                    case "SWITCH_STMT":
                        return handleSwitch(children);

                    case "LOOP_STMT":
                        return handleLoop(children);

                    // FIX 2: Explicit ECHO_INIT case so for-loop initializer errors
                    // (e.g. duplicate declaration, bad type) surface cleanly instead
                    // of being swallowed by the default traversal's outer catch block.
                    case "ECHO_INIT":
                        for (ASTNode child : children) {
                            if (!isEpsilon(child)) {
                                evaluateNode(child);
                            }
                        }
                        return null;

                    // =========================================================
                    // 7. SUBROUTINES
                    // =========================================================
                    case "SUBROUTINE":
                        return handleSubroutineDefinition(children);

                    case "SUBROUTINE_CALL":
                        return handleSubroutineCall(children);

                    case "STATEMENT":
                        return handleStatement(children);

                    case "BUILTIN_CALL":
                        return handleBuiltinCall(children);

                    default:
                        Object last = null;

                        for (ASTNode child : children) {
                            if (!isEpsilon(child)) {
                                last = evaluateNode(child);
                            }
                        }

                        return last;
                }
            } else {
                return extractTerminalValue(node);
            }
        } catch (InterpreterException e) {
            // Already has line info, rethrow
            throw e;
        } catch (Exception e) {
            // Wrap with line info
            throw new InterpreterException("Line " + currentLine + ": Runtime error - " + e.getMessage(), currentLine, e);
        }
    }

    // =========================================================
    // HANDLER METHODS WITH ENHANCED ERROR REPORTING
    // =========================================================
    private Object handleIOStatement(List<ASTNode> children) {
        String ioType = extractLexeme(children.get(0)).toLowerCase();
        try {
            if (ioType.equals("express") || ioType.contains("express")) {
                Object val = evaluateNode(children.get(2));
                System.out.println(String.valueOf(val).replace("thought_lit:", "").trim());
            } else if (ioType.equals("sense") || ioType.contains("sense")) {
                String targetVar = extractLexeme(children.get(2));
                if (!symTable.contains(targetVar)) {
                    throw new InterpreterException(
                            "Line " + currentLine + ": Variable '" + targetVar + "' must be declared before use in 'sense' statement",
                            currentLine
                    );
                }
                java.util.Scanner sc = new java.util.Scanner(System.in);
                if (sc.hasNextInt()) {
                    symTable.setValue(targetVar, sc.nextInt());
                } else if (sc.hasNextFloat() || sc.hasNextDouble()) {
                    symTable.setValue(targetVar, sc.nextFloat());
                } else {
                    symTable.setValue(targetVar, sc.nextLine());
                }
            }
        } catch (InterpreterException e) {
            throw e;
        } catch (Exception e) {
            throw new InterpreterException(
                    "Line " + currentLine + ": I/O operation failed - " + e.getMessage(),
                    currentLine,
                    e
            );
        }
        return null;
    }

    private Object handleConstDeclaration(List<ASTNode> children) {
        String constType = extractLexeme(children.get(1));
        for (ASTNode c : children) {
            if (c instanceof NonTerminalNode && ((NonTerminalNode) c).name.trim().equals("CONST_LIST")) {
                evaluateDeclaration(constType, c, true);
            }
        }
        return null;
    }

    private Object handleVariableDeclaration(List<ASTNode> children) {
        String declDataType = extractLexeme(children.get(0));

        // FIX 3: ID_DECL has two forms in the grammar:
        //   (a) DATA_TYPE CLUSTER CLUSTER_LIST SEMICOLON  → cluster declaration
        //   (b) DATA_TYPE ID_LIST SEMICOLON               → plain variable declaration
        //
        // The parser emits both as ID_DECL nodes. Detect the cluster form by checking
        // whether child[1] is the CLUSTER keyword token, and delegate accordingly.
        // Without this check, cluster declarations silently matched nothing (no ID_LIST
        // child found), the variable was never stored, and all subsequent statements
        // that referenced it were dropped by parser error recovery — making divide-by-zero
        // and array-out-of-bounds unreachable by the interpreter entirely.
        if (children.size() >= 3 && extractLexeme(children.get(1)).equalsIgnoreCase("cluster")) {
            evaluateCluster(declDataType, children.get(2));
            return null;
        }

        // Plain variable declaration
        boolean isInstinct = false;
        for (ASTNode child : children) {
            if (extractLexeme(child).toLowerCase().equals("instinct")) {
                isInstinct = true;
            }
        }

        for (ASTNode child : children) {
            if (child instanceof NonTerminalNode
                    && ((NonTerminalNode) child).name.trim().equals("ID_LIST")) {
                evaluateDeclaration(declDataType, child, isInstinct);
            }
        }
        return null;
    }

    private Object handleAssignment(List<ASTNode> children) {
        String varName = extractLexeme(children.get(0));
        String assignOp = extractLexeme(children.get(1)).toUpperCase();
        Object exprValue;

        try {
            exprValue = evaluateNode(children.get(2));
        } catch (Exception e) {
            throw new InterpreterException(
                    "Line " + currentLine + ": Error evaluating expression in assignment to '" + varName + "' - " + e.getMessage(),
                    currentLine,
                    e
            );
        }

        if (!symTable.contains(varName)) {
            String inferredType = inferCerebraType(exprValue);
            symTable.addLexeme(varName);
            symTable.setDataType(varName, inferredType);
            System.out.println("[Warning] Line " + currentLine + ": Auto-declared missing variable '" + varName + "' as a "
                    + inferredType + ".");
        }

        if (symTable.isInstinct(varName) && symTable.getValue(varName) != null) {
            throw new InterpreterException(
                    "Line " + currentLine + ": Cannot reassign constant (instinct) variable '" + varName + "'. Constants are immutable once initialized.",
                    currentLine
            );
        }

        // Compound assignment math
        try {
            if (assignOp.equals("+=") || assignOp.equals("PLUS_ASSIGN")) {
                exprValue = performMath(symTable.getValue(varName), exprValue, "+");
            } else if (assignOp.equals("-=") || assignOp.equals("MINUS_ASSIGN")) {
                exprValue = performMath(symTable.getValue(varName), exprValue, "-");
            } else if (assignOp.equals("*=") || assignOp.equals("MUL_ASSIGN")
                    || assignOp.equals("STAR_ASSIGN")) {
                exprValue = performMath(symTable.getValue(varName), exprValue, "*");
            } else if (assignOp.equals("/=") || assignOp.equals("DIV_ASSIGN")
                    || assignOp.equals("SLASH_ASSIGN")) {
                exprValue = performMath(symTable.getValue(varName), exprValue, "/");
            } else if (assignOp.equals("%=") || assignOp.equals("MOD_ASSIGN")) {
                exprValue = performMath(symTable.getValue(varName), exprValue, "%");
            }
        } catch (Exception e) {
            throw new InterpreterException(
                    "Line " + currentLine + ": Error in compound assignment operation '" + assignOp + "' for variable '" + varName + "' - " + e.getMessage(),
                    currentLine,
                    e
            );
        }

        // Type coercion
        String expectedType = symTable.getDataType(varName);
        if (expectedType != null) {
            expectedType = expectedType.toLowerCase().trim();
            try {
                if (expectedType.equals("pulse") || expectedType.equals("int")) {
                    exprValue = (int) Double.parseDouble(exprValue.toString());
                } else if (expectedType.equals("synapse") || expectedType.equals("boolean")) {
                    exprValue = isTruthy(exprValue);
                }
            } catch (Exception e) {
                throw new InterpreterException(
                        "Line " + currentLine + ": Cannot convert value to type '" + expectedType + "' for variable '" + varName + "'",
                        currentLine,
                        e
                );
            }
        }

        symTable.setValue(varName, exprValue);
        return null;
    }

    private Object handleVariableAccess(List<ASTNode> children) {
        // Array access: [LEFT_SIDE] [ "[" ] [EXPR] [ "]" ]
        if (children.size() >= 4) {
            Object arrayObj = evaluateNode(children.get(0));
            Object indexObj;

            try {
                indexObj = evaluateNode(children.get(2));
            } catch (Exception e) {
                throw new InterpreterException(
                        "Line " + currentLine + ": Error evaluating array index - " + e.getMessage(),
                        currentLine,
                        e
                );
            }

            int index;
            try {
                index = (int) Double.parseDouble(indexObj.toString());
            } catch (Exception e) {
                throw new InterpreterException(
                        "Line " + currentLine + ": Array index must be a numeric value, got: " + indexObj,
                        currentLine,
                        e
                );
            }

            if (!(arrayObj instanceof java.util.List)) {
                throw new InterpreterException(
                        "Line " + currentLine + ": Cannot use array indexing on non-cluster type",
                        currentLine
                );
            }

            java.util.List<?> list = (java.util.List<?>) arrayObj;

            if (index < 0 || index >= list.size()) {
                throw new InterpreterException(
                        "Line " + currentLine + ": Array index out of bounds. Index: " + index + ", Array size: " + list.size(),
                        currentLine
                );
            }

            return list.get(index);
        }

        // Base case: standard variable
        String accessName = extractLexeme(children.get(0));

        if (!symTable.contains(accessName)) {
            throw new InterpreterException(
                    "Line " + currentLine + ": Variable '" + accessName + "' has not been declared. Please declare it before use.",
                    currentLine
            );
        }

        return symTable.getValue(accessName);
    }

    private Object handleMathExpression(List<ASTNode> children) {
        if (children.size() == 1 || isEpsilon(children.get(1))) {
            return evaluateNode(children.get(0));
        }

        Object leftMath = evaluateNode(children.get(0));
        String mathOp = extractLexeme(children.get(1));
        Object rightMath = evaluateNode(children.get(2));

        return performMath(leftMath, rightMath, mathOp);
    }

    private Object handlePostfixExpression(List<ASTNode> children) {
        if (children.size() == 1) {
            return evaluateNode(children.get(0));
        }

        String postVar = extractLexeme(children.get(0));
        String postOp = extractLexeme(children.get(1)).toUpperCase();

        if (!symTable.contains(postVar)) {
            throw new InterpreterException(
                    "Line " + currentLine + ": Cannot apply postfix operator '" + postOp + "' to undeclared variable '" + postVar + "'",
                    currentLine
            );
        }

        Object currentVal = symTable.getValue(postVar);
        if (currentVal == null) {
            throw new InterpreterException(
                    "Line " + currentLine + ": Cannot apply postfix operator '" + postOp + "' to uninitialized variable '" + postVar + "'",
                    currentLine
            );
        }

        try {
            double numVal = Double.parseDouble(currentVal.toString());
            if (postOp.equals("++") || postOp.equals("INCREMENT")) {
                numVal++;
            }
            if (postOp.equals("--") || postOp.equals("DECREMENT")) {
                numVal--;
            }
            if (currentVal instanceof Integer) {
                symTable.setValue(postVar, (int) numVal);
            } else {
                symTable.setValue(postVar, (float) numVal);
            }
            return currentVal;
        } catch (NumberFormatException e) {
            throw new InterpreterException(
                    "Line " + currentLine + ": Postfix operator '" + postOp + "' requires numeric type, but variable '" + postVar + "' has value: " + currentVal,
                    currentLine,
                    e
            );
        }
    }

    private Object handleUnaryExpression(List<ASTNode> children) {
        if (children.size() == 1) {
            return evaluateNode(children.get(0));
        }

        String unOp = extractLexeme(children.get(0)).toUpperCase();
        Object unVal = evaluateNode(children.get(1));

        // Logical NOT
        if (unOp.equals("!") || unOp.equals("NOT")) {
            return !isTruthy(unVal);
        }

        // Unary minus
        try {
            double dVal = Double.parseDouble(unVal.toString());
            if (unOp.equals("-") || unOp.equals("MINUS")) {
                dVal = -dVal;
            }
            if (unVal instanceof Integer) {
                return (int) dVal;
            }
            return (float) dVal;
        } catch (NumberFormatException e) {
            throw new InterpreterException(
                    "Line " + currentLine + ": Unary operator '" + unOp + "' requires numeric value, got: " + unVal,
                    currentLine,
                    e
            );
        }
    }

    private Object handleRelationalExpression(List<ASTNode> children) {
        if (children.size() == 1) {
            return evaluateNode(children.get(0));
        }

        Object leftRel = evaluateNode(children.get(0));
        String relOp = extractLexeme(children.get(1));
        Object rightRel = evaluateNode(children.get(2));

        return performRelationalCheck(leftRel, rightRel, relOp);
    }

    private Object handleLogicalExpression(List<ASTNode> children) {
        if (children.size() == 1 || isEpsilon(children.get(1))) {
            return evaluateNode(children.get(0));
        }

        boolean leftBool = isTruthy(evaluateNode(children.get(0)));
        String logicOp = extractLexeme(children.get(1)).toUpperCase();
        boolean rightBool = isTruthy(evaluateNode(children.get(2)));

        if (logicOp.equals("||") || logicOp.equals("OR_OP") || logicOp.equals("OR")) {
            return leftBool || rightBool;
        }
        if (logicOp.equals("&&") || logicOp.equals("AND_OP") || logicOp.equals("AND")) {
            return leftBool && rightBool;
        }
        if (logicOp.equals("^") || logicOp.equals("XOR_OP") || logicOp.equals("XOR")) {
            return leftBool ^ rightBool;
        }

        return leftBool;
    }

    private Object handleConditional(List<ASTNode> children) {
        Object condition = evaluateNode(children.get(2));
        if (isTruthy(condition)) {
            evaluateNode(children.get(5));
        } else {
            if (children.size() > 7 && children.get(7) instanceof NonTerminalNode) {
                if (!((NonTerminalNode) children.get(7)).children.isEmpty()) {
                    evaluateNode(children.get(7));
                }
            }
        }
        return null;
    }

    private Object handleSwitch(List<ASTNode> children) {
        Object targetValue = evaluateNode(children.get(2));
        boolean matchFound = evaluateCases(targetValue, children.get(5));

        if (!matchFound && children.size() > 6) {
            ASTNode optBase = children.get(6);
            if (!isEpsilon(optBase) && optBase instanceof NonTerminalNode) {
                evaluateNode(((NonTerminalNode) optBase).children.get(2));
            }
        }
        return null;
    }

    private Object handleLoop(List<ASTNode> children) {
        String loopKeyword = extractLexeme(children.get(0)).toLowerCase();
        try {
            // CYCLE loop (while)
            if (loopKeyword.equals("cycle") || loopKeyword.contains("cycle")) {
                int iterationCount = 0;
                int maxIterations = 1000000; // Prevent infinite loops

                while (isTruthy(evaluateNode(children.get(2)))) {
                    if (++iterationCount > maxIterations) {
                        throw new InterpreterException(
                                "Line " + currentLine + ": Infinite loop detected in 'cycle' loop (exceeded " + maxIterations + " iterations)",
                                currentLine
                        );
                    }
                    try {
                        evaluateNode(children.get(5));
                    } catch (RuntimeException e) {
                        if (e.getMessage().startsWith("CEREBRA_")) {
                            throw e;
                        }
                        throw new InterpreterException("Line " + currentLine + ": " + e.getMessage(), currentLine);
                    }
                }
            } // ECHO loop (for)
            else if (loopKeyword.equals("echo") || loopKeyword.contains("echo")) {
                evaluateNode(children.get(2)); // Initialization
                int iterationCount = 0;
                int maxIterations = 1000000;

                while (isTruthy(evaluateNode(children.get(3)))) {
                    if (++iterationCount > maxIterations) {
                        throw new InterpreterException(
                                "Line " + currentLine + ": Infinite loop detected in 'echo' loop (exceeded " + maxIterations + " iterations)",
                                currentLine
                        );
                    }
                    try {
                        evaluateNode(children.get(8));
                    } catch (RuntimeException innerE) {
                        if (!innerE.getMessage().equals("CEREBRA_FLOW")) {
                            throw innerE;
                        }
                    }
                    evaluateNode(children.get(5));
                }
            } // REACT loop (do-while)
            else if (loopKeyword.equals("react") || loopKeyword.contains("react")) {
                ASTNode reactBody = null;
                ASTNode reactCondition = null;
                for (ASTNode c : children) {
                    if (c instanceof NonTerminalNode) {
                        String nName = ((NonTerminalNode) c).name.trim().toUpperCase();
                        if (nName.contains("LIST") || nName.equals("STATEMENT")) {
                            reactBody = c;
                        }
                        if (nName.equals("EXPR")) {
                            reactCondition = c;
                        }
                    }
                }

                if (reactBody != null && reactCondition != null) {
                    int iterationCount = 0;
                    int maxIterations = 1000000;

                    do {
                        if (++iterationCount > maxIterations) {
                            throw new InterpreterException(
                                    "Line " + currentLine + ": Infinite loop detected in 'react' loop (exceeded " + maxIterations + " iterations)",
                                    currentLine
                            );
                        }
                        try {
                            evaluateNode(reactBody);
                        } catch (RuntimeException innerE) {
                            if (!innerE.getMessage().equals("CEREBRA_FLOW")) {
                                throw innerE;
                            }
                        }
                    } while (isTruthy(evaluateNode(reactCondition)));
                }
            }
        } catch (RuntimeException outerE) {
            if (!outerE.getMessage().equals("CEREBRA_DORMANT")) {
                throw outerE;
            }
        }
        return null;
    }

    private Object handleSubroutineDefinition(List<ASTNode> children) {
        String funcName = extractLexeme(children.get(2));
        symTable.addLexeme("FUNC_" + funcName);
        symTable.setDataType("FUNC_" + funcName, "subroutine");
        symTable.setValue("FUNC_" + funcName, children); // Store the children list instead of node
        return null;
    }

    private Object handleSubroutineCall(List<ASTNode> children) {
        String callName = extractLexeme(children.get(0));
        Object savedFunc = symTable.getValue("FUNC_" + callName);

        if (savedFunc == null) {
            throw new InterpreterException(
                    "Line " + currentLine + ": Subroutine '" + callName + "' has not been defined. Ensure the subroutine is declared before calling it.",
                    currentLine
            );
        }

        @SuppressWarnings("unchecked")
        List<ASTNode> funcChildren = (List<ASTNode>) savedFunc;
        List<Object> args = new ArrayList<>();

        if (children.size() > 2) {
            extractArguments(children.get(2), args);
        }

        try {
            mapParameters(funcChildren.get(4), args);
            evaluateNode(funcChildren.get(7));
        } catch (RuntimeException e) {
            // FIX 4: Check InterpreterException FIRST before CEREBRA_RETURN.
            // Previously the order was reversed: CEREBRA_RETURN was checked first,
            // which meant an InterpreterException (e.g. divide-by-zero thrown inside
            // performMath) whose message didn't start with "CEREBRA_RETURN:" fell
            // through to the generic re-wrap, losing the original error context.
            // Now InterpreterException is always re-thrown immediately, preserving
            // the exact line number and message from where the error originated.
            if (e instanceof InterpreterException) {
                throw e;
            }
            if (e.getMessage() != null && e.getMessage().startsWith("CEREBRA_RETURN:")) {
                String retStr = e.getMessage().substring(15);
                try {
                    if (retStr.contains(".")) {
                        return Float.parseFloat(retStr);
                    }
                    return Integer.parseInt(retStr);
                } catch (Exception numE) {
                    return retStr;
                }
            } else {
                throw new InterpreterException(
                        "Line " + currentLine + ": Error in subroutine '" + callName + "' - " + e.getMessage(),
                        currentLine,
                        e
                );
            }
        }
        return null;
    }

    private Object handleStatement(List<ASTNode> children) {
        if (children.isEmpty()) {
            return null;
        }

        ASTNode firstChild = children.get(0);

        if (firstChild instanceof TerminalNode) {
            String cmd = ((TerminalNode) firstChild).token.lexeme.toLowerCase();

            if (cmd.equals("dormant")) {
                throw new RuntimeException("CEREBRA_DORMANT");
            }
            if (cmd.equals("flow")) {
                throw new RuntimeException("CEREBRA_FLOW");
            }
            if (cmd.equals("recall") || cmd.contains("recall")) {
                Object retVal = evaluateNode(children.get(1));
                throw new RuntimeException("CEREBRA_RETURN:" + retVal);
            }
        }

        return evaluateNode(firstChild);
    }

    private Object handleBuiltinCall(List<ASTNode> children) {
        String builtInName = extractLexeme(children.get(0)).toLowerCase();

        try {
            if (builtInName.equals("transcribe") || builtInName.contains("transcribe")) {
                Object innerVal = evaluateNode(children.get(2));
                return String.valueOf(innerVal).replace("thought_lit:", "").trim();
            } else if (builtInName.equals("length") || builtInName.contains("length")) {
                Object innerVal = evaluateNode(children.get(2));
                if (innerVal == null) {
                    throw new InterpreterException(
                            "Line " + currentLine + ": Cannot get length of null value",
                            currentLine
                    );
                }
                return innerVal.toString().length();
            }
        } catch (InterpreterException e) {
            throw e;
        } catch (Exception e) {
            throw new InterpreterException(
                    "Line " + currentLine + ": Error in builtin function '" + builtInName + "' - " + e.getMessage(),
                    currentLine,
                    e
            );
        }
        return null;
    }

    // =========================================================
    // HELPERS
    // =========================================================
    private void updateLineNumber(ASTNode node) {
        if (node instanceof TerminalNode) {
            TerminalNode tn = (TerminalNode) node;
            if (tn.token != null && tn.token.line > 0) {
                currentLine = tn.token.line;
            }
        } else if (node instanceof NonTerminalNode) {
            NonTerminalNode nt = (NonTerminalNode) node;
            // Try to get line from first terminal child
            for (ASTNode child : nt.children) {
                if (child instanceof TerminalNode) {
                    TerminalNode tn = (TerminalNode) child;
                    if (tn.token != null && tn.token.line > 0) {
                        currentLine = tn.token.line;
                        break;
                    }
                }
            }
        }
    }

    private boolean isEpsilon(ASTNode node) {
        if (node instanceof TerminalNode) {
            return ((TerminalNode) node).token.displayToken().contains("EPSILON");
        }
        return false;
    }

    private boolean isTruthy(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value) != 0;
        }
        return value != null;
    }

    private String extractLexeme(ASTNode node) {
        if (node instanceof TerminalNode) {
            String text = ((TerminalNode) node).token.lexeme;
            return text.replace("ID:", "").replaceAll("[\\[\\]]", "").trim();
        } else if (node instanceof NonTerminalNode) {
            for (ASTNode child : ((NonTerminalNode) node).children) {
                if (!isEpsilon(child)) {
                    return extractLexeme(child);
                }
            }
        }
        return "";
    }

    private Object extractTerminalValue(ASTNode node) {
        if (node instanceof TerminalNode) {
            String text = ((TerminalNode) node).token.lexeme;
            if (text.startsWith("\"") && text.endsWith("\"")) {
                text = text.substring(1, text.length() - 1);
            }
            if (text.equals("true")) {
                return true;
            }
            if (text.equals("false")) {
                return false;
            }
            if (text.contains("thought_lit")) {
                return text.replace("thought_lit:", "").trim();
            }
            try {
                if (text.contains(".")) {
                    return Float.parseFloat(text);
                }
                return Integer.parseInt(text);
            } catch (Exception e) {
                return text.replaceAll("[\\[\\]]", "").replace("ID:", "").trim();
            }
        }
        return null;
    }

    private void evaluateDeclaration(String dataType, ASTNode node, boolean isInstinct) {
        if (node instanceof TerminalNode) {
            return;
        }
        NonTerminalNode nt = (NonTerminalNode) node;
        String name = nt.name.toUpperCase().trim();

        if (name.equals("ID_INIT") || name.equals("ID_ITEM") || name.equals("CONST_INIT")) {
            String varName = extractLexeme(nt.children.get(0));

            if (symTable.contains(varName)) {
                throw new InterpreterException(
                        "Line " + currentLine + ": Variable '" + varName + "' has already been declared. Duplicate declarations are not allowed.",
                        currentLine
                );
            }

            symTable.addLexeme(varName);
            symTable.setDataType(varName, dataType);

            if (isInstinct) {
                symTable.markAsInstinct(varName);
            }

            if (nt.children.size() > 2) {
                try {
                    Object val = evaluateNode(nt.children.get(2));
                    symTable.setValue(varName, val);
                } catch (Exception e) {
                    throw new InterpreterException(
                            "Line " + currentLine + ": Error initializing variable '" + varName + "' - " + e.getMessage(),
                            currentLine,
                            e
                    );
                }
            } else {
                Object defaultValue = getDefaultValueForType(dataType);
                symTable.setValue(varName, defaultValue);
            }
        } else {
            for (ASTNode child : nt.children) {
                if (!isEpsilon(child)) {
                    evaluateDeclaration(dataType, child, isInstinct);
                }
            }
        }
    }

    private void evaluateCluster(String dataType, ASTNode listNode) {
        if (listNode instanceof NonTerminalNode) {
            NonTerminalNode nt = (NonTerminalNode) listNode;
            String name = nt.name.toUpperCase().trim();

            if (name.contains("LIST") || name.equals("CLUSTER_ITEM")) {
                for (ASTNode child : nt.children) {
                    evaluateCluster(dataType, child);
                }
            } else if (name.equals("CLUSTER_1D") || name.equals("CLUSTER_2D")) {
                String arrName = extractLexeme(nt.children.get(0));

                if (symTable.contains(arrName)) {
                    throw new InterpreterException(
                            "Line " + currentLine + ": Cluster '" + arrName + "' has already been declared",
                            currentLine
                    );
                }

                symTable.addLexeme(arrName);
                symTable.setDataType(arrName, "cluster " + dataType);
                java.util.List<Object> arrayData = new java.util.ArrayList<>();

                for (ASTNode child : nt.children) {
                    if (child instanceof NonTerminalNode) {
                        String childName = ((NonTerminalNode) child).name.toUpperCase().trim();
                        if (childName.equals("1D_INIT") || childName.equals("2D_INIT")) {
                            try {
                                extractClusterValues(((NonTerminalNode) child).children.get(1), arrayData);
                            } catch (Exception e) {
                                throw new InterpreterException(
                                        "Line " + currentLine + ": Error initializing cluster '" + arrName + "' - " + e.getMessage(),
                                        currentLine,
                                        e
                                );
                            }
                        }
                    }
                }
                symTable.setValue(arrName, arrayData);
            }
        }
    }

    private void extractClusterValues(ASTNode node, java.util.List<Object> arrayData) {
        if (node instanceof NonTerminalNode) {
            NonTerminalNode nt = (NonTerminalNode) node;
            String name = nt.name.toUpperCase().trim();

            if (name.contains("LIST")) {
                if (nt.children.isEmpty() || isEpsilon(nt.children.get(0))) {
                    return;
                }

                if (nt.children.size() >= 3) {
                    extractClusterValues(nt.children.get(0), arrayData);
                    processClusterItem(nt.children.get(2), arrayData);
                } else if (nt.children.size() == 1) {
                    processClusterItem(nt.children.get(0), arrayData);
                }
            }
        }
    }

    private void processClusterItem(ASTNode itemNode, java.util.List<Object> arrayData) {
        if (itemNode instanceof NonTerminalNode && ((NonTerminalNode) itemNode).name.trim().equals("1D_INIT")) {
            java.util.List<Object> subList = new java.util.ArrayList<>();
            extractClusterValues(((NonTerminalNode) itemNode).children.get(1), subList);
            arrayData.add(subList);
        } else {
            arrayData.add(evaluateNode(itemNode));
        }
    }

    private boolean evaluateCases(Object targetValue, ASTNode node) {
        if (node instanceof NonTerminalNode) {
            NonTerminalNode nt = (NonTerminalNode) node;
            String name = nt.name.toUpperCase().trim();
            if (name.equals("CASE_LIST") || name.equals("CASE_TAIL")) {
                for (ASTNode child : nt.children) {
                    if (evaluateCases(targetValue, child)) {
                        return true;
                    }
                }
            } else if (name.equals("CASE_ITEM")) {
                Object caseVal = evaluateNode(nt.children.get(1));
                if (String.valueOf(targetValue).equals(String.valueOf(caseVal))) {
                    try {
                        evaluateNode(nt.children.get(3));
                    } catch (RuntimeException e) {
                        if (e.getMessage().equals("CEREBRA_DORMANT")) {
                            return true;
                        } else {
                            throw e;
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private Object performMath(Object left, Object right, String op) {
        if (left == null || right == null) {
            throw new InterpreterException(
                    "Line " + currentLine + ": Cannot perform math operation '" + op + "' with null values",
                    currentLine
            );
        }

        String lStr = left.toString();
        String rStr = right.toString();
        String opClean = op.toUpperCase().trim();

        if ((opClean.equals("+") || opClean.equals("PLUS")) && (left instanceof String || right instanceof String)) {
            return lStr + " " + rStr;
        }

        try {
            double lVal = autoCastToNumeric(left);
            double rVal = autoCastToNumeric(right);
            double result = 0;

            switch (opClean) {
                case "+":
                case "PLUS":
                    result = lVal + rVal;
                    break;
                case "-":
                case "MINUS":
                    result = lVal - rVal;
                    break;
                case "*":
                case "STAR":
                    result = lVal * rVal;
                    break;
                case "/":
                case "SLASH":
                    if (rVal == 0) {
                        throw new InterpreterException(
                                "Line " + currentLine + ": Division by zero detected. Cannot divide " + lVal + " by 0.",
                                currentLine
                        );
                    }
                    result = lVal / rVal;
                    break;
                case "%":
                case "MOD":
                case "PERCENT":
                    if (rVal == 0) {
                        throw new InterpreterException(
                                "Line " + currentLine + ": Modulo by zero detected. Cannot compute " + lVal + " % 0.",
                                currentLine
                        );
                    }
                    result = lVal % rVal;
                    break;
                case "**":
                case "EXPONENT":
                    result = Math.pow(lVal, rVal);
                    break;
                default:
                    throw new InterpreterException(
                            "Line " + currentLine + ": Unknown math operator: " + op,
                            currentLine
                    );
            }

            if (left instanceof Integer && right instanceof Integer) {
                return (int) result;
            }
            return (float) result;
        } catch (InterpreterException e) {
            throw e;
        } catch (NumberFormatException e) {
            throw new InterpreterException(
                    "Line " + currentLine + ": Type error - Math operation '" + op + "' requires numeric types. Got: " + left.getClass().getSimpleName() + " and " + right.getClass().getSimpleName(),
                    currentLine,
                    e
            );
        }
    }

    private Boolean performRelationalCheck(Object left, Object right, String op) {
        if (left == null || right == null) {
            throw new InterpreterException(
                    "Line " + currentLine + ": Cannot compare null values using operator '" + op + "'",
                    currentLine
            );
        }

        String opClean = op.toUpperCase().trim();

        if (opClean.equals("==") || opClean.equals("EQUAL_EQUAL") || opClean.equals("EQUAL")) {
            return left.equals(right);
        }
        if (opClean.equals("!=") || opClean.equals("NOT_EQUAL")) {
            return !left.equals(right);
        }

        try {
            double lVal = Double.parseDouble(left.toString());
            double rVal = Double.parseDouble(right.toString());
            switch (opClean) {
                case ">":
                case "GREATER":
                    return lVal > rVal;
                case "<":
                case "LESS":
                    return lVal < rVal;
                case ">=":
                case "GREATER_EQUAL":
                    return lVal >= rVal;
                case "<=":
                case "LESS_EQUAL":
                    return lVal <= rVal;
                default:
                    throw new InterpreterException(
                            "Line " + currentLine + ": Unknown relational operator: " + op,
                            currentLine
                    );
            }
        } catch (NumberFormatException e) {
            throw new InterpreterException(
                    "Line " + currentLine + ": Type error - Relational operator '" + op + "' requires numeric types for comparison. Got: " + left + " and " + right,
                    currentLine,
                    e
            );
        }
    }

    private void extractArguments(ASTNode node, List<Object> args) {
        if (node instanceof NonTerminalNode) {
            NonTerminalNode nt = (NonTerminalNode) node;
            if (nt.name.equals("EXPR")) {
                args.add(evaluateNode(nt));
            } else {
                for (ASTNode child : nt.children) {
                    extractArguments(child, args);
                }
            }
        }
    }

    private void mapParameters(ASTNode paramsNode, List<Object> args) {
        List<String> paramNames = new ArrayList<>();
        extractParamNames(paramsNode, paramNames);

        if (paramNames.size() != args.size()) {
            throw new InterpreterException(
                    "Line " + currentLine + ": Subroutine parameter mismatch. Expected " + paramNames.size() + " arguments but got " + args.size(),
                    currentLine
            );
        }

        for (int i = 0; i < Math.min(paramNames.size(), args.size()); i++) {
            String pName = paramNames.get(i);
            symTable.addLexeme(pName);
            symTable.setValue(pName, args.get(i));
        }
    }

    private void extractParamNames(ASTNode node, List<String> params) {
        if (node instanceof NonTerminalNode) {
            NonTerminalNode nt = (NonTerminalNode) node;
            if (nt.name.equals("PARAM_ITEM")) {
                params.add(extractLexeme(nt.children.get(1)));
            } else {
                for (ASTNode child : nt.children) {
                    extractParamNames(child, params);
                }
            }
        }
    }

    private Object getDefaultValueForType(String dataType) {
        String typeClean = dataType.toLowerCase().trim();

        switch (typeClean) {
            case "pulse":
                return 0;
            case "spark":
                return 0.0f;
            case "stream":
                return 0.0;
            case "synapse":
                return false;
            case "neuron":
                return '\u0000';
            case "thought":
                return null;
            default:
                return null;
        }
    }

    private double autoCastToNumeric(Object val) throws NumberFormatException {
        if (val == null) {
            return 0.0;
        }

        if (val instanceof Boolean) {
            return ((Boolean) val) ? 1.0 : 0.0;
        }

        if (val instanceof Character) {
            return (double) ((Character) val);
        }

        return Double.parseDouble(val.toString());
    }

    private String inferCerebraType(Object val) {
        if (val instanceof Integer) {
            return "pulse";
        }
        if (val instanceof Float) {
            return "spark";
        }
        if (val instanceof Double) {
            return "stream";
        }
        if (val instanceof Boolean) {
            return "synapse";
        }
        if (val instanceof Character) {
            return "neuron";
        }
        if (val instanceof String) {
            return "thought";
        }
        if (val instanceof java.util.List) {
            return "cluster";
        }

        return "thought";
    }
}
