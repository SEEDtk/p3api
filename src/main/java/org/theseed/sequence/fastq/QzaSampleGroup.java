/**
 *
 */
package org.theseed.sequence.fastq;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.io.LineReader;

/**
 * This is a group of samples stored in a Qiime QZA file.  The QZA is a zip file containing a single directory.  In that directory,
 * the "data" subdirectory contains all of the fastq files plus a MANIFEST file that connects them to sample IDs.
 *
 * @author Bruce Parrello
 *
 */
public class QzaSampleGroup extends FastqSampleGroup  {

    /**
     * File filter for QZA files in the input directory
     */
    public static class filter implements FileFilter {

        @Override
        public boolean accept(File pathname) {
            return (pathname.isFile() && StringUtils.endsWith(pathname.getName(), ".qza"));
        }

    }

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(QzaSampleGroup.class);
    /** zip file controller */
    private ZipFile controller;

    public QzaSampleGroup(File sampleDir) throws IOException {
        super(sampleDir);
    }

    @Override
    protected SortedMap<String, SampleDescriptor> computeSamples(File sampleDir) throws IOException {
        Map<String, Pair> pairMap = new HashMap<String, Pair>();
        // Get the enumeration of entries for this zip file and pull out the UUID.
        this.controller = new ZipFile(sampleDir);
        Enumeration<? extends ZipEntry> entries = this.controller.entries();
        if (! entries.hasMoreElements())
            throw new IOException("QZA file " + sampleDir + " has no data in it.");
        ZipEntry entry = entries.nextElement();
        String uuid = StringUtils.substringBefore(entry.getName(), "/");
        // Now that we have the uuid we can build file names.  Get the manifest.
        entry = this.controller.getEntry(uuid + "/data/MANIFEST");
        // We read the file information from the manifest, which is a CSV.
        try (InputStream manStream = this.controller.getInputStream(entry);
                LineReader reader = new LineReader(manStream)) {
            // Discard the header record.
            reader.next();
            while (reader.hasNext()) {
                String[] fields = StringUtils.split(reader.next(), ',');
                // The sample name is in fields[0], the file name in fields[1], and the direction is in fields[2].
                Pair pair = pairMap.computeIfAbsent(fields[0], x -> new Pair());
                String entryName = uuid + "/data/" + fields[1];
                if (fields[2].contentEquals("reverse"))
                    pair.setReverse(entryName);
                else
                    pair.setForward(entryName);
            }
        }
        // Form all the pairs into samples.
        SortedMap<String, SampleDescriptor> retVal = new TreeMap<String, SampleDescriptor>();
        for (Map.Entry<String, Pair> pairEntry : pairMap.entrySet())
            retVal.put(pairEntry.getKey(), pairEntry.getValue().create(this.controller));
        return retVal;
    }

    /**
     * This object represents the two files in a sample:  forward and reverse.  If we only find the
     * forward, it is an unpaired stream.
     */
    public class Pair {

        private String forwardEntry;
        private String reverseEntry;

        /**
         * Create an empty pair.
         */
        public Pair() {
            this.forwardEntry = null;
            this.reverseEntry = null;
        }

        /**
         * Add a forward file.
         *
         * @param entry		name for the forward reads
         */
        public void setForward(String entry) {
            this.forwardEntry = entry;
        }

        /**
         * Add a reverse file.
         *
         * @param entry		name for the reverse reads
         */
        public void setReverse(String entry) {
            this.reverseEntry = entry;
        }

        /**
         * @return a sample descriptor for this file pair
         *
         * @param controller	zip-file controller
         */
        public SampleDescriptor create(ZipFile controller) {
            return new ZipSampleDescriptor(controller, this.forwardEntry, this.reverseEntry);
        }

    }

    @Override
    protected void init() {
        this.controller = null;
    }

    @Override
    protected void cleanup() throws IOException {
        if (this.controller != null)
            this.controller.close();
    }

}
