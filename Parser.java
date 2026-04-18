
import java.util.*;
import java.util.stream.Collectors;

public class Parser {

    private Scanner scanner;
    private ParseTable table;
    private Stack<Integer> stateStack = new Stack<>();
    private Stack<ASTNode> symbolStack = new Stack<>();
    private boolean errorOccurred = false;

    private static final Set<TokenType> STATEMENT_STARTERS = new HashSet<>();

    static {
        STATEMENT_STARTERS.addAll(Arrays.asList(
                TokenType.PULSE, TokenType.STIMULATE, TokenType.CYCLE,
                TokenType.EXPRESS, TokenType.EVALUATE, TokenType.ACTION,
                TokenType.SENSE, TokenType.INSTINCT, TokenType.RECALL,
                TokenType.ECHO, TokenType.REACT, TokenType.DORMANT
        ));
    }

    public Parser(Scanner scanner, ParseTable table) {
        this.scanner = scanner;
        this.table = table;
    }

    public ASTNode parse() {
        stateStack.push(0);
        Token lookahead = scanner.getNextToken();

        while (true) {
            int currentState = stateStack.peek();
            Map<TokenType, Action> stateActions = table.actionTable.get(currentState);
            Action action = (stateActions != null) ? stateActions.get(lookahead.type) : null;

            if (action == null) {
                // R_BRACE often naturally ends a block - check if we should accept it
                if (lookahead.type == TokenType.R_BRACE && !symbolStack.isEmpty()) {
                    // Don't exit, let the parser try to reduce with what it has
                    // Treat missing action for R_BRACE as a sign to finalize
                    if (!errorOccurred) {
                        return finalizeAST();
                    }
                }

                if (lookahead.type == TokenType.EOF) {
                    return finalizeAST();
                }

                reportDescriptiveError(currentState, lookahead);
                errorOccurred = true;
                lookahead = recover(lookahead, currentState);
                if (lookahead.type == TokenType.EOF) {
                    return finalizeAST();
                }
                continue;
            }

            if (action.type == Action.ActionType.SHIFT) {
                stateStack.push(action.value);
                symbolStack.push(new TerminalNode(lookahead));
                lookahead = scanner.getNextToken();
            } else if (action.type == Action.ActionType.REDUCE) {
                Production prod = Grammar.getProduction(action.value);
                NonTerminalNode newNode = new NonTerminalNode(prod.lhs);

                for (int i = 0; i < prod.rhsLength; i++) {
                    stateStack.pop();
                    if (!symbolStack.isEmpty()) {
                        newNode.addChild(symbolStack.pop());
                    }
                }
                newNode.reverseChildren();
                symbolStack.push(newNode);

                Map<String, Integer> gotos = table.gotoTable.get(stateStack.peek());
                Integer nextState = (gotos != null) ? gotos.get(prod.lhs.toUpperCase().replace("<", "").replace(">", "").trim()) : null;

                // If we can't find a next state, just continue - the parse loop will handle it
                if (nextState != null) {
                    stateStack.push(nextState);
                }
            } else if (action.type == Action.ActionType.ACCEPT) {
                return finalizeAST();
            }
        }
    }

    private Token recover(Token lookahead, int currentState) {
        Map<TokenType, Action> actions = table.actionTable.get(currentState);

        // 1. FIX THE CAST WARNING
        // Use a constructor or addAll instead of .clone() to avoid the warning
        Stack<Integer> tempStack = new Stack<>();
        tempStack.addAll(stateStack);

        // 2. VIRTUAL INSERTION (Keep as is, it's working well)
        if (actions != null && actions.containsKey(TokenType.SEMICOLON)) {
            if (STATEMENT_STARTERS.contains(lookahead.type) || lookahead.lexeme.equalsIgnoreCase("dream")) {
                System.err.println("  > [Fix] Inserting missing ';' at line " + lookahead.line);
                return new Token(TokenType.SEMICOLON, ";", lookahead.line);
            }
        }

        // 3. IMPROVED PANIC MODE
        System.err.println("  > [Skip] Skipping '" + lookahead.lexeme + "' to find recovery point...");

        if (lookahead.type != TokenType.EOF) {
            lookahead = scanner.getNextToken();
        }

        while (lookahead.type != TokenType.EOF) {
            if (STATEMENT_STARTERS.contains(lookahead.type) || lookahead.type == TokenType.R_BRACE) {

                // Search for a state on the stack that can handle this token
                while (tempStack.size() > 1) {
                    int state = tempStack.peek();
                    Map<TokenType, Action> tempActions = table.actionTable.get(state);

                    if (tempActions != null && tempActions.containsKey(lookahead.type)) {
                        // SYNC POINT FOUND
                        // Pop the REAL state stack to match the temp stack
                        while (stateStack.size() > tempStack.size()) {
                            stateStack.pop();
                            // Also pop from symbol stack to keep them synchronized
                            if (!symbolStack.isEmpty()) {
                                symbolStack.pop();
                            }
                        }
                        System.err.println("  > [Recovery] Resuming at '" + lookahead.lexeme + "' on line " + lookahead.line);
                        return lookahead;
                    }

                    // STOP popping if we reach the state that started the 'activate' block
                    if (isStatementListState(state)) {
                        break;
                    }

                    tempStack.pop();
                }
            }
            lookahead = scanner.getNextToken();
        }
        return lookahead;
    }

