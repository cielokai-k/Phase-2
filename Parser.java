
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

public class Parser {

    private Scanner scanner;
    private ParseTable table;
    private Stack<Integer> stateStack = new Stack<>();
    private Stack<ASTNode> symbolStack = new Stack<>();
    private boolean errorOccurred = false;

    // Set of tokens where we can safely "restart" parsing 
    private static final Set<TokenType> SYNC_TOKENS = new HashSet<>();

    static {
        SYNC_TOKENS.add(TokenType.SEMICOLON);
        SYNC_TOKENS.add(TokenType.R_BRACE);
        SYNC_TOKENS.add(TokenType.STIMULATE);
        SYNC_TOKENS.add(TokenType.CYCLE);
        SYNC_TOKENS.add(TokenType.ACTION);
        SYNC_TOKENS.add(TokenType.ACTIVATE);
        SYNC_TOKENS.add(TokenType.EXPRESS);
        SYNC_TOKENS.add(TokenType.EVALUATE);
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

            // 1. INDICATING LINE NUMBERS & 2. FIXING ERRORS (Initiate Recovery)
            if (action == null) {
                reportError(lookahead);
                lookahead = recover(lookahead);
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

                int stateAfterPop = stateStack.peek();
                Map<String, Integer> gotos = table.gotoTable.get(stateAfterPop);
                String lhsKey = prod.lhs.toUpperCase().replace("<", "").replace(">", "").trim();
                Integer nextState = (gotos != null) ? gotos.get(lhsKey) : null;

                // 3. BYPASSING GRAMMAR VARIABLES (Structural Error Recovery)
                if (nextState == null) {
                    System.err.println("[Parser Error] Structural mismatch at line " + lookahead.line + " while reducing " + prod.lhs);
                    lookahead = recover(lookahead);
                    if (lookahead.type == TokenType.EOF) {
                        return finalizeAST();
                    }
                    continue;
                }
                stateStack.push(nextState);
            } else if (action.type == Action.ActionType.ACCEPT) {
                return finalizeAST();
            }
        }
    }

    /**
     * Requirement: Indicating line numbers
     */
    private void reportError(Token lookahead) {
        this.errorOccurred = true;
        System.err.println("[Parser Error] Unexpected token '" + lookahead.lexeme
                + "' (" + lookahead.type + ") at line " + lookahead.line);
    }

    /**
     * Requirement: Skipping sequences of tokens in error This method advances
     * the scanner and pops the stack to find a stable state.
     */
    private Token recover(Token lookahead) {
        System.err.println("Attempting to fix error by skipping tokens until next statement...");

        // Skip the current error-causing token immediately
        if (lookahead.type != TokenType.EOF) {
            lookahead = scanner.getNextToken();
        }

        while (lookahead.type != TokenType.EOF) {
            // Check if current token is a synchronization point 
            if (SYNC_TOKENS.contains(lookahead.type)) {
                // Pop stack until we find a state that can handle this sync token
                while (stateStack.size() > 1) {
                    int state = stateStack.peek();
                    if (table.actionTable.get(state).containsKey(lookahead.type)) {
                        return lookahead; // Recovery point found
                    }
                    stateStack.pop();
                    if (!symbolStack.isEmpty()) {
                        symbolStack.pop();
                    }
                }
            }
            lookahead = scanner.getNextToken();
        }
        return lookahead;
    }

    private ASTNode finalizeAST() {
        if (errorOccurred) {
            System.out.println("\n[Parser] Finished with errors.");
        }
        return symbolStack.isEmpty() ? null : symbolStack.peek();
    }
}
