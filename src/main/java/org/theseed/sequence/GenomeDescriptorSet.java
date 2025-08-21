/**
 *
 */
package org.theseed.sequence;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.basic.ParseFailureException;
import org.theseed.genome.Genome;

/**
 * This class implements a set of genome descriptors.  Its primary function is to provide functionality for
 * finding the closest genome in the set.
 *
 * @author Bruce Parrello
 *
 */
public class GenomeDescriptorSet implements Iterable<GenomeDescriptor> {

    // FIELDS
    /** logging facility */
    private static final Logger log = LoggerFactory.getLogger(GenomeDescriptorSet.class);
    /** similarity-based measures */
    public static int[] SIM_TYPES = new int[] { FinderType.SEED_SIMILARITY.ordinal(), FinderType.RNA_SIMILARITY.ordinal() };
    /** distance-based measures */
    public static int[] DIST_TYPES = new int[] { FinderType.SEED_DISTANCE.ordinal(), FinderType.RNA_DISTANCE.ordinal() };
    /** seed-based measures */
    public static int[] SEED_TYPES = new int[] { FinderType.SEED_SIMILARITY.ordinal(), FinderType.SEED_DISTANCE.ordinal() };
    /** map of genome IDs to descriptors */
    private SortedMap<String, GenomeDescriptor> idMap;

    /**
     * Construct an empty genome descriptor set.
     */
    public GenomeDescriptorSet() {
        init();
    }

    /**
     * Initialize a genome descriptor set.
     */
    private void init() {
        this.idMap = new TreeMap<String, GenomeDescriptor>();
    }

    /**
     * Construct a genome descriptor set from a four-column table.
     *
     * @param inFile	file containing the four-column table
     */
    public GenomeDescriptorSet(File inFile) throws IOException {
        init();
        try (GenomeDescriptor.FileIter iter = new GenomeDescriptor.FileIter(inFile)) {
            while (iter.hasNext()) {
                GenomeDescriptor newGenome = iter.next();
                this.add(newGenome);
            }
        }
        log.info("{} genomes read from {}.", this.size(), inFile);
    }

    /**
     * Add a genome to this set.
     *
     * @param genome	genome to add
     *
     * @throws ParseFailureException
     */
    public void add(Genome genome) throws ParseFailureException {
        GenomeDescriptor newGenome = new GenomeDescriptor(genome);
        this.idMap.put(newGenome.getId(), newGenome);
    }

    /**
     * This enum determines the types of comparisons that can be made between genomes.  All of these are converted into
     * a "proximity" rating.  A higher proximity means the genomes are closer.
     *
     * @author Bruce Parrello
     *
     */
    public static enum FinderType {
        SEED_SIMILARITY {
            @Override
            public double getProximity(GenomeDescriptor testGenome, GenomeDescriptor refGenome) {
                return (double) testGenome.getSeedSim(refGenome);
            }

            @Override
            public String label() {
                return "seed_sim";
            }
        }, RNA_SIMILARITY {
            @Override
            public double getProximity(GenomeDescriptor testGenome, GenomeDescriptor refGenome) {
                return (double) testGenome.getRnaSim(refGenome);
            }

            @Override
            public String label() {
                return "rna_sim";
            }
        }, SEED_DISTANCE {
            @Override
            public double getProximity(GenomeDescriptor testGenome, GenomeDescriptor refGenome) {
                return 1.0 - testGenome.getSeedDistance(refGenome);
            }

            @Override
            public String label() {
                return "seed_distance";
            }
        }, RNA_DISTANCE {
            @Override
            public double getProximity(GenomeDescriptor testGenome, GenomeDescriptor refGenome) {
                return 1.0 - testGenome.getRnaDistance(refGenome);
            }

            @Override
            public String label() {
                return "rna_distance";
            }
        };

        /**
         * @return the proximity rating of a test genome and a reference genome
         *
         * @param testGenome	test genome's descriptor
         * @param refGenome		reference genome's descriptor
         */
        public abstract double getProximity(GenomeDescriptor testGenome, GenomeDescriptor refGenome);

        /**
         * @return a descriptor string for this type, suitable for column headers
         */
        public abstract String label();
    }

    /**
     * This class represents the results of an attempt to find the closest genome in the set.
     * It contains the close genome and the proximity measure.
     */
    public static class Rating implements Comparable<Rating> {

        private GenomeDescriptor genome;
        private double proximity;

        /**
         * Construct an empty close-genome result.
         */
        public Rating() {
            this.genome = null;
            this.proximity = 0.0;
        }

        /**
         * The closest genome (highest proximity) always sorts first.
         */
        @Override
        public int compareTo(Rating o) {
            int retVal = Double.compare(o.proximity, this.proximity);
            if (retVal == 0)
                retVal = this.genome.compareTo(o.genome);
            return retVal;
        }

