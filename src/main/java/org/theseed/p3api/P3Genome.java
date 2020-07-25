/**
 *
 */
package org.theseed.p3api;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.theseed.genome.Feature;
import org.theseed.genome.Genome;
import org.theseed.genome.TaxItem;
import org.theseed.p3api.Connection.KeyBuffer;
import org.theseed.p3api.Connection.Table;

import com.github.cliftonlabs.json_simple.JsonObject;

/**
 * Retrieve a genome from the PATRIC API.
 *
 * @author Bruce Parrello
 *
 */
public class P3Genome extends Genome {

    /**
     * level of detail desired in the genome
     */
    public enum Details {
        /** exclude the sequences */
        STRUCTURE_ONLY,
        /** include all the proteins */
        PROTEINS,
        /** load the whole genome */
        FULL,
        /** only load contigs */
        CONTIGS;

        public boolean includesContigs() {
            return this == FULL || this == CONTIGS;
        }

        public boolean includesFeatures() {
            return this != CONTIGS;
        }

        public boolean includesProteins() {
            return (this == PROTEINS || this == FULL);
        }

        public boolean includesSubsystems() {
            return (this == FULL);
        }


    }

    /** JsonKeys for extracting sequences */
    private static final KeyBuffer AA_MD5 = new KeyBuffer("aa_sequence_md5", "");
    private static final KeyBuffer AA_SEQUENCE = new KeyBuffer("sequence", "");

    /**
     * Construct an empty genome object/
     *
     * @param genome_id		ID of the new genome
     */
    protected P3Genome(String genome_id) {
        super(genome_id);
    }

    /**
     * Load a genome into memory from the PATRIC data API or a cache directory.
     *
     * @param p3			a connection to the PATRIC data API
     * @param genome_id		the ID of the desired genome
     * @param detail		the detail level to include
     * @param cache			a directory to use as a cache of GTO files
     *
     * @throws IOException
     */
    public static Genome Load(Connection p3, String genome_id, Details detail, File cache) throws IOException {
        // If there is no cache, we simply call through to the API method.
        Genome retVal = null;
        if (cache == null) {
            retVal = Load(p3, genome_id, detail);
        } else {
            // Check the cache.
            File gFile = new File(cache, genome_id + ".gto");
            if (gFile.exists()) {
                retVal = new Genome(gFile);
            } else {
                // Not in the cache.  Download the full genome.
                retVal = Load(p3, genome_id, Details.FULL);
                // Store it in the cache.
                if (retVal != null)
                    retVal.update(gFile);
            }
        }
        return retVal;
    }

