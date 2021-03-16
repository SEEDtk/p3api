/**
 *
 */
package org.theseed.proteins.kmers.reps;

import org.theseed.sequence.ProteinKmers;
import org.theseed.sequence.Sequence;

/**
 * This is a more limited object than RepGenome that tracks a generic representative sequence.  It consists of the ProteinKmers
 * object with an ID attached.
 *
 * @author Bruce Parrello
 *
 */
public class RepSequence extends ProteinKmers implements Comparable<RepSequence> {

    // FIELDS
    /** sequence ID */
    private String seqId;

    /** Construct a RepSequence object from a sequence
     *
     * @param seq	FASTA sequence object containing the sequence and ID
     */
    public RepSequence(Sequence seq) {
        super(seq.getSequence());
        this.seqId = seq.getLabel();
    }

    /** Compare two sequences by ID.
     *
     * @param other		other sequence to compare
     */
    @Override
    public int compareTo(RepSequence other) {
        return (this.seqId.compareTo(other.seqId));
    }

    @Override
    public int hashCode() {
        int retVal = 0;
        if (this.seqId != null) retVal = this.seqId.hashCode();
        return retVal;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof RepSequence))
            return false;
        RepSequence other = (RepSequence) obj;
        if (seqId == null) {
            if (other.seqId != null)
                return false;
        } else if (!seqId.equals(other.seqId))
            return false;
        return true;
    }

    /**
     * @return the ID of this sequence
     */
    public String getId() {
        return this.seqId;
    }

}
