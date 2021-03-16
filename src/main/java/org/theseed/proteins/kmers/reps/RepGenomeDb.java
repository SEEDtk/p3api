/**
 *
 */
package org.theseed.proteins.kmers.reps;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.theseed.sequence.FastaInputStream;
import org.theseed.sequence.FastaOutputStream;
import org.theseed.sequence.ProteinKmers;
import org.theseed.sequence.Sequence;
import org.theseed.sequence.SequenceKmers;

/**
 * This class manages a set of representative genomes.  For each representative genome, it contains a
 * full RepGenome object.  The objects are hashed by genome ID.
 *
 * Each representative-genome database is associated with a key protein and a similarity threshold.  The
 * key protein is normally represented by a single role name, but it may be multiple role names separated
 * by tabs. Currently, this information is only stored in the object for documentation purposes.
 *
 * The threshold is more important.  A representative genome must have a similarity score less than the
 * threshold with each other representative genome.  We construe the representative genomes so that every
 * good genome in the main genome database is closer than the threshold to at list one representative
 * genome.
 *
 * This object can be saved and loaded to a file, though it does not use standard serialization.
 *
 * @author Bruce Parrello
 *
 */
public class RepGenomeDb implements Iterable<RepGenome> {


    /** name of the role for the default key protein */
    public static final String DEFAULT_PROTEIN = "Phenylalanyl-tRNA synthetase alpha chain";

    /** pattern for parsing the savefile header */
    private static final Pattern HEADER_PATTERN = Pattern.compile("Rep(\\d+),K=(\\d+)");

    // FIELDS
    /** map from genome ID to RepGenome objects */
    private HashMap<String, RepGenome> genomeMap;
    /** kmer size used to create this database */
    private int kmerSize;
    /** name of the key protein */
    private String protName;
    /** similarity threshold for representation */
    private int threshold;
    /** dummy representative-genome object for outliers */
    private final RepGenome dummy = new RepGenome(null, "", "");

    /**
     * Construct a blank, empty representative-genome database.
     */
    public RepGenomeDb(int threshold) {
        this.genomeMap = new HashMap<String, RepGenome>();
        this.kmerSize = ProteinKmers.kmerSize();
        this.protName = DEFAULT_PROTEIN;
        this.threshold = threshold;
    }

    /**
     * Construct an empty representative genome database for a specified key protein.
     */
    public RepGenomeDb(int threshold, String protName) {
        this.genomeMap = new HashMap<String, RepGenome>();
        this.kmerSize = ProteinKmers.kmerSize();
        this.protName = protName;
        this.threshold = threshold;
    }

    /**
     * Return an iterator for traversing the representative genomes.
     */
    @Override
    public Iterator<RepGenome> iterator() {
        Collection<RepGenome> allReps = this.genomeMap.values();
        return allReps.iterator();
    }

    /**
     * Object used to denote how a genome is represented.  It contains the similarity
     * score and the representative-genome object of the closest representative.  If
     * no representative is close, the latter value is a special dummy object.
     */
    public class Representation {

        // FIELDS
        private int similarity;
        private double distance;
        private RepGenome representative;

        /** Denote that the identified genome is the representative with the specified score.
         *
         * @param genomeId	representative genome's ID (or NULL if there is no representative)
         * @param score		similarity score
         */
        protected Representation(String genomeId, int score, double distance) {
            this.similarity = score;
            this.distance = distance;
            this.representative = (genomeId == null ? dummy : genomeMap.get(genomeId));
        }

        /**
         * @return the similarity score
         */
        public int getSimilarity() {
            return this.similarity;
        }

        /**
         * @return the representative genome, or NULL if there is none
         */
        public RepGenome getRepresentative() {
            return this.representative;
        }

        /**
         * If the specified score is higher than this object's current score,
         * store it and the specified genome.
         *
         * @param representative	proposed new representative
         * @param score				similarity score for it
         * @param other				other protein's kmer object
         */
        private void Merge(RepGenome representative, int score, ProteinKmers other) {
            if (score > this.similarity) {
                this.representative = representative;
                this.similarity = score;
                if (score == SequenceKmers.INFINITY) {
                    this.distance = 0.0;
                } else if (score == 0) {
                    this.distance = 1.0;
                } else {
                    double union = (representative.size() + other.size()) - score;
                    this.distance = 1.0 - similarity / union;
                }
            }
        }

