import java.util.ArrayList;
import java.util.List;

/* RECURSIVE DESCENT PARSER FOR CEREBRA

 THREE methods are used in every rule:
  1. check(type)  — looks at the current token WITHOUT consuming it. Returns true/false.
  2. advance()    — consumes the current token and moves forward. Returns the token that was just consumed.
  3. expect(type) — if the current token matches, consumes it (good). If it doesn't match, records a parse error and
                    keeps going so we can find more errors in one run.

 ERROR RECOVERY: PANIC-MODE RECOVERY
    - When a parsing error occurs, this method skips tokens until it finds a "safe" point to resume parsing.
    - Calls synchronize() method
    - Safe points are: semicolon, closing brace, any keyword that can start a new statement (like stimulate, cycle, pulse, etc.)
 */

public class Parser2 {
    private final Scanner scanner;
    private Token current;              // token we are looking at NOW
    private Token previous;             // token we just consumed
    private boolean hadError = false;
    private int indentLevel = 0;      
    private final List<String> errors = new ArrayList<>();

    public Parser2(Scanner scanner, SymTable symTable) {
        this.scanner = scanner;
        advance();   // load the very first real token
    }

    // FOR INDENTATION

    private void printIndent() {
        for (int i = 0; i < indentLevel; i++) System.out.print("  ");
    }

    private void enter(String rule) {
        printIndent();
        System.out.println("> " + rule);
        indentLevel++;
    }

    private void exit(String rule) {
        indentLevel--;
        printIndent();
        System.out.println("< " + rule);
    }

    // <program> → <subroutine_list> ACTIVATE L_BRACE <statement_list> R_BRACE
    public void parse() {
        System.out.println("\n========== STARTING PARSER ==========\n");
        enter("PROGRAM");

        parseSubroutineList();

        printIndent();
        System.out.println("Expecting ACTIVATE...");
        expect(TokenType.ACTIVATE, "Expected 'activate' as the program entry point");
        expect(TokenType.L_BRACE,  "Expected '{' to open the activate block");
        parseStatementList();
        expect(TokenType.R_BRACE,  "Expected '}' to close the activate block");

        if (!check(TokenType.EOF)) {
            recordError("Unexpected tokens after the closing '}' of activate");
        }

        exit("PROGRAM");
        printResults();
    }


    // ----------- SUBROUTINES -----------


    // <subroutine_list> → { <subroutine> }
    private void parseSubroutineList() {
        enter("SUBROUTINE_LIST");
        while (check(TokenType.ACTION)) {
            parseSubroutine();
        }
        exit("SUBROUTINE_LIST");
    }

    // <subroutine> → ACTION <rec_type> IDENTIFIER L_PAREN <params> R_PAREN L_BRACE <statement_list> R_BRACE
    private void parseSubroutine() {
        enter("SUBROUTINE");
        expect(TokenType.ACTION,     "Expected 'action'");
        parseRecType();
        expect(TokenType.IDENTIFIER, "Expected function name after return type");
        expect(TokenType.L_PAREN,    "Expected '(' after function name");
        parseParams();
        expect(TokenType.R_PAREN,    "Expected ')' to close parameter list");
        expect(TokenType.L_BRACE,    "Expected '{' to open function body");
        parseStatementList();
        expect(TokenType.R_BRACE,    "Expected '}' to close function body");
        exit("SUBROUTINE");
    }

    // <rec_type> → <data_type> | CLUSTER <data_type> L_BRACKET R_BRACKET <array_tail> | VOID
    private void parseRecType() {
        enter("REC_TYPE");
        if (check(TokenType.VOID)) {
            advance();
        } else if (check(TokenType.CLUSTER)) {
            advance();
            parseDataType();
            expect(TokenType.L_BRACKET, "Expected '[' in cluster return type");
            expect(TokenType.R_BRACKET, "Expected ']' in cluster return type");
            parseArrayTail();
        } else if (isDataType()) {
            advance();
        } else {
            recordError("Expected a return type (void, data type, or cluster)");
            synchronize();
        }
        exit("REC_TYPE");
    }

