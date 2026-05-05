
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

public class Parser {

    private Scanner scanner;
    private ParseTable table;
    private Stack<Integer> stateStack = new Stack<>();
    private Stack<ASTNode> symbolStack = new Stack<>();
    private boolean errorOccurred = false;
    private boolean inErrorRecovery = false;
    private PrintWriter logWriter;

    public Parser(Scanner scanner, ParseTable table, PrintWriter logWriter) {
        this.scanner = scanner;
        this.table = table;
        this.logWriter = logWriter;
    }

    public ASTNode parse() {
        stateStack.push(0);
        Token lookahead = scanner.getNextToken();

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

                if (!inErrorRecovery) {
                    logWriter.println("[ERROR] Unexpected " + lookahead.lexeme + " at line " + lookahead.line);
                    reportDescriptiveError(currentState, lookahead);
                    errorOccurred = true;
                    inErrorRecovery = true;
                }

                lookahead = recover(lookahead, currentState);

                if (lookahead.type == TokenType.EOF) {
                    return finalizeAST();
                }

                if (lookahead.type == TokenType.SEMICOLON || lookahead.type == TokenType.R_BRACE) {
                    inErrorRecovery = false;
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

                if (prod.rhsLength == 0) {
                    newNode.addChild(new TerminalNode(new Token(TokenType.EPSILON, "ε", 0)));
                } else {
                    for (int i = 0; i < prod.rhsLength; i++) {
                        if (!stateStack.isEmpty()) {
                            stateStack.pop();
                        }
                        if (!symbolStack.isEmpty()) {
                            newNode.addChild(symbolStack.pop());
                        }
                    }
                    newNode.reverseChildren();
                }

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

                if (!symbolStack.isEmpty()) {
                    ASTNode finalProgram = symbolStack.pop();
                    NonTerminalNode augmentedRoot = new NonTerminalNode("<PROGRAM'>");
                    augmentedRoot.addChild(finalProgram);
                    symbolStack.push(augmentedRoot);
                }

                return finalizeAST();
            }
        }
    }

    private static final Set<TokenType> DELIMITERS = Set.of(
            TokenType.SEMICOLON,
            TokenType.R_BRACE,
            TokenType.R_PAREN,
            TokenType.EOF
    );

    private Token recover(Token lookahead, int currentState) {

        Map<TokenType, Action> actions = table.actionTable.get(currentState);

        // --- FIX: Insert ONLY ONE token ---
        if (actions != null) {

            // Priority 1: close expression
            if (actions.containsKey(TokenType.R_PAREN)) {
                System.err.println("  > [Fix] Inserting missing ')' at line " + lookahead.line);
                return new Token(TokenType.R_PAREN, ")", lookahead.line);
            }

            // Priority 2: end statement
            if (actions.containsKey(TokenType.SEMICOLON)) {
                System.err.println("  > [Fix] Inserting missing ';' at line " + lookahead.line);
                return new Token(TokenType.SEMICOLON, ";", lookahead.line);
            }

        }

        // --- PANIC MODE ---
        System.err.println("  > [Skip] Skipping tokens...");

        while (lookahead.type != TokenType.EOF) {

            if (DELIMITERS.contains(lookahead.type)) {

                Stack<Integer> tempStack = new Stack<>();
                tempStack.addAll(stateStack);

                while (tempStack.size() > 1) {
                    int state = tempStack.peek();
                    Map<TokenType, Action> stateActions = table.actionTable.get(state);

                    if (stateActions != null && stateActions.containsKey(lookahead.type)) {

                        while (stateStack.size() > tempStack.size()) {
                            stateStack.pop();
                            if (!symbolStack.isEmpty()) {
                                symbolStack.pop();
                            }
                        }

                        System.err.println("  > [Recovery] Resuming at '" + lookahead.lexeme + "' line " + lookahead.line);
                        return lookahead;
                    }

                    tempStack.pop();
                }

                if (lookahead.type == TokenType.SEMICOLON) {
                    return scanner.getNextToken();
                }
            }

            lookahead = scanner.getNextToken();
        }

        return lookahead;
    }

    private void reportDescriptiveError(int state, Token lookahead) {
        Map<TokenType, Action> actions = table.actionTable.get(state);
        Set<TokenType> expected = (actions != null) ? actions.keySet() : Collections.emptySet();

        System.err.print("[Parser Error] Line " + lookahead.line + ": Unexpected '" + lookahead.lexeme + "'. ");

        if (lookahead.type == TokenType.R_PAREN && expected.contains(TokenType.SEMICOLON)) {
            System.err.println("Missing ';'?");
        } else if (expected.contains(TokenType.L_PAREN)) {
            System.err.println("Missing '('?");
        } else if (expected.contains(TokenType.R_PAREN)) {
            System.err.println("Missing ')'?");
        } else if (expected.contains(TokenType.SEMICOLON)) {
            System.err.println("Missing ';'?");
        } else if (expected.contains(TokenType.R_BRACE)) {
            System.err.println("Missing '}'?");
        } else {
            List<String> hints = expected.stream()
                    .filter(t -> t != TokenType.EOF)
                    .map(Object::toString)
                    .collect(Collectors.toList());

            if (hints.isEmpty()) {
                System.err.println("Statement context lost.");
            } else {
                String exp = hints.stream().limit(3).collect(Collectors.joining(", "));
                System.err.println("Expected: " + exp);
            }
        }
    }

    private ASTNode finalizeAST() {
        if (errorOccurred) {
            System.out.println("\n[Parser] Parsing completed with errors.");
        }

        if (symbolStack.size() == 1) {
            return symbolStack.peek();
        }

        return reconstructTree();
    }

    private ASTNode reconstructTree() {
        List<ASTNode> nodes = new ArrayList<>();
        Stack<ASTNode> temp = new Stack<>();

        while (!symbolStack.isEmpty()) {
            temp.push(symbolStack.pop());
        }
        while (!temp.isEmpty()) {
            nodes.add(temp.pop());
        }

        NonTerminalNode root = null;

        for (ASTNode node : nodes) {
            if (node instanceof NonTerminalNode) {
                String name = ((NonTerminalNode) node).name;
                if (name.equals("<PROGRAM'>") || name.equals("<PROGRAM>")) {
                    root = (NonTerminalNode) node;
                    break;
                }
            }
        }

        if (root == null) {
            root = new NonTerminalNode("<PROGRAM_RECOVERED>");
        }

        for (ASTNode node : nodes) {
            if (node != root) {
                root.addChild(node);
            }
        }

        return root;
    }
}
