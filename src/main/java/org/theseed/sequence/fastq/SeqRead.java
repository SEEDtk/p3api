/**
 *
 */
package org.theseed.sequence.fastq;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.theseed.genome.Contig;
import org.theseed.sequence.DnaKmers;
import org.theseed.sequence.SequenceKmers;

/**
 * This object represents a sequence read.  A sequence read consists of a label, forward and reverse DNA sequences, and a quality score.
 * The reverse sequence is optional.
 *
 * A nested class is provided for representation of the read as DNA kmers.
 *
 * @author Bruce Parrello
 *
 */
public class SeqRead {

    // FIELDS
    /** label for this read */
    private String label;
    /** left sequence string */
    private String lseq;
    /** right sequence string (empty if none) */
    private String rseq;
    /** combined sequence quality (generally 0 to 99, logarithmic) */
    private double qual;
    /** current phred offset, indicating the 0 value for quality */
    private static int phredOffset = 33;
    /** match pattern for extracting sequence label and type */
    private static Pattern ID_PATTERN = Pattern.compile("@(\\S+).*");
    /** match pattern for determining direction */
    private static Pattern DIR_PATTERN = Pattern.compile("(.+)[./]([12])");

    /**
     * Set the phred offset.
     *
     * @param newOffset		new value to use
     */
    public static void setPhredOffset(int newOffset) {
        phredOffset = newOffset;
    }

    /**
     * Construct a sequence read from the string components.
     *
     * @param label		sequence label
     * @param left		left sequence string
     * @param lqual		left quality string
     * @param right		right sequence string (or NULL)
     * @param rqual		right quality string (or NULL)
     */
    public SeqRead(String label, String left, String lqual, String right, String rqual) {
        createRead(label, left, lqual, right, rqual);
    }

    /**
     * Construct an unpaired sequence read from the string components.
     *
     * @param label		sequence label
     * @param left		left sequence string
     * @param lqual		left quality string
     */
    public SeqRead(String label, String left, String lqual) {
        createRead(label, left, lqual, null, null);
    }

    /**
     * Construct a sequence read from the parts.
     *
     * @param left		left sequence part
     * @param right		right sequence part
     */
    public SeqRead(Part left, Part right) {
        createRead(left.label, left.seq, left.qual, right.seq, right.qual);
    }

    /**
     * Construct an unpaired sequence read from one part.
     *
     * @param left		left sequence part
     */
    public SeqRead(Part left) {
        createRead(left.label, left.seq, left.qual, null, null);
    }

    /**
     * Initialize the sequence read.
     *
     * @param label		sequence label
     * @param left		left sequence string
     * @param lqual		left quality string
     * @param right		right sequence string (or NULL)
     * @param rqual		right quality string (or NULL)
     */
    private void createRead(String label, String left, String lqual, String right, String rqual) {
        this.label = label;
        this.lseq = left;
        this.rseq = (right == null ? "" : right);
        // Create the quality string.
        String qualString = (right == null ? lqual : lqual + rqual);
        // Compute the mean quality.
        this.qual = 0.0;
        for (int i = 0; i < qualString.length(); i++)
            this.qual += (int) qualString.charAt(i) - phredOffset;
        if (qualString.length() > 0)
            this.qual /= qualString.length();
    }

    /**
     * @return the label
     */
    public String getLabel() {
        return this.label;
    }

    /**
     * @return the left (forward) sequence
     */
    public String getLseq() {
        return this.lseq;
    }

    /**
     * @return the right (reverse) sequence
     */
    public String getRseq() {
        return this.rseq;
    }

    /**
     * The quality indicates the error probability, which is 1e-x, where "x" is the quality level.
     *
     * @return the quality level
     */
    public double getQual() {
        return this.qual;
    }

    /**
     * @return the length of the read
     */
    public int length() {
        return this.lseq.length() + this.rseq.length();
    }

    /**
     * Create a partial read from the current input.
     *
     * @param reader	buffer reader stream for input
     *
     * @return the read found, or NULL if we are at end of file
     */
    protected static Part read(BufferedReader reader) throws IOException {
        Part retVal = null;
        // We need to read four lines:  label 1, sequence, label 2, quality.
        String line = reader.readLine();
        if (line != null) {
            // Here we have a record to read.
            retVal = new Part();
            Matcher m = ID_PATTERN.matcher(line);
            if (! m.matches()) {
                if (line.isEmpty())
                    throw new IOException("Header record in FASTQ file is empty.");
                else
                    throw new IOException("Invalid header record in FASTQ file beginning with \"" + StringUtils.left(line, 15) + "\".");
            } else {
                // Get the FASTQ label.
                String label = m.group(1);
                // Check for a direction indicator.
                m = DIR_PATTERN.matcher(label);
                if (m.matches()) {
                    retVal.reverse = (m.group(2).charAt(0) == '2');
                    retVal.label = m.group(1);
                } else {
                    retVal.label = label;
                    retVal.reverse = false;
                }
                // Now read the sequence.
                retVal.seq = reader.readLine();
                if (retVal.seq == null)
                    throw new IOException("No sequence record found for \"" + label + "\".");
                // Verify that we have a quality string.
                line = reader.readLine();
                if (line.charAt(0) != '+')
                    throw new IOException("Quality line marker not found for \"" + label + "\".");
                // Read the quality string.
                retVal.qual = reader.readLine();
                if (retVal.qual == null)
                    throw new IOException("Quality line not found for \"" + label + "\".");
                if (retVal.qual.length() != retVal.seq.length())
                    throw new IOException("Quality sequence length error for \"" + label + "\".");
            }
        }
        return retVal;
    }