    // <array_tail> → [ L_BRACKET R_BRACKET ]				
    private void parseArrayTail() {
        enter("ARRAY_TAIL");
        if (check(TokenType.L_BRACKET)) {
            advance();
            expect(TokenType.R_BRACKET, "Expected ']' for second array dimension");
        }
        exit("ARRAY_TAIL");
    }


    // ----------- PARAMETERS -----------


    // <params> → [ <param_item> { COMMA <param_item> } ]
    private void parseParams() {
        enter("PARAMS");

        if (isDataType() || check(TokenType.CLUSTER)) {
            parseParamItem();
            while (check(TokenType.COMMA)) {
                advance();
                if (isDataType() || check(TokenType.CLUSTER)) {
                    parseParamItem();
                } else {
                    recordError("Expected parameter after ','");
                    synchronize();
                }
            }
        } else {
            printIndent();
            System.out.println("  (no parameters)");
        }

        exit("PARAMS");
    }

    // <param_item> → <data_type> IDENTIFIER | CLUSTER <data_type> IDENTIFIER L_BRACKET R_BRACKET <array_tail>
    private void parseParamItem() {
        enter("PARAM_ITEM");
        if (check(TokenType.CLUSTER)) {
            advance();
            parseDataType();
            expect(TokenType.IDENTIFIER, "Expected parameter name in cluster parameter");
            expect(TokenType.L_BRACKET,  "Expected '[' in cluster parameter");
            expect(TokenType.R_BRACKET,  "Expected ']' in cluster parameter");
            parseArrayTail();
        } else {
            parseDataType();
            expect(TokenType.IDENTIFIER, "Expected parameter name after data type");
        }
        exit("PARAM_ITEM");
    }

    
    // ----------- DECLARATIONS -----------


    // <declaration> → <data_type> <typed_decl_tail> | <const_decl>
    private void parseDeclaration() {
        enter("DECLARATION");
        if (check(TokenType.INSTINCT)) {
            parseConstDecl();
        } else {
            parseDataType();
            parseTypedDeclTail();
        }
        exit("DECLARATION");
    }

    // <typed_decl_tail> → CLUSTER <cluster_list> SEMICOLON | <id_list> SEMICOLON
    private void parseTypedDeclTail() {
        enter("TYPED_DECL_TAIL");
        if (check(TokenType.CLUSTER)) {
            advance();
            parseClusterList();
            expect(TokenType.SEMICOLON, "Expected ';' after cluster declaration");
        } else {
            parseIdList();
            expect(TokenType.SEMICOLON, "Expected ';' after variable declaration");
        }
        exit("TYPED_DECL_TAIL");
    }

    // <id_list> → IDENTIFIER [ ASSIGN <expr> ] { COMMA IDENTIFIER [ ASSIGN <expr> ] }
    private void parseIdList() {
        enter("ID_LIST");
        // first identifier
        expect(TokenType.IDENTIFIER, "Expected a variable name");
        if (check(TokenType.ASSIGN)) {
            advance();
            parseExpr();
        }
        // { COMMA IDENTIFIER [ ASSIGN <expr> ] }
        while (check(TokenType.COMMA)) {
            advance();
            if (check(TokenType.IDENTIFIER)) {
                expect(TokenType.IDENTIFIER, "Expected variable name after ','");
                if (check(TokenType.ASSIGN)) {
                    advance();
                    parseExpr();
                }
            } else {
                recordError("Expected variable name after ','");
                synchronize();
            }
        }
        exit("ID_LIST");
    }

    // <const_decl> → INSTINCT <data_type> <const_list> SEMICOLON
    private void parseConstDecl() {
        enter("CONST_DECL");
        expect(TokenType.INSTINCT, "Expected 'instinct'");
        parseDataType();
        parseConstList();
        expect(TokenType.SEMICOLON, "Expected ';' after constant declaration");
        exit("CONST_DECL");
    }

