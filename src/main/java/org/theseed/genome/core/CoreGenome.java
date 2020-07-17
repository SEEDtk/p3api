/**
 *
 */
package org.theseed.genome.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.genome.Genome;
import org.theseed.io.MarkerFile;
import org.theseed.p3api.Connection;

/**
 * This class constructs a genome from a CoreSEED genome directory.
 *
 * @author Bruce Parrello
 *
 */
public class CoreGenome extends Genome {

    // FIELDS

    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(CoreGenome.class);

    /** list of valid domains */
    public static final Set<String> DOMAINS = (Set<String>) Stream.of("Bacteria", "Archaea", "Eukaryota", "Virus")
              .collect(Collectors.toCollection(HashSet::new));

    /** organism directory */
    private File orgDir;

    /**
     * Construct a coreSEED genome from a genome directory.
     *
     * @param p3		connection to PATRIC
     * @param inDir		organism directory for the genome; the base name must be the genome ID
     *
     * @throws IOException
     */
    public CoreGenome(Connection p3, File inDir) throws IOException {
        super(inDir.getName());
        this.setHome("CORE");
        this.setSource("RAST");
        // Save the directory.
        this.orgDir = inDir;
        // Compute the taxonomy, scientific name, domain, and genetic code.
        this.computeTaxonomy();
        // Read the contigs.
        this.readContigs();
        // Read all the features.
        this.readFeatures();
    }

    /**
     * Fill in the taxonomic and name information for the genome.
     *
     * @throws FileNotFoundException
     */
    private void computeTaxonomy() throws FileNotFoundException {
        // First, get the genome name.
        String name = this.readFlag("GENOME");
        if (name == null)
            throw new FileNotFoundException("Missing genome name in " + this.orgDir);
        else {
            this.setName(name);
        }
        // Now, get the taxonomy ID.
        String taxId = this.readFlag("TAXONOMY_ID");
        if (taxId == null) {
            // If there is no taxonomy ID, we use the first part of the genome ID.
            taxId = StringUtils.substringBefore(this.getId(), ".");
        }
        // Convert the tax ID to a number.
        int taxIdNum = Integer.valueOf(taxId);
        // Read the domain from the taxonomy file.
        String domain;
        String taxonomy = this.readFlag("TAXONOMY");
        if (taxonomy == null) {
            // No taxonomy.  Default to Bacteria.
            domain = "Bacteria";
        } else {
            // The domain is the first taxonomic grouping.
            domain = StringUtils.substringBefore(taxonomy, ";");
            if (! DOMAINS.contains(domain))
                log.warn("Invalid domain \"{}\" in taxonomy of {}.", domain, this.orgDir);
        }
        // Store the domain and the taxonomy ID.
        this.setDomain(domain);
        this.setTaxonomyId(taxIdNum);
        // Compute and store the genome lineage.
        Connection p3 = new Connection();
        boolean ok = p3.computeLineage(this, taxIdNum);
        if (! ok)
            log.warn("Could not compute taxonomic lineage for genome in {}.", this.orgDir);
    }

    /**
     * Read all the features from the feature directories.
     */
    private void readFeatures() {
        // TODO read features, assigned functions, and pattyfams

    }

    /**
     * Read in the contigs.
     */
    private void readContigs() {
        // TODO read in the contig FASTA
    }

    /**
     * Read a coreSEED flag file.  Such files are always a single line of text.
     *
     * @param flagName	name of the flag file to read
     *
     * @return the content of the file, or NULL if it is not found
     */
    private String readFlag(String flagName) {
        File inFile = new File(this.orgDir, flagName);
        String retVal = null;
        if (! inFile.canRead()) {
            log.warn("No {} file found in organism directory {}.", flagName, this.orgDir);
        } else {
            retVal = MarkerFile.read(inFile);
        }
        return retVal;
    }

}
