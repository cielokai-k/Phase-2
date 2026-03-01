

public class DelimOp {

    public Token delimOpToken() {
        //--------Char, line, and column can be called from other classes--------
        char c = 'R'; //arbitrary value
        int line = 0;  
        int column = 0;
        //advance(); //consumes current character

        //---These are methods that come from other classes, pinagsama ko muna here as variables for now---
        TokenType last_token = TokenType.IDENTIFIER; 
        char nextChar = 'X';

        boolean isBinary = (last_token == TokenType.R_PAREN ||
                            last_token == TokenType.IDENTIFIER ||
                            last_token == TokenType.PULSE_LIT ||
                            last_token == TokenType.SPARK_LIT ||
                            last_token == TokenType.STREAM_LIT);
        switch (c) {
             case '+':
                if(nextChar == '+')
                    return new Token(TokenType.UNARY_OP, "++", line, column);
                else if(nextChar == '=')
                    return new Token(TokenType.ASSIGN_OP, "+=", line, column);
                else{
                    if(isBinary)
                        return new Token(TokenType.ADD_OP, "+", line, column);
                    else
                        return new Token(TokenType.UNARY_OP, "+", line, column);
                }
            case '-':
                if(nextChar == '-')
                    return new Token(TokenType.UNARY_OP, "--", line, column);
                else if(nextChar == '=')
                    return new Token(TokenType.ASSIGN_OP, "-=", line, column);
                else{
                    if(isBinary)
                        return new Token(TokenType.ADD_OP, "-", line, column);
                    else
                        return new Token(TokenType.UNARY_OP, "-", line, column);
                }
            case '*':
                if(nextChar == '*')
                    return new Token(TokenType.EXP_OP, "**", line, column);
                else if(nextChar == '=')
                    return new Token(TokenType.ASSIGN_OP, "*=", line, column);
                else
                    return new Token(TokenType.MUL_OP, "*", line, column);
            case '/':
                if(nextChar == '=')
                    return new Token(TokenType.ASSIGN_OP, "/=", line, column);
                else
                    return new Token(TokenType.MUL_OP, "/", line, column);
            case '%':
                if(nextChar == '=')
                    return new Token(TokenType.ASSIGN_OP, "%=", line, column);
                else
                    return new Token(TokenType.MUL_OP, "%", line, column);
            case '=':
                if(nextChar == '=')
                    return new Token(TokenType.EQUALITY_OP, "==", line, column);
                else
                    return new Token(TokenType.ASSIGN_OP, "=", line, column);
            case '!':
                if(nextChar == '=')
                    return new Token(TokenType.EQUALITY_OP, "!=", line, column);
                else
                    return new Token(TokenType.UNARY_OP, "!", line, column);
            case '>':
                if(nextChar == '=')
                    return new Token(TokenType.REL_OP, ">=", line, column);
                else
                    return new Token(TokenType.REL_OP, ">", line, column);
            case '<':
                if(nextChar == '=') 
                    return new Token(TokenType.REL_OP, "<=", line, column);
                else
                    return new Token(TokenType.REL_OP, "<", line, column);
            case '&':
                if(nextChar == '&') 
                    return new Token(TokenType.AND_OP, "&&", line, column);
            case '|':
                if(nextChar == '|') 
                    return new Token(TokenType.OR_OP, "||", line, column);
            case '^':
                if(nextChar == '^')
                    return new Token(TokenType.XOR_OP, "^^", line, column);
            case '(':
                return new Token(TokenType.L_PAREN, "(", line, column);
            case ')':
                return new Token(TokenType.R_PAREN, ")", line, column);
            case '{':
                return new Token(TokenType.L_BRACE, "{", line, column);
            case '}':
                return new Token(TokenType.R_BRACE, "}", line, column);
            case '[':
                return new Token(TokenType.L_BRACKET, "[", line, column);       
            case ']':
                return new Token(TokenType.R_BRACKET, "]", line, column);                       
            case ',':
                return new Token(TokenType.COMMA, ",", line, column);
            case ';':
                return new Token(TokenType.SEMICOLON, ";", line, column);   
            case ':':
                return new Token(TokenType.COLON, ":", line, column);
            default:
                return new Token(TokenType.ILLEGAL, Character.toString(c), line, column);
        }
    }

}