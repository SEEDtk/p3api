/**
 *
 */
package org.theseed.gff;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;


/**
 * This is a utility I/O class to read a GFF3 file.  Comment lines are skipped, and utility
 * methods are provided to extract fields from the input lines.
 *
 * @author Bruce Parrello
 */
public class GffReader implements Iterable<GffReader.Line>, Iterator<GffReader.Line>, Closeable, AutoCloseable {

    // FIELDS
    /** buffered line reader */
    private BufferedReader reader;
    /** next input line */
    private String nextLine;

    /**
     * This represents a line of GFF3 input.
     */
    public static class Line {

        // FIELDS
        private String id;
        private String source;
        private String type;
        private int left;
        private int right;
        private double score;
        private char strand;
        private int phase;
        private Map<String, String> attributes;

        private Line(String dataLine) {
            String[] fields = StringUtils.split(dataLine, '\t');
            this.id = fields[0];
            this.source = fields[1];
            this.type = (fields[2].contentEquals(".") || fields[2].contentEquals("CDS") ? "peg" : fields[2]);
            this.left = GffReader.getInt(fields[3]);
            this.right = GffReader.getInt(fields[4]);
            this.score = GffReader.getDouble(fields[5]);
            this.strand = (fields[6].contentEquals(".") ? '+' : fields[6].charAt(0));
            this.phase = GffReader.getInt(fields[7]);
            this.attributes = GffKeywords.gffParse(fields[8]);
        }

        /**
         * @return the ID of this sequence
         */
        public String getId() {
            return id;
        }

        /**
         * @return the source of this sequence
         */
        public String getSource() {
            return source;
        }

        /**
         * @return the type of feature
         */
        public String getType() {
            return type;
        }

        /**
         * @return the left edge position
         */
        public int getLeft() {
            return left;
        }

        /**
         * @return the right edge position
         */
        public int getRight() {
            return right;
        }

        /**
         * @return the BLAST score
         */
        public double getScore() {
            return score;
        }

        /**
         * @return the strand containing the sequence
         */
        public char getStrand() {
            return strand;
        }

        /**
         * @return the phase of the sequence
         */
        public int getPhase() {
            return phase;
        }

        /**
         * @return the value of the specified attribute
         *
         * @param key	name of the desired attribute
         */
        public String getAttribute(String key) {
            String retVal = this.attributes.get(key);
            if (retVal == null)
                throw new RuntimeException("Missing attribute " + key + " for GFF sequence ID "
                        + this.id + " of type " + this.type + ".");
            return retVal;
        }

        /**
         * @return the value of the specified attribute, or an empty string if it is not found
         *
         * @param key	name of the desired attribute
         */
        public String getAttributeOrEmpty(String key) {
            return this.attributes.getOrDefault(key, "");
        }

    }

    /**
     * Construct a GFF reader from a file.
     *
     * @param inputFile		file containing GFF3 data
     *
     * @throws IOException
     */
    public GffReader(File inputFile) throws IOException {
        Reader streamReader = new FileReader(inputFile);
        this.setup(streamReader);
    }

    /**
     * @return an integer value from a GFF fields
     *
     * @param field		text of the field
     */
    protected static int getInt(String field) {
        int retVal;
        if (field.contentEquals("."))
            retVal = 0;
        else
            retVal = Integer.valueOf(field);
        return retVal;
    }

    /**
     * @return a floating-point value from a GFF fields
     *
     * @param field		text of the field
     */
    protected static double getDouble(String field) {
        double retVal;
        if (field.contentEquals("."))
            retVal = 0.0;
        else
            retVal = Double.valueOf(field);
        return retVal;
    }

    /**
     * Construct a GFF reader from a stream.
     *
     * @param inputStream	stream containing GFF3 data
     *
     * @throws IOException
     */
    public GffReader(InputStream inputStream) throws IOException {
        Reader streamReader = new InputStreamReader(inputStream);
        this.setup(streamReader);
    }

    /**
     * Initialize this object for reading.  This includes creating the
     * buffered reader and reading the first line.
     *
     * @param streamReader	open stream reader for input
     */
    private void setup(Reader streamReader) {
        this.reader = new BufferedReader(streamReader);
        this.readNextLine();
    }

    /**
     * Read the next non-comment line of the file.
     */
    private void readNextLine() {
        try {
            this.nextLine = this.reader.readLine();
            // Loop until we find a line or reach end of file.
            while (this.nextLine != null  && this.nextLine.charAt(0) == '#')
                this.nextLine = this.reader.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        try {
            this.reader.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean hasNext() {
        return (this.nextLine != null);
    }

    @Override
    public Line next() {
        Line retVal = null;
        if (this.nextLine != null)
            retVal = new Line(this.nextLine);
        this.readNextLine();
        return retVal;
    }

    @Override
    public Iterator<Line> iterator() {
        return this;
    }

}
