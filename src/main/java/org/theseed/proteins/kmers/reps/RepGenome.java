/**
 *
 */
package org.theseed.proteins.kmers.reps;

import org.apache.commons.lang3.StringUtils;
import org.theseed.genome.Feature;
import org.theseed.sequence.ProteinKmers;
import org.theseed.sequence.Sequence;

/**
 * This class encapsulates the data for a representative genome. In addition to the protein
 * sequence, we need to know the genome name and the feature ID.  The genome ID can be inferred
 * from the feature ID.
 *
 * @author Bruce Parrello
 *
 */
public class RepGenome extends ProteinKmers implements Comparable<RepGenome> {

    // FIELDS
    /** ID of the representative genome */
    private String genomeId;
    /** ID of the key protein's feature */
    private String fid;
    /** name of the representative genome */
    private String name;

     /**
     * Construct a representative genome.
     *
     * @param fid		ID of the feature containing the genome's key protein
     * @param name		name of the genome
     * @param protein	protein sequence
     */
    public RepGenome(String fid, String name, String protein) {
        super(protein);
        fillData(fid, name);
    }

    /**
     * Construct a representative genome from a FASTA sequence
     *
     * @param sequence	incoming sequence; the label must be the key protein's feature ID,
     * 					the comment must be the relevant genome's name, and the sequence
     * 					must be the key protein's amino acid sequence
     */
    public RepGenome(Sequence definition) {
        super(definition.getSequence());
        fillData(definition.getLabel(), definition.getComment());
    }

    /**
     * Fill in the feature ID and the genome information.
     *
     * @param fid	feature ID for this genome's key protein.
     * @param name	genome name
     */
    private void fillData(String fid, String name) {
        // Extract the genome ID from the feature ID.
        this.genomeId = Feature.genomeOf(fid);
        // Store the feature ID and genome name.
        this.fid = fid;
        this.name = name;
    }


    /**
     * @return the genomeId
     */
    public String getGenomeId() {
        return this.genomeId;
    }

    /**
     * @return the key protein feature ID
     */
    public String getFid() {
        return this.fid;
    }

    /**
     * @return the genome name
     */
    public String getName() {
        return this.name;
    }

    /**
     * @return a sequence object representing this genome's key protein
     */
    public Sequence toSequence() {
        Sequence retVal = new Sequence(this.fid, this.name, this.getProtein());
        return retVal;
    }

    @Override
    public int compareTo(RepGenome arg0) {
        String g1[] = StringUtils.split(this.genomeId, '.');
        String g2[] = StringUtils.split(arg0.genomeId, '.');
        int retVal = g1[0].compareTo(g2[0]);
        if (retVal == 0) {
            // Compare the suffixes numerically.  A shorter suffix is always a smaller number.
            retVal = g1[1].length() - g2[1].length();
            if (retVal == 0) {
                retVal = g1[1].compareTo(g2[1]);
            }
        }
        return retVal;
    }

    /**
     * Equality for this object is based on genome ID, so the hash code is too.
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.genomeId == null) ? 0 : this.genomeId.hashCode());
        return result;
    }

    /**
     * Equality for this object is based on genome ID.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof RepGenome))
            return false;
        RepGenome other = (RepGenome) obj;
        if (this.genomeId == null) {
            if (other.genomeId != null) {
                return false;
            }
        } else if (!this.genomeId.equals(other.genomeId)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return this.genomeId + " (" + this.name + ")";
    }



}
