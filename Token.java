public class Token {
    public String lexeme;
    public TokenType type;
    public int line;

    // Constructor
    public Token(TokenType type, String lexeme, int line) {
        this.type = type;
        this.lexeme = lexeme;
        this.line = line;
    }

    // Display Token
    public String displayToken() {
    if (type == TokenType.IDENTIFIER) {
        return "[ID: " + lexeme + "]"; 
    } else if (type == TokenType.UNARY_OP) {
        if (lexeme.equals("-")) return "[UNARY_MINUS]";
        if (lexeme.equals("+")) return "[UNARY_PLUS]";
        return "[" + type.name() + "]";
    } else if (type == TokenType.ILLEGAL) {
        return "[ERROR] " + lexeme; 
    }
    return "[" + type.name() + "]";
}
}