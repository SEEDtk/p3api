/**
 *
 */
package org.theseed.genome.iterator;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.Set;

import org.theseed.genome.Genome;
import org.theseed.genome.GenomeMultiDirectory;
import org.theseed.p3api.P3Genome.Details;

/**
 * This class allows the client to iterate through a master genome directory.
 *
 * @author Bruce Parrello
 *
 */
public class MasterDirectorySource extends GenomeSource {

    // FIELDS
    /** master directory to iterate through */
    private GenomeMultiDirectory source;

    @Override
    protected int init(File inFile) {
        this.source = new GenomeMultiDirectory(inFile);
        return this.source.size();
    }

    @Override
    protected void validate(File inFile) throws FileNotFoundException {
        if (! inFile.isDirectory())
            throw new FileNotFoundException("Genome master directory " + inFile + " is not found or invalid.");
    }

    @Override
    public int actualSize() {
        return this.source.size();
    }

    @Override
    protected Iterator<String> getIdIterator() {
        return source.idIterator();
    }

    @Override
    protected Genome getGenome(String genomeId, Details level) {
        return this.source.get(genomeId);
    }

    @Override
    public Set<String> getIDs() {
        return this.source.getIDs();
    }

}
