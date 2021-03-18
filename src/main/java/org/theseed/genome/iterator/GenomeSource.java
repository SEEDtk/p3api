/**
 *
 */
package org.theseed.genome.iterator;

import java.io.File;
import java.io.IOException;

import org.theseed.genome.Genome;
import org.theseed.utils.ParseFailureException;

/**
 * This interface describes an iterator for returning genomes from a disk source.  This could be a file
 * of IDs for genomes to be read from a particular location (e.g. PATRIC) , or it could be a directory of GTOs, or
 * even a more complicated database directory.
 *
 * @author Bruce Parrello
 *
 */
public abstract class GenomeSource implements Iterable<Genome> {

    /**
     * Initialize the iteration from the specified disk location.
     *
     * @param inFile	disk location of the genomes (file or directory)
     *
     * @return the number of genomes at the location
     */
    public abstract int init(File inFile) throws IOException;

    /**
     * Validate the location.  If the location is invalid, an error will be thrown.
     *
     * @param inFile	disk location of the genomes (file or directory)
     *
     * @throws IOException
     * @throws ParseFailureException
     */
    public abstract void validate(File inFile) throws IOException, ParseFailureException;

    /**
     * @return the number of genomes at the source
     */
    public abstract int size();

    /**
     * Enumerator for genome source types
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
            return source;
        }

    }
}
