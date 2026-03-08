
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class Scanner {

    public File sourceFile;
    public SymTable symTable;
    public Map<String, TokenType> keywords;
    public int currentPos;
    public int line;

    private String sourceCode;

    public Scanner(File sourceFile, SymTable symTable) {
        this.sourceFile = sourceFile;
        this.symTable = symTable;
        this.currentPos = 0;
        this.line = 1;
        this.keywords = new HashMap<>();

        try {
            this.sourceCode = new String(Files.readAllBytes(sourceFile.toPath()));
        } catch (IOException e) {
            this.sourceCode = "";
            System.err.println("Error reading source file");
        }

        // Initialize keywords to the Symbol Table
        keywords.put("thought", TokenType.THOUGHT);
        keywords.put("neuron", TokenType.NEURON);
        keywords.put("synapse", TokenType.SYNAPSE);
        keywords.put("pulse", TokenType.PULSE);
        keywords.put("spark", TokenType.SPARK);
        keywords.put("stream", TokenType.STREAM);
        keywords.put("cluster", TokenType.CLUSTER);
        keywords.put("void", TokenType.VOID);
        keywords.put("instinct", TokenType.INSTINCT);
        keywords.put("stimulate", TokenType.STIMULATE);
        keywords.put("inhibit", TokenType.INHIBIT);
        keywords.put("evaluate", TokenType.EVALUATE);
        keywords.put("path", TokenType.PATH);
        keywords.put("base", TokenType.BASE);
        keywords.put("cycle", TokenType.CYCLE);
        keywords.put("react", TokenType.REACT);
        keywords.put("echo", TokenType.ECHO);
        keywords.put("dormant", TokenType.DORMANT);
        keywords.put("flow", TokenType.FLOW);
        keywords.put("recall", TokenType.RECALL);
        keywords.put("activate", TokenType.ACTIVATE);
        keywords.put("action", TokenType.ACTION);
        keywords.put("sense", TokenType.SENSE);
        keywords.put("express", TokenType.EXPRESS);
        keywords.put("transcribe", TokenType.TRANSCRIBE);
        keywords.put("length", TokenType.LENGTH);

        // Boolean literals - considered as keywords
        keywords.put("true", TokenType.TRUE);
        keywords.put("false", TokenType.FALSE);
    }

    public Token getNextToken() {
        skipWhitespace();
        if (isAtEnd()) {
            return new Token(TokenType.EOF, "", line);
        }

        int startPos = currentPos;
        char ch = readNextChar();
        Token result = null; // We use this variable to avoid 'unreachable' errors

        switch (ch) {
            // Brackets, Braces, Parens and Punctuations
            case '(':
                result = new Token(TokenType.L_PAREN, "(", line);
                break;
            case ')':
                result = new Token(TokenType.R_PAREN, ")", line);
                break;
            case '{':
                result = new Token(TokenType.L_BRACE, "{", line);
                break;
            case '}':
                result = new Token(TokenType.R_BRACE, "}", line);
                break;
            case '[':
                result = new Token(TokenType.L_BRACKET, "[", line);
                break;
            case ']':
                result = new Token(TokenType.R_BRACKET, "]", line);
                break;
            case ',':
                result = new Token(TokenType.COMMA, ",", line);
                break;
            case ';':
                result = new Token(TokenType.SEMICOLON, ";", line);
                break;
            case ':':
                result = new Token(TokenType.COLON, ":", line);
                break;

            // Arithmetic Operators
            case '+':
                if (isMatch('+')) {
                    result = new Token(TokenType.INCREMENT, "++", line);
                } else if (isMatch('=')) {
                    result = new Token(TokenType.PLUS_ASSIGN, "+=", line);
                } else {
                    result = new Token(TokenType.PLUS, "+", line);
                }
                break;

            case '-':
                if (isMatch('-')) {
                    result = new Token(TokenType.DECREMENT, "--", line);
                } else if (isMatch('=')) {
                    result = new Token(TokenType.MINUS_ASSIGN, "-=", line);
                } else {
                    result = new Token(TokenType.MINUS, "-", line);
                }
                break;

            case '*':
                if (isMatch('*')) {
                    result = new Token(TokenType.EXPONENT, "**", line);
                } else if (isMatch('=')) {
                    result = new Token(TokenType.MUL_ASSIGN, "*=", line);
                } else {
                    result = new Token(TokenType.STAR, "*", line);
                }
                break;

            case '/':
                if (isMatch('=')) {
                    result = new Token(TokenType.DIV_ASSIGN, "/=", line);
                } else {
                    result = new Token(TokenType.SLASH, "/", line);
                }
                break;

            case '%':
                if (isMatch('=')) {
                    result = new Token(TokenType.MOD_ASSIGN, "%=", line);
                } else {
                    result = new Token(TokenType.MOD, "%", line); // Fixed from ASSIGN in the diagram typo

                }
                break;

            // Relational and Logical Operators
            case '=':
                if (isMatch('=')) {
                    result = new Token(TokenType.EQUAL_TO, "==", line);
                } else {
                    result = new Token(TokenType.ASSIGN, "=", line);
                }
                break;

            case '!':
                if (isMatch('=')) {
                    result = new Token(TokenType.NOT_EQUAL, "!=", line);
                } else {
                    result = new Token(TokenType.NOT, "!", line);
                }
                break;

            case '>':
                if (isMatch('=')) {
                    result = new Token(TokenType.GREATER_EQ, ">=", line);
                } else {
                    result = new Token(TokenType.GREATER, ">", line);
                }
                break;

            case '<':
                if (isMatch('=')) {
                    result = new Token(TokenType.LESS_EQ, "<=", line);
                } else {
                    result = new Token(TokenType.LESS, "<", line);
                }
                break;

            case '&':
                if (isMatch('&')) {
                    result = new Token(TokenType.AND, "&&", line);
                } else {
                    result = new Token(TokenType.ILLEGAL, "Lexical Error: Expected '&&' but found '&'", line);
                }
                break;

            case '|':
                if (isMatch('|')) {
                    result = new Token(TokenType.OR, "||", line);
                } else {
                    result = new Token(TokenType.ILLEGAL, "Lexical Error: Expected '||' but found '|'", line);
                }
                break;

            case '^':
                result = new Token(TokenType.XOR, "^", line);
                break;

            // Literals and Identifiers
            case '"':
                result = scanThought(startPos);
                break;

            case '\'':
                result = scanNeuron(startPos);
                break;

            default:
                pushbackChar(); // Push back the char so the specific scan methods can read it from the start

                if (isDigit(ch)) {
                    result = scanPulseOrSparkOrStream(startPos);
                } else if (isLetter(ch)) {
                    result = scanId(startPos);
                } else {
                    // Force the scanner to consume the bad character
                    readNextChar();

                    // Loop to group any consecutive bad characters together
                    while (!isAtEnd() && !isWhitespace(lookahead()) && !isAlphaNumeric(lookahead())) {
                        readNextChar();
                    }

                    String illegalStr = sourceCode.substring(startPos, currentPos);
                    result = new Token(TokenType.ILLEGAL, "Invalid Character '" + illegalStr + "'", line);
                }
                break;
        }

        if (result != null) return result;

        return new Token(TokenType.ILLEGAL, "Unknown Error", line);
    }

    public void skipWhitespace() {
        while (true) {
            int startPos = currentPos;
            int startLine = line;

            if (isAtEnd()) {
                break; // EOF
            }

            char ch = readNextChar();

            // Whitespace branch
            if (ch == ' ' || ch == '\r') {
                // q9 loop
                while (!isAtEnd() && (lookahead() == ' ' || lookahead() == '\r')) {
                    readNextChar();
                }
                continue; // q9 - accept (loop to accept more if ever)
            } else if (ch == '\t') {
                // q11 loop
                while (!isAtEnd() && lookahead() == '\t') {
                    readNextChar();
                }
                continue; // q9 - accept
            } else if (ch == '\n') {
                // q13 loop
                line++;
                while (!isAtEnd() && lookahead() == '\n') {
                    line++;
                    readNextChar();
                }
                continue; // q9 - accept state
            } // "dream" comment branch
            else if (ch == 'd') {
                // q1 -> q2 -> q3 -> q4 -> q5
                if (isMatch('r') && isMatch('e') && isMatch('a') && isMatch('m')) {

                    if (isAtEnd()) {
                        currentPos = startPos;
                        break;
                    }

                    char next = readNextChar();

                    if (next == ':') {
                        // q5 to q6
                        while (!isAtEnd()) {
                            if (lookahead() == '\n') {
                                readNextChar(); // q6 to -> q9 - accept
                                line++;
                                break;
                            }
                            readNextChar(); // Loop on q6 ~(\n)
                        }
                        continue; // Comment successfully consumed, loop again
                    } else if (next == ' ') {
                        // Transition q5 -> space -> q7
                        if (isMatch('{')) {
                            // Transition q7 -> { -> q8
                            boolean closed = false;
                            while (!isAtEnd()) {
                                char c = readNextChar();
                                if (c == '\n') {
                                    line++;
                                } else if (c == '}') {
                                    closed = true;
                                    break; // q8 -> } -> q9 (Accept)
                                }
                            }
                            if (!closed) {
                                System.err.println("[Warning: Unterminated multi-line dream comment starting at line "
                                        + startLine + "]");
                            }
                            continue; // Comment successfully consumed, loop again
                        }
                    }
                }

                currentPos = startPos;
                line = startLine;
                break;
            } else {
                // Reached a non-whitespace, non-comment character
                currentPos = startPos; // Push back the character
                break; // Exit the loop
            }
        }
    }

    public Token scanId(int startPos) {
        while (isAlphaNumeric(lookahead())) {
            readNextChar();
        }

        String text = sourceCode.substring(startPos, currentPos);

        // Lexical Error for exceeding maximum length (256 char)
        if (text.length() > 256) {
            return new Token(TokenType.ILLEGAL, "Identifier exceeds maximum length of 256 characters", line);
        }

        TokenType type = keywords.getOrDefault(text, TokenType.IDENTIFIER);

        if (type == TokenType.IDENTIFIER) {
            symTable.addLexeme(text);
        } else if (type == TokenType.TRUE || type == TokenType.FALSE) {
            // Boolean literals
            return new Literal(type, text, Boolean.parseBoolean(text), line);
        }

        return new Token(type, text, line);
    }

    // Combines logic for scanPulse, scanSpark, and scanStream - refer to DFA
    public Token scanPulseOrSparkOrStream(int startPos) {
        // q0 to q1 - consume initial digits
        while (isDigit(lookahead())) {
            readNextChar();
        }

        // In q1 - check if stream/spark if it has "." or pulse
        if (lookahead() == '.') {
            readNextChar(); // q1 to q3 - "." is seen by the lookahead

            // In q3 - need to have at least 1 digit after "." to transition to q4
            if (!isDigit(lookahead())) {
                // Throw error if no digit after decimal
                String badText = sourceCode.substring(startPos, currentPos);
                return new Token(TokenType.ILLEGAL, "Invalid Float Literal (missing trailing digits) '" + badText + "'",
                        line);
            }

            // q3 to q4 - consume decimal digits
            while (isDigit(lookahead())) {
                readNextChar();
            }

            // Catch multiple decimals (multiple "." are present)
            if (lookahead() == '.') {
                // Consume the rest of the broken number
                while (isDigit(lookahead()) || lookahead() == '.') {
                    readNextChar();
                }
                String badText = sourceCode.substring(startPos, currentPos);
                return new Token(TokenType.ILLEGAL, "Invalid character '.', multiple '.' present in '" + badText + "'",
                        line);
            }

            // In q4 - check if spark (if it has "f" or "F") or scanning stops and return as
            // stream
            if (lookahead() == 'f' || lookahead() == 'F') {
                readNextChar(); // Transition q4 -> q6 (Consume 'f'/'F')

                // Catch invalid literals like "123.4f_bad" or "123.4fX"
                if (isLetter(lookahead()) || lookahead() == '_') {
                    return consumeAndReturnInvalidNumeric(startPos);
                }

                // q6 to q7 - return SPARK_LIT
                String text = sourceCode.substring(startPos, currentPos);
                return new Token(TokenType.SPARK_LIT, text, line);
            }

            if (isLetter(lookahead()) || lookahead() == '_') {
                return consumeAndReturnInvalidNumeric(startPos);
            }

            // q4 to q5 - no "f" or "F" then pushback other character and STREAM_LIT is read
            String text = sourceCode.substring(startPos, currentPos);
            return new Token(TokenType.STREAM_LIT, text, line);
        }

        if (isLetter(lookahead()) || lookahead() == '_') {
            return consumeAndReturnInvalidNumeric(startPos);
        }

        // q1 to q2 - no "." then pushback other character and PULSE_LIT is read
        String text = sourceCode.substring(startPos, currentPos);
        return new Token(TokenType.PULSE_LIT, text, line);
    }

    public Token scanThought(int startPos) {
        int startLine = line;

        while (!isAtEnd()) {
            char ch = lookahead();

            if (ch == '"') {
                // q1 to q3 - accept
                readNextChar(); // Consume closing """
                String rawVal = sourceCode.substring(startPos + 1, currentPos - 1);
                String evaluatedVal = unescape(rawVal);

                return new Literal(TokenType.THOUGHT_LIT, rawVal, evaluatedVal, startLine);

            } else if (ch == '\\') {
                // q1 to q2
                readNextChar(); // Consume '\'
                char esc = lookahead();

                // q2 to q1 - only allow valid escape sequences
                if (esc == 'n' || esc == 't' || esc == '\\' || esc == '"' || esc == '\'') {
                    readNextChar(); // Consume the escaped character
                } else {
                    // Invalid escape sequence found
                    while (!isAtEnd() && lookahead() != '"' && lookahead() != '\n') {
                        readNextChar();
                    }

                    if (lookahead() == '"') {
                        readNextChar();
                    }

                    String badText = sourceCode.substring(startPos, currentPos).replaceAll("[\\r\\n]", "");
                    return new Token(TokenType.ILLEGAL, "Invalid escape sequence in thought literal '" + badText + "'",
                            startLine);
                }

            } else if (ch == '\n') {
                // String is unterminated
                break;

            } else {
                // q1 to q1
                readNextChar();
            }
        }
        String badText = sourceCode.substring(startPos, currentPos).replaceAll("[\\r\\n]", "");
        return new Token(TokenType.ILLEGAL, "Unterminated thought (string) literal '" + badText + "'", startLine);
    }

    public Token scanNeuron(int startPos) {
        char ch = lookahead();

        if (ch == '\n' || ch == '\r' || isAtEnd()) {
            String badText = sourceCode.substring(startPos, currentPos);
            return new Token(TokenType.ILLEGAL, "Unterminated neuron (character) literal '" + badText + "'", line);
        }


        if (ch == '\\') {
            // q1 to q3
            readNextChar(); // Consume '\'
            char esc = lookahead();

            // q3 to q2
            if (esc == 'n' || esc == 't' || esc == '\\' || esc == '"' || esc == '\'') {
                readNextChar(); // Consume valid escape character
            } else {
                return consumeAndReturnInvalidNeuron(startPos);
            }
        } else if (ch != '\'') {
            // q1 to q2
            readNextChar(); // Consume normal character
        } else {
            // Empty literal ('') - fails to reach q2
            return consumeAndReturnInvalidNeuron(startPos);
        }

        if (lookahead() == '\n' || lookahead() == '\r' || isAtEnd()) {
            String badText = sourceCode.substring(startPos, currentPos);
            return new Token(TokenType.ILLEGAL, "Unterminated neuron (character) literal '" + badText + "'", line);
        }

        // In q2 - must havee a closing quote to transition to q4
        if (lookahead() == '\'') {
            readNextChar(); // q2 to q4 - accept
            String rawVal = sourceCode.substring(startPos + 1, currentPos - 1);
            String evaluatedVal = unescape(rawVal);
            return new Literal(TokenType.NEURON_LIT, rawVal, evaluatedVal, line);
        } else {
            // Too many characters or missing closing quote
            return consumeAndReturnInvalidNeuron(startPos);
        }
    }

    // Helper methods
    private Token consumeAndReturnInvalidNumeric(int startPos) {
        // Consume the rest of the attached letters/underscores
        while (isAlphaNumeric(lookahead()) || lookahead() == '_') {
            readNextChar();
        }
        String badText = sourceCode.substring(startPos, currentPos);
        return new Token(TokenType.ILLEGAL, "Invalid value as numerical '" + badText + "'", line);
    }

    private Token consumeAndReturnInvalidNeuron(int startPos) {
        // Consume until there is space, newline, or a closing quote to isolate the bad
        // token
        while (!isAtEnd() && lookahead() != '\'' && lookahead() != '\n' && lookahead() != '\r') {
            readNextChar();
        }
        if (lookahead() == '\'') {
            readNextChar(); // Consume the closing quote if it exists
        }
        String badText = sourceCode.substring(startPos, currentPos).replaceAll("[\\r\\n]", "");
        return new Token(TokenType.ILLEGAL, "Invalid neuron (character) literal '" + badText + "'", line);
    }

    // Process the escape characters
    private String unescape(String text) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // backslash is read - check next character
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                switch (next) {
                    case 'n':
                        sb.append('\n');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '"':
                        sb.append('"');
                        break;
                    case '\'':
                        sb.append('\'');
                        break;
                    default:
                        sb.append(next); // Fallback (though our DFA prevents reaching this)
                }
                i++; // Skip the next character
            } else {
                sb.append(c); // Normal character
            }
        }
        return sb.toString();
    }

    public boolean isAtEnd() {
        return currentPos >= sourceCode.length();
    }

    public char readNextChar() {
        if (isAtEnd()) {
            return '\0';
        }
        return sourceCode.charAt(currentPos++);
    }

    public void pushbackChar() {
        if (currentPos > 0) {
            currentPos--;
        }
    }

    public void unreadSeq(int length) {
        currentPos -= length;
        if (currentPos < 0) {
            currentPos = 0;
        }
    }

    public int getCurrentPos() {
        return currentPos;
    }

    public char lookahead() {
        if (isAtEnd()) {
            return '\0';
        }
        return sourceCode.charAt(currentPos);
    }

    public boolean isMatch(char expected) {
        if (isAtEnd() || sourceCode.charAt(currentPos) != expected) {
            return false;
        }
        currentPos++;
        return true;
    }

    public boolean isLetter(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
    }

    public boolean isAlpha(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || ch == '_';
    }

    public boolean isDigit(char ch) {
        return ch >= '0' && ch <= '9';
    }

    public boolean isAlphaNumeric(char ch) {
        return isAlpha(ch) || isDigit(ch);
    }

    private boolean isWhitespace(char ch) {
        return ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n';
    }
}
