/**
 *
 */
package org.theseed.genome.iterator;

import java.io.IOException;

import org.theseed.genome.Genome;

/**
 * This interface supports a genome storage location that can be used as a target for a copy operation.
 *
 * @author Bruce Parrello
 *
 */
public interface IGenomeTarget {

    /**
     * @return TRUE if the genome is found in this directory, else FALSE
     *
     * @param genomeId	ID of genome to check
     */
    public boolean contains(String genomeId);

    /**
     * Add a genome to the target location.  If the genome already exists, it will be overwritten.
     *
     * @param gto	genome to add
     */
    public void add(Genome genome) throws IOException;

}
