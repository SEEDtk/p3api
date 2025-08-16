package org.theseed.p3api;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.github.cliftonlabs.json_simple.JsonObject;

/**
 * This is a subclass of the cursor connection that does not have any field mapping. It is simply
 * a cursor connection that uses the default data map.
 */
public class P3CursorConnection extends CursorConnection {

    // FIELDS
    /** list of domains for prokaryotes */
    public static final List<String> DOMAINS = Arrays.asList("Bacteria", "Archaea");

    public P3CursorConnection() {
        super(BvbrcDataMap.DEFAULT_DATA_MAP);
    }

    /**
     * Put the ID and name of every public, prokaryotic genomes in PATRIC into the specified collection.
     *
     * @param genomes	collection to contain the genome list.
     * 
     * @throws IOException 
     */
    public void addAllProkaryotes(Collection<JsonObject> genomes) throws IOException {
        genomes.addAll(this.getRecords("genome", MAX_LIMIT, 2000, "superkingdom", DOMAINS, "genome_id,genome_name", SolrFilter.EQ("public", "1"),
                SolrFilter.IN("genome_status", "Complete", "WGS")));
    }

}
