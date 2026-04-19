
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

public class Parser {

    private Scanner scanner;
    private ParseTable table;
    private Stack<Integer> stateStack = new Stack<>();
    private Stack<ASTNode> symbolStack = new Stack<>();
    private boolean errorOccurred = false;
    private PrintWriter logWriter;

    private static final Set<TokenType> STATEMENT_STARTERS = new HashSet<>();

    static {
        STATEMENT_STARTERS.addAll(Arrays.asList(
                TokenType.PULSE, TokenType.STIMULATE, TokenType.CYCLE,
                TokenType.EXPRESS, TokenType.EVALUATE, TokenType.ACTION,
                TokenType.INHIBIT, TokenType.REACT,
                TokenType.ECHO, TokenType.RECALL, TokenType.DORMANT
        ));
    }

    public Parser(Scanner scanner, ParseTable table, PrintWriter logWriter) {
        this.scanner = scanner;
        this.table = table;
        this.logWriter = logWriter;
    }

    public ASTNode parse() {
        stateStack.push(0);
        Token lookahead = scanner.getNextToken();

        // Print header to the file
        logWriter.println(String.format("%-15s | %-20s | %-15s", "ACTION", "TOKEN/LHS", "STATE STACK"));
        logWriter.println("--------------------------------------------------------------------------------");

        while (true) {
            int currentState = stateStack.peek();
            Map<TokenType, Action> stateActions = table.actionTable.get(currentState);
            Action action = (stateActions != null) ? stateActions.get(lookahead.type) : null;

            String stackStr = stateStack.toString();

            if (action == null) {
                if (lookahead.type == TokenType.EOF) {
                    return finalizeAST();
                }

                // Log error to file as well
                logWriter.println("[ERROR] Unexpected " + lookahead.lexeme + " at line " + lookahead.line);

                reportDescriptiveError(currentState, lookahead);
                errorOccurred = true;
                lookahead = recover(lookahead, currentState);

                if (lookahead.type == TokenType.EOF) {
                    return finalizeAST();
                }
                continue;
            }

            if (action.type == Action.ActionType.SHIFT) {
                logWriter.println(String.format("%-15s | %-20s | %s", "SHIFT " + action.value, lookahead.lexeme, stackStr));
                stateStack.push(action.value);
                symbolStack.push(new TerminalNode(lookahead));
                lookahead = scanner.getNextToken();

            } else if (action.type == Action.ActionType.REDUCE) {
                Production prod = Grammar.getProduction(action.value);
                logWriter.println(String.format("%-15s | %-20s | %s", "REDUCE " + action.value, prod.lhs, stackStr));

                NonTerminalNode newNode = new NonTerminalNode(prod.lhs);
                for (int i = 0; i < prod.rhsLength; i++) {
                    if (!stateStack.isEmpty()) {
                        stateStack.pop();
                    }
                    if (!symbolStack.isEmpty()) {
                        newNode.addChild(symbolStack.pop());
                    }
                }
                newNode.reverseChildren();
                symbolStack.push(newNode);

                String lhsClean = prod.lhs.replace("<", "").replace(">", "").trim().toUpperCase();
                Map<String, Integer> gotos = table.gotoTable.get(stateStack.peek());
                Integer nextState = (gotos != null) ? gotos.get(lhsClean) : null;

                if (nextState != null) {
                    logWriter.println(String.format("%-15s | %-20s | %s", "GOTO " + nextState, lhsClean, stateStack.toString()));
                    stateStack.push(nextState);
                }
            } else if (action.type == Action.ActionType.ACCEPT) {
                logWriter.println(String.format("%-15s | %-20s | %s", "ACCEPT", "---", stackStr));
                return finalizeAST();
            }
        }
    }

