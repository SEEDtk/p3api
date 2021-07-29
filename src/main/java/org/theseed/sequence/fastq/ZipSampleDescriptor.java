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

    /**
     * Construct a sample descriptor for a sample in a zipped file.
     *
     * @param controller	controlling zip file object
     * @param forward		name of the forward entry, or NULL if there is none
     * @param reverse		name of the reverse entry, or NULL if there is none
     */
    public ZipSampleDescriptor(ZipFile controller, String forward, String reverse) {
        super(forward, reverse);
        this.controller = controller;
    }

    @Override
    protected InputStream getInputStream(String fileName) throws IOException {
        ZipEntry entry = this.controller.getEntry(fileName);
        return this.controller.getInputStream(entry);
    }

}
