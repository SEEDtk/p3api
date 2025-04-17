/**
 *
 */
package org.theseed.genome.iterator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.basic.ParseFailureException;
import org.theseed.genome.Contig;
import org.theseed.genome.Genome;
import org.theseed.genome.TaxItem;
import org.theseed.io.MasterGenomeDir;
import org.theseed.json.JsonFileDir;
import org.theseed.p3api.KeyBuffer;
import org.theseed.p3api.P3Connection;
import org.theseed.p3api.P3Connection.Table;
import org.theseed.p3api.P3Genome;
import org.theseed.p3api.P3Genome.Details;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.JsonKey;

/**
 * This genome source loads genomes from a BV-BRC json dump master directory. Each genome is represented
 * by a sub-directory with the genome ID as the name. Within each sub-directory are json list files
 * containing the genome's information from the SOLR cores. The internal genome object is built from a
 * subset of these cores.
 *
 * @author Bruce Parrello
 *
 */
public class JsonDumpDirectory extends GenomeSource {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(JsonDumpDirectory.class);
    /** map of genome IDs to dump directories */
    private Map<String, File> gMap;
    /** connection to PATRIC for taxon data */
    private P3Connection p3;
    /** key buffer for taxonomic lineage */
    private static final JsonKey LINEAGE_KEY = new KeyBuffer("taxon_lineage_ids", new ArrayList<String>());

    @Override
    protected int init(File inFile) throws IOException {
        // Connect to the master directory and fill the genome map with the sub-directories.
        MasterGenomeDir masterDir = new MasterGenomeDir(inFile);
        this.gMap = masterDir.stream().collect(Collectors.toMap(x -> x.getName(), x -> x));
        // Connect to PATRIC.
        this.p3 = new P3Connection();
        // Return the genome count.
        return this.gMap.size();
    }

    @Override
    protected void validate(File inFile) throws IOException, ParseFailureException {
        if (! inFile.isDirectory())
            throw new FileNotFoundException("Genome source " + inFile + " is not a valid directory.");
    }

    @Override
    public Set<String> getIDs() {
        return this.gMap.keySet();
    }

    @Override
    public int actualSize() {
        return gMap.size();
    }

    @Override
    protected Iterator<String> getIdIterator() {
        return this.gMap.keySet().iterator();
    }

    @Override
    protected Genome getGenome(String genomeId, Details level) {
        Genome retVal = null;
        // Try to find the genome.
        try {
            File genomeDir = this.gMap.get(genomeId);
            if (genomeDir != null) {
                // Create the genome from the genome.json file.
                retVal = this.loadGenomeData(genomeDir);
                // Load the contigs.
                this.loadSequenceData(genomeDir, retVal, level);
                // Load the features.
                this.loadFeatureData(genomeDir, retVal, level);
            }
        } catch (IOException | JsonException e) {
            // Quiesce loading errors.
            log.error("Cannot load genome {}: {}", genomeId, e.toString());
            retVal = null;
        }
        return retVal;
    }

    /**
     * Read the genome record from the specified genome JSON dump directory and create a
     * genome from it.
     *
     * @param genomeDir		genome directory
     *
     * @return the initialized genome
     */
    private Genome loadGenomeData(File genomeDir) throws IOException, JsonException {
        JsonArray genomeRecord = this.readJson(genomeDir, "genome.json");
        if (genomeRecord.size() != 1)
            throw new IOException("Invalid genome.json: must contain exactly one record.");
        JsonObject genomeJson = (JsonObject) genomeRecord.get(0);
        String genomeId = KeyBuffer.getString(genomeJson, "genome_id");
        Collection<String> taxIds = genomeJson.getCollectionOrDefault(LINEAGE_KEY);
        List<TaxItem> taxItems = P3Genome.computeTaxItems(this.p3, taxIds);
        TaxItem[] taxArray = taxItems.stream().toArray(TaxItem[]::new);
        // Get the taxonomy ID.
        int taxId = KeyBuffer.getInt(genomeJson, "taxon_id");
        // Use the tax ID to compute the genetic code.
        JsonObject taxData = this.p3.getRecord(Table.TAXONOMY, Integer.toString(taxId), "genetic_code");
        int gc = (taxData != null ? KeyBuffer.getInt(taxData, "genetic_code") : 11);
        // Finally, get the domain and name.
        String domain = KeyBuffer.getString(genomeJson, "superkingdom");
        String gName = KeyBuffer.getString(genomeJson, "genome_name");
        // Build an empty genome.
        Genome retVal = new Genome(genomeId, gName, domain, gc);
        // Store the taxonomy.
        retVal.setLineage(taxArray);
        // Denote this is a BV-BRC genome.
        retVal.setHome("BVBRC");
        return retVal;
    }

    /**
     * Load the contigs from the genome_sequence.json. If the detail level is
     * high enough, we will also load the contig DNA.
     *
     * @param genomeDir		genome dump directory
     * @param genome		genome object being built
     * @param level			desired detail level
     *
     * @throws JsonException
     * @throws IOException
     */
    private void loadSequenceData(File genomeDir, Genome genome, Details level) throws IOException, JsonException {
        JsonArray seqRecords = this.readJson(genomeDir, "genome_sequence.json");
        // Loop through the sequence records.
        for (var seqObject : seqRecords) {
            JsonObject seqRecord = (JsonObject) seqObject;
            // Create the contig from the JSON object.
            Contig contig = new Contig(seqRecord, genome.getGeneticCode());
            // Remove the sequence data if we are at a low detail level.
            if (! level.includesContigs())
                contig.clearSequence();
            genome.addContig(contig);
        }
    }

    /**
     * Load the genome features from the genome_feature.json. If the detail level is
     * high enough, we will also load the protein sequences. Note that the PheS
     * protein sequence and the SSU rRNA sequence is always loaded.
     *
     * @param genomeDir		genome dump directory
     * @param genome		genome object being built
     * @param level			desired detail level
     *
     * @throws JsonException
     * @throws IOException
     */
    private void loadFeatureData(File genomeDir, Genome genome, Details level) throws IOException, JsonException {
        JsonArray featList = this.readJson(genomeDir, "genome_feature.json");
        // We need the feature records as a collection of JSON objects for compatibility.
        Collection<JsonObject> fidList = featList.stream().map(x -> (JsonObject) x).collect(Collectors.toList());
        P3Genome.storeFeatures(this.p3, level, genome, fidList);
    }

    /**
     * Read the specified JSON file and return its contents as a JSON array.
     *
     * @param gDir	genome directory
     * @param name	name of file to read
     *
     * @return a JsonArray containing the records from the file
     *
     * @throws IOException
     * @throws JsonException
     */
    private JsonArray readJson(File gDir, String name) throws IOException, JsonException {
        File jsonFile = new File(gDir, name);
        if (! jsonFile.canRead())
            throw new FileNotFoundException("Json file " + jsonFile + " is not found or unreadable.");
        JsonArray retVal = JsonFileDir.getJson(jsonFile);
        return retVal;
    }


}
