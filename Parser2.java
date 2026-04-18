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
    public AST.Program parse() {
        System.out.println("\n========== STARTING PARSER ==========\n");
        enter("PROGRAM");

        List<AST.Subroutine> subroutines = parseSubroutineList();

        printIndent();
        System.out.println("Expecting ACTIVATE...");
        expect(TokenType.ACTIVATE, "Expected 'activate' as the program entry point");
        expect(TokenType.L_BRACE,  "Expected '{' to open the activate block");
        List<AST.Stmt> activateBody = parseStatementList();
        expect(TokenType.R_BRACE,  "Expected '}' to close the activate block");

        if (!check(TokenType.EOF)) {
            recordError("Unexpected tokens after the closing '}' of activate");
        }

        exit("PROGRAM");
        printResults();
        return new AST.Program(subroutines, activateBody);
    }


    // ----------- SUBROUTINES -----------


    // <subroutine_list> → { <subroutine> }
    private List<AST.Subroutine> parseSubroutineList() {
        enter("SUBROUTINE_LIST");
        List<AST.Subroutine> subroutines = new ArrayList<>();

        while (check(TokenType.ACTION)) {
            subroutines.add(parseSubroutine());
        }
        
        exit("SUBROUTINE_LIST");
        return subroutines;
    }

    // <subroutine> → ACTION <rec_type> IDENTIFIER L_PAREN <params> R_PAREN L_BRACE <statement_list> R_BRACE
    private AST.Subroutine parseSubroutine() {
        enter("SUBROUTINE");
        expect(TokenType.ACTION, "Expected 'action'");
        
        Token returnType = parseRecType();
        
        Token name = current; // Capture the name before expecting it
        expect(TokenType.IDENTIFIER, "Expected function name after return type");
        
        expect(TokenType.L_PAREN, "Expected '(' after function name");
        List<AST.Param> params = parseParams();
        expect(TokenType.R_PAREN, "Expected ')' to close parameter list");
        
        expect(TokenType.L_BRACE, "Expected '{' to open function body");
        List<AST.Stmt> body = parseStatementList();
        expect(TokenType.R_BRACE, "Expected '}' to close function body");
        
        exit("SUBROUTINE");
        return new AST.Subroutine(returnType, name, params, body);
    }

    // <rec_type> → <data_type> | CLUSTER <data_type> L_BRACKET R_BRACKET <array_tail> | VOID
    private Token parseRecType() {
        enter("REC_TYPE");
        Token typeToken = current; // Default to current token

        if (check(TokenType.VOID)) {
            advance();
        } else if (check(TokenType.CLUSTER)) {
            advance();
            typeToken = parseDataType(); // Capture the specific data type
            expect(TokenType.L_BRACKET, "Expected '[' in cluster return type");
            expect(TokenType.R_BRACKET, "Expected ']' in cluster return type");
            parseArrayTail();
        } else if (isDataType()) {
            typeToken = parseDataType();
        } else {
            recordError("Expected a return type (void, data type, or cluster)");
            synchronize();
        }
        
        exit("REC_TYPE");
        return typeToken;
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
    private List<AST.Param> parseParams() {
        enter("PARAMS");
        List<AST.Param> paramList = new ArrayList<>();

        if (isDataType() || check(TokenType.CLUSTER)) {
            paramList.add(parseParamItem());
            while (check(TokenType.COMMA)) {
                advance();
                if (isDataType() || check(TokenType.CLUSTER)) {
                    paramList.add(parseParamItem());
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
        return paramList;
    }

    // <param_item> → <data_type> IDENTIFIER | CLUSTER <data_type> IDENTIFIER L_BRACKET R_BRACKET <array_tail>
    private AST.Param parseParamItem() {
        enter("PARAM_ITEM");
        boolean isCluster = false;
        Token dataType;
        Token name;

        if (check(TokenType.CLUSTER)) {
            advance();
            isCluster = true;
            dataType = parseDataType();
            
            name = current; // Capture parameter name
            expect(TokenType.IDENTIFIER, "Expected parameter name in cluster parameter");
            expect(TokenType.L_BRACKET,  "Expected '[' in cluster parameter");
            expect(TokenType.R_BRACKET,  "Expected ']' in cluster parameter");
            parseArrayTail();
        } else {
            dataType = parseDataType();
            name = current; // Capture parameter name
            expect(TokenType.IDENTIFIER, "Expected parameter name after data type");
        }
        
        exit("PARAM_ITEM");
        return new AST.Param(dataType, name, isCluster);
    }

    
    // ----------- DECLARATIONS -----------


    // <declaration> → <data_type> <typed_decl_tail> | <const_decl>
    private AST.Stmt parseDeclaration() {
        enter("DECLARATION");
        
        // Changed this variable type from AST.DeclarationStmt to AST.Stmt
        AST.Stmt declNode; 

        if (check(TokenType.INSTINCT)) {
            declNode = parseConstDecl();
        } else {
            Token dataType = parseDataType();
            declNode = parseTypedDeclTail(dataType); 
        }

        exit("DECLARATION");
        return declNode;
    }

    // <typed_decl_tail> → CLUSTER <cluster_list> SEMICOLON | <id_list> SEMICOLON
    private AST.Stmt parseTypedDeclTail(Token dataType) {
        enter("TYPED_DECL_TAIL");
        
        if (check(TokenType.CLUSTER)) {
            advance();
            List<AST.ClusterItem> clusters = parseClusterList(); 
            expect(TokenType.SEMICOLON, "Expected ';' after cluster declaration");
            exit("TYPED_DECL_TAIL");
            // Return our new array node!
            return new AST.ClusterDeclStmt(dataType, clusters);
        } else {
            List<AST.VariableDeclaration> vars = parseIdList();
            expect(TokenType.SEMICOLON, "Expected ';' after variable declaration");
            exit("TYPED_DECL_TAIL");
            return new AST.DeclarationStmt(dataType, vars, false); 
        }
    }

    // <id_list> → IDENTIFIER [ ASSIGN <expr> ] { COMMA IDENTIFIER [ ASSIGN <expr> ] }
    private List<AST.VariableDeclaration> parseIdList() {
        enter("ID_LIST");
        List<AST.VariableDeclaration> variables = new java.util.ArrayList<>();

        // First identifier
        Token name = current;
        expect(TokenType.IDENTIFIER, "Expected a variable name");
        
        AST.Expr initializer = null;
        if (check(TokenType.ASSIGN)) {
            advance();
            initializer = parseExpr();
        }
        variables.add(new AST.VariableDeclaration(name, initializer));

        // { COMMA IDENTIFIER [ ASSIGN <expr> ] }
        while (check(TokenType.COMMA)) {
            advance();
            if (check(TokenType.IDENTIFIER)) {
                name = current;
                expect(TokenType.IDENTIFIER, "Expected variable name after ','");
                
                initializer = null;
                if (check(TokenType.ASSIGN)) {
                    advance();
                    initializer = parseExpr();
                }
                variables.add(new AST.VariableDeclaration(name, initializer));
            } else {
                recordError("Expected variable name after ','");
                synchronize();
            }
        }
        
        exit("ID_LIST");
        return variables;
    }

    // <const_decl> → INSTINCT <data_type> <const_list> SEMICOLON
    private AST.DeclarationStmt parseConstDecl() {
        enter("CONST_DECL");
        expect(TokenType.INSTINCT, "Expected 'instinct'");
        
        Token dataType = parseDataType();
        List<AST.VariableDeclaration> constants = parseConstList();
        
        expect(TokenType.SEMICOLON, "Expected ';' after constant declaration");
        exit("CONST_DECL");
        
        // true = this is an instinct (constant)
        return new AST.DeclarationStmt(dataType, constants, true); 
    }

    // <const_list> → IDENTIFIER ASSIGN <expr> { COMMA IDENTIFIER ASSIGN <expr> }						
    private List<AST.VariableDeclaration> parseConstList() {
        enter("CONST_LIST");
        List<AST.VariableDeclaration> constants = new java.util.ArrayList<>();

        Token name = current;
        expect(TokenType.IDENTIFIER, "Expected a constant name");
        expect(TokenType.ASSIGN, "Constants must be assigned — missing '='");
        AST.Expr initializer = parseExpr();
        
        constants.add(new AST.VariableDeclaration(name, initializer));

        // Additional constants (zero or more)
        while (check(TokenType.COMMA)) {
            advance(); 
            if (check(TokenType.IDENTIFIER)) {
                name = current;
                expect(TokenType.IDENTIFIER, "Expected a constant name after ','");
                expect(TokenType.ASSIGN, "Expected '=' for constant assignment");
                initializer = parseExpr();
                
                constants.add(new AST.VariableDeclaration(name, initializer));
            } else {
                recordError("Expected constant name after ','");
                synchronize();
            }
        }
        
        exit("CONST_LIST");
        return constants;
    }


    //  ----------- ARRAY (CLUSTER) DECLARATIONS -----------


    // <cluster_list> → <cluster_item> { <cluster_item> }
    private List<AST.ClusterItem> parseClusterList() {
        enter("CLUSTER_LIST");
        List<AST.ClusterItem> list = new java.util.ArrayList<>();
        list.add(parseClusterItem());
        
        while (check(TokenType.COMMA)) {
            advance(); 
            if (check(TokenType.IDENTIFIER)) {
                list.add(parseClusterItem());
            } else {
                recordError("Expected array name after ','");
                synchronize();
            }
        }
        exit("CLUSTER_LIST");
        return list;
    }

    // <cluster_item> → IDENTIFIER L_BRACKET PULSE_LIT R_BRACKET <cluster_dim_tail>
    private AST.ClusterItem parseClusterItem() {
        enter("CLUSTER_ITEM");
        
        Token name = current;
        expect(TokenType.IDENTIFIER, "Expected array name");
        expect(TokenType.L_BRACKET,  "Expected '[' for array size");
        Token size1 = current;
        expect(TokenType.PULSE_LIT,  "Array size must be an integer literal");
        expect(TokenType.R_BRACKET,  "Expected ']' after array size");
        
        Token size2 = null;
        List<AST.Expr> init1D = null;
        List<List<AST.Expr>> init2D = null;

        // Checking for a 2nd dimension or assignments
        if (check(TokenType.L_BRACKET)) {
            advance();
            size2 = current;
            expect(TokenType.PULSE_LIT, "Second dimension must be an integer literal");
            expect(TokenType.R_BRACKET, "Expected ']' after second dimension");
            if (check(TokenType.ASSIGN)) {
                advance();
                init2D = parse2DInit();
            }
        } else {
            if (check(TokenType.ASSIGN)) {
                advance();
                init1D = parse1DInit();
            }
        }
        
        exit("CLUSTER_ITEM");
        return new AST.ClusterItem(name, size1, size2, init1D, init2D);
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
    private List<AST.Expr> parse1DInit() {
        enter("1D_INIT");
        List<AST.Expr> elements = new java.util.ArrayList<>();
        
        expect(TokenType.L_BRACE, "Expected '{' to open 1D initialiser");
        elements.add(parseAddExpr()); // Use AddExpr to avoid matching logic operators in arrays
        
        while (check(TokenType.COMMA)) {
            advance();
            if (isLiteral() || check(TokenType.IDENTIFIER) || check(TokenType.L_PAREN)) {
                elements.add(parseAddExpr());
            } else {
                recordError("Expected expression after ','");
                synchronize();
            }
        }
        expect(TokenType.R_BRACE, "Expected '}' to close 1D initialiser");
        
        exit("1D_INIT");
        return elements;
    }

    // <2D_init> → L_BRACE <1D_init> { COMMA <1D_init> } R_BRACE
    private List<List<AST.Expr>> parse2DInit() {
        enter("2D_INIT");
        List<List<AST.Expr>> rows = new java.util.ArrayList<>();
        
        expect(TokenType.L_BRACE, "Expected '{' to open 2D initialiser");
        rows.add(parse1DInit());
        
        while (check(TokenType.COMMA)) {
            advance();
            if (check(TokenType.L_BRACE)) {
                rows.add(parse1DInit());
            } else {
                recordError("Expected '{' for 2D array row after ','");
                synchronize();
            }
        }
        expect(TokenType.R_BRACE, "Expected '}' to close 2D initialiser");
        
        exit("2D_INIT");
        return rows;
    }


    // ----------- DATA TYPE -----------


    private Token parseDataType() {
        enter("DATA_TYPE");
        Token typeToken = current; // Capture the token (pulse, spark, etc.)
        if (isDataType()) {
            advance();
        } else {
            recordError("Expected a data type (pulse, spark, stream, thought, neuron, synapse)");
            synchronize();
        }
        exit("DATA_TYPE");
        return typeToken;
    }


    //  ----------- STATEMENTS -----------


    // <statement_list> → { <statement> }
    private List<AST.Stmt> parseStatementList() {
        enter("STATEMENT_LIST");
        List<AST.Stmt> statements = new ArrayList<>();
        
        while (!check(TokenType.R_BRACE) && !check(TokenType.EOF)
               && !check(TokenType.PATH)  && !check(TokenType.BASE)) {
            statements.add(parseStatement());
        }
        exit("STATEMENT_LIST");
        return statements;
    }

    // <statement> → (see below for all branches)
    private AST.Stmt parseStatement() {
        enter("STATEMENT");
        AST.Stmt stmtNode = null; // We will store the resulting node here

        if (check(TokenType.INSTINCT) || isDataType()) {
            stmtNode = parseDeclaration();

        } else if (check(TokenType.RECALL)) {
            Token keyword = advance(); // capture the recall token
            AST.Expr returnVal = parseExpr();
            expect(TokenType.SEMICOLON, "Expected ';' after recall");
            stmtNode = new AST.FlowControlStmt(keyword, returnVal);

        } else if (check(TokenType.FLOW)) {
            Token keyword = advance();
            expect(TokenType.SEMICOLON, "Expected ';' after 'flow'");
            stmtNode = new AST.FlowControlStmt(keyword, null);

        } else if (check(TokenType.DORMANT)) {
            Token keyword = advance();
            expect(TokenType.SEMICOLON, "Expected ';' after 'dormant'");
            stmtNode = new AST.FlowControlStmt(keyword, null);

        } else if (check(TokenType.STIMULATE)) {
            stmtNode = parseConditionalStmt();

        } else if (check(TokenType.CYCLE) || check(TokenType.REACT) || check(TokenType.ECHO)) {
            stmtNode = parseLoopStmt();

        } else if (check(TokenType.EVALUATE)) {
            stmtNode = parseSwitchStmt();

        } else if (check(TokenType.SENSE) || check(TokenType.EXPRESS)) {
            stmtNode = parseIoStmt();

        } else if (check(TokenType.LENGTH) || check(TokenType.TRANSCRIBE)) {
            AST.Expr builtinCall = parseBuiltinCall();
            expect(TokenType.SEMICOLON, "Expected ';' after built-in call");
            stmtNode = new AST.ExprStmt(builtinCall);

        } else if (check(TokenType.IDENTIFIER)) {
            Token next = scanner.lookaheadToken();

            if (next != null && next.type == TokenType.L_PAREN) {
                AST.Expr funcCall = parseSubroutineCall();
                expect(TokenType.SEMICOLON, "Expected ';' after function call");
                stmtNode = new AST.ExprStmt(funcCall);
            } 
            else if (next != null && (next.type == TokenType.INCREMENT || next.type == TokenType.DECREMENT)) {
                AST.Expr varAccess = parseVariableAccess();
                Token operator = advance(); // capture ++ or --
                expect(TokenType.SEMICOLON, "Expected ';' after increment/decrement");
                
                // An increment is technically a unary expression sitting by itself as a statement
                AST.Expr postfix = new AST.UnaryExpr(operator, varAccess, true);
                stmtNode = new AST.ExprStmt(postfix);
            } 
            else {
                // Assignment: x = 5;
                stmtNode = parseAssignStmt();
                expect(TokenType.SEMICOLON, "Expected ';' after assignment");
            }
        } else {
            recordError("Unexpected token '" + current.lexeme + "' — not a valid statement start");
            synchronize();
        }
        
        exit("STATEMENT");
        return stmtNode;
    }


    //  I/O


    // <io_stmt> → SENSE <data_type> IDENTIFIER SEMICOLON | EXPRESS L_PAREN <expr> R_PAREN SEMICOLON
    private AST.IoStmt parseIoStmt() {
        enter("IO_STMT");
        Token action = current; // Capture 'sense' or 'express'
        AST.Expr target = null;

        if (check(TokenType.SENSE)) {
            advance();
            parseDataType(); // Consumes the data type
            
            Token varName = current;
            expect(TokenType.IDENTIFIER, "Expected variable name in 'sense'");
            expect(TokenType.SEMICOLON,  "Expected ';' after sense");
            
            // Package the identifier into a VariableAccessExpr
            target = new AST.VariableAccessExpr(varName, new java.util.ArrayList<>());
            
        } else if (check(TokenType.EXPRESS)) {
            advance();
            expect(TokenType.L_PAREN,   "Expected '(' after 'express'");
            target = parseExpr();       // Capture the expression to print
            expect(TokenType.R_PAREN,   "Expected ')' to close express");
            expect(TokenType.SEMICOLON, "Expected ';' after express");
        }
        
        exit("IO_STMT");
        return new AST.IoStmt(action, target);
    }

    //  Conditional


    // <conditional_stmt> → STIMULATE L_PAREN <expr> R_PAREN L_BRACE <statement_list> R_BRACE 
    //                     [ INHIBIT L_BRACE <statement_list> R_BRACE ]
    private AST.StimulateStmt parseConditionalStmt() {
        enter("CONDITIONAL_STMT");
        
        expect(TokenType.STIMULATE, "Expected 'stimulate'");
        expect(TokenType.L_PAREN,   "Expected '(' after 'stimulate'");
        AST.Expr condition = parseExpr();
        expect(TokenType.R_PAREN,   "Expected ')' after stimulate condition");
        
        expect(TokenType.L_BRACE,   "Expected '{' to open stimulate body");
        List<AST.Stmt> stimulateBody = parseStatementList();
        expect(TokenType.R_BRACE,   "Expected '}' to close stimulate body");
        
        List<AST.Stmt> inhibitBody = null;
        if (check(TokenType.INHIBIT)) {
            advance();
            expect(TokenType.L_BRACE, "Expected '{' after 'inhibit'");
            inhibitBody = parseStatementList();
            expect(TokenType.R_BRACE, "Expected '}' to close inhibit body");
        }
        
        exit("CONDITIONAL_STMT");
        return new AST.StimulateStmt(condition, stimulateBody, inhibitBody);
    }


    //  Loops

    /*  <loop_stmt> → CYCLE L_PAREN <expr> R_PAREN L_BRACE <statement_list> R_BRACE
            | REACT L_BRACE <statement_list> R_BRACE CYCLE L_PAREN <expr> R_PAREN SEMICOLON
            | ECHO L_PAREN <echo_init> SEMICOLON <expr> SEMICOLON <echo_update> R_PAREN L_BRACE <statement_list> R_BRACE
    */
    private AST.Stmt parseLoopStmt() {
        enter("LOOP_STMT");
        AST.Stmt loopNode = null;

        if (check(TokenType.CYCLE)) {
            advance();
            expect(TokenType.L_PAREN,   "Expected '(' after 'cycle'");
            AST.Expr condition = parseExpr();
            expect(TokenType.R_PAREN,   "Expected ')' after cycle condition");
            expect(TokenType.L_BRACE,   "Expected '{' to open cycle body");
            List<AST.Stmt> body = parseStatementList();
            expect(TokenType.R_BRACE,   "Expected '}' to close cycle body");
            
            loopNode = new AST.CycleStmt(condition, body);

        } else if (check(TokenType.REACT)) {
            advance();
            expect(TokenType.L_BRACE,   "Expected '{' to open react body");
            List<AST.Stmt> body = parseStatementList();
            expect(TokenType.R_BRACE,   "Expected '}' to close react body");
            expect(TokenType.CYCLE,     "Expected 'cycle' after react body");
            expect(TokenType.L_PAREN,   "Expected '(' for cycle condition");
            AST.Expr condition = parseExpr();
            expect(TokenType.R_PAREN,   "Expected ')' after cycle condition");
            expect(TokenType.SEMICOLON, "Expected ';' after react...cycle");
            
            loopNode = new AST.ReactStmt(body, condition);

        } else if (check(TokenType.ECHO)) {
            advance();
            expect(TokenType.L_PAREN, "Expected '(' after 'echo'");
            AST.Stmt init = parseEchoInit();
            expect(TokenType.SEMICOLON, "Expected ';' after echo initialiser");
            AST.Expr condition = parseExpr();
            expect(TokenType.SEMICOLON, "Expected ';' after echo condition");
            AST.Stmt update = parseEchoUpdate();
            expect(TokenType.R_PAREN, "Expected ')' to close echo header");
            expect(TokenType.L_BRACE, "Expected '{' to open echo body");
            List<AST.Stmt> body = parseStatementList();
            expect(TokenType.R_BRACE, "Expected '}' to close echo body");
            
            loopNode = new AST.EchoStmt(init, condition, update, body);
        }
        
        exit("LOOP_STMT");
        return loopNode;
    }

    // <echo_init> → <assign_stmt> | <echo_decl>
    private AST.Stmt parseEchoInit() {
        enter("ECHO_INIT");
        AST.Stmt initStmt = null;
        
        if (isDataType()) {
            initStmt = parseEchoDecl();
        } else if (check(TokenType.IDENTIFIER)) {
            initStmt = parseAssignStmt();
        } else {
            recordError("Expected a declaration or assignment in echo initialiser");
            synchronize();
        }
        
        exit("ECHO_INIT");
        return initStmt;
    }

    // <echo_decl> → <data_type> <id_list>
    private AST.DeclarationStmt parseEchoDecl() {
        enter("ECHO_DECL");
        Token dataType = parseDataType();
        List<AST.VariableDeclaration> vars = parseIdList();
        exit("ECHO_DECL");
        return new AST.DeclarationStmt(dataType, vars, false);
    }

    // <echo_update> → IDENTIFIER (INCREMENT | DECREMENT) | <assign_stmt>
    private AST.Stmt parseEchoUpdate() {
        enter("ECHO_UPDATE");
        AST.Stmt updateStmt = null;
        
        if (check(TokenType.IDENTIFIER)) {
            Token next = scanner.lookaheadToken();
            if (next != null && (next.type == TokenType.INCREMENT || next.type == TokenType.DECREMENT)) {
                AST.VariableAccessExpr varAccess = parseVariableAccess();
                Token operator = advance(); // capture ++ or --
                
                // Wrap the x++ inside an expression statement
                AST.Expr postfixExpr = new AST.UnaryExpr(operator, varAccess, true);
                updateStmt = new AST.ExprStmt(postfixExpr);
            } else {
                updateStmt = parseAssignStmt();
            }
        } else {
            recordError("Expected a variable name in echo update expression");
            synchronize();
        }
        
        exit("ECHO_UPDATE");
        return updateStmt;
    }


    //  Switch


    /* <switch_stmt> → EVALUATE L_PAREN <expr> R_PAREN L_BRACE 
                   { PATH <literal> COLON <statement_list> [ DORMANT SEMICOLON ] } 
                     [ BASE COLON <statement_list> ] R_BRACE
    */
    private AST.EvaluateStmt parseSwitchStmt() {
        enter("SWITCH_STMT");

        expect(TokenType.EVALUATE, "Expected 'evaluate'");
        expect(TokenType.L_PAREN, "Expected '(' after 'evaluate'");
        AST.Expr condition = parseExpr();
        expect(TokenType.R_PAREN, "Expected ')' after evaluate expression");
        expect(TokenType.L_BRACE, "Expected '{' to open evaluate block");

        List<AST.PathCase> paths = new java.util.ArrayList<>();

        // { PATH <literal> COLON <statement_list> [ DORMANT SEMICOLON ] }
        while (check(TokenType.PATH)) {
            advance(); // Consume PATH
            
            if (isLiteral()) {
                AST.Expr literal = parseLiteral(); // Wait, we will fix parseLiteral next!
                expect(TokenType.COLON, "Expected ':' after path value");
                List<AST.Stmt> body = parseStatementList();

                boolean hasDormant = false;
                if (check(TokenType.DORMANT)) {
                    advance();
                    expect(TokenType.SEMICOLON, "Expected ';' after 'dormant'");
                    hasDormant = true;
                }
                
                paths.add(new AST.PathCase(literal, body, hasDormant));
                
            } else {
                recordError("Expected literal value after 'path'");
                synchronize();
                while (!check(TokenType.EOF) && !check(TokenType.PATH) 
                    && !check(TokenType.BASE) && !check(TokenType.R_BRACE)) {
                    advance();
                }
            }
        }
        
        // [ BASE COLON <statement_list> ]
        List<AST.Stmt> baseCase = null;
        if (check(TokenType.BASE)) {
            advance();
            expect(TokenType.COLON, "Expected ':' after 'base'");
            baseCase = parseStatementList();
        }
        
        expect(TokenType.R_BRACE, "Expected '}' to close evaluate block");
        exit("SWITCH_STMT");
        
        return new AST.EvaluateStmt(condition, paths, baseCase);
    }


    //  Assignment


    // <assign_stmt> → <variable_access> <assign_op> <expr>
    private AST.AssignStmt parseAssignStmt() {
        enter("ASSIGN_STMT");
        
        AST.VariableAccessExpr target = parseVariableAccess();
        Token operator = parseAssignOp();
        AST.Expr value = parseExpr();
        
        exit("ASSIGN_STMT");
        return new AST.AssignStmt(target, operator, value);
    }

    // <assign_op> → ASSIGN | PLUS_ASSIGN | MINUS_ASSIGN | MUL_ASSIGN | DIV_ASSIGN | MOD_ASSIGN
    private Token parseAssignOp() {
        enter("ASSIGN_OP");
        Token opToken = current; // Default to current
        
        if (check(TokenType.ASSIGN)       || check(TokenType.PLUS_ASSIGN)  ||
            check(TokenType.MINUS_ASSIGN) || check(TokenType.MUL_ASSIGN)   ||
            check(TokenType.DIV_ASSIGN)   || check(TokenType.MOD_ASSIGN)) {
            opToken = advance();
        } else {
            recordError("Expected an assignment operator (=, +=, -=, *=, /=, %=)");
            synchronize();
        }
        
        exit("ASSIGN_OP");
        return opToken; // Return the specific operator token
    }


    //  ----------- EXPRESSIONS -----------

    // <expr> → <logic_or>
    private AST.Expr parseExpr() {
        enter("EXPR");
        AST.Expr expr = parseLogicOr();
        exit("EXPR");
        return expr;
    }

    // <logic_or> → <logic_xor> { OR <logic_xor> }
    private AST.Expr parseLogicOr() {
        enter("LOGIC_OR");
        AST.Expr expr = parseLogicXor();
        while (check(TokenType.OR)) {
            Token operator = advance();
            AST.Expr right = parseLogicXor();
            expr = new AST.BinaryExpr(expr, operator, right);
        }
        exit("LOGIC_OR");
        return expr;
    }

    // <logic_xor> → <logic_and> { XOR <logic_and> }
    private AST.Expr parseLogicXor() {
        enter("LOGIC_XOR");
        AST.Expr expr = parseLogicAnd();
        while (check(TokenType.XOR)) {
            Token operator = advance();
            AST.Expr right = parseLogicAnd();
            expr = new AST.BinaryExpr(expr, operator, right);
        }
        exit("LOGIC_XOR");
        return expr;
    }

    // <logic_and> → <rel_equal> { AND <rel_equal> }
    private AST.Expr parseLogicAnd() {
        enter("LOGIC_AND");
        AST.Expr expr = parseRelEqual();
        while (check(TokenType.AND)) {
            Token operator = advance();
            AST.Expr right = parseRelEqual();
            expr = new AST.BinaryExpr(expr, operator, right);
        }
        exit("LOGIC_AND");
        return expr;
    }

    // <rel_equal> → <rel_expr> [ ( EQUAL_TO | NOT_EQUAL ) <rel_expr> ]
    private AST.Expr parseRelEqual() {
        enter("REL_EQUAL");
        AST.Expr expr = parseRelExpr();
        if (check(TokenType.EQUAL_TO) || check(TokenType.NOT_EQUAL)) {
            Token operator = advance();
            AST.Expr right = parseRelExpr();
            expr = new AST.BinaryExpr(expr, operator, right);
        }
        exit("REL_EQUAL");
        return expr;
    }

    // <rel_expr> → <add_expr> [ ( GREATER | LESS | GREATER_EQ | LESS_EQ ) <add_expr> ]
    private AST.Expr parseRelExpr() {
        enter("REL_EXPR");
        AST.Expr expr = parseAddExpr();
        if (check(TokenType.GREATER) || check(TokenType.LESS) ||
            check(TokenType.GREATER_EQ) || check(TokenType.LESS_EQ)) {
            Token operator = advance();
            AST.Expr right = parseAddExpr();
            expr = new AST.BinaryExpr(expr, operator, right);
        }
        exit("REL_EXPR");
        return expr;
    }

    // <add_expr> → <mult_expr> { ( PLUS | MINUS ) <mult_expr> }
    private AST.Expr parseAddExpr() {
        enter("ADD_EXPR");
        AST.Expr expr = parseMultExpr();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            Token operator = advance();
            AST.Expr right = parseMultExpr();
            expr = new AST.BinaryExpr(expr, operator, right);
        }
        exit("ADD_EXPR");
        return expr;
    }

    // <mult_expr> → <pow_expr> { ( STAR | SLASH | MOD ) <pow_expr> }
    private AST.Expr parseMultExpr() {
        enter("MULT_EXPR");
        AST.Expr expr = parsePowExpr();
        while (check(TokenType.STAR) || check(TokenType.SLASH) || check(TokenType.MOD)) {
            Token operator = advance();
            AST.Expr right = parsePowExpr();
            expr = new AST.BinaryExpr(expr, operator, right);
        }
        exit("MULT_EXPR");
        return expr;
    }

    // <pow_expr> → <unary_expr> [ EXPONENT <pow_expr> ]
    private AST.Expr parsePowExpr() {
        enter("POW_EXPR");
        AST.Expr expr = parseUnaryExpr();
        if (check(TokenType.EXPONENT)) {
            Token operator = advance();
            AST.Expr right = parsePowExpr(); // Recursively call powExpr for right-associativity
            expr = new AST.BinaryExpr(expr, operator, right);
        }
        exit("POW_EXPR");
        return expr;
    }

    // <unary_expr> → ( INCREMENT | DECREMENT | PLUS | MINUS | NOT ) <unary_expr> | <postfix_expr>
    private AST.Expr parseUnaryExpr() {
        enter("UNARY_EXPR");
        AST.Expr expr;
        if (check(TokenType.NOT)  || check(TokenType.MINUS) ||
            check(TokenType.PLUS) || check(TokenType.INCREMENT) ||
            check(TokenType.DECREMENT)) {
            Token operator = advance();
            AST.Expr operand = parseUnaryExpr();
            expr = new AST.UnaryExpr(operator, operand, false); // false = prefix
        } else {
            expr = parsePostfixExpr();
        }
        exit("UNARY_EXPR");
        return expr;
    }

    // <postfix_expr> → <factor> { INCREMENT | DECREMENT }
    private AST.Expr parsePostfixExpr() {
        enter("POSTFIX_EXPR");
        AST.Expr expr = parseFactor();
        while (check(TokenType.INCREMENT) || check(TokenType.DECREMENT)) {
            Token operator = advance();
            expr = new AST.UnaryExpr(operator, expr, true); // true = postfix
        }
        exit("POSTFIX_EXPR");
        return expr;
    }

    // <factor>	→ <variable_access> | <literal> | L_PAREN <expr> R_PAREN | <subroutine_call> | <builtin_call>		
    private AST.Expr parseFactor() {
        enter("FACTOR");
        AST.Expr expr = null;

        if (check(TokenType.LENGTH) || check(TokenType.TRANSCRIBE)) {
            expr = parseBuiltinCall();
        } else if (check(TokenType.IDENTIFIER)) {
            Token next = scanner.lookaheadToken();
            if (next != null && next.type == TokenType.L_PAREN) {
                expr = parseSubroutineCall();
            } else {
                expr = parseVariableAccess();
            }
        } else if (isLiteral()) {
            expr = parseLiteral();
        } else if (check(TokenType.L_PAREN)) {
            advance();
            expr = parseExpr();
            expect(TokenType.R_PAREN, "Expected ')' to close grouped expression");
        } else {
            recordError("Expected an expression value but found '" + current.lexeme + "'");
            synchronize();
        }
        
        exit("FACTOR");
        return expr;
    }

    // <literal> → PULSE_LIT | SPARK_LIT | STREAM_LIT | THOUGHT_LIT | NEURON_LIT | TRUE | FALSE
    private AST.LiteralExpr parseLiteral() {
        enter("LITERAL");
        Token value = current;
        if (isLiteral()) {
            advance();
        } else {
            recordError("Expected a literal");
            synchronize();
        }
        exit("LITERAL");
        return new AST.LiteralExpr(value);
    }


    // ----------- VARIABLE ACCESS, CALLS, BUILT-INS -----------


    // <variable_access> → IDENTIFIER { L_BRACKET <expr> R_BRACKET }
    private AST.VariableAccessExpr parseVariableAccess() {
        enter("VARIABLE_ACCESS");
        
        Token name = current; 
        expect(TokenType.IDENTIFIER, "Expected a variable name");
        
        List<AST.Expr> indices = new java.util.ArrayList<>();
        
        while (check(TokenType.L_BRACKET)) {
            advance();
            indices.add(parseExpr()); // Capture the index expression (e.g., the 'i' in arr[i])
            expect(TokenType.R_BRACKET, "Expected ']' to close array index");
        }
        
        exit("VARIABLE_ACCESS");
        return new AST.VariableAccessExpr(name, indices);
    }

    // <subroutine_call> → IDENTIFIER L_PAREN [ <expr> { COMMA <expr> } ] R_PAREN
    private AST.CallExpr parseSubroutineCall() {
        enter("SUBROUTINE_CALL");
        Token callee = current;
        expect(TokenType.IDENTIFIER, "Expected a function name");
        expect(TokenType.L_PAREN,    "Expected '(' after function name");
        
        java.util.List<AST.Expr> arguments = new java.util.ArrayList<>();
        if (!check(TokenType.R_PAREN)) {
            arguments.add(parseExpr());
            while (check(TokenType.COMMA)) {
                advance();
                arguments.add(parseExpr());
            }
        }
        expect(TokenType.R_PAREN,    "Expected ')' to close argument list");
        exit("SUBROUTINE_CALL");
        return new AST.CallExpr(callee, arguments);
    }

    // <builtin_call> → (LENGTH | TRANSCRIBE) L_PAREN <expr> R_PAREN				
    private AST.CallExpr parseBuiltinCall() {
        enter("BUILTIN_CALL");
        Token callee = current;
        if (check(TokenType.LENGTH) || check(TokenType.TRANSCRIBE)) {
            advance();
        } else {
            recordError("Expected 'length' or 'transcribe'");
            synchronize();
        }
        expect(TokenType.L_PAREN, "Expected '(' after built-in name");
        
        java.util.List<AST.Expr> arguments = new java.util.ArrayList<>();
        arguments.add(parseExpr()); // Pass the argument in
        
        expect(TokenType.R_PAREN, "Expected ')' to close built-in call");
        exit("BUILTIN_CALL");
        return new AST.CallExpr(callee, arguments);
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