    // <const_list> → IDENTIFIER ASSIGN <expr> { COMMA IDENTIFIER ASSIGN <expr> }						
    private void parseConstList() {
        enter("CONST_LIST");
        expect(TokenType.IDENTIFIER, "Expected a constant name");
        expect(TokenType.ASSIGN, "Constants must be assigned — missing '='");
        parseExpr();
        // Additional constants (zero or more)
        while (check(TokenType.COMMA)) {
            advance(); 
            if (check(TokenType.IDENTIFIER)) {
                expect(TokenType.IDENTIFIER, "Expected a constant name after ','");
                expect(TokenType.ASSIGN, "Expected '=' for constant assignment");
                parseExpr();
            } else {
                recordError("Expected constant name after ','");
                synchronize();
            }
        }
        exit("CONST_LIST");
    }


    //  ----------- ARRAY (CLUSTER) DECLARATIONS -----------


    // <cluster_list> → <cluster_item> { <cluster_item> }
    private void parseClusterList() {
        enter("CLUSTER_LIST");
        parseClusterItem();
        // Additional cluster items (zero or more)
        while (check(TokenType.COMMA)) {
            advance(); 
            if (check(TokenType.IDENTIFIER)) {
                parseClusterItem();
            } else {
                recordError("Expected array name after ','");
                synchronize();
            }
        }
        exit("CLUSTER_LIST");
    }

    // <cluster_item> → IDENTIFIER L_BRACKET PULSE_LIT R_BRACKET <cluster_dim_tail>
    private void parseClusterItem() {
        enter("CLUSTER_ITEM");
        expect(TokenType.IDENTIFIER, "Expected array name");
        expect(TokenType.L_BRACKET,  "Expected '[' for array size");
        expect(TokenType.PULSE_LIT,  "Array size must be an integer literal");
        expect(TokenType.R_BRACKET,  "Expected ']' after array size");
        parseClusterDimTail();
        exit("CLUSTER_ITEM");
    }

    // <cluster_dim_tail> → L_BRACKET PULSE_LIT R_BRACKET [ ASSIGN <2D_init> ] | [ ASSIGN <1D_init> ]
    private void parseClusterDimTail() {
        enter("CLUSTER_DIM_TAIL");
        if (check(TokenType.L_BRACKET)) {
            advance();
            expect(TokenType.PULSE_LIT, "Second dimension must be an integer literal");
            expect(TokenType.R_BRACKET, "Expected ']' after second dimension");
            // optional: ASSIGN <2D_init>
            if (check(TokenType.ASSIGN)) {
                advance();
                parse2DInit();
            }
        } else { // optional: ASSIGN <1D_init>
            if (check(TokenType.ASSIGN)) {
                advance();
                parse1DInit();
            }
        }
        exit("CLUSTER_DIM_TAIL");
    }

    // <1D_init> → L_BRACE <add_expr> { COMMA <add_expr> } R_BRACE
    private void parse1DInit() {
        enter("1D_INIT");
        expect(TokenType.L_BRACE, "Expected '{' to open 1D initialiser");
        parseAddExpr();
        // { COMMA <add_expr> }
        while (check(TokenType.COMMA)) {
            advance();
            if (isLiteral() || check(TokenType.IDENTIFIER) || check(TokenType.L_PAREN)) {
                parseAddExpr();
            } else {
                recordError("Expected expression after ','");
                synchronize();
            }
        }
        expect(TokenType.R_BRACE, "Expected '}' to close 1D initialiser");
        exit("1D_INIT");
    }

    // <2D_init> → L_BRACE <1D_init> { COMMA <1D_init> } R_BRACE
    private void parse2DInit() {
        enter("2D_INIT");
        expect(TokenType.L_BRACE, "Expected '{' to open 2D initialiser");
        parse1DInit();
        // { COMMA <1D_init> }
        while (check(TokenType.COMMA)) {
            advance();
            if (check(TokenType.L_BRACE)) {
                parse1DInit();
            } else {
                recordError("Expected '{' for 2D array row after ','");
                synchronize();
            }
        }
        expect(TokenType.R_BRACE, "Expected '}' to close 2D initialiser");
        exit("2D_INIT");
    }


    // ----------- DATA TYPE -----------


    private void parseDataType() {
        enter("DATA_TYPE");
        if (isDataType()) {
            advance();
        } else {
            recordError("Expected a data type (pulse, spark, stream, thought, neuron, synapse)");
            synchronize();
        }
        exit("DATA_TYPE");
    }


