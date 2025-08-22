/**
 *
 */
package org.theseed.p3api;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.p3api.P3Connection.Table;

import com.github.cliftonlabs.json_simple.JsonObject;

/**
 * This object reads in the taxonomy table and produces a structure that identifies all the taxonomic groupings
 * for species, genus, and family.  For the species, it also contains the genetic code.
 *
 * @author Bruce Parrello
 *
 */
public class P3TaxData {

    // FIELDS
    /** logging facility */
    private static final Logger log = LoggerFactory.getLogger(P3TaxData.class);
    /** set of taxon IDs associated with genera */
    private final Set<String> genera;
    /** set of taxon IDs associated with families */
    private final Set<String> families;
    /* map of taxon IDs associated with species to genetic codes */
    private final HashMap<String, Integer> species;

    /**
     * Construct a taxonomy database.
     *
     * @param p3	PATRIC connection to use
     */
    public P3TaxData(P3Connection p3) {
        // Get all the species.
        log.info("Downloading taxonomic data.");
        List<JsonObject> taxonList = p3.query(Table.TAXONOMY,
                "taxon_id,genetic_code", "eq(taxon_rank,species)");
        this.species = new HashMap<>(taxonList.size());
        for (JsonObject taxonData : taxonList) {
            String speciesId = KeyBuffer.getString(taxonData, "taxon_id");
            int gc = KeyBuffer.getInt(taxonData, "genetic_code");
            this.species.put(speciesId, gc);
        }
        log.info("{} species tabulated.", this.species.size());
        // Get all the genera and families.
        this.genera = getRankSet(p3, "genus");
        this.families = getRankSet(p3, "family");
    }

    /**
     * Get a set of taxon IDs for all taxons of the specified rank.
     *
     * @param p3		PATRIC connection for queries
     * @param rank		name of the rank to fetch
     *
     * @return a set of taxon IDs for the named rank
     */
    public static Set<String> getRankSet(P3Connection p3, String rank) {
        List<JsonObject> taxonList;
        taxonList = p3.query(Table.TAXONOMY, "taxon_id", "eq(taxon_rank," + rank + ")");
        Set<String> retVal = taxonList.stream().map(x -> KeyBuffer.getString(x, "taxon_id")).collect(Collectors.toSet());
        log.info("{} {} taxons tabulated.", retVal.size(), rank);
        return retVal;
    }

    /**
     * Check a taxon ID to see if it is for a species and if it is, return its genetic code.
     *
     * @param taxon		taxon ID to check
     *
     * @return the genetic code, or 0 if the taxon ID is not a species
     */
    public int checkSpecies(String taxon) {
        Integer gc = this.species.get(taxon);
        int retVal;
        if (gc == null)
            retVal = 0;
        else
            retVal = gc;
        return retVal;
    }

    /**
     * @return TRUE if a taxon ID is for a genus
     *
     * @param taxon		taxon ID to check
     */
    public boolean isGenus(String taxon) {
        return this.genera.contains(taxon);
    }

    /**
     * @return TRUE if a taxon ID is for a family
     *
     * @param taxon		taxon ID to check
     */
    public boolean isFamily(String taxon) {
        return this.families.contains(taxon);
    }

}