    private boolean isStatementListState(int state) {
        Map<TokenType, Action> actions = table.actionTable.get(state);
        if (actions == null) {
            return false;
        }
        // This state represents the list of statements inside activate { ... }
        return actions.containsKey(TokenType.PULSE) && actions.containsKey(TokenType.STIMULATE);
    }

    private void reportDescriptiveError(int state, Token lookahead) {
        Map<TokenType, Action> actions = table.actionTable.get(state);
        Set<TokenType> expected = (actions != null) ? actions.keySet() : Collections.emptySet();
        System.err.print("[Parser Error] Line " + lookahead.line + ": Unexpected '" + lookahead.lexeme + "'. ");

        if (expected.contains(TokenType.SEMICOLON)) {
            System.err.println("Missing ';'?");
        } else if (expected.contains(TokenType.L_PAREN)) {
            System.err.println("Missing '('?");
        } else {
            String exp = expected.stream().limit(3).map(Object::toString).collect(Collectors.joining(", "));
            System.err.println("Expected: " + exp);
        }
    }

    private ASTNode finalizeAST() {
        if (errorOccurred) {
            System.out.println("\n[Parser] Parsing completed with errors.");
        }

        // If we have only one node, return it as-is
        if (symbolStack.size() <= 1) {
            return symbolStack.isEmpty() ? null : symbolStack.peek();
        }

        // Multiple nodes on stack - reconstruct the tree intelligently
        return reconstructTree();
    }

    private ASTNode reconstructTree() {
        // Collect all nodes from stack (bottom to top)
        java.util.List<ASTNode> nodes = new java.util.ArrayList<>();
        java.util.Stack<ASTNode> temp = new java.util.Stack<>();

        while (!symbolStack.isEmpty()) {
            temp.push(symbolStack.pop());
        }
        while (!temp.isEmpty()) {
            nodes.add(temp.pop());
        }

        // Find SUBROUTINE_LIST as root, or create structure around it
        NonTerminalNode subroutineList = null;
        int slIndex = -1;

        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i) instanceof NonTerminalNode) {
                NonTerminalNode nt = (NonTerminalNode) nodes.get(i);
                if (nt.name.equals("SUBROUTINE_LIST")) {
                    subroutineList = nt;
                    slIndex = i;
                    break;
                }
            }
        }

        if (subroutineList != null) {
            // Nodes before SUBROUTINE_LIST
            java.util.List<ASTNode> before = nodes.subList(0, slIndex);
            // Nodes after SUBROUTINE_LIST
            java.util.List<ASTNode> after = nodes.subList(slIndex + 1, nodes.size());

            // Try to build a SUBROUTINE node from the tokens after SUBROUTINE_LIST
            if (!after.isEmpty()) {
                NonTerminalNode subroutine = buildSubroutine(after);
                subroutineList.addChild(subroutine);
            }

            return subroutineList;
        }

        // Fallback: wrap everything in PROGRAM_RECOVERED
        NonTerminalNode root = new NonTerminalNode("PROGRAM_RECOVERED");
        for (ASTNode node : nodes) {
            root.addChild(node);
        }
        return root;
    }

    private NonTerminalNode buildSubroutine(java.util.List<ASTNode> nodes) {
        NonTerminalNode subroutine = new NonTerminalNode("SUBROUTINE");

        // Pattern: ACTIVATE { STATEMENT_LIST } [INHIBIT { STATEMENT_LIST }]
        for (ASTNode node : nodes) {
            subroutine.addChild(node);
        }

        return subroutine;
    }
}
