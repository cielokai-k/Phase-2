
public class InterpreterException extends RuntimeException {

    private int lineNumber;

    public InterpreterException(String message, int lineNumber) {
        super(message);
        this.lineNumber = lineNumber;
    }

    public InterpreterException(String message, int lineNumber, Throwable cause) {
        super(message, cause);
        this.lineNumber = lineNumber;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    @Override
    public String toString() {
        return getMessage();
    }
}
