
public class Action {

    public enum ActionType {
        SHIFT, REDUCE, ACCEPT
    }
    public ActionType type;
    public int value; // State index for SHIFT, Production ID for REDUCE

    public Action(ActionType type, int value) {
        this.type = type;
        this.value = value;
    }
}
