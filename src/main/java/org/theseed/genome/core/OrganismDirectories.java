/**
 *
 */
package org.theseed.genome.core;

import java.io.File;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * This class iterates through all the SEED genome directories in an Organism directory.
 * The genome IDs are returned as strings.
 *
 * @author Bruce Parrello
 *
 */
public class OrganismDirectories implements Iterable<String> {

    // FIELDS
    /** list of genome IDs found */
    private SortedMap<String, File> genomes;
    /** base organism directory */
    private File baseDir;

    // CONSTANTS
    private static final Pattern GENOME_PATTERN = Pattern.compile("\\d+\\.\\d+");

    /**
     * Create a new organism directory iterator.
     *
     * @param orgDir	root organism directory
     */
    public OrganismDirectories(File orgDir) {
        this.baseDir = orgDir;
        // Get all the subdirectories and files.
        File[] subFiles = orgDir.listFiles();
        // Initialize the output list.
        this.genomes = new TreeMap<String, File>();
        for (File subFile : subFiles) {
            String genomeId = subFile.getName();
            if (GENOME_PATTERN.matcher(genomeId).matches() && subFile.isDirectory()) {
                // Here we have a valid genome directory.  Insure it's not deleted.
                File deleteFile = new File(subFile, "DELETED");
                if (! deleteFile.exists())
                    genomes.put(genomeId, subFile);
            }
        }
    }

    @Override
    public Iterator<String> iterator() {
        return this.genomes.keySet().iterator();
    }

    /**
     * @return the number of genomes
     */
    public int size() {
        return this.genomes.size();
    }

    /**
     * @return the directory for a genome (or NULL if none exists)
     */
    public File getDir(String genomeId) {
        return this.genomes.get(genomeId);
    }

    /**
     * @return the directory of a genome, adding it if it doesn't exist
     *
     * @param genomeId	ID of the genome of interest
     */
    public File computeDir(String genomeId) {
        return this.genomes.computeIfAbsent(genomeId, x -> new File(this.baseDir, x));
    }

    /**
     * @return the base organism directory
     */
    public File getBaseDir() {
        return this.baseDir;
    }

    /**
     * @return TRUE if the genome is present in this directory
     *
     * @param genomeId	ID of the genome of interest
     */
    public boolean contains(String genomeId) {
        return this.genomes.containsKey(genomeId);
    }

    /**
     * @return the IDs of all the genomes in this directory
     */
    public Set<String> getIDs() {
        return this.genomes.keySet();
    }


}
