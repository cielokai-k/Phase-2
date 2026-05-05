
import grtree.Tree;
import grtree.TreeScrollFrame;
import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Tester {

    public static void main(String[] args) {
        // Define paths
        String logPath = "Misc/parser_log.txt";
        String astPath = "Misc/ast_tree.txt";
        String csvPath = "Misc/LR1_Parsing_Table.csv";
        String inputPath = "Sample Programs/small_parse.txt";

        try (PrintWriter logWriter = new PrintWriter(new File(logPath))) {
            ParseTable table = TableLoader.load(csvPath);
            SymTable symTable = new SymTable();

            logWriter.println("=== SCANNER TOKEN STREAM ===");
            Scanner scannerForTokens = new Scanner(new File(inputPath), symTable);
            Token t;
            while ((t = scannerForTokens.getNextToken()).type != TokenType.EOF) {
                logWriter.println(t.displayToken());
            }
            logWriter.println("============================\n");

            logWriter.println("=== PARSER ACTION LOG ===");
            Scanner scannerForParsing = new Scanner(new File(inputPath), symTable);

            Parser parser = new Parser(scannerForParsing, table, logWriter);
            ASTNode root = parser.parse();

            if (root != null) {
                // UI Window
                new TreeScrollFrame(convertToGrtree(root));

                // Export final AST Structure
                String treeContent = exportToGtreeFormat(root);
                Files.write(Paths.get(astPath), treeContent.getBytes());

                System.out.println("[Success] Parsing complete.");
                System.out.println("[Success] Action Log: " + logPath);
                System.out.println("[Success] AST Tree: " + astPath);
            }

            logWriter.flush(); // Ensure everything is written
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
