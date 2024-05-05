import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class CustomLoggingHandler extends Handler {
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_RESET = "\u001B[0m";

    static public void prepareLogger(Logger logger) {
        logger.setUseParentHandlers(false);
        for (Handler h : logger.getHandlers()) {
            logger.removeHandler(h);
        }
        logger.addHandler(new CustomLoggingHandler());
    }

    public CustomLoggingHandler() {
        Formatter formatter = new Formatter() {
            @Override
            public String format(LogRecord record) {
                String out = "";
                // may be useful for checking logging levels:
                // out += record.getLevel() + ": ";
                out += record.getMessage();
                out += ANSI_RESET;
                return out;
            }
        };
        this.setFormatter(formatter);
    }

    @Override
    public void publish(LogRecord record) {
        Formatter formatter = getFormatter();
        System.out.println(formatter.format(record));
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() throws SecurityException {
    }
}

