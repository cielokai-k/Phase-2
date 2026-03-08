
import java.io.File;

public class Tester {

    public static void main(String[] args) {
        // Put the file name here
        String filePath = "Sample Programs" + File.separator + "test_numeric_literals.txt";

        // Initialiaze a Symbol Table
        SymTable symTable = new SymTable();

        // Intialize the File to be read
        File inputFile = new File(filePath);

        if (!inputFile.exists()) {
            System.err.println("File not found: " + inputFile.getAbsolutePath());
            return;
        }

        // Initialize the Scanner
        Scanner scanner = new Scanner(inputFile, symTable);

        Token token;

        // Track the line of the last token read
        int lastPrintedLine = -1;

        do {
            token = scanner.getNextToken();
            if (token.type != TokenType.EOF) {

                // Error already includes the line number and reason - not included how to fix the error
                if (token.type == TokenType.ILLEGAL) {
                    System.err.print("[ERROR: Line " + token.line + " | Reason: " + token.lexeme + "] ");
                    continue;
                }

                // Normal Same-Line Formatting
                if (lastPrintedLine != -1 && token.line > lastPrintedLine) {
                    System.out.println(); 
                }
                
                // Print the token with a space
                System.out.print(token.displayToken() + " "); 
                
                // Update the tracker to this token's line
                lastPrintedLine = token.line;
            }
        } while (token.type != TokenType.EOF);
    }
}
