/**
 *
 */
package org.theseed.genome.iterator;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.genome.Feature;
import org.theseed.genome.Genome;
import org.theseed.sequence.FastaOutputStream;
import org.theseed.sequence.Sequence;

/**
 * This is a genome copy target containing all the proteins in the various genomes in the form of
 * FASTA files.  Each protein is identified by its feature ID and the comment is the function.
 *
 * @author Bruce Parrello
 *
 */
public class PegFastaBuilder implements IGenomeTarget {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(PegFastaBuilder.class);

    /** output directory name */
    private File outDir;

    /**
     * Create the output directory.
     */
    public PegFastaBuilder(File directory, boolean clearFlag) throws IOException {
        if (! directory.isDirectory()) {
            log.info("Creating output directory {}.", directory);
            FileUtils.forceMkdir(directory);
        } else if (clearFlag) {
            log.info("Erasing output directory {}.", directory);
            FileUtils.cleanDirectory(directory);
        } else
            log.info("Output will be to directory {}.", directory);
        this.outDir = directory;
    }

    @Override
    public boolean contains(String genomeId) {
        File gFile = getFileName(genomeId);
        return gFile.exists();
    }

    /**
     * @return the name of the file for the specified genome ID
     *
     * @param genomeId	ID of the genome to look for
     * @return
     */
    protected File getFileName(String genomeId) {
        File gFile = new File(this.outDir, genomeId + ".faa");
        return gFile;
    }

    @Override
    public void add(Genome genome) throws IOException {
        File gFile = this.getFileName(genome.getId());
        try (FastaOutputStream outStream = new FastaOutputStream(gFile)) {
            for (Feature peg : genome.getPegs()) {
                String prot = peg.getProteinTranslation();
                if (! StringUtils.isBlank(prot)) {
                     Sequence seq = new Sequence(peg.getId(), peg.getPegFunction(), prot);
                     outStream.write(seq);
                }
            }
        }
    }

    @Override
    public void finish() {
    }

}
