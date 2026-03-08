
import java.util.HashMap;
import java.util.Map;

public class SymTable {

    public Map<String, IdDetails> table;

    // Constructor
    public SymTable() {
        table = new HashMap<>();
    }

    // Method that will add identifier if missing
    public void addLexeme(String lexeme) {
        if (!table.containsKey(lexeme)) {
            table.put(lexeme, new IdDetails(lexeme, TokenType.IDENTIFIER));
        }
    }

    // Display the whole Symbol table for tracking
    public void displayTable() {
        for (String key : table.keySet()) {
            System.out.println("ID: " + key);
        }
    }
}

// IdDetails as composition
class IdDetails {

    public String lexeme;
    public TokenType type;
    public String dataType = null;
    public Object value = null;

    // Constructor
    public IdDetails(String lexeme, TokenType type) {
        this.lexeme = lexeme;
        this.type = type;
    }
}
