package org.theseed.p3api;

/**
 * This is a subclass of the cursor connection that does not have any field mapping. It is simply
 * a cursor connection that uses the default data map.
 */
public class P3CursorConnection extends CursorConnection {

    public P3CursorConnection() {
        super(BvbrcDataMap.DEFAULT_DATA_MAP);
    }

}
