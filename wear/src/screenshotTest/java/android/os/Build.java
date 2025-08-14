package android.os;

public class Build {
    // On screenshot-0.0.1-alpha11 FINGERPRINT is not set because of that we need to set everything
    // For more details: https://issuetracker.google.com/issues/422488144#comment17
    public static final String FINGERPRINT = "STUB_FINGERPRINT";
    public static final String DEVICE = "STUB_DEVICE";
    public static final String MANUFACTURER = "STUB_MANUFACTURER";
    public static final String MODEL = "STUB_MODEL";
}
