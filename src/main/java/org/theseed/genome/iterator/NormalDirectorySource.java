/**
 *
 */
package org.theseed.genome.iterator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.basic.ParseFailureException;
import org.theseed.genome.Genome;
import org.theseed.genome.GenomeDirectory;
import org.theseed.p3api.P3Genome.Details;

/**
 * This allows the client to iterate over or store into a standard single-level genome directory.
 *
 * @author Bruce Parrello
 *
 */
public class NormalDirectorySource extends GenomeSource implements IGenomeTarget {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(NormalDirectorySource.class);
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
    public Genome getGenome(String genomeId, Details level) {
        Genome retVal = null;
        if (this.source.contains(genomeId))
            retVal = this.source.getGenome(genomeId);
        return retVal;
    }

    @Override
    public Set<String> getIDs() {
        return source.getGenomeIDs();
    }

    @Override
    public boolean contains(String genomeId) {
        return this.source.contains(genomeId);
    }

    @Override
    public void add(Genome genome) throws IOException {
        this.source.store(genome);
    }

    /**
     * Create a normal genome directory, optionally clearing existing files.
     *
     * @param directory		file name of directory
     * @param clearFlag		TRUE to erase existing files
     *
     * @return the genome directory source created
     *
     * @throws IOException
     */
    public static NormalDirectorySource create(File directory, boolean clearFlag) throws IOException {
        if (! directory.isDirectory()) {
            log.info("Creating new genome directory {}.", directory);
            FileUtils.forceMkdir(directory);
        } else if (clearFlag) {
            log.info("Erasing genome directory {}.", directory);
            FileUtils.cleanDirectory(directory);
        }
        NormalDirectorySource retVal = new NormalDirectorySource();
        retVal.init(directory);
        return retVal;
    }

    @Override
    public void finish() {
    }

    @Override
    public String toString() {
        return "GTO Directory " + this.source.getName();
    }

    @Override
    public void remove(String genomeId) throws IOException {
        this.source.remove(genomeId);
    }

    @Override
    public boolean canDelete() {
        return true;
    }

    @Override
    public Set<String> getGenomeIDs() {
        return new TreeSet<>(this.source.getGenomeIDs());
    }


}
