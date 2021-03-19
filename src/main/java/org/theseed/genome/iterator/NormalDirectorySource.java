/**
 *
 */
package org.theseed.genome.iterator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

import org.theseed.genome.Genome;
import org.theseed.genome.GenomeDirectory;
import org.theseed.p3api.P3Genome.Details;
import org.theseed.utils.ParseFailureException;

/**
 * This allows the client to iterate over a standard single-level genome directory.
 *
 * @author Bruce Parrello
 *
 */
public class NormalDirectorySource extends GenomeSource {

    // FIELDS
    /** genome directory to iterate over */
    private GenomeDirectory source;

    @Override
    public int init(File inFile) throws IOException {
        this.source = new GenomeDirectory(inFile);
        return this.source.size();
    }

    @Override
    public void validate(File inFile) throws IOException, ParseFailureException {
        if (! inFile.isDirectory())
            throw new FileNotFoundException("Genome directory " + inFile + " is not found or invalid.");
    }

    @Override
    public int actualSize() {
        return this.source.size();
    }

    @Override
    protected Iterator<String> getIdIterator() {
        return this.source.getGenomeIDs().iterator();
    }

    @Override
    protected Genome getGenome(String genomeId, Details level) {
        return this.source.getGenome(genomeId);
    }

    @Override
    protected Set<String> getIDs() {
        return source.getGenomeIDs();
    }


}