        /**
         * @return the ID of the representing genome, or NULL if there is none
         */
        public String getGenomeId() {
            String retVal = null;
            if (this.representative != null) {
                retVal = this.representative.getGenomeId();
            }
            return retVal;
        }

        /**
         * @return TRUE if this object indicates the genome is represented, that is,
         * 		   the similarity score is higher that the threshold
         */
        public boolean isRepresented() {
            return this.similarity >= threshold;
        }

        /**
         * @return TRUE if this object indicates the genome is an extreme outlier, that
         * 		   is, it has nothing in common with any representative
         */
        public boolean isExtreme() {
            return this.similarity == 0;
        }

        /**
         * @return the distance to the representative
         */
        public double getDistance() {
            return distance;
        }

    }


    /**
     * @return the kmer size used to check similarity
     */
    public int getKmerSize() {
        return this.kmerSize;
    }

    /**
     * @return the name of the key protein
     */
    public String getProtName() {
        return this.protName;
    }

    /**
     * @return the similarity threshold
     */
    public int getThreshold() {
        return this.threshold;
    }

    /**
     * Process a list of sequences to add genomes to this database.  Each sequence should have the key protein feature
     * ID as its label, the genome name as its comment, and the key protein sequence as its sequence.
     *
     * @param fastaStream	an iterable set of sequences to process
     *
     * @return the number of new representatives found
     */
    public int addGenomes(Iterable<Sequence> fastaStream) {
        int retVal = 0;
        for (Sequence seqObject : fastaStream) {
            RepGenome newGenome = new RepGenome(seqObject.getLabel(), seqObject.getComment(), seqObject.getSequence());
            // Loop through the current representatives, searching for the best score.
            if (! this.checkSimilarity(newGenome, this.threshold)) {
                // Here we have a new representative.
                this.addRep(newGenome);
                retVal++;
            }
        }
        return retVal;
    }


    /**
     * @return TRUE if the specified sequence has a similarity score of the specified
     * 		   level or higher with at least one genome in this database, else FALSE
     *
     * @param newSeq	sequence to check
     * @param sim		similarity score to use
     */
    public boolean checkSimilarity(ProteinKmers newSeq, int sim) {
        Iterator<RepGenome> iter = this.iterator();
        int simFound = 0;
        while (iter.hasNext() && simFound < sim) {
            simFound = iter.next().similarity(newSeq);
        }
        return (simFound >= sim);
    }

    /**
     * Add a new representative genome to the database.
     *
     * @param newGenome	new genome to add
     */
    /* package */ void addRep(RepGenome newGenome) {
        this.genomeMap.put(newGenome.getGenomeId(), newGenome);
    }

    /**
     * @return the closest representative genome to a specified key protein
     *
     * @param testSeq	protein kmer object containing the protein
     */
    public Representation findClosest(ProteinKmers testSeq) {
        // Assume there is no representative.
        Representation retVal = new Representation(null, 0, 1.0);
        // Loop through the representatives, looking for a match.
        for (RepGenome rep : this) {
            int newScore = rep.similarity(testSeq);
            retVal.Merge(rep, newScore, testSeq);
        }
        return retVal;
    }

    /**
     * @return the closest representative genome to a specified key protein
     *
     * @param sequence	the sequence of the specified protein
     */
    public Representation findClosest(String sequence) {
        ProteinKmers testSeq = new ProteinKmers(sequence);
        return findClosest(testSeq);
    }

    /**
     * @return the closest representative genome to a specified key protein
     *
     * @param sequence	the sequence of the specified protein
     */
    public Representation findClosest(Sequence sequence) {
        ProteinKmers testSeq = new ProteinKmers(sequence.getSequence());
        return findClosest(testSeq);
    }

