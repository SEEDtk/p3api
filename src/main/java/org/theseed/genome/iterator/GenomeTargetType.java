/**
 *
 */
package org.theseed.genome.iterator;

import java.io.File;
import java.io.IOException;

import org.theseed.genome.GenomeMultiDirectory;

/**
 * This enumeration describes the different types of genome storage targets.
 *
 * @author Bruce Parrello
 *
 */
public enum GenomeTargetType {
    /** compressed multiple-directory structure designed to hold large genome sets */
    MASTER {
        @Override
        public IGenomeTarget create(File directory, boolean clearFlag) throws IOException {
            GenomeMultiDirectory retVal;
            if (clearFlag)
                retVal = GenomeMultiDirectory.create(directory, true);
            else
                retVal = new GenomeMultiDirectory(directory);
            return retVal;
        }
    },
    /** standard directory of GTO files */
    DIR {
        @Override
        public IGenomeTarget create(File directory, boolean clearFlag) throws IOException {
            return NormalDirectorySource.create(directory, clearFlag);
        }
    },
    /** single file of all proteins in the genomes */
    PFASTA {
        @Override
        public IGenomeTarget create(File fastaFile, boolean clearFlag) throws IOException {
            return new ProteinFastaBuilder(fastaFile, clearFlag);
        }
    },
    /** directory of contig FASTA files, one per genome */
    DNAFASTA {
        @Override
        public IGenomeTarget create(File fastaFile, boolean clearFlag) throws IOException {
            return new ContigFastaBuilder(fastaFile, clearFlag);
        }
    },
    /** SEED organism directory, with one subdirectory per genome */
    CORE {
        @Override
        public IGenomeTarget create(File directory, boolean clearFlag) throws IOException {
            return new CoreOutputDirectory(directory, clearFlag);
        }
    },
    /** flat file listing the genomes */
    LIST {
        @Override
        public IGenomeTarget create(File listFile, boolean clearFlag) throws IOException {
            return new ListFileBuilder(listFile, clearFlag);
        }
    },
    /** directory of protein FASTA files, one per genome */
    PEGFASTA {
        @Override
        public IGenomeTarget create(File directory, boolean clearFlag) throws IOException {
            return new PegFastaBuilder(directory, clearFlag);
        }

    };

    /**
     * Create a genome target directory of the appropriate type.
     *
     * @param directory		file name of the directory
     * @param clearFlag		TRUE to erase the directory before processing
     *
     * @return the directory object as a genome target
     *
     * @throws IOException
     */
    public abstract IGenomeTarget create(File directory, boolean clearFlag) throws IOException;

}
