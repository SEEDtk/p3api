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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.genome.Genome;
import org.theseed.genome.core.CoreGenome;
import org.theseed.genome.core.OrganismDirectories;
import org.theseed.p3api.P3Connection;
import org.theseed.p3api.P3Genome.Details;
import org.theseed.utils.ParseFailureException;

/**
 * This genome source loads genomes from a CoreSEED directory.  It iterates through the genomes in the Organism subdirectory.
 *
 * @author Bruce Parrello
 *
 */
public class CoreInputDirectory extends GenomeSource {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(CoreInputDirectory.class);
    // organism directory handler
    private OrganismDirectories orgDir;
    // base directory name
    private File rootDir;
    // connection to PATRIC and NCBI
    private P3Connection p3;

    @Override
    protected int init(File inFile) throws IOException {
        this.p3 = new P3Connection();
        this.rootDir = inFile;
        this.orgDir = new OrganismDirectories(new File(inFile, "Organisms"));
        return this.orgDir.size();
    }

    @Override
    protected void validate(File inFile) throws IOException, ParseFailureException {
        File orgBase = new File(inFile, "Organisms");
        if (! orgBase.isDirectory())
            throw new FileNotFoundException(inFile + " does not appear to be a SEED data directory.");
    }

    @Override
    public Set<String> getIDs() {
        return this.orgDir.getIDs();
    }

    @Override
    public int actualSize() {
        return this.orgDir.size();
    }

    @Override
    protected Iterator<String> getIdIterator() {
        return this.orgDir.getIDs().iterator();
    }

    @Override
    protected Genome getGenome(String genomeId, Details level) {
        Genome retVal = null;
        File gFile = this.orgDir.getDir(genomeId);
        if (gFile != null) {
            try {
                retVal = new CoreGenome(this.p3, gFile);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return retVal;
    }

    @Override
    public String toString() {
        return "CoreSEED directory at " + this.rootDir;
    }

}
