/**
 *
 */
package org.theseed.genome.iterator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.theseed.basic.ParseFailureException;
import org.theseed.genome.Genome;
import org.theseed.io.TabbedLineReader;
import org.theseed.p3api.P3CursorConnection;
import org.theseed.p3api.P3Genome;
import org.theseed.p3api.P3Genome.Details;

/**
 * This genome source reads genome IDs from a file and loads them individually.  It should only be used for small files,
 * since large PATRIC processes neeed to be restartable.  A failure to load is processed as a fatal error.
 * A version of this class that is restartable and allows detail tuning is available using the GenomeSource.Type.PATRIC
 * code path.
 *
 * The input file must be tab-delimited with headers, and the genome IDs should be in the first column.
 *
 * @author Bruce Parrello
 *
 */
public class PatricFileSource extends GenomeSource {

    // FIELDS
    /** set of genome IDs */
    private SortedSet<String> genomeIDs;
    /** PATRIC connection */
    private P3CursorConnection p3;

    @Override
    public int init(File inFile) throws IOException {
        // Connect to PATRIC.
        this.p3 = new P3CursorConnection();
        // Load, then sort, the input file genome IDs.
        this.genomeIDs = new TreeSet<>(TabbedLineReader.readSet(inFile, "1"));
        return this.genomeIDs.size();
    }

    @Override
    public void validate(File inFile) throws IOException, ParseFailureException {
        if (! inFile.canRead())
            throw new FileNotFoundException("Genome ID input file " + inFile + " is not found or unreadable.");
    }

    @Override
    public int actualSize() {
        return this.genomeIDs.size();
    }

    @Override
    protected Iterator<String> getIdIterator() {
        return this.genomeIDs.iterator();
    }

    @Override
    public Genome getGenome(String genomeId, Details level) {
        P3Genome retVal;
        try {
            retVal = P3Genome.load(this.p3, genomeId, P3Genome.Details.FULL);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return retVal;
    }

    @Override
    public Set<String> getIDs() {
        return this.genomeIDs;
    }

}
