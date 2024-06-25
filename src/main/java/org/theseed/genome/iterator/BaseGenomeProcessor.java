/**
 *
 */
package org.theseed.genome.iterator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Set;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.basic.BaseProcessor;
import org.theseed.basic.ParseFailureException;
import org.theseed.genome.Genome;
import org.theseed.p3api.P3Genome;

/**
 * This is a base class for commands that process a genome source.  It contains built-in options for
 * specifying the source and the source type.  The source itself is accessible through the
 * "getGenome" and "getGenomeIds" methods.
 *
 * The client can call the "setLevel" method to set the preferred detail level.  If the source does
 * not support multiple detail levels, this will have no effect, but it can greatly increase efficiency
 * when it is supported.  During validation, the "isAvailable" method can be used to determine if
 * a genome is present in the source.
 *
 * The first positional parameter is the genome source file or directory.
 *
 * The command-line option are as follows:
 *
 * -h	display command-line usage
 * -v	display more frequent log messages
 * -t	genome source type (default DIR)
 *
 * @author Bruce Parrello
 *
 */
public abstract class BaseGenomeProcessor extends BaseProcessor {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(BaseGenomeProcessor.class);
    /** input genome source */
    private GenomeSource source;
    /** set of genome IDs in the source */
    private Set<String> genomeIds;
    /** detail level for retrievals */
    private P3Genome.Details level;

    // COMMAND-LINE OPTIONS

    /** genome source type */
    @Option(name = "--sourceType", aliases = { "--type", "-t" }, usage = "genome source type")
    private GenomeSource.Type sourceType;

    /** input genome source file or directory */
    @Argument(index = 0, metaVar = "inDir", usage = "input genome source directory (or file)", required = true)
    private File inDir;

    @Override
    protected final void setDefaults() {
        this.sourceType = GenomeSource.Type.DIR;
        this.level = P3Genome.Details.FULL;
        this.setSourceDefaults();
    }

    /**
     * Specify the defaults for the command-line options.
     */
    protected abstract void setSourceDefaults();

    @Override
    protected final boolean validateParms() throws IOException, ParseFailureException {
        // Verify that the source exists.
        if (! this.inDir.exists())
            throw new FileNotFoundException("Input genome source " + this.inDir + " does not exist.");
        // Connect to the source.
        this.source = this.sourceType.create(this.inDir);
        log.info("{} genomes found in {} source {}.", this.source.size(), this.sourceType, this.inDir);
        // Save the genome IDs.
        this.genomeIds = this.source.getIDs();
        // Validate the other parameters.
        this.validateSourceParms();
        return true;
    }

    /**
     * Validate the subclass command-line options and arguments, and perform pre-run initialization.
     */
    protected abstract void validateSourceParms() throws IOException, ParseFailureException;

    /**
     * Specify a new detail level for the genomes.
     *
     * @param newLevel	new detail level to use
     */
    public void setLevel(P3Genome.Details newLevel) {
        this.level = newLevel;
    }

    /**
     * @return the ID set for the genome source
     */
    public Set<String> getGenomeIds() {
        return this.genomeIds;
    }

    /**
     * @return the genome with the specified ID
     *
     * @param genomeId	ID of the desired genome
     * @param level		minimum necessary detail level
     */
    public Genome getGenome(String genomeId) {
        return this.source.getGenome(genomeId, this.level);
    }

    /**
     * @return TRUE if a genome is present in the source, else FALSE
     *
     * @param genomeId	ID of the genome to check
     */
    public boolean isAvailable(String genomeId) {
        return this.genomeIds.contains(genomeId);
    }

    /**
     * @return the actual genome source
     */
    protected GenomeSource getSource() {
        return this.source;
    }


}
