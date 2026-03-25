package ca.bradj.questown.commands.test;

import ca.bradj.questown.QT;

public class LogTestOutput implements TestOutput {

    private final String prefix;

    public LogTestOutput(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public void msg(String text) {
        QT.FLAG_LOGGER.info("[{}] {}", prefix, text);
    }

    @Override
    public void error(String text) {
        QT.FLAG_LOGGER.error("[{}] {}", prefix, text);
    }
}
