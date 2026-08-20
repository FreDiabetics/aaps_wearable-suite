package jamorham.keks.util;

/**
 * JamOrHam
 */

public class Log {

    public static void d(final String TAG, final String msg) {
        // Intentionally disabled. Upstream debug strings can contain raw authentication
        // packets; Sugarlicious records only stable, non-secret collector error codes.
    }

    public static void l(final String msg) {
        d("KEKS-Plugin", msg);
    }
}
