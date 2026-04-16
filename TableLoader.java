
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TableLoader {

    private static final Set<String> TERMINAL_NAMES = new HashSet<>();

    static {
        for (TokenType t : TokenType.values()) {
            TERMINAL_NAMES.add(t.name());
        }
        TERMINAL_NAMES.add("$");
    }

    public static ParseTable load(String csvPath) throws Exception {
        ParseTable table = new ParseTable();
        BufferedReader br = new BufferedReader(new FileReader(csvPath));

        String headerLine = br.readLine();
        if (headerLine == null) {
            return table;
        }
        String[] headers = headerLine.split(",", -1);

        boolean[] isTerminal = new boolean[headers.length];
        for (int i = 1; i < headers.length; i++) {
            String col = headers[i].trim();
            isTerminal[i] = TERMINAL_NAMES.contains(col);
        }

        String line;
        while ((line = br.readLine()) != null) {
            String[] cells = line.split(",", -1);
            if (cells.length == 0 || cells[0].trim().isEmpty()) {
                continue;
            }

            // Handle "S346" or "346"
            int state = Integer.parseInt(cells[0].trim().replaceAll("[^0-9]", ""));

            Map<TokenType, Action> actionMap = new HashMap<>();
            Map<String, Integer> gotoMap = new HashMap<>();

            for (int i = 1; i < cells.length && i < headers.length; i++) {
                String val = cells[i].trim();
                if (val.isEmpty() || val.equalsIgnoreCase("NaN")) {
                    continue;
                }

                String colName = headers[i].trim();

                if (isTerminal[i]) {
                    TokenType type = mapToTokenType(colName);
                    if (type == null) {
                        continue;
                    }

                    if (val.equalsIgnoreCase("ACCEPT")) {
                        actionMap.put(type, new Action(Action.ActionType.ACCEPT, 0));
                    } else if (val.toUpperCase().startsWith("S")) {
                        actionMap.put(type, new Action(Action.ActionType.SHIFT, Integer.parseInt(val.substring(1))));
                    } else if (val.toUpperCase().startsWith("R")) {
                        actionMap.put(type, new Action(Action.ActionType.REDUCE, Integer.parseInt(val.substring(1))));
                    }
                } else {
                    // Normalize: Remove < > and force Uppercase (e.g., <STATEMENT_LIST> -> STATEMENT_LIST)
                    String cleanNonTerminal = colName.replace("<", "").replace(">", "").replace("'", "").toUpperCase().trim();
                    try {
                        int nextState = Integer.parseInt(val.replaceAll("[^0-9]", ""));
                        gotoMap.put(cleanNonTerminal, nextState);
                    } catch (Exception e) {
                    }
                }
            }
            table.actionTable.put(state, actionMap);
            table.gotoTable.put(state, gotoMap);
        }
        br.close();
        return table;
    }

    private static TokenType mapToTokenType(String name) {
        if (name.equals("$")) {
            return TokenType.EOF;
        }
        try {
            return TokenType.valueOf(name);
        } catch (Exception e) {
            return null;
        }
    }
}
