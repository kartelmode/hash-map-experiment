package internal;

/** Simulates actual payload */
public class DataPayload {
    private final AsciiString key;
    private int inCachePosition = -1;

    public DataPayload(AsciiString key) {
        this.key = key;
    }

    public void setInCachePosition(int inCachePosition) {
        this.inCachePosition = inCachePosition;
    }

    public int getInCachePosition() {
        return inCachePosition;
    }

    public AsciiString getKey() {
        return key;
    }

}
