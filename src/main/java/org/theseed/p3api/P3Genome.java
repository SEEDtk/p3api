/**
 *
 */
package org.theseed.p3api;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.theseed.genome.Feature;
import org.theseed.genome.Genome;
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
        FULL;

        /**
         * @return TRUE if this detail level is equal to or greater than the given level
         *
         * @param min	minimum level against which to compare
         */
        public boolean includes(Details min) {
            return this.ordinal() >= min.ordinal();
        }
    }

    /** JsonKeys for extracting sequences */
    private static final KeyBuffer AA_MD5 = new KeyBuffer("aa_sequence_md5", "");
    private static final KeyBuffer AA_SEQUENCE = new KeyBuffer("sequence", "");

    public P3Genome(Connection p3, String genome_id, Details detail) {
        super(genome_id);
        // Start by getting the genome-level data.
        JsonObject genomeData = p3.getRecord(Table.GENOME, genome_id,
                "genome_id,genome_name,taxon_id,taxon_lineage_ids,kingdom");
        this.p3Store(genomeData);
        // Get the genetic code.  It only works if we have a lineage.
        String[] lineages = this.getLineage();
        if (lineages.length > 0) {
            JsonObject taxData = p3.getRecord(Table.TAXONOMY, lineages[lineages.length - 1], "genetic_code");
            this.setGeneticCode(Connection.getInt(taxData, "genetic_code"));
        }
        // Process the contigs.  If the detail level is FULL, we get the DNA, too.
        String contigFields = (detail.includes(Details.FULL) ? "sequence_id,sequence" : "sequence_id,length");
        Collection<JsonObject> contigs = p3.query(Table.CONTIG, contigFields, Criterion.EQ("genome_id", genome_id));
        this.p3Contigs(contigs);
        // Process the features.
        Collection<JsonObject> fidList = p3.query(Table.FEATURE,
                "patric_id,sequence_id,start,end,strand,product,aa_sequence_md5,plfam_id", Criterion.EQ("genome_id", genome_id),
                Criterion.EQ("annotation", "PATRIC"));
        // Set up for protein sequences if we want them.
        boolean wantSequences = detail.includes(Details.PROTEINS);
        Map<String, JsonObject> proteins = null;
        if (wantSequences) {
            Collection<String> md5Keys = fidList.stream().map(x -> x.getStringOrDefault(AA_MD5)).filter(x -> ! x.isEmpty()).collect(Collectors.toList());
            proteins = p3.getRecords(Table.SEQUENCE, md5Keys, "sequence");
        }
        // Store the features.
        for (JsonObject fid : fidList) {
            Feature feat = new Feature(Connection.getString(fid, "patric_id"), Connection.getString(fid, "product"),
                    Connection.getString(fid, "sequence_id"), Connection.getString(fid, "strand"),
                    Connection.getInt(fid, "start"), Connection.getInt(fid, "end"));
            feat.storeLocalFamily(Connection.getString(fid, "plfam_id"));
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
                feat.storeProtein(protein.getStringOrDefault(AA_SEQUENCE));
            // Store the feature.
            this.addFeature(feat);
        }
    }

}
