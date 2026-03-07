public enum TokenType {
    // Datatypes
    THOUGHT, NEURON, SYNAPSE, PULSE, SPARK, STREAM, CLUSTER, VOID, INSTINCT,

    // Control Flow
    STIMULATE, INHIBIT, EVALUATE, PATH, BASE, CYCLE, REACT, ECHO, DORMANT, FLOW, RECALL,

    // Program Structure
    ACTIVATE, ACTION,

    // Input and Output
    SENSE, EXPRESS,

    // String operations
    TRANSCRIBE, LENGTH,

    // OPERATORS - following the DFA
    // Arithmetic
    PLUS, MINUS, STAR, SLASH, MOD, EXPONENT,
    
    // Unary & Increment/Decrement
    UNARY_OP, INCREMENT, DECREMENT, NOT,
    
    // Assignment & Compound Assignment
    ASSIGN, PLUS_ASSIGN, MINUS_ASSIGN, MUL_ASSIGN, DIV_ASSIGN, MOD_ASSIGN,
    
    // Relational
    EQUAL_TO, NOT_EQUAL, GREATER, GREATER_EQ, LESS, LESS_EQ,
    
    // Logical / Bitwise
    AND, OR, XOR,

    // Delimiters
    L_PAREN, R_PAREN, L_BRACE, R_BRACE, L_BRACKET, R_BRACKET, COMMA, SEMICOLON, COLON,

    // BOOLEANS
    TRUE, FALSE,

    // User-defined tokens and Literals
    IDENTIFIER, PULSE_LIT, SPARK_LIT, STREAM_LIT, NEURON_LIT, THOUGHT_LIT,

    // SPECIAL TOKENS
    EOF,
    ILLEGAL
}