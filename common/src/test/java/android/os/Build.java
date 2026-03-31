package android.os;

/**
 * Shadow class for android.os.Build used in unit tests.
 * ExoPlayer accesses Build fields in static initializers.
 * This stub provides default values allowing us to use mockk on ExoPlayer.
 */
public class Build {
    public static final String DEVICE = "generic";
    public static final String MANUFACTURER = "STUB_MANUFACTURER";
    public static final String MODEL = "STUB_MODEL";
}
