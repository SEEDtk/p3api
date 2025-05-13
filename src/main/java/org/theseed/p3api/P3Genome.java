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
import org.theseed.p3api.P3Connection.Table;
import org.theseed.roles.RoleUtilities;

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

        /**
         * @return the detail level to download all data needed by this and the specified other level
         *
         * @param other		other detail level to compare
         */
        public Details merge(Details other) {
            Details retVal;
            if (this == other) {
                // A level subsumes itself.
                retVal = this;
            } else if (other == CONTIGS || this == CONTIGS) {
                // CONTIGS is the only non-FULL one that downloads contigs, so it is incompatible with all others.
                retVal = FULL;
            } else if (this.compareTo(other) > 0) {
                // Except for contigs, they are in increasing order by structures downloaded.  Choose the largest.
                retVal = this;
            } else
                retVal = other;
            return retVal;
        }

    }

    /** JsonKeys for extracting sequences */
    private static final KeyBuffer AA_MD5 = new KeyBuffer("aa_sequence_md5", "");
    private static final KeyBuffer AA_SEQUENCE = new KeyBuffer("sequence", "");
    private static final KeyBuffer NA_MD5 = new KeyBuffer("na_sequence_md5", "");
    private static final KeyBuffer NA_SEQUENCE = new KeyBuffer("sequence", "");

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
    public static Genome load(P3Connection p3, String genome_id, Details detail, File cache) throws IOException {
        // If there is no cache, we simply call through to the API method.
        Genome retVal = null;
        if (cache == null) {
            retVal = load(p3, genome_id, detail);
        } else {
            // Check the cache.
            File gFile = new File(cache, genome_id + ".gto");
            if (gFile.exists()) {
                retVal = new Genome(gFile);
            } else {
                // Not in the cache.  Download the full genome.
                retVal = load(p3, genome_id, Details.FULL);
                // Store it in the cache.
                if (retVal != null)
                    retVal.save(gFile);
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
    public static P3Genome load(P3Connection p3, String genome_id, Details detail) {
        P3Genome retVal = null;
        long start = System.currentTimeMillis();
        // Start by getting the genome-level data.
        JsonObject genomeData = p3.getRecord(Table.GENOME, genome_id,
                "genome_id,genome_name,taxon_id,taxon_lineage_ids,superkingdom");
        if (genomeData == null)
            log.info("Genome {} not found in PATRIC.", genome_id);
        else {
            // Create the genome object.
            retVal = new P3Genome(genome_id);
            // Load the taxonomy data.
            Collection<String> taxIDs = genomeData.getCollectionOrDefault(GenomeKeys.TAXON_LINEAGE_IDS);
            List<TaxItem> taxItems = computeTaxItems(p3, taxIDs);
            retVal.p3Store(genomeData, taxItems);
            // Get the genetic code.  It only works if we have a lineage.  We default to 11.
            int code = 11;
            int[] lineages = retVal.getLineage();
            if (lineages.length > 0) {
                JsonObject taxData = p3.getRecord(Table.TAXONOMY, Integer.toString(lineages[lineages.length - 1]), "genetic_code");
                // Some of the genomes have invalid taxonomy data, so we have to check here.
                if (taxData != null) code = KeyBuffer.getInt(taxData, "genetic_code");
            }
            retVal.setGeneticCode(code);
            // Process the contigs.  If the detail level is FULL or CONTIGS, we get the DNA, too.
            String contigFields = "sequence_id,accession,description," + (detail.includesContigs() ? "sequence" : "length");
            Collection<JsonObject> contigs = p3.query(Table.CONTIG, contigFields, Criterion.EQ("genome_id", genome_id));
            retVal.p3Contigs(contigs);
            // Process the features if we want them.
            if (detail.includesFeatures()) {
                Collection<JsonObject> fidList = p3.query(Table.FEATURE,
                        "patric_id,sequence_id,start,end,strand,product,aa_sequence_md5,na_sequence_md5,plfam_id,pgfam_id,figfam_id,gi,gene,gene_id,refseq_locus_tag,go,uniprotkb_accession,protein_id",
                        Criterion.EQ("genome_id", genome_id), Criterion.EQ("annotation", "PATRIC"));
                storeFeatures(p3, detail, retVal, fidList);
            }
            if (P3Connection.log.isInfoEnabled()) {
                long duration = System.currentTimeMillis() - start;
                P3Connection.log.info("{} seconds to load {}.", String.format("%4.3f", duration / 1000.0), genome_id);
            }
        }
        return retVal;
    }

    /**
     * Store the features for a genome. We take as input a collection of JSON objects representing feature records.
     *
     * @param p3		PATRIC connection for retrieving protein sequences
     * @param detail	desired detail level
     * @param genome	genome being built
     * @param fidList	list of feature records
     */
    public static void storeFeatures(P3Connection p3, Details detail, Genome genome, Collection<JsonObject> fidList) {
        // Set up for protein sequences if we want them.
        boolean wantSequences = detail.includesProteins();
        Map<String, JsonObject> proteins = null;
        if (wantSequences) {
            Collection<String> md5Keys = fidList.stream().map(x -> x.getStringOrDefault(AA_MD5)).filter(x -> ! x.isEmpty()).collect(Collectors.toList());
            proteins = p3.getRecords(Table.SEQUENCE, md5Keys, "sequence");
        }
        // Assume until we prove otherwise that we don't have an SSU rRNA in this genome.
        genome.setSsuRRna("");
        // Store the features.  Note we skip the ones with empty IDs.
        for (JsonObject fid : fidList) {
            String id = KeyBuffer.getString(fid, "patric_id");
            if (id != null && id.length() > 0) {
                Feature feat = new Feature(KeyBuffer.getString(fid, "patric_id"), KeyBuffer.getString(fid, "product"),
                        KeyBuffer.getString(fid, "sequence_id"), KeyBuffer.getString(fid, "strand"),
                        KeyBuffer.getInt(fid, "start"), KeyBuffer.getInt(fid, "end"));
                feat.setPlfam(KeyBuffer.getString(fid, "plfam_id"));
                feat.setPgfam(KeyBuffer.getString(fid, "pgfam_id"));
                feat.setFigfam(KeyBuffer.getString(fid, "figfam_id"));
                feat.addAlias("gi", KeyBuffer.getString(fid, "gi"));
                feat.addAlias("gene_name", KeyBuffer.getString(fid, "gene"));
                feat.addAlias("LocusTag", KeyBuffer.getString(fid, "refseq_locus_tag"));
                feat.addAlias("Uniprot", KeyBuffer.getString(fid, "uniprotkb_accession"));
                feat.addAlias("protein_id", KeyBuffer.getString(fid, "protein_id"));
                String geneId = KeyBuffer.getString(fid, "gene_id");
                if (geneId.length() > 0 && ! geneId.contentEquals("0"))
                    feat.addAlias("GeneID", geneId);
                // Add in the GO terms.
                String[] goTermList = KeyBuffer.getStringList(fid, "go");
                for (String goString : goTermList)
                    feat.addGoTerm(goString);
                // Process the translation according to the feature type.
                switch (feat.getType()) {
                case "CDS" :
                    // This is a protein. Check to see if we are storing the protein translation.
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
                    break;
                case "rna" :
                    // This is an RNA.  Check for the SSU rRNA.
                    if (feat.getLocation().getLength() > genome.getSsuRRna().length() &&
                            RoleUtilities.SSU_R_RNA.matcher(feat.getPegFunction()).find()) {
                        // We need the nucleotide sequence of this RNA feature.
                        String na_md5 = fid.getString(NA_MD5);
                        if (na_md5 != null && ! na_md5.isEmpty()) {
                            JsonObject dnaSeq = p3.getRecord(Table.SEQUENCE, fid.getString(NA_MD5), "sequence");
                            if (dnaSeq != null) {
                                // If this SSU is well-formed, we save it.
                                String checkSeq = dnaSeq.getStringOrDefault(NA_SEQUENCE);
                                if (Genome.isValidSsuRRna(checkSeq))
                                    genome.setSsuRRna(checkSeq);
                            }
                        }
                    }
                    break;
                }
                // Store the feature.
                genome.addFeature(feat);
            }
        }
    }

    /**
     * Create a list of taxonomy items to correspond to a taxonomy ID list.
     *
     * @param p3		connection to PATRIC
     * @param taxIDs	list of taxonomy IDs
     *
     * @return a list of taxonomic descriptors (taxItems) for the indicated lineage
     */
    public static List<TaxItem> computeTaxItems(P3Connection p3, Collection<String> taxIDs) {
        Map<String, JsonObject> taxRecords = p3.getRecords(Table.TAXONOMY, taxIDs, "taxon_id,taxon_name,taxon_rank");
        List<TaxItem> retVal = new ArrayList<TaxItem>(taxRecords.size());
        for (String taxID : taxIDs) {
            JsonObject taxRecord = taxRecords.get(taxID);
            // Note that if the taxonomic ID is invalid, we will get NULL from the above query.
            if (taxRecord != null) {
                retVal.add(new TaxItem(taxRecord));
            }
        }
        return retVal;
    }

}
