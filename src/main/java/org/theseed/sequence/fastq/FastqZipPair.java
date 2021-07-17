/**
 *
 */
package org.theseed.sequence.fastq;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.lang3.StringUtils;
import org.theseed.io.LineReader;

/**
 * This class represents a pair of FASTQ files stored in a ZipFile object.  The stored names
 * are ZipEntry names.  A singleton contains a left file only.
 *
 * @author Bruce Parrello
 */
public class FastqZipPair extends FastqPair {

    // FIELDS
    /** controlling zip file */
    private ZipFile zipInput;
    /** left file */
    private String leftEntryName;
    /** right file */
    private String rightEntryName;

    /**
     * Construct a blank, empty stream pair.
     *
     * @param controller	controlling ZipFile object
     */
    public FastqZipPair(ZipFile controller) {
        super();
        this.zipInput = controller;
        this.leftEntryName = null;
        this.rightEntryName = null;
    }

    @Override
    public BufferedReader openLeft() throws IOException {
        if (this.leftEntryName == null)
            throw new IOException("No left pair file found for \"" + this.rightEntryName + "\".");
        this.setLeftInput(this.getInputStream(this.leftEntryName));
        BufferedReader retVal = getReader(this.getLeftInput());
        return retVal;
    }

    @Override
    public BufferedReader openRight() throws IOException {
        BufferedReader retVal = null;
        if (this.rightEntryName != null) {
            this.setRightInput(this.getInputStream(this.rightEntryName));
            retVal = getReader(this.getRightInput());
        }
        return retVal;
    }

    /**
     * Return a collection of the file pairs in the QZA file.
     *
     * @param inFile	zip controller for the QZA file to parse
     *
     * @return a collection of file-pair objects from the runs in this QZA file
     *
     * @throws IOException
     */
    public static Collection<FastqPair> organize(ZipFile inFile) throws IOException {
        // Get a map of names to file-pair objects.  All of this information is in the manifest.
        // We use a tree because the number of pairs is expected to be small.
        Map<String, FastqPair> pairMap = new TreeMap<String, FastqPair>();
        // Get the enumeration of entries for this zip file and pull out the UUID.
        Enumeration<? extends ZipEntry> entries = inFile.entries();
        if (! entries.hasMoreElements())
            throw new IOException("QZA file " + inFile.getName() + " has no data in it.");
        ZipEntry entry = entries.nextElement();
        String uuid = StringUtils.substringBefore(entry.getName(), "/");
        // Now that we have the uuid we can build file names.  Get the manifest.
        entry = inFile.getEntry(uuid + "/data/MANIFEST");
        // We read the file information from the manifest, which is a CSV.
        try (InputStream manStream = inFile.getInputStream(entry);
                LineReader reader = new LineReader(manStream)) {
            // Discard the header record.
            reader.next();
            while (reader.hasNext()) {
                String[] fields = StringUtils.split(reader.next(), ',');
                // The sample name is in fields[0], the file name in fields[1], and the direction is in fields[2].
                FastqZipPair pair = (FastqZipPair) pairMap.computeIfAbsent(fields[0], x -> new FastqZipPair(inFile));
                String entryName = uuid + "/data/" + fields[1];
                if (fields[2].contentEquals("reverse"))
                    pair.rightEntryName = entryName;
                else
                    pair.leftEntryName = entryName;
                pair.setCompressed(fields[1].endsWith(".gz"));
            }
        }
        // Return the pairs found.
        return pairMap.values();
    }

    /**
     * Open the input stream for one of the entries.  This method figures out if we're compressed or not.
     *
     * @param entryName		name of entry to open
     *
     * @return the input stream opened
     *
     * @throws IOException
     */
    protected InputStream getInputStream(String entryName) throws IOException {
        // Get the file entry.
        ZipEntry entry = this.zipInput.getEntry(entryName);
        // Open an input stream for it.
        InputStream retVal = this.zipInput.getInputStream(entry);
        if (this.isCompressed()) {
            // If we are compressed, we need to wrap it in a GZIP stream.  Closing the GZIP closes the underlying zip stream.
            retVal = new GZIPInputStream(retVal);
        }
        return retVal;
    }

}
