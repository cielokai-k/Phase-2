
import java.io.File;

public class Tester {

    public static void main(String[] args) {
        try {
            String csvPath = "Misc/Cerebra_Parsing_Table.csv";
            ParseTable table = TableLoader.load(csvPath);

            String filePath = "Sample Programs/small_parse.txt";
            SymTable symTable = new SymTable();
            Scanner scanner = new Scanner(new File(filePath), symTable);

            Parser parser = new Parser(scanner, table);
            ASTNode root = parser.parse();

            if (root != null) {
                System.out.println("\n--- Abstract Syntax Tree ---");
                root.display("");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
