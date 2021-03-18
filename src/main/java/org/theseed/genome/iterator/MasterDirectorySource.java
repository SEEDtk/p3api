/**
 *
 */
package org.theseed.genome.iterator;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Iterator;

import org.theseed.genome.Genome;
import org.theseed.genome.GenomeMultiDirectory;

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
    public Iterator<Genome> iterator() {
        return source.iterator();
    }

    @Override
    public int init(File inFile) {
        this.source = new GenomeMultiDirectory(inFile);
        return this.source.size();
    }

    @Override
    public void validate(File inFile) throws FileNotFoundException {
        if (! inFile.isDirectory())
            throw new FileNotFoundException("Genome master directory " + inFile + " is not found or invalid.");
    }

    @Override
    public int size() {
        return this.source.size();
    }

}