    /**
     * This class represents a partial read.  It contains the label, the sequence, the quality string, and the
     * read type.
     */
    public static class Part {

        /** sequence label */
        private String label;
        /** TRUE for a right (reverse) sequence, else FALSE */
        private boolean reverse;
        /** sequence string */
        private String seq;
        /** quality string */
        private String qual;

        /**
         * @return the sequence label (with the direction indicator removed)
         */
        public String getLabel() {
            return this.label;
        }

        /**
         * @return TRUE if this is a reverse sequence, else FALSE
         */
        public boolean isReverse() {
            return this.reverse;
        }

        /**
         * @return the seq
         */
        public String getSeq() {
            return this.seq;
        }

        /**
         * @return the qual
         */
        public String getQual() {
            return this.qual;
        }

        /**
         * @return TRUE if the two parts are for the same spot
         *
         * @param other		part to match
         */
        public boolean matches(Part other) {
            return this.label.equals(other.label);
        }

    }

    /**
     * This class is used to construct kmers from the read.  The left and right sequences are built separately
     * In other words, the kmer set contains kmers for both sequences, but there is no kmer that crosses the
     * sequences.  Because DNA kmers are symmetric, we do not need to reverse the right sequence.
     *
     */
    public class Kmers extends SequenceKmers {

        /** kmer size */
        private final int K;

        /**
         * Construct the kmers using the default kmer size.
         */
        public Kmers() {
            this.K = DnaKmers.kmerSize();
            setup();
        }

        /**
         * Construct the kmers using a specified kmer size.
         *
         * @param kSize		kmer size to use
         */
        public Kmers(int kSize) {
            this.K = kSize;
            setup();
        }

        /**
         * Initialize the kmer hash.
         */
        private void setup() {
            // Form the sequence out of the two pieces.
            this.sequence = SeqRead.this.lseq + "<>" + SeqRead.this.rseq;
            // Create the hash set.
            this.kmerSet = new HashSet<String>(this.sequence.length() * 8 / 3);
            // Fill in the kmers.
            this.processSequence(SeqRead.this.lseq);
            this.processSequence(SeqRead.this.rseq);
        }

        /**
         * Incorporate kmers from a sequence into this kmer object.
         *
         * @param sequence		sequence to process
         */
        private void processSequence(String sequence) {
            String original = sequence.toLowerCase();
            String rDna = Contig.reverse(original);
            int K = DnaKmers.kmerSize();
            int n = original.length() - K;
            for (int i = 0; i <= n; i++) {
                this.kmerSet.add(original.substring(i, i + K));
                this.kmerSet.add(rDna.substring(i, i + K));
            }
        }

        @Override
        public int getK() {
            return this.K;
        }

    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.label == null) ? 0 : this.label.hashCode());
        result = prime * result + ((this.lseq == null) ? 0 : this.lseq.hashCode());
        long temp;
        temp = Double.doubleToLongBits(this.qual);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        result = prime * result + ((this.rseq == null) ? 0 : this.rseq.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SeqRead)) {
            return false;
        }
        SeqRead other = (SeqRead) obj;
        if (this.label == null) {
            if (other.label != null) {
                return false;
            }
        } else if (!this.label.equals(other.label)) {
            return false;
        }
        if (this.lseq == null) {
            if (other.lseq != null) {
                return false;
            }
        } else if (!this.lseq.equals(other.lseq)) {
            return false;
        }
        if (Double.doubleToLongBits(this.qual) != Double.doubleToLongBits(other.qual)) {
            return false;
        }
        if (this.rseq == null) {
            if (other.rseq != null) {
                return false;
            }
        } else if (!this.rseq.equals(other.rseq)) {
            return false;
        }
        return true;
    }

    /**
     * @return the maximum length of a read string.
     */
    public int maxlength() {
        int retVal = this.lseq.length();
        if (this.rseq.length() > retVal)
            retVal = this.rseq.length();
        return retVal;
    }

}
