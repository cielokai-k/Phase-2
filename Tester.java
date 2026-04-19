
import grtree.Tree;
import grtree.TreeScrollFrame;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Tester {

    public static void main(String[] args) {
        try {
            String csvPath = "Misc/LR1_Parsing_Table.csv";
            ParseTable table = TableLoader.load(csvPath);

            String filePath = "Sample Programs/small_parse.txt";
            SymTable symTable = new SymTable();
            Scanner scanner = new Scanner(new File(filePath), symTable);

            Parser parser = new Parser(scanner, table);
            ASTNode root = parser.parse();

            if (root != null) {
                // COMMENTED OUT: This line was responsible for the console print
                // root.display(""); 

                // Convert to gtree format and display interactive window
                Tree grtree = convertToGrtree(root);
                new TreeScrollFrame(grtree);

                // Export AST to file
                String treeContent = exportToGtreeFormat(root);
                Files.write(Paths.get("Misc/ast_tree.txt"), treeContent.getBytes());

                // Keep these for status updates
                System.out.println("\n[Success] Tree exported to Misc/ast_tree.txt");
                System.out.println("[Success] Interactive tree displayed in window.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Tree convertToGrtree(ASTNode node) {
        if (node instanceof NonTerminalNode) {
            NonTerminalNode ntNode = (NonTerminalNode) node;
            Tree tree = new Tree("[" + ntNode.name + "]");
            for (ASTNode child : ntNode.children) {
                tree.addChild(convertToGrtree(child));
            }
            return tree;
        } else if (node instanceof TerminalNode) {
            TerminalNode tNode = (TerminalNode) node;
            return new Tree("Terminal: " + tNode.token.displayToken());
        }
        return new Tree("NULL");
    }

    private static String exportToGtreeFormat(ASTNode node) {
        StringBuilder sb = new StringBuilder();
        exportNode(node, "", true, sb);
        return sb.toString();
    }

    private static void exportNode(ASTNode node, String prefix, boolean isLast, StringBuilder sb) {
        if (node instanceof NonTerminalNode) {
            NonTerminalNode ntNode = (NonTerminalNode) node;
            sb.append(prefix);
            sb.append(isLast ? "└── " : "├── ");
            sb.append("[").append(ntNode.name).append("]").append("\n");

            for (int i = 0; i < ntNode.children.size(); i++) {
                boolean isLastChild = (i == ntNode.children.size() - 1);
                String newPrefix = prefix + (isLast ? "    " : "│   ");
                exportNode(ntNode.children.get(i), newPrefix, isLastChild, sb);
            }
        } else if (node instanceof TerminalNode) {
            TerminalNode tNode = (TerminalNode) node;
            sb.append(prefix);
            sb.append(isLast ? "└── " : "├── ");
            sb.append("Terminal: ").append(tNode.token.displayToken()).append("\n");
        }
    }
}
