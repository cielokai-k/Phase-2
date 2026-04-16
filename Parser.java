
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

public class Parser {

    private Scanner scanner;
    private ParseTable table;
    private Stack<Integer> stateStack = new Stack<>();
    private Stack<ASTNode> symbolStack = new Stack<>();

    private static final Set<TokenType> SYNC_TOKENS = new HashSet<>();

    static {
        SYNC_TOKENS.add(TokenType.SEMICOLON);
        SYNC_TOKENS.add(TokenType.R_BRACE);
        SYNC_TOKENS.add(TokenType.ACTION);
        SYNC_TOKENS.add(TokenType.ACTIVATE);
        SYNC_TOKENS.add(TokenType.STIMULATE);
        SYNC_TOKENS.add(TokenType.CYCLE);
        SYNC_TOKENS.add(TokenType.EVALUATE);
        SYNC_TOKENS.add(TokenType.PULSE);
        SYNC_TOKENS.add(TokenType.EXPRESS);
    }

    public Parser(Scanner scanner, ParseTable table) {
        this.scanner = scanner;
        this.table = table;
    }

    public ASTNode parse() {
        stateStack.push(0);
        Token lookahead = scanner.getNextToken();
        boolean errorOccurred = false;

        while (true) {
            int currentState = stateStack.peek();
            Map<TokenType, Action> stateActions = table.actionTable.get(currentState);
            Action action = (stateActions != null) ? stateActions.get(lookahead.type) : null;

            if (action == null) {
                // Explicitly indicate Parser Error with Line Number
                System.err.println("[Parser Error] Unexpected token '" + lookahead.lexeme
                        + "' (" + lookahead.type + ") at line " + lookahead.line);
                errorOccurred = true;

                //Reset the stack first to move out of the "broken" context
                resetStackToStatementLevel();

                // Consume at least one token and search for sync point to break the infinite loop
                lookahead = recover(lookahead);

                if (lookahead.type == TokenType.EOF) {
                    return null;
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
                if (nextState == null) {
                    // Bypass failed non-terminal reduction
                    resetStackToStatementLevel();
                    lookahead = scanner.getNextToken();
                    continue;
                }
                stateStack.push(nextState);
            } else if (action.type == Action.ActionType.ACCEPT) {
                if (errorOccurred) {
                    System.out.println("\nParsing completed with errors.");
                }
                return symbolStack.isEmpty() ? null : symbolStack.pop();
            }
        }
    }

    /**
     * Pops the stack until a state is found that can handle a fresh statement
     * starter.
     */
    private void resetStackToStatementLevel() {
        while (stateStack.size() > 1) {
            int state = stateStack.peek();
            if (canStartStatement(state)) {
                break;
            }
            stateStack.pop();
            if (!symbolStack.isEmpty()) {
                symbolStack.pop();
            }
        }
    }

    private boolean canStartStatement(int state) {
        Map<TokenType, Action> actions = table.actionTable.get(state);
        if (actions == null) {
            return false;
        }

        return actions.containsKey(TokenType.PULSE)
                || actions.containsKey(TokenType.STIMULATE)
                || actions.containsKey(TokenType.EXPRESS)
                || actions.containsKey(TokenType.CYCLE)
                || actions.containsKey(TokenType.ACTION)
                || actions.containsKey(TokenType.IDENTIFIER);
    }

    private Token recover(Token currentLookahead) {
        System.out.println("Bypassing invalid sequence. Searching for next valid statement...");

        // Force movement: If we hit an error, we MUST move past the current token
        // to ensure we don't evaluate the same "Unexpected" token in the same state.
        Token current = scanner.getNextToken();

        while (current.type != TokenType.EOF) {
            // Stop at a semicolon and consume it to start truly fresh
            if (current.type == TokenType.SEMICOLON) {
                return scanner.getNextToken();
            }
            // Stop at any major statement starters
            if (SYNC_TOKENS.contains(current.type)) {
                return current;
            }
            current = scanner.getNextToken();
        }
        return current;
    }
}
