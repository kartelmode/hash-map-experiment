package maps;

import internal.AsciiString;
import internal.DataPayload;

public interface Cache {
    boolean putIfEmpty(DataPayload entry);
    DataPayload get(AsciiString key);
    void deactivate(DataPayload entry);

    long collisionCount();
    int capacity();
    int size();
}
