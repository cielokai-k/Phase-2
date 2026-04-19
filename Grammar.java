
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
        add(38, "CLUSTER_2D", 7);
        add(39, "CLUSTER_2D", 9);
        add(40, "2D_INIT", 3);
        add(41, "CLUSTER_2D_LIST", 3);
        add(42, "CLUSTER_2D_LIST", 1);

        // Data Types
        add(43, "DATA_TYPE", 1);
        add(44, "DATA_TYPE", 1);
        add(45, "DATA_TYPE", 1);
        add(46, "DATA_TYPE", 1);
        add(47, "DATA_TYPE", 1);
        add(48, "DATA_TYPE", 1);

        // Statements
        add(49, "STATEMENT_LIST", 2);
        add(50, "STATEMENT_LIST", 0);
        add(51, "STATEMENT", 1);
        add(52, "STATEMENT", 2);
        add(53, "STATEMENT", 2);
        add(54, "STATEMENT", 2);
        add(55, "STATEMENT", 2);
        add(56, "STATEMENT", 1);
        add(57, "STATEMENT", 1);
        add(58, "STATEMENT", 1);
        add(59, "STATEMENT", 1);
        add(60, "STATEMENT", 3);
        add(61, "STATEMENT", 2);
        add(62, "STATEMENT", 2);

        // IO & Conditionals
        add(63, "IO_STMT", 4);
        add(64, "IO_STMT", 5);
        add(65, "CONDITIONAL_STMT", 8);
        add(66, "OPT_INHIBIT", 4);
        add(67, "OPT_INHIBIT", 0);
        add(68, "LOOP_STMT", 7);
        add(69, "LOOP_STMT", 9);
        add(70, "LOOP_STMT", 10);
        add(71, "ECHO_INIT", 2);
        add(72, "ECHO_INIT", 1);
        add(73, "SWITCH_STMT", 8);
        add(74, "CASE_LIST", 2);
        add(75, "CASE_LIST", 0);
        add(76, "CASE_ITEM", 4);
        add(77, "OPT_BASE", 3);
        add(78, "OPT_BASE", 0);

        // Case Values
        add(79, "CASE_VALUE", 1);
        add(80, "CASE_VALUE", 1);
        add(81, "CASE_VALUE", 1);
        add(82, "CASE_VALUE", 1);
        add(83, "CASE_VALUE", 1);

        // Expressions (Note the shifts here from 84 onwards)
        add(84, "ASSIGN_STMT", 3);
        add(85, "EXPR", 1);
        add(86, "LOGIC_OR", 3);
        add(87, "LOGIC_OR", 1);
        add(88, "LOGIC_XOR", 3);
        add(89, "LOGIC_XOR", 1);
        add(90, "LOGIC_AND", 3);
        add(91, "LOGIC_AND", 1);
        add(92, "REL_EQUAL", 3);
        add(93, "REL_EQUAL", 1);
        add(94, "REL_EXPR", 3);
        add(95, "REL_EXPR", 1);
        add(96, "ADD_EXPR", 3);
        add(97, "ADD_EXPR", 1);
        add(98, "MULT_EXPR", 3);
        add(99, "MULT_EXPR", 1);
        add(100, "POW_EXPR", 3);
        add(101, "POW_EXPR", 1);
        add(102, "UNARY_EXPR", 2);
        add(103, "UNARY_EXPR", 1);
        add(104, "POSTFIX_EXPR", 2);
        add(105, "POSTFIX_EXPR", 2);
        add(106, "POSTFIX_EXPR", 1);
        add(107, "FACTOR", 1);
        add(108, "FACTOR", 1);
        add(109, "FACTOR", 3);
        add(110, "FACTOR", 1);
        add(111, "FACTOR", 1);
        add(112, "VARIABLE_ACCESS", 4);
        add(113, "VARIABLE_ACCESS", 1);
        add(114, "SUBROUTINE_CALL", 4);
        add(115, "BUILTIN_CALL", 4);
        add(116, "BUILTIN_CALL", 4);
        add(117, "ARG_LIST", 1);
        add(118, "ARG_LIST", 0);
        add(119, "ARGS", 3);
        add(120, "ARGS", 1);

        // Literals
        add(121, "LITERAL", 1);
        add(122, "LITERAL", 1);
        add(123, "LITERAL", 1);
        add(124, "LITERAL", 1);
        add(125, "LITERAL", 1);
        add(126, "LITERAL", 1);
        add(127, "LITERAL", 1);

        // Operators
        add(128, "ASSIGN_OP", 1);
        add(129, "ASSIGN_OP", 1);
        add(130, "ASSIGN_OP", 1);
        add(131, "ASSIGN_OP", 1);
        add(132, "ASSIGN_OP", 1);
        add(133, "ASSIGN_OP", 1);
        add(134, "OR_OP", 1);
        add(135, "XOR_OP", 1);
        add(136, "AND_OP", 1);
        add(137, "RELEQ_OP", 1);
        add(138, "RELEQ_OP", 1);
        add(139, "REL_OP", 1);
        add(140, "REL_OP", 1);
        add(141, "REL_OP", 1);
        add(142, "REL_OP", 1);
        add(143, "ADD_OP", 1);
        add(144, "ADD_OP", 1);
        add(145, "MUL_OP", 1);
        add(146, "MUL_OP", 1);
        add(147, "MUL_OP", 1);
        add(148, "UNARY_OP", 1);
        add(149, "UNARY_OP", 1);
        add(150, "UNARY_OP", 1);
        add(151, "UNARY_OP", 1);
        add(152, "UNARY_OP", 1);
    }

    private static void add(int id, String lhs, int rhsLength) {
        productions.put(id, new Production(id, lhs, rhsLength));
    }

    public static Production getProduction(int id) {
        return productions.get(id);
    }
}
