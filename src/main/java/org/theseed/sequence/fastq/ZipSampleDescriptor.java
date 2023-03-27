/**
 *
 */
package org.theseed.sequence.fastq;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * This sample descriptor handles a sample taken from a zipped file.  The sample's entry names identify zip entries.
 *
 * @author Bruce Parrello
 *
 */
public class ZipSampleDescriptor extends SampleDescriptor {

    // FIELDS
    /** controlling zip file object */
    private ZipFile controller;
    /** default length when none is available (20 gigabytes) */
    private long DEFAULT_SIZE = 20000000000L;

    /**
     * Construct a sample descriptor for a sample in a zipped file.
     *
     * @param controller	controlling zip file object
     * @param id			sample ID
     * @param forward		name of the forward entry, or NULL if there is none
     * @param reverse		name of the reverse entry, or NULL if there is none
     */
    public ZipSampleDescriptor(ZipFile controller, String id, String forward, String reverse) {
        super(id, forward, reverse);
        this.controller = controller;
    }

    @Override
    protected InputStream getInputStream(String fileName) throws IOException {
        ZipEntry entry = this.controller.getEntry(fileName);
        return this.controller.getInputStream(entry);
    }

    @Override
    public long estimatedSize() {
        return this.size(this.getForwardName()) + this.size(this.getReverseName());
    }

    /**
     * @return the estimated size of the file with the specified entry name
     *
     * @param baseName	entry name of the relevant file, or NULL if none
     */
    private long size(String baseName) {
        long retVal;
        if (baseName == null)
            retVal = 0;
        else {
            ZipEntry entry = this.controller.getEntry(baseName);
            retVal = entry.getSize();
            // Some compressors don't save the size, so we have to punt.
            if (retVal <= 0)
                retVal = DEFAULT_SIZE;
        }
        return retVal;
    }

}
