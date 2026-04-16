
public class TerminalNode extends ASTNode {

    public Token token;

    public TerminalNode(Token token) {
        this.token = token;
    }

    @Override
    public void display(String indent) {
        System.out.println(indent + "Terminal: " + token.displayToken());
    }
}
