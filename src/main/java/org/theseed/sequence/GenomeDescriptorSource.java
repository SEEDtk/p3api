/**
 *
 */
package org.theseed.sequence;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import org.theseed.genome.Genome;
import org.theseed.genome.iterator.GenomeSource;
import org.theseed.utils.ParseFailureException;

/**
 * This class presents the client with a list of genome descriptors from a genome source.
 *
 * @author Bruce Parrello
 *
 */
public class GenomeDescriptorSource implements Iterable<GenomeDescriptor> {

    // FIELDS
    /** genome source */
    private GenomeSource genomes;

    /**
     * Create a genome descriptor iterator from the specified genome source.
     *
     * @param sourceFile	file or directory containing the genomes
     * @param type			genome source type (MASTER, DIR, PATRIC)
     *
     * @throws ParseFailureException
     * @throws IOException
     */
    public GenomeDescriptorSource(File sourceFile, GenomeSource.Type type)
            throws IOException, ParseFailureException {
        this.genomes = type.create(sourceFile);
    }

    @Override
    public Iterator<GenomeDescriptor> iterator() {
        return this.new Iter();
    }

    /**
     * This class is the actual iterator through the genome descriptors.
     */
    public class Iter implements Iterator<GenomeDescriptor> {

        /** iterator for the underlying genome source */
        private Iterator<Genome> genomeIter;

        /**
         * Create the iterator.
         */
        public Iter() {
            this.genomeIter = GenomeDescriptorSource.this.genomes.iterator();
        }

        @Override
        public boolean hasNext() {
            return this.genomeIter.hasNext();
        }

        @Override
        public GenomeDescriptor next() {
            Genome genome = this.genomeIter.next();
            GenomeDescriptor retVal;
            try {
                retVal = new GenomeDescriptor(genome);
            } catch (ParseFailureException e) {
                throw new RuntimeException(e);
            }
            return retVal;
        }

    }

}
