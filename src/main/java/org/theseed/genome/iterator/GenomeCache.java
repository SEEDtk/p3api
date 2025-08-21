/**
 *
 */
package org.theseed.genome.iterator;

import org.apache.commons.collections4.map.LRUMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.genome.Genome;

/**
 * A genome cache provides random access to a genome source with an in-memory cache.  The
 * constructor specifies the source and the cache size.  The cache is implemented using an
 * LRU LinkedHashMap.
 *
 * @author Bruce Parrello
 *
 */
public class GenomeCache {

    // FIELDS
    /** logging facility */
    private static final Logger log = LoggerFactory.getLogger(GenomeCache.class);
    /** underlying genome source */
    private GenomeSource genomes;
    /** hash map for the cache */
    private LRUMap<String, Genome> cache;
    /** number of fast gets */
    private int getsFound;
    /** number of loads */
    private int getsNotFound;

    /**
     * Construct a cache with a specified genome source backing it.
     *
     * @param source	source for the genomes
     * @param size		size for the cache
     */
    public GenomeCache(GenomeSource source, int size) {
        this.genomes = source;
        this.cache = new LRUMap<String, Genome>(size);
        this.getsFound = 0;
        this.getsNotFound = 0;
    }

    /**
     * Get a genome from the cache.
     *
     * @param genomeId	ID of the desired genome
     *
     * @return the genome with the specified ID, or NULL if it does not exist
     */
    public Genome get(String genomeId) {
        // Look in the cache for the genome.
        Genome retVal = this.cache.get(genomeId);
        if (retVal != null)
            this.getsFound++;
        else {
            // Here we must get the genome from the source and add it to the cache.
            // This may cause the oldest genome to be removed.
            retVal = genomes.getGenome(genomeId);
            this.cache.put(genomeId, retVal);
            this.getsNotFound++;
        }
        return retVal;
    }

    /**
     * Log the statistics.
     *
     * @param label		label to give to the cache
     */
    public void logStats(String label) {
        log.info("{} cache: {} fast gets, {} loads", label, this.getsFound, this.getsNotFound);
    }

    /**
     * @return the number of genome loads
     */
    public int getLoadCount() {
        return this.getsNotFound;
    }

}
