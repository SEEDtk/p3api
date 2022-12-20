/**
 *
 */
package org.theseed.genome.iterator;

import java.io.IOException;
import java.util.Set;

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

    /**
     * Delete a genome from this target.  This is an optional operation, and it is permissible to
     * throw an UnsupportedOperationException.
     *
     * @param genomeId	ID of genome to delete
     *
     * @throws IOException
     */
    public void remove(String genomeId) throws IOException;

    /**
     * @return TRUE if it is possible to to random deletes on this target type, else FALSE
     */
    public boolean canDelete();

    /**
     * Complete processing for this directory's output.
     */
    public void finish();

    /**
     * Get the set of genome IDs in this target.  This is an optional operation, and it is permissible
     * to throw an UnsupportedOperationException.
     *
     * @return a sorted clone of the ID set for the target
     */
    public Set<String> getGenomeIDs();

}
