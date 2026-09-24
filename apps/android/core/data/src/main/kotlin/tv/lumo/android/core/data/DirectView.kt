package tv.lumo.android.core.data

/**
 * The two views of the Direct destination (S9-04).
 *
 * A destination, one screen: Channels lists what can be watched now, Guide says
 * what is on. The pair is deliberately a closed set rather than a free string,
 * so that what a device remembers is one of the two values the screen can draw
 * — a stored value this build does not know degrades to the default instead of
 * drawing nothing.
 *
 * Lives in `core:data` and not in `feature:live` because the memory that carries
 * it is shared by both applications: the phone and the television must agree on
 * what "the remembered view" means even though they draw it differently.
 */
enum class DirectView {

    /** The list of channels. What a source with no memory opens on (GD-02). */
    Channels,

    /** What is on now. Reached by the Guide switch or the home "TV guide" link. */
    Guide;

    companion object {

        /**
         * The view a source this device has never opened starts on.
         *
         * Channels, not Guide: the first thing somebody wants from a direct
         * destination is to watch, and the guide is a detour until they ask for
         * it (GD-02).
         */
        val Default: DirectView = Channels

        /**
         * Reads back what [DataStoreDirectViewStore] stored.
         *
         * The value is a name this enumeration wrote. A file written by a newer
         * build, or a corrupted entry, reads as [Default] — the memory of a view
         * is not worth failing a screen for.
         */
        fun fromStored(value: String?): DirectView =
            entries.firstOrNull { it.name == value } ?: Default
    }
}