        /**
         * Update this result with the new genome if it's closer.  If it is the same distance,
         * we arbitrarily pick the one with the lower genome ID.  This increases the odds that
         * different measures will produce the same result.
         *
         * @param testGenome	descriptor of new genome
         * @param newRefGenome	descriptor of relevant reference genome
         * @param type			type of proximity measure
         *
         * @return TRUE if the new genome is closer
         */
        protected boolean check(GenomeDescriptor testGenome, GenomeDescriptor newRefGenome, FinderType type) {
            double newProx = type.getProximity(testGenome, newRefGenome);
            // This is set to TRUE if the new genome is better.
            boolean retVal;
            if (newProx < this.proximity)
                retVal = false;
            else if (newProx > this.proximity)
                retVal = true;
            else if (this.genome == null)
                retVal = true;
            else if (this.genome.compareTo(newRefGenome) > 0)
                retVal = true;
            else
                retVal = false;
            if (retVal) {
                this.proximity = newProx;
                this.genome = newRefGenome;
            }
            return retVal;
        }

        /**
         * @return a tab-delimited output string for this result
         */
        public String output() {
            StringBuilder retVal = new StringBuilder(40);
            if (this.genome != null) {
                retVal.append(this.genome.getId());
            }
            retVal.append("\t");
            retVal.append(String.format("%8.4f", this.proximity));
            return retVal.toString();
        }

        /**
         * @return TRUE if this result has the same genome as another result
         *
         * @param other		other result to compare
         */
        public boolean isSameGenome(Rating other) {
            boolean retVal;
            if (this.genome == null)
                retVal = (other.genome == null);
            else if (other.genome == null)
                retVal = false;
            else
                retVal = (this.genome.getId().contentEquals(other.genome.getId()));
            return retVal;
        }

        /**
         * @return TRUE if all the close genomes in a collection are the same, else FALSE
         *
         * @param results		a list of the results for all the types
         * @param positions		an array of the relevant positions in the list
         */
        public static boolean test(List<Rating> results, int[] positions) {
            boolean retVal = true;
            Rating result0 = results.get(positions[0]);
            for (int i = 1; i < positions.length && retVal; i++) {
                retVal = result0.isSameGenome(results.get(positions[i]));
            }
            return retVal;
        }

        /**
         * @return the proximity
         */
        public double getProximity() {
            return this.proximity;
        }

        /**
         * @return the genome ID of the close genome
         */
        public String getGenomeId() {
            String retVal;
            if (this.genome == null)
                retVal = "";
            else
                retVal = this.genome.getId();
            return retVal;
        }

        /**
         * @return the name of the close genome
         */
        public String getGenomeName() {
            String retVal;
            if (this.genome == null)
                retVal = "(none)";
            else
                retVal = this.genome.getName();
            return retVal;
        }

        /**
         * @return the descriptor of the close genome
         */
        public GenomeDescriptor getGenome() {
            return this.genome;
        }

        /**
         * @return the better of this rating and another rating
         *
         * @param x2	other rating to compare
         */
        protected void merge(Rating x2) {
            boolean merge;
            if (this.proximity > x2.proximity)
                merge = false;
            else if (this.proximity < x2.proximity)
                merge = true;
            else if (this.genome == null)
                merge = true;
            else if (x2.genome == null)
                merge = false;
            else if (this.genome.compareTo(x2.genome) > 0)
                merge = true;
            else
                merge = false;
            if (merge) {
                this.proximity = x2.proximity;
                this.genome = x2.genome;
            }
        }

    }

    /**
     * Find the closest genome to the specified testing genome according to the specified finder type.
     *
     * @param genome	descriptor for the testing genome
     * @param type		type of proximity criterion to use
     *
     * @return a close-genome object describing the result
     */
    public Rating findClosest(GenomeDescriptor testGenome, FinderType type) {
        // Use a parallel search to find the best match.
        Rating retVal = this.idMap.values().parallelStream()
                .collect(Rating::new, (x,g) -> x.check(testGenome, g, type),
                (x1,x2) -> x1.merge(x2));
        return retVal;
    }

    /**
     * @return the number of genomes described by this set
     */
    public int size() {
        return this.idMap.size();
    }

    @Override
    public Iterator<GenomeDescriptor> iterator() {
        return this.idMap.values().iterator();
    }

    /**
     * Add a new genome descriptor to this set.
     *
     * @param e		new descriptor to add
     *
     * @return TRUE if the genome is truly new, else FALSE
     */
    public boolean add(GenomeDescriptor e) {
        boolean retVal = false;
        String genomeId = e.getId();
        if (! this.idMap.containsKey(genomeId)) {
            this.idMap.put(genomeId, e);
            retVal = true;
        }
        return retVal;
    }

    /**
     * Remove everything from this set.
     */
    public void clear() {
        this.idMap.clear();

    }

    /**
     * @return the name of the specified genome
     *
     * @param repId		genome of interest (must be in the set)
     */
    public String getName(String repId) {
        GenomeDescriptor desc = this.idMap.get(repId);
        String retVal;
        if (desc == null)
            retVal = "Unknown genome " + repId;
        else
            retVal = desc.getName();
        return retVal;
    }

    /**
     * @return an iterator through this set for all genomes with an ID higher than the one given
     *
     * @param genomeId	ID to use as the starting point
     */
    public Iterator<GenomeDescriptor> tailIter(String genomeId) {
        SortedMap<String, GenomeDescriptor> tailView = this.idMap.tailMap(genomeId);
        Iterator<GenomeDescriptor> retVal = tailView.values().iterator();
        // Skip past the identified genome, if it's in there.
        if (tailView.containsKey(genomeId))
            retVal.next();
        return retVal;
    }

}
