package org.theseed.p3api;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.genome.Contig;
import org.theseed.genome.Feature;
import org.theseed.genome.Genome;
import org.theseed.genome.TaxItem;
import org.theseed.roles.RoleUtilities;

import com.github.cliftonlabs.json_simple.JsonObject;


/**
 * This class loads a batch of genomes to be processed from the BV-BRC using a cursor connection. This is
 * considerably faster than loading them individually. Once loaded, the genomes can be retrieved by ID.
 */
public class P3GenomeBatch implements Iterable<Genome> {

    // FIELDS
    /** logging facility */
    private static final Logger log = LoggerFactory.getLogger(P3GenomeBatch.class);
    /** map of genome IDs to genome objects */
    private final Map<String, Genome> genomeMap;

    /**
     * Create and load a new genome batch.
     * 
     * @param p3    BV-BRC cursor connection to use
     * @param ids   list of genome IDs to load
     * 
     * @throws IOException
     */
    public P3GenomeBatch(P3CursorConnection p3, Collection<String> ids, P3Genome.Details level) throws IOException {
        long start = System.currentTimeMillis();
        log.info("Loading {} genomes from BV-BRC.", ids.size());
        this.genomeMap = new HashMap<>(ids.size() * 4 / 3 + 1);
        // Get the genome and taxonomy records and create the genomes in the map.
        this.createGenomes(p3, ids);
        // Get the contigs and add them to the genomes.
        this.addContigs(p3, level);
        // If we are including features, get the features and add them to the genomes.
        if (level.includesFeatures())
            this.addFeatures(p3, level);
        if (log.isInfoEnabled()) {
            Duration time = Duration.ofMillis(System.currentTimeMillis() - start);
            log.info("{} genomes loaded in {}.", this.genomeMap.size(), time);
        }
    }

    /**
     * This method reads the genome records from the BV-BRC and creates the Genome objects
     * in the map. It will also collect the taxonomic IDs from the lineages. These are
     * used to query the taxonomy table, so we can fill in the ranks and names of the taxa
     * as well as the genetic codes of the genomes.
     * 
     * @param p3        BV-BRC cursor connection to use
     * @param ids       list of genome IDs to load
     * @param level     detail level for genome information
     * 
     * @throws IOException
     */
    private void createGenomes(P3CursorConnection p3, Collection<String> ids) throws IOException {
        // First, we need to read the genome records.
        List<JsonObject> genomeRecords = p3.getRecords("genome", ids.size(),
                ids.size(), "genome_id", ids, "genome_id,genome_name,superkingdom,taxon_lineage_ids,taxon_id");
        // We need to get the taxonomic IDs from the lineage information and then build a map of taxonomy
        // data. We will treat the taxon IDs as strings because that is what the key field of the
        // taxonomy record is. Theoretically, the genome taxon ID should be in the lineage, but we take no chances
        // and add that as well.
        Set<String> taxIdKeys = new HashSet<>();
        for (JsonObject genome : genomeRecords) {
            Arrays.stream(KeyBuffer.getStringList(genome, "taxon_lineage_ids")).forEach(x -> taxIdKeys.add(x));
            taxIdKeys.add(KeyBuffer.getString(genome, "taxon_id"));
        }
        // Now we have a set of taxonomic IDs. Get the taxonomy records and put them in a map. Note we cleverly convert to
        // integer taxon IDs here using the taxon_id_i field.
        Map<Integer, JsonObject> taxMap = new HashMap<>(taxIdKeys.size() * 4 / 3 + 1);
        p3.getRecords("taxon", taxIdKeys.size(), ids.size(), "taxon_id", taxIdKeys, "taxon_id_i,taxon_name,taxon_rank,genetic_code",
            Collections.emptyList(), (x -> taxMap.putIfAbsent(KeyBuffer.getInt(x, "taxon_id_i"), x)));
        // For each genome, we use its main taxon ID to find the genetic code so we can create the genome object, and
        // then we build and add the lineage array.
        for (JsonObject genome : genomeRecords) {
            String genomeId = KeyBuffer.getString(genome, "genome_id");
            int taxonId = KeyBuffer.getInt(genome, "taxon_id");
            JsonObject taxon = taxMap.get(taxonId);
            int gc = 11;
            if (taxon == null)
                log.warn("Genome {} has taxon ID {} but no corresponding taxonomy record was found.", genomeId, taxonId);
            else
                gc = KeyBuffer.getInt(taxon, "genetic_code");
                // Get the other values needs to create the genome object, and then create it.
            String genomeName = KeyBuffer.getString(genome, "genome_name");
            String domain = KeyBuffer.getString(genome, "superkingdom");
            Genome newGenome = new Genome(genomeId, genomeName, domain, gc);
            this.genomeMap.put(genomeId, newGenome);
            // Add some stuff we already know.
            newGenome.setTaxonomyId(taxonId);
            newGenome.setHome("BV-BRC");
            // Initialize to no SSU rRNA.
            newGenome.setSsuRRna("");
            // Now we need to build the lineage array.
            int[] taxIds = KeyBuffer.getIntegerList(genome, "taxon_lineage_ids");
            TaxItem[] lineage = new TaxItem[taxIds.length];
            for (int i = 0; i < taxIds.length; i++) {
                int taxId = taxIds[i];
                JsonObject tax = taxMap.get(taxId);
                if (tax != null) {
                    String taxName = KeyBuffer.getString(tax, "taxon_name");
                    String taxRank = KeyBuffer.getString(tax, "taxon_rank");
                    lineage[i] = new TaxItem(taxId, taxName, taxRank);
                } else {
                    log.warn("Genome {} has lineage taxon ID {} but no corresponding taxonomy record was found.", genomeId, taxId);
                    lineage[i] = new TaxItem(taxId, "<unknown>", "<unknown>");
                }
            }
            newGenome.setLineage(lineage);
        }
        log.info("{} genomes initialized.", this.genomeMap.size());
    }