    private Token recover(Token lookahead, int currentState) {
        Map<TokenType, Action> actions = table.actionTable.get(currentState);
        Stack<Integer> tempStack = new Stack<>();
        tempStack.addAll(stateStack);

        // 1. Virtual Semicolon Insertion
        if (actions != null && actions.containsKey(TokenType.SEMICOLON)) {
            if (STATEMENT_STARTERS.contains(lookahead.type) || lookahead.lexeme.equalsIgnoreCase("dream")) {
                System.err.println("  > [Fix] Inserting missing ';' at line " + lookahead.line);
                return new Token(TokenType.SEMICOLON, ";", lookahead.line);
            }
        }

        System.err.println("  > [Skip] Skipping '" + lookahead.lexeme + "' to find recovery point...");
        if (lookahead.type != TokenType.EOF) {
            lookahead = scanner.getNextToken();
        }

        while (lookahead.type != TokenType.EOF) {
            // SYNC POINT: Stop skipping if we hit a keyword, a closing brace, OR a semicolon
            if (STATEMENT_STARTERS.contains(lookahead.type)
                    || lookahead.type == TokenType.R_BRACE
                    || lookahead.type == TokenType.SEMICOLON) {

                while (tempStack.size() > 1) {
                    int state = tempStack.peek();
                    Map<TokenType, Action> tempActions = table.actionTable.get(state);

                    if (tempActions != null && tempActions.containsKey(lookahead.type)) {
                        // Sync the real stacks to this point
                        while (stateStack.size() > tempStack.size()) {
                            stateStack.pop();
                            if (!symbolStack.isEmpty()) {
                                symbolStack.pop();
                            }
                        }
                        System.err.println("  > [Recovery] Resuming at '" + lookahead.lexeme + "' on line " + lookahead.line);
                        return lookahead;
                    }

                    // If we are in a block context, don't pop past it!
                    if (isStatementListState(state)) {
                        break;
                    }
                    tempStack.pop();
                }
            }
            // If we hit a semicolon and still haven't found a recovery state, 
            // stop here anyway to prevent skipping the whole file.
            if (lookahead.type == TokenType.SEMICOLON) {
                return scanner.getNextToken();
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

        // A state is a "List State" if it can handle several different statement starters
        // (This is typical for the inside of a block or the top level of a program)
        long starterCount = STATEMENT_STARTERS.stream()
                .filter(actions::containsKey)
                .count();

        // Also include states that are specifically waiting to close a block
        return starterCount >= 2 || actions.containsKey(TokenType.R_BRACE);
    }

    private void reportDescriptiveError(int state, Token lookahead) {
        Map<TokenType, Action> actions = table.actionTable.get(state);
        Set<TokenType> expected = (actions != null) ? actions.keySet() : Collections.emptySet();

        System.err.print("[Parser Error] Line " + lookahead.line + ": Unexpected '" + lookahead.lexeme + "'. ");

        // Specific Case: Missing semicolon in middle of echo (Line 82)
        if (lookahead.type == TokenType.R_PAREN && expected.contains(TokenType.SEMICOLON)) {
            System.err.println("Missing ';'?");
        } // Structural Priorities
        else if (expected.contains(TokenType.L_PAREN)) {
            System.err.println("Missing '('?");
        } else if (expected.contains(TokenType.R_PAREN)) {
            System.err.println("Missing ')'?");
        } else if (expected.contains(TokenType.SEMICOLON)) {
            System.err.println("Missing ';'?");
        } else if (expected.contains(TokenType.R_BRACE)) {
            System.err.println("Missing '}'?");
        } else {
            // Line 86 & 91: Provide specific keyword expectations
            List<String> hints = expected.stream()
                    .filter(t -> t != TokenType.EOF)
                    .map(Object::toString)
                    .collect(Collectors.toList());

            if (hints.isEmpty()) {
                // This will likely trigger for the Orphaned Inhibit (Line 86)
                System.err.println("Statement context lost. Searching for next valid instruction...");
            } else {
                // This will trigger for the Missing 'cycle' (Line 91)
                String exp = hints.stream().limit(3).collect(Collectors.joining(", "));
                System.err.println("Expected: " + exp);
            }
        }
    }

    private ASTNode finalizeAST() {
        if (errorOccurred) {
            System.out.println("\n[Parser] Parsing completed with errors.");
        }
        if (symbolStack.size() <= 1) {
            return symbolStack.isEmpty() ? null : symbolStack.peek();
        }
        return reconstructTree();
    }

    private ASTNode reconstructTree() {
        java.util.List<ASTNode> nodes = new java.util.ArrayList<>();
        java.util.Stack<ASTNode> temp = new java.util.Stack<>();
        while (!symbolStack.isEmpty()) {
            temp.push(symbolStack.pop());
        }
        while (!temp.isEmpty()) {
            nodes.add(temp.pop());
        }

        NonTerminalNode root = null;
        for (ASTNode node : nodes) {
            if (node instanceof NonTerminalNode && ((NonTerminalNode) node).name.contains("LIST")) {
                root = (NonTerminalNode) node;
                break;
            }
        }

        if (root == null) {
            root = new NonTerminalNode("PROGRAM_RECOVERED");
        }
        for (ASTNode node : nodes) {
            if (node != root) {
                root.addChild(node);
            }
        }
        return root;
    }

    private NonTerminalNode buildSubroutine(java.util.List<ASTNode> nodes) {
        NonTerminalNode subroutine = new NonTerminalNode("SUBROUTINE");
        for (ASTNode node : nodes) {
            subroutine.addChild(node);
        }
        return subroutine;
    }
}
