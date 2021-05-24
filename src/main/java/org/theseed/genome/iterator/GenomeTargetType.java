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
    }, DIR {
        @Override
        public IGenomeTarget create(File directory, boolean clearFlag) throws IOException {
            return NormalDirectorySource.create(directory, clearFlag);
        }
    }, PFASTA {
        @Override
        public IGenomeTarget create(File fastaFile, boolean clearFlag) throws IOException {
            return new ProteinFastaBuilder(fastaFile, clearFlag);
        }
    }, DNAFASTA {
        @Override
        public IGenomeTarget create(File fastaFile, boolean clearFlag) throws IOException {
            return new ContigFastaBuilder(fastaFile, clearFlag);
        }
    }, CORE {
        @Override
        public IGenomeTarget create(File directory, boolean clearFlag) throws IOException {
            return new CoreOutputDirectory(directory, clearFlag);
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