    /**
     * This method reads contig information from the contig table and adds the contigs to the
     * genome. If we are not storing DNA, we only get the contig length. The genomes must
     * be already initialized in the genome map.
     * 
     * @param p3        cursor connection for accessing BV-BRC data
     * @param level     detail level for genome information
     * 
     * @throws IOException
     */
    private void addContigs(P3CursorConnection p3, P3Genome.Details level) throws IOException {
        // Compute the necessary fields. The only tricky part is if we need the sequence itself.
        // If we do, we will reduce the batch size to prevent the response from being insanely large.
        StringBuilder fieldsBuf = new StringBuilder(60);
        int batchSize;
        fieldsBuf.append("sequence_id,genome_id,accession,description,");
        if (level.includesContigs()) {
            fieldsBuf.append("sequence");
            batchSize = 25;
        } else {
            fieldsBuf.append("length");
            batchSize = 1000;
        }
        p3.getRecords("contig", CursorConnection.MAX_LIMIT, batchSize, "genome_id", this.genomeMap.keySet(),
                fieldsBuf.toString(), Collections.emptyList(), (x -> this.processContig(x, level)));
    }

    /**
     * This method processes a contig record from the BV-BRC. It adds the contig to the genome
     * in the map. If we are not storing DNA, we only get the contig length.
     * 
     * @param record        JSON object containing the contig information
     * @param level    detail level for genome information
     */
    private void processContig(JsonObject record, P3Genome.Details level) {
        // Get the genome ID from the record and find the genome. We need the 
        // genome to add the contig and to get the genetic code.
        String genomeId = KeyBuffer.getString(record, "genome_id");
        Genome genome = this.genomeMap.get(genomeId);
        int gc = genome.getGeneticCode();
        // Get the contig information.
        String contigId = KeyBuffer.getString(record, "sequence_id");
        Contig contig;
        if (level.includesContigs()) {
            String sequence = KeyBuffer.getString(record, "sequence");
            contig = new Contig(contigId, sequence, gc);
        } else {
            int length = KeyBuffer.getInt(record, "length");
            contig = new Contig(contigId, length, gc);
        }
        // Update the accession and description.
        String accession = KeyBuffer.getString(record, "accession");
        String description = KeyBuffer.getString(record, "description");
        contig.setAccession(accession);
        contig.setDescription(description);
        // Add the contig to the genome.
        genome.addContig(contig);
    }