    /**
     * Load a genome into memory from the PATRIC data API.
     *
     * @param p3			a connection to the PATRIC data API
     * @param genome_id		the ID of the desired genome
     * @param detail		the detail level to include
     *
     * @return the genome object, or NULL if the genome does not exist
     */
    public static P3Genome Load(Connection p3, String genome_id, Details detail) {
        P3Genome retVal = null;
        long start = System.currentTimeMillis();
        // Start by getting the genome-level data.
        JsonObject genomeData = p3.getRecord(Table.GENOME, genome_id,
                "genome_id,genome_name,taxon_id,taxon_lineage_ids,kingdom");
        if (genomeData != null) {
            // Create the genome object.
            retVal = new P3Genome(genome_id);
            // Load the taxonomy data.
            Collection<String> taxIDs = genomeData.getCollectionOrDefault(GenomeKeys.TAXON_LINEAGE_IDS);
            Map<String, JsonObject> taxRecords = p3.getRecords(Table.TAXONOMY, taxIDs, "taxon_id,taxon_name,taxon_rank");
            List<TaxItem> taxItems = new ArrayList<TaxItem>(taxRecords.size());
            for (String taxID : taxIDs) {
                JsonObject taxRecord = taxRecords.get(taxID);
                // Note that if the taxonomic ID is invalid, we will get NULL from the above query.
                if (taxRecord != null) {
                    taxItems.add(new TaxItem(taxRecord));
                }
            }
            retVal.p3Store(genomeData, taxItems);
            // Get the genetic code.  It only works if we have a lineage.  We default to 11.
            int code = 11;
            int[] lineages = retVal.getLineage();
            if (lineages.length > 0) {
                JsonObject taxData = p3.getRecord(Table.TAXONOMY, Integer.toString(lineages[lineages.length - 1]), "genetic_code");
                // Some of the genomes have invalid taxonomy data, so we have to check here.
                if (taxData != null) code = Connection.getInt(taxData, "genetic_code");
            }
            retVal.setGeneticCode(code);
            // Process the contigs.  If the detail level is FULL or CONTIGS, we get the DNA, too.
            String contigFields = "sequence_id,accession,description," + (detail.includesContigs() ? "sequence" : "length");
            Collection<JsonObject> contigs = p3.query(Table.CONTIG, contigFields, Criterion.EQ("genome_id", genome_id));
            retVal.p3Contigs(contigs);
            // Process the features if we want them.
            if (detail.includesFeatures()) {
                Collection<JsonObject> fidList = p3.query(Table.FEATURE,
                        "patric_id,sequence_id,start,end,strand,product,aa_sequence_md5,plfam_id,pgfam_id,gi,gene,gene_id,refseq_locus_tag,go",
                        Criterion.EQ("genome_id", genome_id), Criterion.EQ("annotation", "PATRIC"));
                // Set up for protein sequences if we want them.
                boolean wantSequences = detail.includesProteins();
                Map<String, JsonObject> proteins = null;
                if (wantSequences) {
                    Collection<String> md5Keys = fidList.stream().map(x -> x.getStringOrDefault(AA_MD5)).filter(x -> ! x.isEmpty()).collect(Collectors.toList());
                    proteins = p3.getRecords(Table.SEQUENCE, md5Keys, "sequence");
                }
                // Store the features.  Note we skip the ones with empty IDs.
                for (JsonObject fid : fidList) {
                    String id = Connection.getString(fid, "patric_id");
                    if (id != null && id.length() > 0) {
                        Feature feat = new Feature(Connection.getString(fid, "patric_id"), Connection.getString(fid, "product"),
                                Connection.getString(fid, "sequence_id"), Connection.getString(fid, "strand"),
                                Connection.getInt(fid, "start"), Connection.getInt(fid, "end"));
                        feat.setPlfam(Connection.getString(fid, "plfam_id"));
                        feat.setPgfam(Connection.getString(fid, "pgfam_id"));
                        feat.formAlias("gi|", Connection.getString(fid, "gi"));
                        feat.formAlias("", Connection.getString(fid, "gene"));
                        feat.formAlias("", Connection.getString(fid, "refseq_locus_tag"));
                        String geneId = Connection.getString(fid, "gene_id");
                        if (geneId.length() > 0 && ! geneId.contentEquals("0"))
                            feat.formAlias("GeneID:", geneId);
                        // Add in the GO terms.
                        String[] goTermList = Connection.getStringList(fid, "go");
                        for (String goString : goTermList)
                            feat.addGoTerm(goString);
                        // Check to see if we are storing the protein translation.
                        JsonObject protein = null;
                        if (wantSequences) {
                            // Here we are storing protein translations for all the features that have them.
                            protein = proteins.get(fid.getStringOrDefault(AA_MD5));
                        } else if (feat.getFunction().contentEquals("Phenylalanyl-tRNA synthetase alpha chain (EC 6.1.1.20)")) {
                            // We always store the PheS protein.
                            protein = p3.getRecord(Table.SEQUENCE, fid.getString(AA_MD5), "sequence");
                        }
                        if (protein != null)
                            feat.setProteinTranslation(protein.getStringOrDefault(AA_SEQUENCE));
                        // Store the feature.
                        retVal.addFeature(feat);
                    }
                }
            }
            if (Connection.log.isInfoEnabled()) {
                long duration = System.currentTimeMillis() - start;
                Connection.log.info("{} seconds to load {}.", String.format("%4.3f", duration / 1000.0), genome_id);
            }
        }
        return retVal;
    }

}
