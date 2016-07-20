package norswap.autumn

object Autumn
{
    /**
     * Controls the global debug mode (applies to all [Context]s).
     * Within this mode, more information is recorded (e.g. [Parser.lineage]).
     *
     * This mode does not automatically trigger the per-context debugging mode ([Context.debug]),
     * which controls the recording of different information.
     */
    var DEBUG = false
}