    /**
     * This method reads feature information from the feature table and adds the features to the
     * genome. This may or may not include protein sequences. We also use this opportunity to
     * store the primary SSU rRNA sequence, and the seed protein sequence. The genomes must
     * be already initialized in the genome map.
     * 
     * @param p3        cursor connection for accessing BV-BRC data
     * @param level     detail level for genome information
     * 
     * @throws IOException 
     */
    private void addFeatures(P3CursorConnection p3, P3Genome.Details level) throws IOException {
        // We will store the MD5s of the protein sequences we want to keep in here. Each MD5
        // will be mapped to a list of the features to contain the proteins.
        Map<String, List<Feature>> proteinMD5s = new HashMap<>();
        // This will map each SSU rRNA MD5 to a list of the genomes to contain it.
        Map<String, List<Genome>> dnaMD5s = new HashMap<>();
        // Now read the features from the database and add them to the genomes.
        p3.getRecords("feature", CursorConnection.MAX_LIMIT, 500, "genome_id", 
            this.genomeMap.keySet(), "patric_id,genome_id,sequence_id,start,end,strand,product,plfam_id,pgfam_id,figfam_id" +
                        ",gi,gene,gene_id,refseq_locus_tag,go,uniprotkb_accession,protein_id,na_sequence_md5,aa_sequence_md5" +
                        ",feature_type", 
            Arrays.asList(SolrFilter.EQ("patric_id", "*")),
            (x -> this.processFeature(x, level, proteinMD5s, dnaMD5s)));
        // Get the DNA for the SSU rRNAs and store the longest one belonging to each genome.
        p3.getRecords("sequence", CursorConnection.MAX_LIMIT, 400, "md5", dnaMD5s.keySet(),
            "md5,sequence", Collections.emptyList(), (x -> this.processSsuRna(x, dnaMD5s)));
        // Finally, store the saved protein sequences in the features.
        p3.getRecords("sequence", CursorConnection.MAX_LIMIT, 400, "md5", proteinMD5s.keySet(),
            "md5,sequence", Collections.emptyList(), (x -> this.processProtein(x, proteinMD5s)));
    }
    /**
     * This method processes a feature record and updates the protein and DNA MD5 maps.
     * 
     * @param xrecord       JSON object containing the feature information
     * @param level         detail level for genome information
     * @param proteinMD5s   map of necessary protein MD5s to the features containing them
     * @param dnaMD5s       map of necessary DNA MD5s to the genomes containing them
     */
    private void processFeature(JsonObject xrecord, P3Genome.Details level, Map<String, List<Feature>> proteinMD5s,
            Map<String, List<Genome>> dnaMD5s) {
        // Get the feature ID and find our containing genome.
        String fid = KeyBuffer.getString(xrecord, "patric_id");
        Genome genome = this.genomeMap.get(Feature.genomeOf(fid));
        // Build the feature object.
        String function = KeyBuffer.getString(xrecord, "product");
        String contigId = KeyBuffer.getString(xrecord, "sequence_id");
        String strand = KeyBuffer.getString(xrecord, "strand");
        int left = KeyBuffer.getInt(xrecord, "start");
        int right = KeyBuffer.getInt(xrecord, "end");
        String type = KeyBuffer.getString(xrecord, "feature_type");
        Feature feat = new Feature(fid, function, contigId, strand, left, right);
        // Figure out if this feature includes a protein or could be an SSU rRNA. These
        // are the situations where we need to track the MD5s.
        switch (type) {
            case "CDS", "mat_peptide" -> {
                // Get the protein MD5. We will keep this protein if proteins are included or it is a potential seed
                // protein.
                if (level.includesProteins() || function.equals(RoleUtilities.SEED_FUNCTION)) {
                    String protMd5 = KeyBuffer.getString(xrecord, "aa_sequence_md5");
                    // If the MD5 value is missing, we ignore it.
                    if (! StringUtils.isBlank(protMd5))
                        proteinMD5s.computeIfAbsent(protMd5, k -> new ArrayList<>(2)).add(feat);
                }
            }
            case "rRNA", "misc_RNA" -> {
                // Get the DNA MD5. We will keep this rRNA if it is an SSU rRNA.
                if (RoleUtilities.SSU_R_RNA.matcher(function).find()) {
                    String dnaMd5 = KeyBuffer.getString(xrecord, "na_sequence_md5");
                    // If the MD5 value is missing, we ignore it.
                    if (! StringUtils.isBlank(dnaMd5))
                        dnaMD5s.computeIfAbsent(dnaMd5, k -> new ArrayList<>(2)).add(genome);
                }
            }
        }
        // Set the protein families.
        feat.setPlfam(KeyBuffer.getString(xrecord, "plfam_id"));
        feat.setPgfam(KeyBuffer.getString(xrecord, "pgfam_id"));
        feat.setFigfam(KeyBuffer.getString(xrecord, "figfam_id"));
        // Add the aliases.
        feat.addAlias("gi", KeyBuffer.getString(xrecord, "gi"));
        feat.addAlias("gene_name", KeyBuffer.getString(xrecord, "gene"));
        feat.addAlias("LocusTag", KeyBuffer.getString(xrecord, "refseq_locus_tag"));
        feat.addAlias("Uniprot", KeyBuffer.getString(xrecord, "uniprotkb_accession"));
        feat.addAlias("protein_id", KeyBuffer.getString(xrecord, "protein_id"));
        // Store the gene name.
        String geneId = KeyBuffer.getString(xrecord, "gene_id");
        if (geneId.length() > 0 && ! geneId.contentEquals("0"))
            feat.addAlias("GeneID", geneId);
        // Add in the GO terms.
        String[] goTermList = KeyBuffer.getStringList(xrecord, "go");
        for (String goString : goTermList)
            feat.addGoTerm(goString);
        // Add the feature to the genome.
        genome.addFeature(feat);
    }


