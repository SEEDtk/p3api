/**
 *
 */
package org.theseed.p3api;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.theseed.genome.Contig;
import org.theseed.p3api.Connection.Table;
import org.theseed.sequence.MD5Hex;

import com.github.cliftonlabs.json_simple.JsonObject;

/**
 * This class produces a hexadecimal MD5 of a string, suitable for computing protein IDs or contig checksums.
 *
 * @author Bruce Parrello
 *
 */
public class P3MD5Hex extends MD5Hex {

    // FIELDS

    /** connection to PATRIC */
    private Connection p3;

    public P3MD5Hex() throws NoSuchAlgorithmException {
        super();
        this.p3 = new Connection();
    }

    /**
     * Compute the MD5 of a PATRIC genome.
     *
     * @param genomeId	ID of the target genome
     *
     * @return the MD5 checksum of the genome's DNA
     * @throws UnsupportedEncodingException
     */
    public String genomeMD5(String genomeId) throws UnsupportedEncodingException {
        Collection<JsonObject> contigData = p3.query(Table.CONTIG, "sequence_id,sequence", Criterion.EQ("genome_id", genomeId));
        Collection<Contig> contigs = contigData.stream().map(x -> new Contig(x, 11)).collect(Collectors.toSet());
        return sequenceMD5(contigs);
    }

    /**
     * Compute the MD5s of multiple genomes.
     *
     * @param genomes	collection of genome IDs
     *
     * @return a map from the genome IDs to the MD5 checksum
     * @throws UnsupportedEncodingException
     */
    public Map<String,String> genomeMD5s(Collection<String> genomes) throws UnsupportedEncodingException {
        Map<String,String> retVal = new HashMap<String,String>(genomes.size());
        // This map associates contigs with genome IDs.
        Map<String,Collection<Contig>> genomeMap = new HashMap<String,Collection<Contig>>(genomes.size());
        for (String genomeId : genomes)
            genomeMap.put(genomeId, new ArrayList<Contig>(100));
        // Now read in the contigs.
        List<JsonObject> contigObjects = p3.getRecords(Table.CONTIG, "genome_id", genomes, "sequence_id,sequence");
        for (JsonObject contigObject : contigObjects) {
            Contig contig = new Contig(contigObject, 11);
            String genomeId = Connection.getString(contigObject, "genome_id");
            genomeMap.get(genomeId).add(contig);
        }
        // Process each genome that had contigs.
        for (String genomeId : genomeMap.keySet()) {
            Collection<Contig> contigs = genomeMap.get(genomeId);
            if (contigs.size() > 0) {
                String md5 = this.sequenceMD5(contigs);
                retVal.put(genomeId, md5);
            }
        }
        return retVal;
    }


}
