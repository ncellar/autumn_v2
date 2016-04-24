package norswap.autumn;

public class Utils
{
    /**
     * An exception type that does not create a stack trace when instantiated.
     */
    public static class NoStackTrace extends Throwable {
        public NoStackTrace(String msg) {
            super(msg, null, false, false);
        }
    }
}
