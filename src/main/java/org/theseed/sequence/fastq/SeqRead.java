/**
 *
 */
package org.theseed.sequence.fastq;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.genome.Contig;
import org.theseed.sequence.DnaKmers;
import org.theseed.sequence.ISequence;
import org.theseed.sequence.SequenceKmers;

/**
 * This object represents a sequence read.  A sequence read consists of a label, forward and reverse DNA sequences, and a quality string.
 * The reverse sequence is optional.
 *
 * A nested class is provided for representation of the read as DNA kmers.
 *
 * @author Bruce Parrello
 *
 */
public class SeqRead {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(SeqRead.class);
    /** label for this read */
    private String label;
    /** left sequence string */
    private String lseq;
    /** right sequence string (empty if none) */
    private String rseq;
    /** left quality string */
    private String lqual;
    /* right quality string */
    private String rqual;
    /** coverage (defaults to 1.0 for FASTQ) */
    private double coverage;
    /** current phred offset, indicating the 0 value for quality */
    private static int phredOffset = 33;
    /** bad-result chance for each known quality code */
    private static final double[] phredFactors = IntStream.range(0, 45).mapToDouble(i -> Math.pow(10.0, -i/10.0)).toArray();
    /** minimum overlap score */
    private static int minOverlap = 5;
    /** match pattern for extracting sequence label and type */
    private static Pattern ID_PATTERN = Pattern.compile("@(\\S+).*");
    /** match pattern for determining direction */
    private static Pattern DIR_PATTERN = Pattern.compile("(.+)[./]([12])");
    /** list of valid DNA characters */
    private static String VALID_NA = "acgt";
    /** default quality for contigs */
    private static char DEFAULT_QUAL = 'F';

    /**
     * Set the phred offset.
     *
     * @param newOffset		new value to use
     */
    public static void setPhredOffset(int newOffset) {
        phredOffset = newOffset;

    }

