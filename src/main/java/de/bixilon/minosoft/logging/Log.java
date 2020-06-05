package de.bixilon.minosoft.logging;

import de.bixilon.minosoft.util.Util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class Log {
    static LogLevel level = LogLevel.PROTOCOL;
    final static SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    final static List<String> queue = new ArrayList<>();

    public static void log(LogLevel l, String message) {
        if (l.getId() > level.getId()) {
            // log level too low
            return;
        }
        queue.add(String.format("[%s] [%s] %s", timeFormat.format(System.currentTimeMillis()), l.name(), message));
    }

    public static void initThread() {

        Thread logThread = new Thread(() -> {
            while (true) {
                while (queue.size() > 0) {
                    // something to print
                    System.out.println(queue.get(0));

                    //ToDo: log to file

                    queue.remove(0);
                }
                Util.sleep(1);
            }

        });
        logThread.start();
    }

    /**
     * Logs all game related things (chunk loading, rendering, ...)
     *
     * @param message Raw message to log
     */
    public static void game(String message) {
        log(LogLevel.GAME, message);
    }

    /**
     * Logs all fatal errors (critical exceptions, etc)
     *
     * @param message Raw message to log
     */
    public static void fatal(String message) {
        log(LogLevel.FATAL, message);
    }

    /**
     * Logs all general infos (connecting to server, ...)
     *
     * @param message Raw message to log
     */
    public static void info(String message) {
        log(LogLevel.INFO, message);
    }

    /**
     * Logs all warnings (connection to server failed, ...)
     *
     * @param message Raw message to log
     */
    public static void warn(String message) {
        log(LogLevel.WARNING, message);
    }

    /**
     * Logs all debug relevant infos (...)
     *
     * @param message Raw message to log
     */
    public static void debug(String message) {
        log(LogLevel.DEBUG, message);
    }

    /**
     * Logs all debug relevant infos (even higher level!) (connection status, ...)
     *
     * @param message Raw message to log
     */
    public static void verbose(String message) {
        log(LogLevel.VERBOSE, message);
    }

    /**
     * Logs all protocol data (received protocol with length and command x,...)
     *
     * @param message Raw message to log
     */
    public static void protocol(String message) {
        log(LogLevel.PROTOCOL, message);
    }

    public static LogLevel getLevel() {
        return level;
    }

    public static void setLevel(LogLevel level) {
        Log.level = level;
    }
}
