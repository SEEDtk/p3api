/**
 *
 */
package org.theseed.genome.iterator;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import org.theseed.genome.Genome;
import org.theseed.p3api.P3Genome;
import org.theseed.utils.ParseFailureException;

/**
 * This interface describes an iterator for returning genomes from a disk source.  This could be a file
 * of IDs for genomes to be read from a particular location (e.g. PATRIC) , or it could be a directory of GTOs, or
 * even a more complicated database directory.
 *
 * The size of this set is not exact.  If a genome is not found that will not be known until it is retrieved.
 * Nonetheless, in a static environment (the skip-set does not change) it will produce an accurate estimate.
 *
 * @author Bruce Parrello
 *
 */
public abstract class GenomeSource implements Iterable<Genome> {

    // FIELDS
    /** set of genome IDs to skip */
    private Set<String> skipSet;
    /** desired detail level */
    private P3Genome.Details level;
    /** saved collection size */
    private int size;

    /**
     * Create a genome source.
     */
    public GenomeSource() {
        this.skipSet = Collections.emptySet();
        this.level = P3Genome.Details.FULL;
        this.size = -1;
    }

    /**
     * Initialize the iteration from the specified disk location.
     *
     * @param inFile	disk location of the genomes (file or directory)
     *
     * @return the number of genomes at the location
     */
    protected abstract int init(File inFile) throws IOException;

    /**
     * Validate the location.  If the location is invalid, an error will be thrown.
     *
     * @param inFile	disk location of the genomes (file or directory)
     *
     * @throws IOException
     * @throws ParseFailureException
     */
    protected abstract void validate(File inFile) throws IOException, ParseFailureException;

    /**
     * @return the list of genomes at the source
     */
    public abstract Set<String> getIDs();

    /**
     * @return the size of this set
     */
    public final int size() {
        return this.size;
    }

    /**
     * @return the full size of the genome set (before filtering)
     */
    public abstract int actualSize();

    /**
     * @return an iterator for the genome IDs of the source
     */
    protected abstract Iterator<String> getIdIterator();

    /**
     * @return the genome with the specified ID at the specified detail level, or NULL if it is not available
     *
     * @param genomeId	ID of the desired genome
     * @param level		detail level of the desired genome
     *
     * @throws IOException
     */
    protected abstract Genome getGenome(String genomeId, P3Genome.Details level);

    /**
     * @return the genome withbthe specified ID, or NULL if it is not available
     *
     * @param genomeId	ID of the desired genome
     */
    public Genome getGenome(String genomeId) {
        return this.getGenome(genomeId, P3Genome.Details.FULL);
    }

    /**
     * Specify a set of genome IDs to be skipped.
     *
     * @param skipSet	set of IDs for genomes to skip
     */
    public void setSkipSet(Set<String> skipSet) {
        this.skipSet = skipSet;
        // Recompute the set size.
        Set<String> idSet = this.getIDs();
        if (idSet.isEmpty())
            this.size = this.actualSize();
        else {
            this.size = 0;
            for (String id : idSet) {
                if (! this.skipSet.contains(id))
                    this.size++;
            }
        }
    }

    /**
     * Specify a detail level.  Not all sources allow customized detail levels, so this is
     * merely an advisory if an expensive load operation is needed.
     *
     * @param level		desired detail level
     */
    public void setDetailLevel(P3Genome.Details level) {
        this.level = level;
    }

    /**
     * @return an iterator for this genome source
     */
    public final Iterator<Genome> iterator() {
        return this.new Iter();
    }

    /**
     * This class manages the iteration.  It will ask the subclass for the next genome ID, and then
     * run it through the filter set before asking for the genome.
     */
    public class Iter implements Iterator<Genome> {

        /** iterator for genome IDs */
        private Iterator<String> idIter;
        /** next genome to return */
        private Genome nextGenome;

        /**
         * Construct an iterator for this genome source.
         */
        private Iter() {
            this.idIter = GenomeSource.this.getIdIterator();
            this.lookAhead();
        }

        /**
         * Find the next legitimate genome to return.
         */
        private void lookAhead() {
            this.nextGenome = null;
            while (idIter.hasNext() && this.nextGenome == null) {
                String nextId = idIter.next();
                if (! GenomeSource.this.skipSet.contains(nextId))
                    this.nextGenome = GenomeSource.this.getGenome(nextId,
                            GenomeSource.this.level);
            }
        }

        @Override
        public boolean hasNext() {
            return this.nextGenome != null;
        }

        @Override
        public Genome next() {
            Genome retVal = nextGenome;
            this.lookAhead();
            return retVal;
        }

    }

    /**
     * Enumeration for genome source types
     */
    public static enum Type {
        /** read from a master genome directory, which contains compressed GTOs in multiple directories */
        MASTER {
            @Override
            public GenomeSource create(File inFile) throws IOException, ParseFailureException {
                return this.setup(new MasterDirectorySource(), inFile);
            }
        },
        /** read from a standard GTO directory */
        DIR {
            @Override
            public GenomeSource create(File inFile) throws IOException, ParseFailureException {
                return this.setup(new NormalDirectorySource(), inFile);
            }
        },
        /** load from PATRIC, using genome IDs found in a file */
        PATRIC {
            @Override
            public GenomeSource create(File inFile) throws IOException, ParseFailureException {
                return this.setup(new PatricFileSource(), inFile);
            }
        };

        /**
         * Create and initialize the genome source.
         *
         * @param inFile 	file or directory containing the genome data
         *
         * @return the initialized genome source
         *
         * @throws ParseFailureException
         * @throws IOException
         */
        public abstract GenomeSource create(File inFile) throws IOException, ParseFailureException;

        /**
         * Set up and return a genome source.
         *
         * @param source	source to set up
         * @param inFile	input file
         *
         * @return the incoming genome source
         *
         * @throws ParseFailureException
         * @throws IOException
         */
        protected GenomeSource setup(GenomeSource source, File inFile) throws IOException, ParseFailureException {
            source.validate(inFile);
            source.init(inFile);
            source.size = source.actualSize();
            return source;
        }

    }

}
