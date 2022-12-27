/**
 *
 */
package org.theseed.proteins.kmers.reps;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.theseed.genome.Genome;
import org.theseed.genome.Feature;
import org.theseed.proteins.Role;
import org.theseed.proteins.RoleMap;
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
    /** display name of the key protein */
    private String protName;
    /** all aliases of the key protein */
    private List<String> protAliases;
    /** similarity threshold for representation */
    private int threshold;
    /** mini role map for finding seed proteins (created as needed) */
    private RoleMap seedMap;
    /** dummy representative-genome object for outliers */
    private final RepGenome dummy = new RepGenome(null, "", "");

    /**
     * Construct an empty representative genome database for a specified key protein.
     *
     * @param threshold		kmer threshold to use
     * @param protNames		array of names for the key protein (if empty, the default will be used)
     */
    public RepGenomeDb(int threshold, String... protNames) {
        this.genomeMap = new HashMap<String, RepGenome>();
        this.kmerSize = ProteinKmers.kmerSize();
        if (protNames.length == 0) {
            this.protName = DEFAULT_PROTEIN;
            this.protAliases = List.of(DEFAULT_PROTEIN);
        } else {
            this.protName = protNames[0];
            this.protAliases = List.of(protNames);
        }
        this.threshold = threshold;
        this.seedMap = null;
    }

    /**
     * This is an iterator for RepGenomes that supports deletion in-stream.
     */
    public class Iter implements Iterator<RepGenome> {

        /** underlying iterator through the hash */
        private Iterator<Map.Entry<String, RepGenome>> iter;

        /**
         * Construct the iterator.
         */
        protected Iter() {
            this.iter = RepGenomeDb.this.genomeMap.entrySet().iterator();
        }

        @Override
        public boolean hasNext() {
            return this.iter.hasNext();
        }

        @Override
        public RepGenome next() {
            var entry = this.iter.next();
            return entry.getValue();
        }

        @Override
        public void remove() {
            this.iter.remove();
        }

    }

    /**
     * Return an iterator for traversing the representative genomes.
     */
    @Override
    public Iterator<RepGenome> iterator() {
        return this.new Iter();
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
        public Representation(String genomeId, int score, double distance) {
            this.similarity = score;
            this.distance = distance;
            this.representative = (genomeId == null ? dummy : genomeMap.get(genomeId));
        }

        /**
         * Create a representation for an incoming sequence.
         *
         * @param rep		representative-genome object
         * @param seq		kmers for the incoming sequence
         */
        public Representation(RepGenome rep, ProteinKmers seq) {
            this.representative = rep;
            this.similarity = rep.similarity(seq);
            this.distance = SequenceKmers.distance(this.similarity, rep, seq);
        }

        /**
         * Create a default representation.
         */
        public Representation() {
            this.representative = null;
            this.similarity = 0;
            this.distance = 1.0;
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
         * Merge a representation into this one, keeping the closest.
         *
         * This method needs to be deterministic, since the order of operations is random.
         * We prefer higher similarity, then lower distance, then longer protein, and
         * finally lexically earliest genome ID.
         *
         * @param otherRep	proposed new representation
         */
        private void merge(Representation other) {
            boolean merge = false;
            if (other.similarity > this.similarity)
                merge = true;
            else if (other.similarity == this.similarity) {
                if (other.distance < this.distance)
                    merge = true;
                else if (other.distance == this.distance) {
                    if (this.representative == null)
                        merge = true;
                    else if (other.representative != null) {
                        int myLen = this.representative.getProtein().length();
                        int otherLen = other.representative.getProtein().length();
                        if (otherLen > myLen)
                            merge = true;
                        else if (otherLen == myLen)
                            merge = (this.representative.getGenomeId()
                                    .compareTo(other.representative.getGenomeId()) < 0);
                    }
                }
            }
            if (merge) {
                this.representative = other.representative;
                this.similarity = other.similarity;
                this.distance = other.distance;
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
     * @return the full list of protein aliases
     */
    public List<String> getProtAliases() {
        return this.protAliases;
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
        boolean retVal = this.genomeMap.values().parallelStream()
                .anyMatch(x -> x.similarity(newSeq) >= sim);
        return retVal;
    }

    /**
     * Add a new representative genome to the database.
     *
     * @param newGenome	new genome to add
     */
    protected void addRep(RepGenome newGenome) {
        this.genomeMap.put(newGenome.getGenomeId(), newGenome);
    }

    /**
     * @return the closest representative genome to a specified key protein
     *
     * @param testSeq	protein kmer object containing the protein
     */
    public Representation findClosest(ProteinKmers testSeq) {
        // Loop through the representatives, looking for a match.  We do this in parallel.
        Representation retVal = this.genomeMap.values().parallelStream().map(x -> new Representation(x, testSeq))
            .collect(Representation::new, (x,r) -> x.merge(r), (x,y) -> x.merge(y));
        return retVal;
    }

    /**
     * @return a collection of acceptable representatives for a specific genome
     *
     * @param genome	genome to test
     */
    public Collection<Representation> findClose(Genome genome) {
        // Get the seed protein.
        var protKmers = this.getSeedProtein(genome);
        // Get all the good representatives.
        List<Representation> retVal = this.genomeMap.values().parallelStream().map(x -> new Representation(x, protKmers))
                .filter(x -> x.getSimilarity() >= this.threshold).collect(Collectors.toList());
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
        // Get all the representative-genome objects in a sorted array.
        RepGenome[] retVal = this.genomeMap.values().stream().sorted().toArray(RepGenome[]::new);
        // Return the result.
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
        String repDbComment = StringUtils.join(this.protAliases, " @ ");
        Sequence repSequence = new Sequence(repDbLabel, repDbComment, "");
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
        String[] protNames = StringUtils.splitByWholeSeparator(header.getComment(), " @ ");
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
            retVal = new RepGenomeDb(threshold, protNames);
        }
        // Now read in the representative genomes.
        retVal.putGenomes(reader);
        // All done.
        reader.close();
        return retVal;
    }


    /**
     * Put all the genomes identified by the incoming sequences into this database.
     *
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

    /**
     * This method returns the longest instance of the seed protein in the specified genome.
     *
     *  @param genome	the genome whose seed protein is desired
     *
     *  @return a RepGenome object for the genome, or NULL if there is no seed protein
     */
    public RepGenome getSeedProtein(Genome genome) {
        if (this.seedMap == null)
            this.seedMap = this.createSeedMap();
        // Now loop through the genome, searching for the best seed protein.
        String bestProt = "";
        String bestFid = "";
        for (Feature peg : genome.getPegs()) {
            if (peg.isInteresting(this.seedMap)) {
                // Here the peg contains the seed role.
                String prot = peg.getProteinTranslation();
                if (prot.length() > bestProt.length()) {
                    bestProt = prot;
                    bestFid = peg.getId();
                }
            }
        }
        // Now create the representative-genome object.
        RepGenome retVal = null;
        if (! bestProt.isEmpty())
            retVal = new RepGenome(bestFid, genome.getName(), bestProt);
        return retVal;
    }

    /**
     * @return a new role map created to find the seed protein
     */
    private RoleMap createSeedMap() {
        RoleMap retVal = new RoleMap();
        // Start with the default role name.
        Role seedRole = retVal.findOrInsert(this.protName);
        // If there is more than one alias, add the others.
        final int n = this.protAliases.size();
        if (n > 1) {
            String roleId = seedRole.getId();
            for (int i = 1; i < n; i++)
                retVal.addRole(roleId, this.protAliases.get(i));
        }
        return retVal;
    }

    @Override
    public String toString() {
        return String.format("RepDb%d(k=%d)", this.threshold, this.kmerSize);
    }

    /**
     * @return the list file name for this repgen set
     */
    public String getListFileName() {
        return String.format("rep%d.list.tbl", this.threshold);
    }

}