    //  ----------- STATEMENTS -----------


    // <statement_list> → { <statement> }
    private void parseStatementList() {
        enter("STATEMENT_LIST");
        while (!check(TokenType.R_BRACE) && !check(TokenType.EOF)
               && !check(TokenType.PATH)  && !check(TokenType.BASE)) {
            parseStatement();
        }
        exit("STATEMENT_LIST");
    }

    // <statement> → (see below for all branches)
    private void parseStatement() {
        enter("STATEMENT");

        if (check(TokenType.INSTINCT) || isDataType()) {
            parseDeclaration();

        } else if (check(TokenType.RECALL)) {
            advance();
            parseExpr();
            expect(TokenType.SEMICOLON, "Expected ';' after recall");

        } else if (check(TokenType.FLOW)) {
            advance();
            expect(TokenType.SEMICOLON, "Expected ';' after 'flow'");

        } else if (check(TokenType.DORMANT)) {
            advance();
            expect(TokenType.SEMICOLON, "Expected ';' after 'dormant'");

        } else if (check(TokenType.STIMULATE)) {
            parseConditionalStmt();

        } else if (check(TokenType.CYCLE) || check(TokenType.REACT) || check(TokenType.ECHO)) {
            parseLoopStmt();

        } else if (check(TokenType.EVALUATE)) {
            parseSwitchStmt();

        } else if (check(TokenType.SENSE) || check(TokenType.EXPRESS)) {
            parseIoStmt();

        } else if (check(TokenType.LENGTH) || check(TokenType.TRANSCRIBE)) {
            parseBuiltinCall();
            expect(TokenType.SEMICOLON, "Expected ';' after built-in call");

        } else if (check(TokenType.IDENTIFIER)) {
            Token next = scanner.lookaheadToken();

            if (next != null && next.type == TokenType.L_PAREN) {
                parseSubroutineCall();
                expect(TokenType.SEMICOLON, "Expected ';' after function call");
            } 
            else if (next != null && (next.type == TokenType.INCREMENT || next.type == TokenType.DECREMENT)) {
                parseVariableAccess();
                advance(); 
                expect(TokenType.SEMICOLON, "Expected ';' after increment/decrement");
            } 
            else {
                // Assignment: x = 5;
                parseAssignStmt();
                expect(TokenType.SEMICOLON, "Expected ';' after assignment");
            }
        } else {
            recordError("Unexpected token '" + current.lexeme + "' — not a valid statement start");
            synchronize();
        }
        exit("STATEMENT");
    }


    //  I/O


    // <io_stmt> → SENSE <data_type> IDENTIFIER SEMICOLON | EXPRESS L_PAREN <expr> R_PAREN SEMICOLON
    private void parseIoStmt() {
        enter("IO_STMT");
        if (check(TokenType.SENSE)) {
            advance();
            parseDataType();
            expect(TokenType.IDENTIFIER, "Expected variable name in 'sense'");
            expect(TokenType.SEMICOLON,  "Expected ';' after sense");
        } else if (check(TokenType.EXPRESS)) {
            advance();
            expect(TokenType.L_PAREN,   "Expected '(' after 'express'");
            parseExpr();
            expect(TokenType.R_PAREN,   "Expected ')' to close express");
            expect(TokenType.SEMICOLON, "Expected ';' after express");
        }
        exit("IO_STMT");
    }


    //  Conditional


    // <conditional_stmt> → STIMULATE L_PAREN <expr> R_PAREN L_BRACE <statement_list> R_BRACE 
    //                     [ INHIBIT L_BRACE <statement_list> R_BRACE ]
    private void parseConditionalStmt() {
        enter("CONDITIONAL_STMT");
        expect(TokenType.STIMULATE, "Expected 'stimulate'");
        expect(TokenType.L_PAREN,   "Expected '(' after 'stimulate'");
        parseExpr();
        expect(TokenType.R_PAREN,   "Expected ')' after stimulate condition");
        expect(TokenType.L_BRACE,   "Expected '{' to open stimulate body");
        parseStatementList();
        expect(TokenType.R_BRACE,   "Expected '}' to close stimulate body");
        if (check(TokenType.INHIBIT)) {
            advance();
            expect(TokenType.L_BRACE, "Expected '{' after 'inhibit'");
            parseStatementList();
            expect(TokenType.R_BRACE, "Expected '}' to close inhibit body");
        }
        exit("CONDITIONAL_STMT");
    }


