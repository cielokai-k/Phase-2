import java.io.File;

public class Tester {
    public static void main(String[] args) {
        // Put the file name here
        String filePath = "Sample Programs/test_whitespace.txt";

        // Initialiaze a Symbol Table
        SymTable symTable = new SymTable();

        // Intialize the File to be read
        File inputFile = new File(filePath);

        // Initialize the Scanner
        Scanner scanner = new Scanner(inputFile, symTable);

        Token token;
        
       do {
            token = scanner.getNextToken();
            if (token.type != TokenType.EOF) {
                
                // Error already includes the line number and reason - not included how to fix the error
                if (token.type == TokenType.ILLEGAL) {
                    System.err.print("[ERROR: Line " + token.line + " | Reason: " + token.lexeme + "] ");
                    continue; 
                }
                
                // Normal Same-Line Formatting
                System.out.print(token.displayToken() + " ");
                
                // Format to create a new line if statement logicall ends
                if (token.type == TokenType.SEMICOLON || 
                    token.type == TokenType.L_BRACE || 
                    token.type == TokenType.R_BRACE) {
                    System.out.println();
                }
            }
        } while (token.type != TokenType.EOF);
    }
}