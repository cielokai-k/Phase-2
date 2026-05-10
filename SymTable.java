
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

public class SymTable {

    public Map<String, IdDetails> table;

    // Constructor
    public SymTable() {
        table = new HashMap<>();
        scopeStack.push(new HashSet<>()); // Initialize global scope
    }

    private Map<String, Boolean> initialized = new HashMap<>();

    private Stack<Set<String>> scopeStack = new Stack<>();

    // Method to track initialized/default variables
    public void markAsInitialized(String lexeme) {
        initialized.put(lexeme, true);
    }

    public boolean isInitialized(String lexeme) {
        return initialized.getOrDefault(lexeme, false);
    }

    // Scope management methods
    public void pushScope() {
        scopeStack.push(new HashSet<>());
    }

    public void popScope() {
        if (scopeStack.size() > 1) { // Keep at least global scope
            Set<String> localVars = scopeStack.pop();
            // Remove all variables declared in this scope
            for (String varName : localVars) {
                removeLexeme(varName);
            }
        }
    }

    // Method that will add identifier if missing
    public void addLexeme(String lexeme) {
        if (!table.containsKey(lexeme)) {
            table.put(lexeme, new IdDetails(lexeme, TokenType.IDENTIFIER));
            if (!scopeStack.isEmpty()) {
                scopeStack.peek().add(lexeme); // Track in current scope
            }
        }
    }

    // Helper method to remove a lexeme from all tracking structures
    public void removeLexeme(String lexeme) {
        table.remove(lexeme);
        initialized.remove(lexeme);
    }

    // Locks the variable so it cannot be changed
    public void markAsInstinct(String key) {
        if (table.containsKey(key)) {
            table.get(key).isInstinct = true;
        }
    }

    // Checks if the variable is locked
    public boolean isInstinct(String key) {
        if (table.containsKey(key)) {
            return table.get(key).isInstinct;
        }
        return false;
    }

    // Updates the data type of an existing identifier
    public void setDataType(String lexeme, String dataType) {
        if (table.containsKey(lexeme)) {
            table.get(lexeme).dataType = dataType;
        } else {
            System.err.println("Semantic Error: Variable '" + lexeme + "' not declared.");
        }
    }

    // Updates the value of an existing identifier
    public void setValue(String lexeme, Object value) {
        if (table.containsKey(lexeme)) {
            table.get(lexeme).value = value;
        } else {
            System.err.println("Runtime Error: Variable '" + lexeme + "' not declared.");
        }
    }

    // Retrieves the data type (Returns null if not found or not set)
    public String getDataType(String lexeme) {
        if (table.containsKey(lexeme)) {
            return table.get(lexeme).dataType;
        }
        return null;
    }

    // Retrieves the current value (Returns null if not found or not set)
    public Object getValue(String lexeme) {
        if (table.containsKey(lexeme)) {
            return table.get(lexeme).value;
        }
        return null;
    }

    // Checks if a variable actually exists in the table
    public boolean contains(String lexeme) {
        return table.containsKey(lexeme);
    }

    // Display the whole Symbol table for tracking
    public void displayTable() {
        System.out.println("--- SYMBOL TABLE ---");
        for (String key : table.keySet()) {
            IdDetails details = table.get(key);
            System.out.println("ID: " + key
                    + " | Type: " + details.dataType
                    + " | Value: " + details.value);
        }
        System.out.println("--------------------");
    }
}

// IdDetails as composition
class IdDetails {

    public String lexeme;
    public TokenType type;
    public String dataType = null;
    public Object value = null;
    public boolean isInstinct = false; // Tracks if it is a constant

    // Constructor
    public IdDetails(String lexeme, TokenType type) {
        this.lexeme = lexeme;
        this.type = type;
    }
}