    //  Loops

    /*  <loop_stmt> → CYCLE L_PAREN <expr> R_PAREN L_BRACE <statement_list> R_BRACE
            | REACT L_BRACE <statement_list> R_BRACE CYCLE L_PAREN <expr> R_PAREN SEMICOLON
            | ECHO L_PAREN <echo_init> SEMICOLON <expr> SEMICOLON <echo_update> R_PAREN L_BRACE <statement_list> R_BRACE
    */
            private void parseLoopStmt() {
        enter("LOOP_STMT");
        if (check(TokenType.CYCLE)) {
            advance();
            expect(TokenType.L_PAREN,   "Expected '(' after 'cycle'");
            parseExpr();
            expect(TokenType.R_PAREN,   "Expected ')' after cycle condition");
            expect(TokenType.L_BRACE,   "Expected '{' to open cycle body");
            parseStatementList();
            expect(TokenType.R_BRACE,   "Expected '}' to close cycle body");

        } else if (check(TokenType.REACT)) {
            advance();
            expect(TokenType.L_BRACE,   "Expected '{' to open react body");
            parseStatementList();
            expect(TokenType.R_BRACE,   "Expected '}' to close react body");
            expect(TokenType.CYCLE,     "Expected 'cycle' after react body");
            expect(TokenType.L_PAREN,   "Expected '(' for cycle condition");
            parseExpr();
            expect(TokenType.R_PAREN,   "Expected ')' after cycle condition");
            expect(TokenType.SEMICOLON, "Expected ';' after react...cycle");

        } else if (check(TokenType.ECHO)) {
            advance();
            expect(TokenType.L_PAREN, "Expected '(' after 'echo'");
            parseEchoInit();
            expect(TokenType.SEMICOLON, "Expected ';' after echo initialiser");
            parseExpr();
            expect(TokenType.SEMICOLON, "Expected ';' after echo condition");
            parseEchoUpdate();
            expect(TokenType.R_PAREN, "Expected ')' to close echo header");
            expect(TokenType.L_BRACE, "Expected '{' to open echo body");
            parseStatementList();
            expect(TokenType.R_BRACE, "Expected '}' to close echo body");
        }
        exit("LOOP_STMT");
    }

    // <echo_init> → <assign_stmt> | <echo_decl>
    private void parseEchoInit() {
        enter("ECHO_INIT");
        if (isDataType()) {
            parseEchoDecl();
        } else if (check(TokenType.IDENTIFIER)) {
            parseAssignStmt();
        } else {
            recordError("Expected a declaration or assignment in echo initialiser");
            synchronize();
        }
        exit("ECHO_INIT");
    }

    // <echo_decl> → <data_type> <id_list>
    private void parseEchoDecl() {
        enter("ECHO_DECL");
        parseDataType();
        parseIdList();
        exit("ECHO_DECL");
    }

    // <echo_update> → IDENTIFIER (INCREMENT | DECREMENT) | <assign_stmt>
    private void parseEchoUpdate() {
        enter("ECHO_UPDATE");
        if (check(TokenType.IDENTIFIER)) {
            Token next = scanner.lookaheadToken();
            if (next != null &&
            (next.type == TokenType.INCREMENT ||
                next.type == TokenType.DECREMENT)) {
                advance(); // IDENTIFIER
                advance(); // ++ or --
            } else {
                parseAssignStmt();
            }
        } else {
            recordError("Expected a variable name in echo update expression");
            synchronize();
        }
        exit("ECHO_UPDATE");
    }


    //  Switch