    /**
     * Set the minimum overlap score.
     *
     * @param newMin		new value to use
     */
    public static void setMinOverlap(int newMin) {
        minOverlap = newMin;
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
        this.lseq = StringUtils.lowerCase(left);
        this.rseq = (right == null ? "" : StringUtils.lowerCase(right));
        // Default the quality if there is no quality string.
        if (lqual == null)
            this.lqual = StringUtils.repeat(DEFAULT_QUAL, left.length());
        else
            this.lqual = lqual;
        if (rqual == null)
            this.rqual = StringUtils.repeat(DEFAULT_QUAL, this.getRseq().length());
        else
            this.rqual = rqual;
        this.coverage = 1.0;
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
     * The quality indicates the error probability as a fraction of 1.
     *
     * @return the quality level
     */
    public double getQual() {
        double retVal = qualChance(this.lqual, 0, this.lqual.length());
        if (this.rqual != null)
            retVal *= qualChance(this.rqual, 0, this.rqual.length());
        return 1.0 - retVal;
    }

    /**
     * The expected error is the number of bad base pairs expected.
     *
     * @return the expected error
     */
    public double getExpectedErrors() {
        double retVal = 0.0;
        if (this.lqual != null)
            retVal = IntStream.range(0, lqual.length()).mapToDouble(i -> baseError(this.lqual, i)).sum();
        if (this.rqual != null)
            retVal += IntStream.range(0, rqual.length()).mapToDouble(i -> baseError(this.rqual, i)).sum();
        return retVal;
    }

    /**
     * Compute the correct-result chance for a substring of a quality string.
     *
     * @param qual		full quality string
     * @param pos		start position
     * @param len		length to check
     *
     * @return the chance of an correct in the specified region of the sequence
     */
    public static double qualChance(String qual, int pos, int len) {
        double retVal = 1.0;
        final int n = pos + len;
        for (int i = pos; i < n; i++) {
            double chance = baseQual(qual, i);
            retVal *= chance;
        }
        return retVal;
    }

    /**
     * Compute the correct-result change for a single quality character.
     *
     * @param qual	quality string
     * @param i		character position
     *
     * @return the chance of a correct result
     */
    public static double baseQual(String qual, int i) {
        double retVal = baseError(qual, i);
        return 1.0 - retVal;
    }

    /**
     * Compute the bad-result chance for a single quality character.
     *
     * @param qual	quality string
     * @param i		character position
     *
     * @return the chance of a bad result
     */
    public static double baseError(String qual, int i) {
        int lvl = qual.charAt(i) - phredOffset;
        if (lvl > 45) lvl = 45;
        double retVal = phredFactors[lvl];
        return retVal;
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
                retVal.seq = reader.readLine().toLowerCase();
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
     * Create a singleton read part from the current input.
     *
     * @param reader	buffer reader stream for input
     *
     * @return the read found, or NULL if we are at end of file
     */
    protected static Part readSingle(BufferedReader reader) throws IOException {
        Part retVal = null;
        // We need to read four lines:  label 1, sequence, label 2, quality.
        String line = reader.readLine();
        if (line != null) {
            // Here we have a record to read.
            retVal = new Part();
            // Get the FASTQ label.
            Matcher m = ID_PATTERN.matcher(line);
            if (! m.matches()) {
                if (line.isEmpty())
                    throw new IOException("Header record in FASTQ file is empty.");
                else
                    throw new IOException("Invalid header record in FASTQ file beginning with \"" + StringUtils.left(line, 15) + "\".");
            } else {
                // Get the FASTQ label.
                String label = m.group(1);
                retVal.label = label;
                retVal.reverse = false;
                // Now read the sequence.
                retVal.seq = reader.readLine().toLowerCase();
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
    public static class Part implements ISequence {

        /** sequence label */
        private String label;
        /** TRUE for a right (reverse) sequence, else FALSE */
        private boolean reverse;
        /** sequence string */
        private String seq;
        /** quality string */
        private String qual;

        /**
         * Construct a blank part.
         */
        public Part() {
            this.label = "";
            this.reverse = false;
            this.seq = "";
            this.qual = "";
        }

        /**
         * Construct a simple part.
         *
         * @param lbl	sequence label
         * @param sq	sequence string
         * @param q		quality string
         */
        public Part(String lbl, String sq, String q) {
            this.label = lbl;
            this.reverse = false;
            this.seq = sq;
            this.qual = q;
        }

        /**
         * @return the reverse complement of a part.
         *
         * @param part	part to reverse
         */
        public static Part reverse(Part fwd) {
            String sq = Contig.reverse(fwd.seq);
            String q = StringUtils.reverse(fwd.qual);
            Part retVal = new Part(fwd.label, sq, q);
            retVal.reverse = ! fwd.reverse;
            return retVal;
        }


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
         * @return the sequence string
         */
        public String getSequence() {
            return this.seq;
        }

        /**
         * @return the quality string
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

        /**
         * @return the length of this part's sequence
         */
        public int length() {
            return this.seq.length();
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
        result = prime * result + ((this.lqual == null) ? 0 : this.lqual.hashCode());
        result = prime * result + ((this.rseq == null) ? 0 : this.rseq.hashCode());
        result = prime * result + ((this.rqual == null) ? 0 : this.rqual.hashCode());
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
        if (this.lqual == null) {
            if (other.lqual != null)
                return false;
        } else if (! this.lqual.equals(other.lqual))
            return false;
        if (this.rseq == null) {
            if (other.rseq != null) {
                return false;
            }
        } else if (!this.rseq.equals(other.rseq)) {
            return false;
        }
        if (this.rqual == null) {
            if (other.rqual != null)
                return false;
        } else if (! this.rqual.equals(other.rqual))
            return false;
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

    /**
     * Compute the single sequence for this read.  If it is paired, find the overlap and merge the
     * sequences.
     *
     * @return a forward sequence for this read, consisting of the isolated forward part, the
     * 		   overlapping part, and the reverse-complement of the isolated backward part
     */
    public Part getSequence() {
        String seq;
        String qual;
        if (this.rseq.isEmpty()) {
            seq = this.lseq;
            qual = this.lqual;
        } else if (this.lseq.isEmpty()) {
            seq = this.rseq;
            qual = this.rqual;
        } else {
            // Get the left sequence.
            String nrmLeft = this.lseq;
            String lq = this.lqual;
            String revRight = Contig.reverse(this.rseq);
            String rq = StringUtils.reverse(this.rqual);
            // Get the lesser of the two lengths.
            int leftLen = nrmLeft.length();
            final int maxN = (leftLen < revRight.length() ? leftLen : revRight.length());
            // Now we find the overlap.  We score each possible overlap length, keeping the high score.  The
            // overlap score is +1 for each match, -1 for each mismatch, and 0 for each position with an
            // ambiguity character.
            int bestN = 0;
            int bestScore = 0;
            for (int n = minOverlap; n < maxN; n++) {
                // Score this overlap.
                int left0 = leftLen - n;
                int right0 = 0;
                int score = 0;
                for (int i = 0; i < n; i++)
                    score += this.score(nrmLeft.charAt(left0 + i), revRight.charAt(right0 + i));
                if (score > bestScore) {
                    bestN = n;
                    bestScore = score;
                }
            }
            // Now we stitch the pieces together.
            String right = revRight.substring(bestN);
            String rightq = rq.substring(bestN);
            if (bestN < minOverlap) {
                right = "x" + right;
                rightq = "!" + rightq;
            }
            seq = nrmLeft + right;
            qual = lq + rightq;
        }
        return new Part(this.label, seq, qual);
    }

    /**
     * @return the score for two DNA characters in an overlap alignment
     *
     * @param ch1	first character
     * @param ch2	aligned character
     */
    private int score(char ch1, char ch2) {
        int retVal;
        if (ch1 == ch2)
            retVal = 1;
        else if (VALID_NA.indexOf(ch1) < 0 || VALID_NA.indexOf(ch2) < 0)
            retVal = 0;
        else
            retVal = -1;
        return retVal;
    }

    /**
     * @return the coverage
     */
    public double getCoverage() {
        return this.coverage;
    }

    /**
     * Specify the coverage of the read.  This is only used if the read is actually a
     * contig.
     *
     * @param coverage 	the coverage to set
     */
    public void setCoverage(double coverage) {
        this.coverage = coverage;
    }

    /**
     * @return the left quality string
     */
    public String getLQual() {
        return this.lqual;
    }

    /**
     * @return the rightt quality string
     */
    public String getRQual() {
        return this.rqual;
    }

}
