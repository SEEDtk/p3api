
/**
 *
 */
package org.theseed.genome.iterator;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.genome.Feature;
import org.theseed.genome.Genome;
import org.theseed.sequence.FastaInputStream;
import org.theseed.sequence.FastaOutputStream;
import org.theseed.sequence.Sequence;

/**
 * This is a genome target that builds a protein FASTA file.  A temporary directory is created, and the genomes are stored
 * in individual FASTA files.  When the object is closed, all the temp files are merged and the temp directory is deleted.
 *
 * @author Bruce Parrello
 *
 */
public class ProteinFastaBuilder implements IGenomeTarget {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(ProteinFastaBuilder.class);
    /** temp directory containing saved FASTA files */
    private File tempDir;
    /** map of genome IDs to file names */
    private Map<String, File> fileMap;
    /** output file name */
    private File outFile;

    /**
     * Create a protein fasta file manager.
     *
     * @param fastaFile		FASTA file to manage
     * @param clearFlag		TRUE to delete all the existing data
     */
    public ProteinFastaBuilder(File fastaFile, boolean clearFlag) {
        // Save the output file name.
        this.outFile = fastaFile;
        try {
            // Create the genome map.  Note that we use a tree map so that the output file is ordered by genome ID.
            this.fileMap = new TreeMap<String, File>();
            // Create the temporary directory.
            Path dirPath = fastaFile.getAbsoluteFile().getParentFile().toPath();
            this.tempDir = Files.createTempDirectory(dirPath, "fasta").toFile();
            if (fastaFile.exists() && ! clearFlag) {
                // Here we want to preserve the genomes already in the file.  We presume that we created the file, which
                // means that it is sorted in genome order.  If this is NOT the case, an error will be thrown.
                // The following variables contain the genome ID and sequence set for the current genome.
                String genomeId = "";
                Set<Sequence> sequences = new HashSet<Sequence>(4000);
                try (FastaInputStream inStream = new FastaInputStream(fastaFile)) {
                    log.info("Reading existing genomes from {}.", fastaFile);
                    for (Sequence seq : inStream) {
                        String newGenomeId = Feature.genomeOf(seq.getLabel());
                        if (! newGenomeId.contentEquals(genomeId)) {
                            this.writeGenomeSequences(genomeId, sequences);
                            genomeId = newGenomeId;
                            sequences.clear();
                        }
                        sequences.add(seq);
                    }
                    // Flush out the residual genome.
                    this.writeGenomeSequences(genomeId, sequences);
                    log.info("{} genomes found in input file {}.", this.fileMap.size(), this.outFile);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Here we are writing genome sequences during the initial load.  We verify that they are not duplicates.
     *
     * @param genomeId		ID of the genome to which the sequences belong
     * @param sequences		set of sequences to write
     *
     * @throws IOException
     */
    private void writeGenomeSequences(String genomeId, Collection<Sequence> sequences) throws IOException {
        // Don't bother unless we have at least one sequence.
        if (! sequences.isEmpty()) {
            // Do we already have a file?
            if (this.fileMap.containsKey(genomeId))
                throw new IOException("Duplicate genome " + genomeId + " in " + this.outFile + ".  File must be sorted by genome Id.");
            // Here we can go ahead.
            this.writeGenomeFile(genomeId, sequences);
        }
    }

    /**
     * Write sequences to a genome's temporary file.
     *
     * @param genomeId		ID of the genome of interest
     * @param sequences		sequences to write
     *
     * @throws IOException
     */
    private void writeGenomeFile(String genomeId, Collection<Sequence> sequences) throws IOException {
        File outFile = new File(this.tempDir, genomeId + ".fasta");
        log.info("Writing {} sequences for genome {}.", sequences.size(), genomeId);
        try (FastaOutputStream outStream = new FastaOutputStream(outFile)) {
            outStream.write(sequences);
        }
        // Save the file name in the file map for this genome ID.
        this.fileMap.put(genomeId, outFile);
    }

    @Override
    public boolean contains(String genomeId) {
        return this.fileMap.containsKey(genomeId);
    }

    @Override
    public void add(Genome genome) throws IOException {
        // Extract all the pegs of the genome as sequences.
        List<Sequence> seqs = new ArrayList<Sequence>(genome.getFeatureCount());
        for (Feature feat : genome.getPegs()) {
            // Only proceed if this peg has a real protein translation.
            String prot = feat.getProteinTranslation();
            if (prot != null && ! prot.isEmpty())
                seqs.add(new Sequence(feat.getId(), feat.getPegFunction(), prot));
        }
        this.writeGenomeFile(genome.getId(), seqs);
    }

    @Override
    public void finish() {
        // Now we need to unspool all the saved genomes to the output file.
        try {
            try (FastaOutputStream outStream = new FastaOutputStream(this.outFile)) {
                // Loop through the saved genomes.
                int count = 0;
                int total = this.fileMap.size();
                for (File gFile : this.fileMap.values()) {
                    count++;
                    log.info("Copying genome {} of {} from file {}.", count, total, gFile);
                    try (FastaInputStream inStream = new FastaInputStream(gFile)) {
                        for (Sequence seq : inStream)
                            outStream.write(seq);
                    }
                }
            } finally {
                // Here we want to try to delete the temporary directory.  If an error occurs, we
                // issue a warning and keep going.  It's an annoyance, not a disaster.
                try {
                    log.info("Deleting temporary directory {}.", this.tempDir);
                    FileUtils.forceDelete(this.tempDir);
                } catch (IOException e) {
                    log.error("Could not delete {}: {}", this.tempDir, e.toString());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String toString() {
        return "Protein Fasta File " + this.outFile;
    }

    @Override
    public void remove(String genomeId) throws IOException {
        throw new UnsupportedOperationException("Cannot delete a genome from a protein fasta file.");
    }

    @Override
    public boolean canDelete() {
        return false;
    }

    @Override
    public Set<String> getGenomeIDs() {
        throw new UnsupportedOperationException("Protein FASTA files do not support this operation.");
    }

}
