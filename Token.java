
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
        } else if (type == TokenType.ILLEGAL) {
            return "[ERROR] " + lexeme;
        } else if (type == TokenType.PULSE_LIT || type == TokenType.STREAM_LIT || type == TokenType.SPARK_LIT
                || type == TokenType.THOUGHT_LIT || type == TokenType.NEURON_LIT) {
            return "[" + type.name() + ": " + lexeme + "]";
        }

        return "[" + type.name() + "]";
    }

    // Getter method
    public TokenType getType() {
        return this.type;
    }
}
