/**
 *
 */
package org.theseed.genome.iterator;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.genome.Genome;
import org.theseed.io.TabbedLineReader;

/**
 * This target is simple a flat file listing all the genomes in the input source.
 *
 * @author Bruce Parrello
 *
 */
public class ListFileBuilder implements IGenomeTarget {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(ListFileBuilder.class);
    /** map of genome IDs to output lines */
    private Map<String, String> genomes;
    /** output file name */
    private File outFile;
    /** output header */
    private static final String HEADER = "genome_id\tgenome_name\tdna_size\tpegs\n";

    /**
     * Create the list writer.  We open the file for output or append.  If it already exists, we load in
     * the genomes already present.
     *
     * @param listFile		output file
     * @param clearFlag		TRUE to erase the file before starting
     */
    public ListFileBuilder(File listFile, boolean clearFlag) throws IOException {
        // Save the output file name.
        this.outFile = listFile;
        // Start with an empty set of genomes already present.
        this.genomes = new TreeMap<String, String>();
        if (listFile.exists() && ! clearFlag) {
            // Here we need to read the file and fill in the current genomes.
            try (TabbedLineReader inStream = new TabbedLineReader(listFile)) {
                for (TabbedLineReader.Line line : inStream) {
                    String genomeId = line.get(0);
                    String lineString = line.toString();
                    this.genomes.put(genomeId, lineString);
                }
            }
            log.info("{} genomes found in existing output file {}.", this.genomes.size(), listFile);
        }
    }

    @Override
    public boolean contains(String genomeId) {
        return this.genomes.containsKey(genomeId);
    }

    @Override
    public void add(Genome genome) throws IOException {
        // Format the output line.
        String outLine = String.format("%s\t%s\t%d\t%d", genome.getId(), genome.getName(), genome.getLength(),
                genome.getPegs().size());
        this.genomes.put(genome.getId(), outLine);
    }

    @Override
    public void finish() {
        // Open the file for output.
        try (PrintWriter writer = new PrintWriter(this.outFile)) {
            // Write out the header.
            writer.println(HEADER);
            // Write out the genomes.
            for (String gLine : this.genomes.values())
                writer.println(gLine);
            // Flush the output.
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void remove(String genomeId) throws IOException {
        throw new UnsupportedOperationException("Cannot delete a genome from a list file.");
    }

    @Override
    public boolean canDelete() {
        return false;
    }

    @Override
    public Set<String> getGenomeIDs() {
        throw new UnsupportedOperationException("List file targets do not support this operation.");
    }



}
