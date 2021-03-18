/**
 *
 */
package org.theseed.genome.iterator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.genome.Genome;
import org.theseed.io.TabbedLineReader;
import org.theseed.p3api.Connection;
import org.theseed.p3api.P3Genome;
import org.theseed.utils.ParseFailureException;

/**
 * This genome source reads genome IDs from a file and loads them individually.  It should only be used for small files,
 * since large PATRIC processes neeed to be restartable.  A failure to load is processed as a fatal error.
 *
 * The input file must be tab-delimited with headers, and the genome IDs should be in the first column.
 *
 * @author Bruce Parrello
 *
 */
public class PatricFileSource extends GenomeSource {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(PatricFileSource.class);
    /** set of genome IDs */
    private SortedSet<String> genomeIDs;

    @Override
    public Iterator<Genome> iterator() {
        return this.new Iter();
    }

    @Override
    public int init(File inFile) throws IOException {
        // Load, then sort, the input file genome IDs.
        this.genomeIDs = new TreeSet<String>(TabbedLineReader.readSet(inFile, "1"));
        return this.genomeIDs.size();
    }

    @Override
    public void validate(File inFile) throws IOException, ParseFailureException {
        if (! inFile.canRead())
            throw new FileNotFoundException("Genome ID input file " + inFile + " is not found or unreadable.");
    }

    @Override
    public int size() {
        return this.genomeIDs.size();
    }

    /**
     * This is the actual iterator for this object's genomes.
     */
    public class Iter implements Iterator<Genome> {

        /** iterator for the genome IDs */
        private Iterator<String> genomeIdIter;
        /** PATRIC connection */
        private Connection p3;

        /**
         * Construct this iterator.
         */
        private Iter() {
            // Connect to PATRIC.
            this.p3 = new Connection();
            // Iterate through the full list of genome IDs.
            this.genomeIdIter = PatricFileSource.this.genomeIDs.iterator();
        }

        @Override
        public boolean hasNext() {
            return this.genomeIdIter.hasNext();
        }

        @Override
        public Genome next() {
            String genomeId = this.genomeIdIter.next();
            P3Genome retVal = P3Genome.load(p3, genomeId, P3Genome.Details.FULL);
            if (retVal == null)
                throw new IllegalArgumentException("Genome " + genomeId + " was not found in PATRIC.");
            return retVal;
        }

    }

}
