/**
 *
 */
package org.theseed.genome.iterator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;

import org.theseed.genome.Genome;
import org.theseed.genome.GenomeDirectory;
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
    public Iterator<Genome> iterator() {
        return this.source.iterator();
    }

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
    public int size() {
        return this.source.size();
    }


}
