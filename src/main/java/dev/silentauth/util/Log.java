package dev.silentauth.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Log {

    private static final Logger LOGGER = LogManager.getLogger("SilentAuth");

    private Log() {
    }

    public static void info(String message) {
        LOGGER.info(message);
    }

    public static void warn(String message) {
        LOGGER.warn(message);
    }

    public static void warn(String message, Throwable t) {
        LOGGER.warn(message, t);
    }

    public static void error(String message, Throwable t) {
        LOGGER.error(message, t);
    }

    /**
     * Tokens must never end up in latest.log - crash reports and log uploads are
     * shared around far too easily. Everything that touches a token logs it through here.
     */
    public static String redact(String secret) {
        if (secret == null || secret.isEmpty()) {
            return "<none>";
        }
        if (secret.length() <= 8) {
            return "****";
        }
        return secret.substring(0, 4) + "****" + secret.substring(secret.length() - 4);
    }
}