    /**
     * @return an array of this database's representative genome objects
     */
    public RepGenome[] all() {
        // Get all the representative-genome objects.
        Collection<RepGenome> allReps = this.genomeMap.values();
        // Create an array big enough to hold them.
        RepGenome[] retVal = new RepGenome[allReps.size()];
        // Fill the array.  Note that because we set the size properly, it will not
        // re-allocate.
        retVal = allReps.toArray(retVal);
        return retVal;
    }

    /**
     * @return a sorted list of this database's representative genome objects
     */
    public SortedSet<RepGenome> sorted() {
        return new TreeSet<RepGenome>(this.genomeMap.values());
    }

    /**
     * @return the number of representative genomes in this database
     */
    public int size() {
        return this.genomeMap.size();
    }

    /**
     * @return the specified representative genome, or NULL if it is not in this database
     *
     * @param genomeId	ID of the genome to find
     */
    public RepGenome get(String genomeId) {
        return this.genomeMap.get(genomeId);
    }

    /**
     * Save this database to a file.  The file is in FASTA format for easy manipulation
     * by other languages.  The first record is a header with no sequence information.
     *
     * @param saveFile	file to contain the database
     * @throws IOException
     */
    public void save(File saveFile) throws IOException {
        FastaOutputStream writer = new FastaOutputStream(saveFile);
        // Start with the basic parameters.
        String repDbLabel = String.format("Rep%d,K=%d", this.threshold, this.kmerSize);
        Sequence repSequence = new Sequence(repDbLabel, this.protName, "");
        writer.write(repSequence);
        // Now write the proteins.
        for (RepGenome rep : this) {
            repSequence = rep.toSequence();
            writer.write(repSequence);
        }
        // Close the stream.
        writer.close();
    }

    /**
     * @return a representative-genome database loaded from a file
     *
     * @param loadFile	file from which to load the database
     * @throws IOException
     */
    public static RepGenomeDb load(File loadFile) throws IOException {
        RepGenomeDb retVal = null;
        FastaInputStream reader = new FastaInputStream(loadFile);
        // Read the basic parameters.
        Sequence header = reader.next();
        String protName = header.getComment();
        Matcher m = HEADER_PATTERN.matcher(header.getLabel());
        if (! m.matches()) {
            // Here we have an invalid header.  This is not a real load file.
            reader.close();
            throw new IOException("Invalid header in repGenome file.");
        } else {
            int threshold = Integer.valueOf(m.group(1));
            int kmerSize = Integer.valueOf(m.group(2));
            // Create the new database.  Note we have to update the global kmer size.
            ProteinKmers.setKmerSize(kmerSize);
            retVal = new RepGenomeDb(threshold, protName);
        }
        // Now read in the representative genomes.
        retVal.putGenomes(reader);
        // All done.
        reader.close();
        return retVal;
    }


    /**
     * Put all the genomes identified by the incoming sequences into this database.
     * @param sequences
     */
    private void putGenomes(Iterable<Sequence> sequences) {
        for (Sequence seq : sequences) {
            RepGenome seqRep = new RepGenome(seq);
            this.addRep(seqRep);
        }
    }

    /** Determine whether or not this genome belongs in the database, and add it if it does.
     *
     * @param newGenome	genome to potentially add
     *
     * @return TRUE if the genome was added, else FALSE
     */
    public boolean checkGenome(RepGenome newGenome) {
        boolean retVal = false;
        // Loop through the current representatives, searching for the best score.
        if (! this.checkSimilarity(newGenome, this.threshold)) {
            // Here we have a new representative.
            this.addRep(newGenome);
            retVal = true;
        }
        return retVal;
    }

    /**
     * @return TRUE if the specified sequence has a similarity score of the specified
     * 		   level or higher with at least one genome in this database, else FALSE
     *
     * @param inSeq	sequence to check
     * @param sim		similarity score to use
     */
    public boolean checkSimilarity(Sequence inSeq, int sim) {
        ProteinKmers kmerThing = new ProteinKmers(inSeq.getSequence());
        return checkSimilarity(kmerThing, sim);
    }

}