    /* <switch_stmt> → EVALUATE L_PAREN <expr> R_PAREN L_BRACE 
                   { PATH <literal> COLON <statement_list> [ DORMANT SEMICOLON ] } 
                     [ BASE COLON <statement_list> ] R_BRACE
    */
    private void parseSwitchStmt() {
        enter("SWITCH_STMT");

        expect(TokenType.EVALUATE, "Expected 'evaluate'");
        expect(TokenType.L_PAREN, "Expected '(' after 'evaluate'");
        parseExpr();
        expect(TokenType.R_PAREN, "Expected ')' after evaluate expression");
        expect(TokenType.L_BRACE, "Expected '{' to open evaluate block");

        // { PATH <literal> COLON <statement_list> [ DORMANT SEMICOLON ] }
        while (check(TokenType.PATH)) {
            advance(); // PATH
            
            // Check if there's a literal after PATH
            if (isLiteral()) {
                parseLiteral();
                expect(TokenType.COLON, "Expected ':' after path value");
                parseStatementList();

                if (check(TokenType.DORMANT)) {
                    advance();
                    expect(TokenType.SEMICOLON, "Expected ';' after 'dormant'");
                }
            } else {
                recordError("Expected literal value after 'path'");
                synchronize();
                // Skip to next safe point (next PATH or BASE or })
                while (!check(TokenType.EOF) && !check(TokenType.PATH) 
                    && !check(TokenType.BASE) && !check(TokenType.R_BRACE)) {
                    advance();
                }
            }
        }
        
        // [ BASE COLON <statement_list> ]
        if (check(TokenType.BASE)) {
            advance();
            expect(TokenType.COLON, "Expected ':' after 'base'");
            parseStatementList();
        }
        
        expect(TokenType.R_BRACE, "Expected '}' to close evaluate block");
        exit("SWITCH_STMT");
    }


    //  Assignment


    // <assign_stmt> → <variable_access> <assign_op> <expr>
    private void parseAssignStmt() {
        enter("ASSIGN_STMT");
        parseVariableAccess();
        parseAssignOp();
        parseExpr();
        exit("ASSIGN_STMT");
    }

    // <assign_op> → ASSIGN | PLUS_ASSIGN | MINUS_ASSIGN | MUL_ASSIGN | DIV_ASSIGN | MOD_ASSIGN
    private void parseAssignOp() {
        enter("ASSIGN_OP");
        if (check(TokenType.ASSIGN)       || check(TokenType.PLUS_ASSIGN)  ||
            check(TokenType.MINUS_ASSIGN) || check(TokenType.MUL_ASSIGN)   ||
            check(TokenType.DIV_ASSIGN)   || check(TokenType.MOD_ASSIGN)) {
            advance();
        } else {
            recordError("Expected an assignment operator (=, +=, -=, *=, /=, %=)");
            synchronize();
        }
        exit("ASSIGN_OP");
    }


    //  ----------- EXPRESSIONS -----------

    // <expr> → <logic_or>
    private void parseExpr() {
        enter("EXPR");
        parseLogicOr();
        exit("EXPR");
    }

    // <logic_or> → <logic_xor> { OR <logic_xor> }
    private void parseLogicOr() {
        enter("LOGIC_OR");
        parseLogicXor();
        while (check(TokenType.OR)) {
            advance();
            parseLogicXor();
        }
        exit("LOGIC_OR");
    }

    // <logic_xor> → <logic_and> { XOR <logic_and> }
    private void parseLogicXor() {
        enter("LOGIC_XOR");
        parseLogicAnd();
        while (check(TokenType.XOR)) {
            advance();
            parseLogicAnd();
        }
        exit("LOGIC_XOR");
    }

    // <logic_and> → <rel_equal> { AND <rel_equal> }
    private void parseLogicAnd() {
        enter("LOGIC_AND");
        parseRelEqual();
        while (check(TokenType.AND)) {
            advance();
            parseRelEqual();
        }
        exit("LOGIC_AND");
    }

    // <rel_equal> → <rel_expr> [ ( EQUAL_TO | NOT_EQUAL ) <rel_expr> ]
    private void parseRelEqual() {
        enter("REL_EQUAL");
        parseRelExpr();
        if (check(TokenType.EQUAL_TO)) {
            advance();
            parseRelExpr();
        } else if (check(TokenType.NOT_EQUAL)) {
            advance();
            parseRelExpr();
        }
        exit("REL_EQUAL");
    }

