
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

public class Parser {

    private Scanner scanner;
    private ParseTable table;
    private Stack<Integer> stateStack = new Stack<>();
    private Stack<ASTNode> symbolStack = new Stack<>();
    private Stack<Token> tokenStack = new Stack<>();  // Track shifted tokens for context
    private boolean errorOccurred = false;

    private static final Set<TokenType> SYNC_TOKENS = new HashSet<>();
    private static final Set<TokenType> DATA_TYPES = new HashSet<>();
    private static final Set<TokenType> KEYWORDS_REQUIRING_PARENS = new HashSet<>();

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

        // Data types for grammar
        DATA_TYPES.add(TokenType.PULSE);
        DATA_TYPES.add(TokenType.SPARK);
        DATA_TYPES.add(TokenType.STREAM);
        DATA_TYPES.add(TokenType.THOUGHT);
        DATA_TYPES.add(TokenType.NEURON);
        DATA_TYPES.add(TokenType.SYNAPSE);

        // Keywords that require opening parenthesis
        KEYWORDS_REQUIRING_PARENS.add(TokenType.STIMULATE);
        KEYWORDS_REQUIRING_PARENS.add(TokenType.CYCLE);
        KEYWORDS_REQUIRING_PARENS.add(TokenType.EVALUATE);
        KEYWORDS_REQUIRING_PARENS.add(TokenType.EXPRESS);
        KEYWORDS_REQUIRING_PARENS.add(TokenType.LENGTH);
        KEYWORDS_REQUIRING_PARENS.add(TokenType.TRANSCRIBE);
        KEYWORDS_REQUIRING_PARENS.add(TokenType.ECHO);
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
                reportDescriptiveError(currentState, lookahead);
                errorOccurred = true;

                resetStackToStatementLevel();
                lookahead = recover(lookahead);

