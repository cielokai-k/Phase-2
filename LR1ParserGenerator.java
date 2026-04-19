
import java.io.*;
import java.util.*;

public class LR1ParserGenerator {

    static class Rule {

        int id;
        String lhs;
        List<String> rhs;

        Rule(int id, String lhs, List<String> rhs) {
            this.id = id;
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        public String toString() {
            return lhs + " -> " + (rhs.isEmpty() ? "ε" : String.join(" ", rhs));
        }
    }

    static class LR1Item {

        Rule rule;
        int dot;
        String lookahead;

        LR1Item(Rule rule, int dot, String lookahead) {
            this.rule = rule;
            this.dot = dot;
            this.lookahead = lookahead;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof LR1Item)) {
                return false;
            }
            LR1Item other = (LR1Item) o;
            return dot == other.dot && rule.id == other.rule.id && lookahead.equals(other.lookahead);
        }

        @Override
        public int hashCode() {
            return Objects.hash(rule.id, dot, lookahead);
        }
    }

    static class State {

        int id;
        Set<LR1Item> items;
        Map<String, Integer> transitions = new HashMap<>();

        State(int id, Set<LR1Item> items) {
            this.id = id;
            this.items = items;
        }
    }

    private List<Rule> grammar = new ArrayList<>();
    private Set<String> terminals = new LinkedHashSet<>();
    private Set<String> nonTerminals = new LinkedHashSet<>();
    private Map<String, Set<String>> firstSets = new HashMap<>();
    private Map<String, Set<String>> followSets = new HashMap<>();
    private List<State> states = new ArrayList<>();
    private Map<Integer, Map<String, String>> actionTable = new HashMap<>();
    private Map<Integer, Map<String, Integer>> gotoTable = new HashMap<>();

    public static void main(String[] args) {
        LR1ParserGenerator gen = new LR1ParserGenerator();
        String inputFile = "grammar2.csv";

        try {
            gen.loadGrammarFromCSV(inputFile);
            gen.computeFirstFollow();
            gen.buildStates();
            gen.buildParsingTable();

            // Export First and Follow sets
            gen.printFirstFollowToCSV("First_Follow_Sets.csv");

            // States Data CSV
            gen.printStatesToCSV("States_Data.csv");

            // Parsing Table CSV
            gen.printParsingTableToCSV("LR1_Parsing_Table.csv");

            System.out.println("Generation successful! Files ready for GSheets.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadGrammarFromCSV(String fileName) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(fileName));
        String line;
        br.readLine();
        while ((line = br.readLine()) != null) {
            String[] cols = line.split(",", -1);
            if (cols.length < 4 || cols[0].trim().isEmpty()) {
                continue;
            }
            try {
                int id = (int) Double.parseDouble(cols[0].trim());
                String lhs = cols[1].trim();
                String rhsStr = cols[3].trim();
                List<String> rhs = (rhsStr.equals("ε") || rhsStr.isEmpty()) ? new ArrayList<>() : Arrays.asList(rhsStr.split("\\s+"));
                grammar.add(new Rule(id, lhs, rhs));
                nonTerminals.add(lhs);
            } catch (Exception e) {
                continue;
            }
        }
        br.close();
        for (Rule r : grammar) {
            for (String sym : r.rhs) {
                if (!nonTerminals.contains(sym) && !sym.equals("ε")) {
                    terminals.add(sym);
                }
            }
        }
        terminals.add("$");
    }

    public void computeFirstFollow() {
        // Initialize First Sets
        for (String nt : nonTerminals) {
            firstSets.put(nt, new HashSet<>());
        }
        for (String t : terminals) {
            firstSets.put(t, new HashSet<>(Collections.singleton(t)));
        }

        boolean changed = true;
        while (changed) {
            changed = false;
            for (Rule r : grammar) {
                if (firstSets.get(r.lhs).addAll(getFirstOfSequence(r.rhs))) {
                    changed = true;
                }
            }
        }

        // Initialize Follow Sets
        for (String nt : nonTerminals) {
            followSets.put(nt, new HashSet<>());
        }
        if (!grammar.isEmpty()) {
            followSets.get(grammar.get(0).lhs).add("$");
        }

        changed = true;
        while (changed) {
            changed = false;
            for (Rule r : grammar) {
                for (int i = 0; i < r.rhs.size(); i++) {
                    String sym = r.rhs.get(i);
                    if (nonTerminals.contains(sym)) {
                        List<String> trailer = r.rhs.subList(i + 1, r.rhs.size());
                        Set<String> firstOfTrailer = getFirstOfSequence(trailer);

                        Set<String> toAdd = new HashSet<>(firstOfTrailer);
                        boolean hasEpsilon = toAdd.remove("ε");

                        if (followSets.get(sym).addAll(toAdd)) {
                            changed = true;
                        }
                        if (hasEpsilon || trailer.isEmpty()) {
                            if (followSets.get(sym).addAll(followSets.get(r.lhs))) {
                                changed = true;
                            }
                        }
                    }
                }
            }
        }
    }

    private Set<String> getFirstOfSequence(List<String> seq) {
        Set<String> res = new HashSet<>();
        if (seq.isEmpty()) {
            res.add("ε");
            return res;
        }
        for (String sym : seq) {
            Set<String> firstSym = firstSets.getOrDefault(sym, new HashSet<>(Collections.singleton(sym)));
            res.addAll(firstSym);
            if (!firstSym.contains("ε")) {
                res.remove("ε");
                break;
            }
        }
        return res;
    }

    // --- New CSV Export Method for First/Follow ---
    public void printFirstFollowToCSV(String fileName) throws IOException {
        try (PrintWriter csv = new PrintWriter(new FileWriter(fileName))) {
            csv.println("NON-TERMINAL,FIRST SET,FOLLOW SET");
            for (String nt : nonTerminals) {
                String first = String.join(" ", firstSets.get(nt));
                String follow = String.join(" ", followSets.get(nt));
                csv.printf("\"%s\",\"%s\",\"%s\"\n", nt, first, follow);
            }
        }
    }

    // Rest of your existing buildStates, buildParsingTable, and print methods...
    // (Included below to ensure the file is complete and compilable)
    private Set<LR1Item> closure(Set<LR1Item> items) {
        Set<LR1Item> closureSet = new LinkedHashSet<>(items);
        boolean changed = true;
        while (changed) {
            int prevSize = closureSet.size();
            Set<LR1Item> newItems = new HashSet<>();
            for (LR1Item item : closureSet) {
                if (item.dot < item.rule.rhs.size()) {
                    String nextSym = item.rule.rhs.get(item.dot);
                    if (nonTerminals.contains(nextSym)) {
                        List<String> beta = new ArrayList<>(item.rule.rhs.subList(item.dot + 1, item.rule.rhs.size()));
                        Set<String> lookaheads = getFirstOfSequence(beta);
                        if (lookaheads.remove("ε") || beta.isEmpty()) {
                            lookaheads.add(item.lookahead);
                        }
                        for (Rule r : grammar) {
                            if (r.lhs.equals(nextSym)) {
                                for (String la : lookaheads) {
                                    newItems.add(new LR1Item(r, 0, la));
                                }
                            }
                        }
                    }
                }
            }
            changed = closureSet.addAll(newItems);
        }
        return closureSet;
    }

    public void buildStates() {
        Set<LR1Item> start = new HashSet<>();
        if (grammar.isEmpty()) {
            return;
        }
        start.add(new LR1Item(grammar.get(0), 0, "$"));
        states.add(new State(0, closure(start)));
        for (int i = 0; i < states.size(); i++) {
            State curr = states.get(i);
            Set<String> symbols = new HashSet<>();
            for (LR1Item itm : curr.items) {
                if (itm.dot < itm.rule.rhs.size()) {
                    symbols.add(itm.rule.rhs.get(itm.dot));
                }
            }
            for (String sym : symbols) {
                Set<LR1Item> next = new HashSet<>();
                for (LR1Item itm : curr.items) {
                    if (itm.dot < itm.rule.rhs.size() && itm.rule.rhs.get(itm.dot).equals(sym)) {
                        next.add(new LR1Item(itm.rule, itm.dot + 1, itm.lookahead));
                    }
                }
                Set<LR1Item> closed = closure(next);
                int sid = -1;
                for (State s : states) {
                    if (s.items.equals(closed)) {
                        sid = s.id;
                        break;
                    }
                }
                if (sid == -1) {
                    sid = states.size();
                    states.add(new State(sid, closed));
                }
                curr.transitions.put(sym, sid);
            }
        }
    }

    public void buildParsingTable() {
        for (State s : states) {
            actionTable.put(s.id, new HashMap<>());
            gotoTable.put(s.id, new HashMap<>());
            for (LR1Item item : s.items) {
                if (item.dot < item.rule.rhs.size()) {
                    String sym = item.rule.rhs.get(item.dot);
                    if (terminals.contains(sym)) {
                        actionTable.get(s.id).put(sym, "s" + s.transitions.get(sym));
                    } else if (nonTerminals.contains(sym)) {
                        gotoTable.get(s.id).put(sym, s.transitions.get(sym));
                    }
                } else if (item.rule.id == 0 && item.lookahead.equals("$")) {
                    actionTable.get(s.id).put("$", "acc");
                } else {
                    actionTable.get(s.id).put(item.lookahead, "r" + item.rule.id);
                }
            }
        }
    }

    public void printStatesToCSV(String fileName) throws IOException {
        try (PrintWriter csv = new PrintWriter(new FileWriter(fileName))) {
            csv.println("STATE,RULE,LOOKAHEAD,ACTION / GOTO,STATE(S) / RULE (R)");
            for (State s : states) {
                Map<String, Set<String>> groupedItems = new LinkedHashMap<>();
                Map<String, LR1Item> referenceItem = new HashMap<>();
                List<LR1Item> sortedItems = new ArrayList<>(s.items);
                sortedItems.sort(Comparator.comparingInt((LR1Item a) -> a.rule.id).thenComparingInt(a -> a.dot));
                for (LR1Item item : sortedItems) {
                    String key = item.rule.id + "|" + item.dot;
                    groupedItems.computeIfAbsent(key, k -> new TreeSet<>()).add(item.lookahead);
                    referenceItem.putIfAbsent(key, item);
                }
                for (String key : groupedItems.keySet()) {
                    LR1Item item = referenceItem.get(key);
                    String las = String.join(", ", groupedItems.get(key));
                    String action = "";
                    String target = "";
                    if (item.dot < item.rule.rhs.size()) {
                        String next = item.rule.rhs.get(item.dot);
                        Integer tid = s.transitions.get(next);
                        if (tid != null) {
                            action = (terminals.contains(next) ? "Shift (" : "Goto (") + next + ")";
                            target = "S" + tid;
                        }
                    } else {
                        if (item.rule.id == 0 && groupedItems.get(key).contains("$")) {
                            action = "Acceptance";
                            target = "Accept";
                        } else {
                            action = "Reduce (" + item.rule.lhs + ")";
                            target = "R" + item.rule.id;
                        }
                    }
                    csv.printf("\"S%d\",\"%s\",\"%s\",\"%s\",\"%s\"\n", s.id, formatManualRule(item), las, action, target);
                }
                csv.println(",,,,");
            }
        }
    }

    private String formatManualRule(LR1Item item) {
        StringBuilder sb = new StringBuilder();
        sb.append(item.rule.lhs).append(" -> ");
        if (item.rule.rhs.isEmpty()) {
            sb.append(". ε");
        } else {
            for (int i = 0; i < item.rule.rhs.size(); i++) {
                if (i == item.dot) {
                    sb.append(". ");
                }
                sb.append(item.rule.rhs.get(i)).append(" ");
            }
            if (item.dot == item.rule.rhs.size()) {
                sb.append(". ");
            }
        }
        return sb.toString().trim().replace("\"", "\"\"");
    }

    public void printParsingTableToCSV(String fileName) throws IOException {
        try (PrintWriter csv = new PrintWriter(new FileWriter(fileName))) {
            List<String> rawNonTerminals = new ArrayList<>(nonTerminals);
            csv.print("STATE");
            for (String t : terminals) {
                csv.print("," + t);
            }
            for (String nt : rawNonTerminals) {
                csv.print("," + nt.replace("<", "").replace(">", "").toUpperCase());
            }
            csv.println();

            for (int i = 0; i < states.size(); i++) {
                csv.print("S" + i);
                for (String t : terminals) {
                    String action = actionTable.get(i).getOrDefault(t, "");
                    if (action.startsWith("s")) {
                        action = "S" + action.substring(1);
                    } else if (action.startsWith("r")) {
                        action = "R" + action.substring(1);
                    } else if (action.equals("acc")) {
                        action = "ACCEPT";
                    }
                    csv.print("," + action);
                }
                for (String nt : rawNonTerminals) {
                    Integer gotoState = gotoTable.get(i).get(nt);
                    csv.print("," + (gotoState != null ? "S" + gotoState : ""));
                }
                csv.println();
            }
        }
    }
}
