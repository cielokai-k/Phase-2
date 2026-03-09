
import java.io.File;

public class Tester {

    public static void main(String[] args) {
        // Put the file name here
        String filePath = "Sample Programs" + File.separator + "test_strings_chars.txt";

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

                if (lastPrintedLine != -1 && token.line > lastPrintedLine) {
                    System.out.println(); 
                }

                // Print the token
                if (token.type == TokenType.ILLEGAL) {
                    System.out.print("[ERROR: Line " + token.line + " | Reason: " + token.lexeme + "] ");
                } else {
                    // Print normal tokens
                    System.out.print(token.displayToken() + " ");
                }

                lastPrintedLine = token.line;
            }
        } while (token.type != TokenType.EOF);

        System.out.println("\n");
        System.out.println("Symbol Table:");
        symTable.displayTable();
    }
}
