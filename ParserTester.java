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

        // TODO: Change class into Parser or Parser2
        Parser2  parser  = new Parser2(scanner, symTable);
        
        AST.Program rootTree = parser.parse(); 
        
        // 2. Print the AST
        System.out.println("\n==========================================");
        System.out.println("          ABSTRACT SYNTAX TREE            ");
        System.out.println("==========================================");
        
        ASTPrinter printer = new ASTPrinter();
        printer.print(rootTree);

        /* Commented out if visualizer code is not needed. 
        // Generate the visualizer code
        System.out.println("\n================================================");
        System.out.println("GRAPHVIZ DOT EXPORT (Paste output to edotor.net)");
        System.out.println("==============================================");
        
        ASTToDot dotExporter = new ASTToDot();
        String dotCode = dotExporter.generateDot(rootTree);
        System.out.println(dotCode); */
    }
}