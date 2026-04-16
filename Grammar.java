
import java.util.HashMap;
import java.util.Map;

public class Grammar {

    private static final Map<Integer, Production> productions = new HashMap<>();

    static {
        // Core Structure
        add(0, "PROGRAM'", 1);
        add(1, "PROGRAM", 5);
        add(2, "SUBROUTINE_LIST", 2);
        add(3, "SUBROUTINE_LIST", 0);
        add(4, "SUBROUTINE", 9);

        // Parameters
        add(5, "PARAMS", 1);
        add(6, "PARAMS", 0);
        add(7, "PARAM_LIST", 3);
        add(8, "PARAM_LIST", 1);
        add(9, "PARAM_ITEM", 2);
        add(10, "PARAM_ITEM", 5);
        add(11, "PARAM_ITEM", 7);

        // Record Types & Declarations
        add(12, "REC_TYPE", 1);
        add(13, "REC_TYPE", 4);
        add(14, "REC_TYPE", 6);
        add(15, "REC_TYPE", 1);
        add(16, "DECLARATION", 1);
        add(17, "DECLARATION", 1);
        add(18, "DECLARATION", 1);
        add(19, "ID_DECL", 3);
        add(20, "ID_LIST", 3);
        add(21, "ID_LIST", 1);
        add(22, "ID_INIT", 1);
        add(23, "ID_INIT", 3);
        add(24, "CONST_DECL", 4);
        add(25, "CONST_LIST", 3);
        add(26, "CONST_LIST", 1);
        add(27, "CONST_INIT", 3);
        add(28, "CLUSTER_DECL", 4);
        add(29, "CLUSTER_LIST", 3);
        add(30, "CLUSTER_LIST", 1);
        add(31, "CLUSTER_ITEM", 1);
        add(32, "CLUSTER_ITEM", 1);

        // Clusters (Arrays)
        add(33, "CLUSTER_1D", 4);
        add(34, "CLUSTER_1D", 6);
        add(35, "1D_INIT", 3);
        add(36, "CLUSTER_1D_LIST", 3);
        add(37, "CLUSTER_1D_LIST", 1);
        add(38, "CLUSTER_2D", 6); // IDENTIFIER [ LIT ] [ LIT ]
        add(39, "CLUSTER_2D", 8); // IDENTIFIER [ LIT ] [ LIT ] ASSIGN 2D_INIT
        add(40, "2D_INIT", 3);
        add(41, "CLUSTER_2D_LIST", 3);
        add(42, "CLUSTER_2D_LIST", 1);

        // Data Types
        add(43, "DATA_TYPE", 1); // PULSE
        add(44, "DATA_TYPE", 1); // SPARK
        add(45, "DATA_TYPE", 1); // STREAM
        add(46, "DATA_TYPE", 1); // THOUGHT
        add(47, "DATA_TYPE", 1); // NEURON
        add(48, "DATA_TYPE", 1); // SYNAPSE

        // Statements
        add(49, "STATEMENT_LIST", 2);
        add(50, "STATEMENT_LIST", 0);
        add(51, "STATEMENT", 1);
        add(52, "STATEMENT", 2); // assign SEMICOLON
        add(53, "STATEMENT", 2); // builtin SEMICOLON
        add(54, "STATEMENT", 2); // subroutine SEMICOLON
        add(55, "STATEMENT", 1); // conditional
        add(56, "STATEMENT", 1); // loop
        add(57, "STATEMENT", 1); // switch
        add(58, "STATEMENT", 1); // io
        add(59, "STATEMENT", 3); // RECALL expr SEMICOLON
        add(60, "STATEMENT", 2); // FLOW SEMICOLON
        add(61, "STATEMENT", 2); // DORMANT SEMICOLON

        // IO & Conditionals
        add(62, "IO_STMT", 4);
        add(63, "IO_STMT", 5);
        add(64, "CONDITIONAL_STMT", 7); // STIMULATE ( expr ) { list } opt_inhibit
        add(65, "OPT_INHIBIT", 4); // INHIBIT { list }
        add(66, "OPT_INHIBIT", 0);
        add(67, "LOOP_STMT", 7);
        add(68, "LOOP_STMT", 8);
        add(69, "LOOP_STMT", 10);
        add(70, "ECHO_INIT", 2);
        add(71, "ECHO_INIT", 1);
        add(72, "SWITCH_STMT", 8);
        add(73, "CASE_LIST", 2);
        add(74, "CASE_LIST", 0);
        add(75, "CASE_ITEM", 4); // PATH value : list
        add(76, "OPT_BASE", 3);  // BASE : list
        add(77, "OPT_BASE", 0);

        // Case Values
        add(80, "CASE_VALUE", 1);
        add(81, "CASE_VALUE", 1);
        add(82, "CASE_VALUE", 1);
        add(83, "CASE_VALUE", 1);
        add(84, "CASE_VALUE", 1);

        // Expressions
        add(85, "ASSIGN_STMT", 3);
        add(86, "EXPR", 1);
        add(87, "LOGIC_OR", 3);
        add(88, "LOGIC_OR", 1);
        add(89, "LOGIC_XOR", 3);
        add(90, "LOGIC_XOR", 1);
        add(91, "LOGIC_AND", 3);
        add(92, "LOGIC_AND", 1);
        add(93, "REL_EQUAL", 3);
        add(94, "REL_EQUAL", 1);
        add(95, "REL_EXPR", 3);
        add(96, "REL_EXPR", 1);
        add(97, "ADD_EXPR", 3);
        add(98, "ADD_EXPR", 1);
        add(99, "MULT_EXPR", 3);
        add(100, "MULT_EXPR", 1);
        add(101, "POW_EXPR", 3);
        add(102, "POW_EXPR", 1);
        add(103, "UNARY_EXPR", 2);
        add(104, "UNARY_EXPR", 1);
        add(105, "POSTFIX_EXPR", 2);
        add(106, "POSTFIX_EXPR", 2);
        add(107, "POSTFIX_EXPR", 1);
        add(108, "FACTOR", 1);
        add(109, "FACTOR", 1);
        add(110, "FACTOR", 3);
        add(111, "FACTOR", 1);
        add(112, "FACTOR", 1);
        add(113, "VARIABLE_ACCESS", 4);
        add(114, "VARIABLE_ACCESS", 1);
        add(115, "SUBROUTINE_CALL", 4);
        add(116, "BUILTIN_CALL", 4);
        add(117, "BUILTIN_CALL", 4);
        add(118, "ARG_LIST", 1);
        add(119, "ARG_LIST", 0);
        add(120, "ARGS", 3);
        add(121, "ARGS", 1);

        // Literals (Rules 122-128)
        for (int i = 122; i <= 128; i++) {
            add(i, "LITERAL", 1);
        }

        // Operators
        for (int i = 129; i <= 134; i++) {
            add(i, "ASSIGN_OP", 1);
        }
        add(135, "OR_OP", 1);
        add(136, "XOR_OP", 1);
        add(137, "AND_OP", 1);
        add(138, "RELEQ_OP", 1);
        add(139, "RELEQ_OP", 1);
        for (int i = 140; i <= 143; i++) {
            add(i, "REL_OP", 1);
        }
        add(144, "ADD_OP", 1);
        add(145, "ADD_OP", 1);
        add(146, "MUL_OP", 1);
        add(147, "MUL_OP", 1);
        add(148, "MUL_OP", 1);
        for (int i = 149; i <= 153; i++) {
            add(i, "UNARY_OP", 1);
        }
    }

    private static void add(int id, String lhs, int rhsLength) {
        productions.put(id, new Production(id, lhs, rhsLength));
    }

    public static Production getProduction(int id) {
        return productions.get(id);
    }
}
