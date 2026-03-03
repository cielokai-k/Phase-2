class Literal extends Token {
    public Object literalValue;

    // Constructor
    public Literal(TokenType type, String lexeme, Object literalValue, int line) {
        super(type, lexeme, line);
        this.literalValue = literalValue;
    }

    // Specify the Literal (refer to the DFA)
    @Override
    public String displayToken() {
        if (type == TokenType.PULSE_LIT) {
            return "[pulse_lit: " + literalValue + "]";
        } else if (type == TokenType.SPARK_LIT) {
            return "[spark_lit= " + literalValue + "]";
        } else if (type == TokenType.STREAM_LIT) {
            return "[stream_lit = " + literalValue + "]";
        } else if (type == TokenType.THOUGHT_LIT) {
            return "[thought_lit: " + literalValue + "]";
        } else if (type == TokenType.NEURON_LIT) {
            return "[neuron_lit: " + literalValue + "]";
        }
        
        return "[" + type.name() + ": " + literalValue + "]"; 
    }
}