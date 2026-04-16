
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NonTerminalNode extends ASTNode {

    public String name;
    public List<ASTNode> children = new ArrayList<>();

    public NonTerminalNode(String name) {
        this.name = name;
    }

    public void addChild(ASTNode child) {
        children.add(child);
    }

    public void reverseChildren() {
        Collections.reverse(children);
    }

    @Override
    public void display(String indent) {
        System.out.println(indent + "[" + name + "]");
        for (ASTNode child : children) {
            child.display(indent + "  ");
        }
    }
}
