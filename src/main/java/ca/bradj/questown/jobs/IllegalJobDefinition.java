package ca.bradj.questown.jobs;

public class IllegalJobDefinition extends RuntimeException {
    public IllegalJobDefinition(String s) {
        super(s);
    }
}
