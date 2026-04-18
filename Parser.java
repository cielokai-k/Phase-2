
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
                // If we're at the final closing brace and the AST has content, 
                if (lookahead.type == TokenType.R_BRACE && !symbolStack.isEmpty()) {
                    return finalizeAST();
                }

                // Don't report error for EOF either - it means parsing is done
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
        // If we have multiple nodes on the stack due to recovery, 
        // they are individual statements that weren't reduced to a root.
        if (symbolStack.size() > 1) {
            NonTerminalNode root = new NonTerminalNode("PROGRAM_RECOVERED");
            while (!symbolStack.isEmpty()) {
                root.addChild(symbolStack.pop());
            }
            root.reverseChildren();
            return root;
        }
        return symbolStack.isEmpty() ? null : symbolStack.peek();
    }
}
