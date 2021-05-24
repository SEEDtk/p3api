/**
 *
 */
package org.theseed.genome.iterator;

import java.io.File;
import java.io.IOException;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.genome.Contig;
import org.theseed.genome.Genome;
import org.theseed.sequence.FastaOutputStream;
import org.theseed.sequence.Sequence;

/**
 * This is a genome target that creates contig FASTA files for all the genomes.  Each genome is written to an FNA
 * file whose name is based on the genome ID.
 *
 * @author Bruce Parrello
 *
 */
public class ContigFastaBuilder implements IGenomeTarget {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(ContigFastaBuilder.class);
    /** output directory name */
    private File outDir;
    /** map of genome IDs to file names */
    private SortedMap<String, File> genomeMap;
    /** file name pattern */
    private static final Pattern GENOME_FASTA_FILE = Pattern.compile("(\\d+\\.\\d+)\\.fna");


    public ContigFastaBuilder(File dir, boolean clearFlag) throws IOException {
        this.outDir = dir;
        this.genomeMap = new TreeMap<String, File>();
        if (this.outDir.isDirectory()) {
            if (clearFlag) {
                log.info("Erasing output directory {}.", dir);
                FileUtils.cleanDirectory(dir);
            } else {
                // Search for genome FASTA files in the directory.
                for (File file : dir.listFiles()) {
                    if (! file.isDirectory()) {
                        Matcher m = GENOME_FASTA_FILE.matcher(file.getName());
                        if (m.matches())
                            this.genomeMap.put(m.group(1), file);
                    }
                }
            }
        } else {
            log.info("Creating output directory {}.", dir);
            FileUtils.forceMkdir(dir);
        }
    }

    @Override
    public boolean contains(String genomeId) {
        return this.genomeMap.containsKey(genomeId);
    }

    @Override
    public void add(Genome genome) throws IOException {
        File genomeFile = this.genomeMap.computeIfAbsent(genome.getId(), x -> new File(this.outDir, x + ".fna"));
        try (FastaOutputStream contigStream = new FastaOutputStream(genomeFile)) {
            for (Contig contig : genome.getContigs()) {
                Sequence seq = new Sequence(contig.getId(), contig.getDescription(), contig.getSequence());
                contigStream.write(seq);
            }
        }
    }

    @Override
    public void finish() {
    }

    @Override
    public String toString() {
        return "Contig Fasta Directory " + this.outDir;
    }

}