    /**
     * Store the SSU rRNA sequence for each genome to which it belongs. We only store the longest
     * of a particular genome, so if there is already a longer one present, we ignore the new one.
     * 
     * @param xrecord   sequence record for an SSU rRNA DNA sequence
     * @param dnaMD5s   map of necessary DNA MD5s to the genomes containing them
     */
    private void processSsuRna(JsonObject xrecord, Map<String, List<Genome>> dnaMD5s) {
        // Get the DNA sequence.
        String ssuRna = KeyBuffer.getString(xrecord, "sequence");
        // Get the MD5 and the list of genomes that use it.
        String ssuMd5 = KeyBuffer.getString(xrecord, "md5");
        List<Genome> genomes = dnaMD5s.get(ssuMd5);
        if (genomes == null)
            log.warn("No genomes found for SSU rRNA MD5: {}", ssuMd5);
        else {
            // Update the SSU rRNA for each genome.
            for (Genome genome : genomes) {
                // Check if the current genome has a shorter SSU rRNA (or none).
                String oldRna = genome.getSsuRRna();
                if (oldRna.length() < ssuRna.length())
                    genome.setSsuRRna(ssuRna);
            }
        }
    }

    /**
     * Store the protein sequence for each feature to which it belongs.
     * 
     * @param xrecord       sequence record for a protein sequence
     * @param proteinMD5s   map of necessary protein MD5s to the features containing them
     */
    private void processProtein(JsonObject xrecord, Map<String, List<Feature>> proteinMD5s) {
        // Get the protein sequence.
        String protein = KeyBuffer.getString(xrecord, "sequence");
        // Get the MD5 and the list of features that use it.
        String protMd5 = KeyBuffer.getString(xrecord, "md5");
        List<Feature> features = proteinMD5s.get(protMd5);
        if (features == null)
            log.warn("No features found for protein MD5: {}", protMd5);
        else {
            // Update the protein for each feature. Each feature has only one protein sequence,
            // so there is no need to check for length or anything.
            for (Feature feat : features)
                feat.setProteinTranslation(protein);
        }
    }

    /**
     * @return the genome with the specified ID, or NULL if it does not exist
     * 
     * @param genomeID      the ID of the genome to retrieve
     */
    public Genome get(String genomeID) {
        return this.genomeMap.get(genomeID);
    }

    /**
     * @return the number of genomes in the batch
     */
    public int size() {
        return this.genomeMap.size();
    }

    @Override
    public Iterator<Genome> iterator() {
        // Our genome set is simply the genome map values.
        return this.genomeMap.values().iterator();
    }

}
