/**
 *
 */
package org.theseed.proteins.kmers.reps;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.p3api.Connection;
import org.theseed.p3api.Connection.Table;
import org.theseed.p3api.Criterion;
import org.theseed.proteins.Role;
import org.theseed.sequence.Sequence;

import com.github.cliftonlabs.json_simple.JsonObject;

/**
 * This is a utility class that finds the representative genomes for all PATRIC genomes in a list.
 *
 * @author Bruce Parrello
 *
 */
public class P3RepGenomeDb {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(P3RepGenomeDb.class);


    /**
     * Process all the representative genomes in a list.  Unrepresented genomes will not appear in the output map.
     *
     * @param p3		PATRIC connection to use
     * @param genomes	collection of genome IDs
     * @param repdb		representative-genome database
     *
     * @return a map from genome IDs to representatives
     */
    public static Map<String, RepGenomeDb.Representation> getReps(Connection p3, Collection<String> genomes, RepGenomeDb repdb) {
        int nGenomes = genomes.size();
        Map<String, RepGenomeDb.Representation> retVal = new HashMap<>(nGenomes);
        // We need to get the seed protein sequences from the PATRIC genomes.  This is a two-step process.  First we find
        // the MD5s, then the actual sequences.
        log.info("Reading seed proteins for {} genomes.", nGenomes);
        String protName = repdb.getProtName();
        Role seedRole = new Role("seed", protName);
        List<JsonObject> possibleSeeds = p3.getRecords(Table.FEATURE, "genome_id", genomes, "genome_id,patric_id,product,aa_length,aa_sequence_md5",
                Criterion.EQ("product", protName));
        log.info("{} seed proteins found.", possibleSeeds.size());
        // For each genome, we memorize the longest sequence.
        Map<String, JsonObject> seqMap = new HashMap<>(nGenomes);
        for (JsonObject record : possibleSeeds) {
            String product = Connection.getString(record, "product");
            if (seedRole.matches(product)) {
                // Here we have a real seed protein.
                String genomeId = Connection.getString(record, "genome_id");
                JsonObject previous = seqMap.get(genomeId);
                // If it's the first, save it.  If it is a duplicate, save it if it is longer.
                if (previous == null || Connection.getInt(record, "aa_length") > Connection.getInt(previous, "aa_length"))
                    seqMap.put(genomeId, record);
            }
        }
        log.info("{} genomes have at least one seed protein.", seqMap.size());
        // Now we need to get the actual sequences.
        Set<String> protSet = seqMap.values().stream().map(x -> Connection.getString(x, "aa_sequence_md5")).collect(Collectors.toSet());
        Map<String, JsonObject> protMap = p3.getRecords(Table.SEQUENCE, protSet, "md5,sequence");
        log.info("{} seed protein sequences returned from PATRIC.", protMap.size());
        // Get a map of genome IDs to seed protein sequences.
        Map<String, Sequence> seedMap = new HashMap<>(nGenomes);
        for (Map.Entry<String, JsonObject> seqEntry : seqMap.entrySet()) {
            JsonObject seqRecord = seqEntry.getValue();
            String genome = seqEntry.getKey();
            String seqMd5 = Connection.getString(seqRecord, "aa_sequence_md5");
            JsonObject prot = protMap.get(seqMd5);
            if (prot == null)
                log.warn("No seed protein found for {}.", genome);
            else
                seedMap.put(seqEntry.getKey(), new Sequence(seqEntry.getKey(), Connection.getString(seqRecord, "product"),
                        Connection.getString(prot, "sequence")));
        }
        log.info("{} seed sequences found.", seedMap.size());
        // Now find the representatives.
        for (Sequence seq : seedMap.values()) {
            RepGenomeDb.Representation found = repdb.findClosest(seq);
            if (found.getSimilarity() >= repdb.getThreshold())
                retVal.put(seq.getLabel(), found);
        }
        log.info("Representatives found for {} genomes.", retVal.size());
        return retVal;
    }
}
