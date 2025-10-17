package internal;

public class DataWrapper {
    private AsciiString asciiString;
    private int inCachePosition = -1;

    private boolean isActive = false;

    public DataWrapper() {
        asciiString = new AsciiString();
    }

    public void setInCachePosition(int inCachePosition) {
        this.inCachePosition = inCachePosition;
    }

    public int getInCachePosition() {
        return inCachePosition;
    }

    public AsciiString getAsciiString() {
        return asciiString;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }

    public void setAsciiString(AsciiString asciiString) {
        this.asciiString.copyFrom(asciiString);
    }
}
