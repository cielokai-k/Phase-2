
import java.util.HashMap;
import java.util.Map;

public class ParseTable {

    // State -> TokenType -> Action
    public Map<Integer, Map<TokenType, Action>> actionTable = new HashMap<>();
    // State -> Non-Terminal Name -> Next State
    public Map<Integer, Map<String, Integer>> gotoTable = new HashMap<>();
}
