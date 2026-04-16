import java.io.File;

/* 1. Put your source file in the "Parser Programs" folder.
   2. Edit filePath variable below to run your desired source file.
*/
 
public class ParserTester {

    public static void main(String[] args) {

        // TODO: Change to desired file
        String filePath = "Parser Programs" + File.separator + "test_parser.txt";

        System.out.println("         CEREBRA PARSER TESTER");
        System.out.println("------------------------------------------");
        System.out.println("File: " + filePath);

        SymTable symTable = new SymTable();
        File inputFile   = new File(filePath);

        if (!inputFile.exists()) {
            System.err.println("File not found: " + inputFile.getAbsolutePath());
            return;
        }

        Scanner scanner = new Scanner(inputFile, symTable);
        Parser  parser  = new Parser(scanner, symTable);
        parser.parse();
    }
}