                if (lookahead.type == TokenType.EOF) {
                    return finalizeAST();
                }
                continue;
            }

            if (action.type == Action.ActionType.SHIFT) {
                // Validate semantic rules before shifting
                if (lookahead.type == TokenType.CLUSTER && !tokenStack.isEmpty()) {
                    // Search for a recent data type token (check last 5 tokens)
                    Token dataTypeToken = null;
                    int searchLimit = Math.min(5, tokenStack.size());
                    for (int i = tokenStack.size() - 1; i >= tokenStack.size() - searchLimit; i--) {
                        Token candidate = tokenStack.get(i);
                        if (DATA_TYPES.contains(candidate.type)) {
                            dataTypeToken = candidate;
                            break;
                        }
                    }

                    // Pulse, spark, stream, thought cannot be used with cluster (only neuron and synapse can)
                    if (dataTypeToken != null
                            && dataTypeToken.type != TokenType.NEURON
                            && dataTypeToken.type != TokenType.SYNAPSE) {
                        System.err.println("[Parser Error] Line " + lookahead.line + ": Data type '" + dataTypeToken.lexeme + "' cannot be used with 'cluster' keyword.");
                        errorOccurred = true;
                        lookahead = scanner.getNextToken();
                        continue;
                    }
                }

                stateStack.push(action.value);
                symbolStack.push(new TerminalNode(lookahead));
                tokenStack.push(lookahead); // For Error Recovery Context
                lookahead = scanner.getNextToken();
            } else if (action.type == Action.ActionType.REDUCE) {
                Production prod = Grammar.getProduction(action.value);
                NonTerminalNode newNode = new NonTerminalNode(prod.lhs);

                for (int i = 0; i < prod.rhsLength; i++) {
                    stateStack.pop();
                    if (!symbolStack.isEmpty()) {
                        newNode.addChild(symbolStack.pop());
                    }
                    if (!tokenStack.isEmpty()) {
                        tokenStack.pop();
                    }
                }
                newNode.reverseChildren();
                symbolStack.push(newNode);

                int stateAfterPop = stateStack.peek();
                Map<String, Integer> gotos = table.gotoTable.get(stateAfterPop);
                String lhsKey = prod.lhs.toUpperCase().replace("<", "").replace(">", "").trim();
                Integer nextState = (gotos != null) ? gotos.get(lhsKey) : null;

                if (nextState == null) {
                    reportDescriptiveError(stateAfterPop, lookahead);
                    resetStackToStatementLevel();
                    lookahead = recover(lookahead);
                    continue;
                }
                stateStack.push(nextState);
            } else if (action.type == Action.ActionType.ACCEPT) {
                return finalizeAST();
            }
        }
    }

    private void reportDescriptiveError(int state, Token lookahead) {
        Set<TokenType> expected = getExpectedTokens(state);

        System.err.print("\n[Parser Error] Line " + lookahead.line + ": ");

        // Check for context-specific errors using token stack
        String contextError = checkContextSpecificError(lookahead);
        if (contextError != null) {
            System.err.println(contextError);
            return;
        }

        // Check for missing semicolon
        if (expected.contains(TokenType.SEMICOLON) && SYNC_TOKENS.contains(lookahead.type)) {
            System.err.println("Missing ';' before '" + lookahead.lexeme + "'.");
            return;
        }

        // Check for missing parentheses after keywords
        if (expected.contains(TokenType.L_PAREN) && !lookahead.type.equals(TokenType.L_PAREN)) {
            if (!tokenStack.isEmpty()) {
                Token lastToken = tokenStack.peek();
                if (KEYWORDS_REQUIRING_PARENS.contains(lastToken.type)) {
                    String keyword = lastToken.lexeme.toLowerCase();
                    if (keyword.equals("stimulate") || keyword.equals("cycle")) {
                        System.err.println("'" + keyword + "' requires parentheses around the condition.");
                        return;
                    } else if (keyword.equals("evaluate")) {
                        System.err.println("'" + keyword + "' requires parentheses around the expression.");
                        return;
                    } else if (keyword.equals("express") || keyword.equals("length") || keyword.equals("transcribe")) {
                        System.err.println("'" + keyword + "' requires parentheses around the argument.");
                        return;
                    } else if (keyword.equals("echo")) {
                        System.err.println("'" + keyword + "' requires parentheses around the loop components.");
                        return;
                    }
                }
            }
            System.err.println("Unexpected '" + lookahead.lexeme + "'. Expected '('.");
            return;
        }

        // Check for missing data type after SENSE
        if (!tokenStack.isEmpty() && tokenStack.peek().type == TokenType.SENSE) {
            if (!DATA_TYPES.contains(lookahead.type)) {
                System.err.println("'sense' requires a data type (pulse, spark, stream, thought, neuron, or synapse).");
                return;
            }
        }

        // Check for invalid keyword 'case' (should be 'path')
        if (lookahead.type == TokenType.IDENTIFIER && lookahead.lexeme.equalsIgnoreCase("case")) {
            System.err.println("Invalid keyword 'case'. Did you mean 'path'?");
            return;
        }

        // Check for invalid keyword 'default' (should be 'base')
        if (lookahead.type == TokenType.IDENTIFIER && lookahead.lexeme.equalsIgnoreCase("default")) {
            System.err.println("Invalid keyword 'default'. Did you mean 'base'?");
            return;
        }

        // Check for missing comma in parameter/argument list
        if (expected.contains(TokenType.COMMA) && (lookahead.type == TokenType.IDENTIFIER || lookahead.type == TokenType.PULSE_LIT)) {
            System.err.println("Missing ',' between list items before '" + lookahead.lexeme + "'.");
            return;
        }

        // Check for missing assignment operator
        if (expected.contains(TokenType.ASSIGN) && (lookahead.type == TokenType.PULSE_LIT || lookahead.type == TokenType.IDENTIFIER)) {
            String identifier = "identifier";
            if (tokenStack.size() >= 2) {
                Token prevToken = tokenStack.get(tokenStack.size() - 2);
                if (prevToken.type == TokenType.IDENTIFIER) {
                    identifier = prevToken.lexeme;
                }
            }
            System.err.println("Missing '=' operator after '" + identifier + "'.");
            return;
        }

        // Check for code after EOF (missing closing brace)
        if (expected.contains(TokenType.EOF) && lookahead.type != TokenType.EOF) {
            System.err.println("Unexpected code after the end of the main block. Did you forget a closing brace '}'?");
            return;
        }

        // Default error message with expected tokens
        String expectedList = expected.stream()
                .map(this::getFriendlyName)
                .distinct()
                .limit(5)
                .collect(Collectors.joining(", "));

        if (expected.size() > 5) {
            expectedList += ", etc.";
        }

        System.err.println("Unexpected '" + lookahead.lexeme + "'. Expected one of: " + expectedList);
    }

    private String checkContextSpecificError(Token lookahead) {
        if (tokenStack.isEmpty()) {
            return null;
        }

        Token lastToken = tokenStack.peek();

        // Check for invalid constant declaration
        if (tokenStack.size() >= 2) {
            Token firstToken = tokenStack.get(0);
            if (firstToken.type == TokenType.INSTINCT && DATA_TYPES.contains(tokenStack.get(1).type)) {
                // After instinct and data_type, if we get an error, it's a malformed declaration
                return "Invalid constant declaration. Expected 'instinct <data_type> <name> = <value>'.";
            }
        }

        // Check for missing parameter name in function declaration
        if (DATA_TYPES.contains(lastToken.type)) {
            if (lookahead.type == TokenType.R_PAREN || lookahead.type == TokenType.COMMA) {
                return "Missing parameter name after data type '" + lastToken.lexeme + "'.";
            }
        }

        // Check for missing closing parenthesis in function calls
        if (lastToken.type == TokenType.L_PAREN && lookahead.type == TokenType.SEMICOLON) {
            return "Missing ')' before ';'. Did you forget to close the parentheses?";
        }

        // Check for invalid array type (synapse/neuron cannot be arrays)
        if (lookahead.type == TokenType.L_BRACKET && tokenStack.size() >= 2) {
            Token dataTypeToken = tokenStack.get(tokenStack.size() - 2);
            if (dataTypeToken.type == TokenType.SYNAPSE || dataTypeToken.type == TokenType.NEURON) {
                return "'" + dataTypeToken.lexeme + "' type cannot be declared as an array.";
            }
        }

        // Check for missing operator in expression
        if ((lastToken.type == TokenType.PLUS || lastToken.type == TokenType.MINUS
                || lastToken.type == TokenType.STAR || lastToken.type == TokenType.SLASH)
                && lookahead.type == TokenType.SEMICOLON) {
            return "Missing operand after '" + lastToken.lexeme + "' operator.";
        }

        // Check for missing comma in function arguments 
        if ((lastToken.type == TokenType.PULSE_LIT || lastToken.type == TokenType.SPARK_LIT
                || lastToken.type == TokenType.STREAM_LIT || lastToken.type == TokenType.THOUGHT_LIT)
                && (lookahead.type == TokenType.PULSE_LIT || lookahead.type == TokenType.IDENTIFIER)) {
            return "Missing ',' between function arguments before '" + lookahead.lexeme + "'.";
        }

        // Check for missing expression in recall statement
        if (lastToken.type == TokenType.RECALL && lookahead.type == TokenType.SEMICOLON) {
            return "Missing expression in 'recall' statement.";
        }

        // Check for invalid echo loop structure
        if (lastToken.type == TokenType.SEMICOLON && lookahead.type == TokenType.R_PAREN) {
            // In context of echo, this might indicate missing loop progression
            if (tokenStack.size() >= 2 && tokenStack.get(0).type == TokenType.ECHO) {
                return "Invalid 'echo' loop structure. Expected: 'echo (init; condition; progression)'.";
            }
        }

        return null;
    }

    private String getFriendlyName(TokenType type) {
        switch (type) {
            case SEMICOLON:
                return "';'";
            case L_PAREN:
                return "'('";
            case R_PAREN:
                return "')'";
            case L_BRACE:
                return "'{'";
            case R_BRACE:
                return "'}'";
            case IDENTIFIER:
                return "an identifier";
            case PULSE_LIT:
                return "a number";
            case THOUGHT_LIT:
                return "a string literal";
            case ASSIGN:
                return "'='";
            case PLUS:
                return "'+'";
            case PLUS_ASSIGN:
            case MINUS_ASSIGN:
            case MUL_ASSIGN:
            case DIV_ASSIGN:
            case MOD_ASSIGN:
                return "an assignment operator";
            default:
                return type.toString().toLowerCase();
        }
    }

    private Set<TokenType> getExpectedTokens(int state) {
        Map<TokenType, Action> actions = table.actionTable.get(state);
        if (actions == null) {
            return Collections.emptySet();
        }
        return actions.keySet();
    }

    private ASTNode finalizeAST() {
        if (errorOccurred) {
            System.out.println("\n[Parser] Parsing completed with errors.");
        }
        return symbolStack.isEmpty() ? null : symbolStack.peek();
    }

    private void resetStackToStatementLevel() {
        while (stateStack.size() > 1) {
            int state = stateStack.peek();

            if (isAtStatementListLevel(state)) {
                break;
            }

            stateStack.pop();
            if (!symbolStack.isEmpty()) {
                symbolStack.pop();
            }
        }
    }

    private boolean isAtStatementListLevel(int state) {
        Map<TokenType, Action> actions = table.actionTable.get(state);
        if (actions == null) {
            return false;
        }

        // Check Keywords for Start Statement
        return actions.containsKey(TokenType.STIMULATE)
                && actions.containsKey(TokenType.CYCLE)
                && actions.containsKey(TokenType.EXPRESS);
    }

    private Token recover(Token currentLookahead) {
        // If the token that caused the error is NOT a synchronization point, skip it.
        // This prevents the "Unexpected token" from being processed again in the same state.
        if (!SYNC_TOKENS.contains(currentLookahead.type)) {
            System.out.println("Skipping '" + currentLookahead.lexeme + "' to resynchronize...");
            currentLookahead = scanner.getNextToken();
        }

        while (currentLookahead.type != TokenType.EOF) {
            // Return the token AFTER the semicolon
            if (currentLookahead.type == TokenType.SEMICOLON) {
                return scanner.getNextToken();
            }

            // Check if current lookahead is a valid token
            if (SYNC_TOKENS.contains(currentLookahead.type)) {
                int currentState = stateStack.peek();
                if (table.actionTable.get(currentState).containsKey(currentLookahead.type)) {
                    return currentLookahead;
                }
            }

            currentLookahead = scanner.getNextToken();
        }
        return currentLookahead;
    }
}