    // <rel_expr> → <add_expr> [ ( GREATER | LESS | GREATER_EQ | LESS_EQ ) <add_expr> ]
    private void parseRelExpr() {
        enter("REL_EXPR");
        parseAddExpr();
        if (check(TokenType.GREATER) || check(TokenType.LESS) ||
            check(TokenType.GREATER_EQ) || check(TokenType.LESS_EQ)) {
            advance();
            parseAddExpr();
        }
        exit("REL_EXPR");
    }

    // <add_expr> → <mult_expr> { ( PLUS | MINUS ) <mult_expr> }
    private void parseAddExpr() {
        enter("ADD_EXPR");
        parseMultExpr();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            advance();
            parseMultExpr();
        }
        exit("ADD_EXPR");
    }

    // <mult_expr> → <pow_expr> { ( STAR | SLASH | MOD ) <pow_expr> }
    private void parseMultExpr() {
        enter("MULT_EXPR");
        parsePowExpr();
        while (check(TokenType.STAR) || check(TokenType.SLASH) || check(TokenType.MOD)) {
            advance();
            parsePowExpr();
        }
        exit("MULT_EXPR");
    }

    // <pow_expr> → <unary_expr> [ EXPONENT <pow_expr> ]
    private void parsePowExpr() {
        enter("POW_EXPR");
        parseUnaryExpr();
        if (check(TokenType.EXPONENT)) {
            advance();
            parsePowExpr();
        }
        exit("POW_EXPR");
    }

    // <unary_expr> → ( INCREMENT | DECREMENT | PLUS | MINUS | NOT ) <unary_expr> | <postfix_expr>
    private void parseUnaryExpr() {
        enter("UNARY_EXPR");
        if (check(TokenType.NOT)  || check(TokenType.MINUS) ||
            check(TokenType.PLUS) || check(TokenType.INCREMENT) ||
            check(TokenType.DECREMENT)) {
            advance();
            parseUnaryExpr();
        } else {
            parsePostfixExpr();
        }
        exit("UNARY_EXPR");
    }

    // <postfix_expr> → <factor> { INCREMENT | DECREMENT }
    private void parsePostfixExpr() {
        enter("POSTFIX_EXPR");
        parseFactor();
        while (check(TokenType.INCREMENT) || check(TokenType.DECREMENT)) {
            advance();
        }
        exit("POSTFIX_EXPR");
    }

    // <factor>	→ <variable_access> | <literal> | L_PAREN <expr> R_PAREN | <subroutine_call> | <builtin_call>		
    private void parseFactor() {
        enter("FACTOR");

        if (check(TokenType.LENGTH) || check(TokenType.TRANSCRIBE)) {
            parseBuiltinCall();
        } else if (check(TokenType.IDENTIFIER)) {
            Token next = scanner.lookaheadToken();
            if (next != null && next.type == TokenType.L_PAREN) {
                parseSubroutineCall();
            } else {
                parseVariableAccess();
            }
        } else if (isLiteral()) {
            parseLiteral();
        } else if (check(TokenType.L_PAREN)) {
            advance();
            parseExpr();
            expect(TokenType.R_PAREN, "Expected ')' to close grouped expression");
        } else {
            recordError("Expected an expression value but found '" + current.lexeme + "'");
            synchronize();
        }
        exit("FACTOR");
    }

    // <literal> → PULSE_LIT | SPARK_LIT | STREAM_LIT | THOUGHT_LIT | NEURON_LIT | TRUE | FALSE
    private void parseLiteral() {
        enter("LITERAL");

        if (check(TokenType.PULSE_LIT) ||
            check(TokenType.SPARK_LIT) ||
            check(TokenType.STREAM_LIT) ||
            check(TokenType.THOUGHT_LIT) ||
            check(TokenType.NEURON_LIT) ||
            check(TokenType.TRUE) ||
            check(TokenType.FALSE)) {

            advance();
        } else {
            recordError("Expected a literal");
            synchronize();
        }

        exit("LITERAL");
    }


    // ----------- VARIABLE ACCESS, CALLS, BUILT-INS -----------


    // <variable_access> → IDENTIFIER { L_BRACKET <expr> R_BRACKET }
    private void parseVariableAccess() {
        enter("VARIABLE_ACCESS");
        expect(TokenType.IDENTIFIER, "Expected a variable name");
        while (check(TokenType.L_BRACKET)) {
            advance();
            parseExpr();
            expect(TokenType.R_BRACKET, "Expected ']' to close array index");
        }
        exit("VARIABLE_ACCESS");
    }

    // <subroutine_call> → IDENTIFIER L_PAREN [ <expr> { COMMA <expr> } ] R_PAREN
    private void parseSubroutineCall() {
        enter("SUBROUTINE_CALL");
        expect(TokenType.IDENTIFIER, "Expected a function name");
        expect(TokenType.L_PAREN,    "Expected '(' after function name");
        if (!check(TokenType.R_PAREN)) {
            parseExpr();
            while (check(TokenType.COMMA)) {
                advance();
                parseExpr();
            }
        }
        expect(TokenType.R_PAREN,    "Expected ')' to close argument list");
        exit("SUBROUTINE_CALL");
    }

    // <builtin_call> → (LENGTH | TRANSCRIBE) L_PAREN <expr> R_PAREN				
    private void parseBuiltinCall() {
        enter("BUILTIN_CALL");
        if (check(TokenType.LENGTH) || check(TokenType.TRANSCRIBE)) {
            advance();
        } else {
            recordError("Expected 'length' or 'transcribe'");
            synchronize();
        }
        expect(TokenType.L_PAREN, "Expected '(' after built-in name");
        parseExpr();
        expect(TokenType.R_PAREN, "Expected ')' to close built-in call");
        exit("BUILTIN_CALL");
    }


    // ----------- HELPERS -----------

    private boolean isDataType() {
        return check(TokenType.PULSE)   || check(TokenType.SPARK)  ||
               check(TokenType.STREAM)  || check(TokenType.THOUGHT)||
               check(TokenType.NEURON)  || check(TokenType.SYNAPSE);
    }

    private boolean isLiteral() {
        return check(TokenType.PULSE_LIT)  || check(TokenType.SPARK_LIT)   ||
               check(TokenType.STREAM_LIT) || check(TokenType.THOUGHT_LIT) ||
               check(TokenType.NEURON_LIT) || check(TokenType.TRUE)        ||
               check(TokenType.FALSE);
    }

    private boolean check(TokenType type) {
        return current.type == type;
    }

    private Token advance() {
        previous = current;
        do {
            current = scanner.getNextToken();
        } while (current.type == TokenType.ILLEGAL);

        if (previous != null) {
            printIndent();
            System.out.println("  / matched: [" + previous.type + "] \"" + previous.lexeme + "\"");
        }
        return previous;
    }

    private void expect(TokenType type, String message) {
        if (check(type)) {
            advance();
        } else {
            recordError(message + " — found '" + current.lexeme + "'");
        }
    }

    private void recordError(String message) {
        hadError = true;
        String err = "[PARSE ERROR] Line " + current.line + ": " + message;
        errors.add(err);
        System.err.println("\n  *** " + err + " ***\n");
    }

    private void synchronize() {
        while (!check(TokenType.EOF)) {
            if (check(TokenType.SEMICOLON)) { advance(); return; }
            if (check(TokenType.R_BRACE))   { return; }
            switch (current.type) {
                case STIMULATE: case INHIBIT:  case CYCLE:   case REACT:
                case ECHO:      case EVALUATE: case RECALL:  case SENSE:
                case EXPRESS:   case FLOW:     case DORMANT: case INSTINCT:
                case PULSE:     case SPARK:    case STREAM:  case THOUGHT:
                case NEURON:    case SYNAPSE:
                    return;
                default:
                    advance();
            }
        }
    }

    private void printResults() {
        System.out.println("\n//////// PARSING COMPLETE! ////////");
        System.out.println("\nPARSER RESULTS");
        System.out.println("--------------------------------");
        if (errors.isEmpty()) {
            System.out.println("  No syntax errors found. Program is valid.");
        } else {
            System.out.println("  Found " + errors.size() + " syntax error(s):\n");
            for (String e : errors) System.out.println("  " + e);
        }
        System.out.println("--------------------------------");
    }
}