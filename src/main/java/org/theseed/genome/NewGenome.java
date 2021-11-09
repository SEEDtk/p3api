package org.theseed.genome;

import org.theseed.p3api.P3Connection;
import org.theseed.p3api.IdClearinghouse;

/**
 * This is a subclass for creating brand-new genomes.  It provides common utilities for such processes.
 *
 * @author Bruce Parrello
 */

public abstract class NewGenome extends Genome {

    /**
     * Construct a new genome.  The actual genome name will consist of the taxonomic name followed by
     * the provided suffix.
     *
     * @param suffix	name suffix for the genome
     * @param domain	domain of the genome (Archaea, Bacteria, ...)
     */
    protected NewGenome(String suffix, String domain) {
        super(suffix, domain);
    }

    /**
     * Fill in the taxonomic information for this genome.
     *
     * @param taxon		taxonomic grouping for this genome
     *
     * @return a unique ID for this genome
     */
    protected String getGenomeId(String taxon) {
        IdClearinghouse idConnection = new IdClearinghouse();
        int taxId = Integer.valueOf(taxon);
        String retVal = idConnection.computeGenomeId(taxId);
        this.setId(retVal);
        // Fill in the taxonomy.
        P3Connection p3 = new P3Connection();
        boolean taxFound = p3.computeLineage(this, taxId);
        String name;
        if (! taxFound) {
            // Nothing we can do here, but we default the genetic code and the name.
            String domain = this.getDomain();
            int gc = 1;
            if (domain.contentEquals("Bacteria") || domain.contentEquals("Archaea"))
                gc = 11;
            this.setGeneticCode(gc);
            name = String.format("Unknown %s", this.getDomain());
        } else {
            // Build the name from the bottom taxonomic value.
            name = this.getTaxonomyName();
            if (! this.getName().isEmpty())
                name += " " + this.getName();
        }
        this.setName(name);
        return retVal;
    }

}
