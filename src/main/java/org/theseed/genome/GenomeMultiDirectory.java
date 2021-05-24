/**
 *
 */
package org.theseed.genome;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.genome.iterator.IGenomeTarget;
import org.theseed.io.LineReader;

/**
 * This object maintains a directory of PATRIC GTO objects at a specified detail level.  The files are kept
 * in multiple subdirectories to prevent directory overload. An in-memory hash maps each genome ID to a file name.
 * Methods are provided to iterate through all the GTOs, to retrieve them by ID, and to add new GTOs.
 *
 * When iterating, the genomes are returned in sorted order.
 *
 * The subdirectories are numbered starting from zero.  Genomes are always added to the last directory, until
 * it fills and a new subdirectory is created.
 *
 * Each GTO *must* have its genome ID plus ".gto" as the file name.  This is not a requirement for general genome
 * directories, but is strictly enforced here.  Copying in files with wrong names will cause chaos.
 *
 * @author Bruce Parrello
 *
 */
public class GenomeMultiDirectory implements Iterable<Genome>, IGenomeTarget {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(GenomeMultiDirectory.class);
    /** main directory name */
    private File masterDir;
    /** map of genome IDs to files */
    private SortedMap<String, File> gtoMap;
    /** number of last directory */
    private int newDirNum;
    /** name of the last directory */
    private File newDir;
    /** number of files in last directory */
    private int newDirSize;

    /** maximum number of files allowed per sub-directory */
    public static int MAX_FILES_PER_DIRECTORY = 5000;
    /** filename extension */
    protected static final String EXTENSION = ".gtoz";
    /** filename filter */
    private static final FileFilter FILE_FILTER = new WildcardFileFilter("*.gtoz");

    /**
     * Create a new, empty master directory.
     *
     * @param master		name of the master directory
     * @param clearFlag		TRUE if it is ok to erase an existing master directory
     *
     * @throws IOException
     */
    public static GenomeMultiDirectory create(File master, boolean clearFlag) throws IOException {
        if (! master.isDirectory()) {
            log.info("Creating master genome directory {}.", master);
            FileUtils.forceMkdir(master);
        } else if (clearFlag) {
            log.info("Erasing master genome directory {}.", master);
            FileUtils.cleanDirectory(master);
        } else
            throw new IOException("Cannot create a master genome directory in existing directory " +  master + ".");
        // Create the first sub-directory.
        File zeroDir = new File(master, "0");
        FileUtils.forceMkdir(zeroDir);
        return new GenomeMultiDirectory(master);
    }

    /**
     * Load an existing master directory.
     *
     * @param master	name of the master directory
     */
    public GenomeMultiDirectory(File master) {
        log.info("Loading master genome directory {}.", master);
        this.masterDir = master;
        this.newDirNum = -1;
        this.newDir = null;
        // Get the subdirectory list.
        File[] subDirs = this.masterDir.listFiles(File::isDirectory);
        // Create the file map.
        this.gtoMap = new TreeMap<String, File>();
        // Loop through the sub-directories.
        for (File subDir : subDirs) {
            try {
                // Get the directory index number.
                int dirNum = Integer.parseInt(subDir.getName());
                // Get the GTOs in the directory.
                File[] gtoFiles = subDir.listFiles(FILE_FILTER);
                for (File gtoFile : gtoFiles) {
                    String genomeId = StringUtils.substring(gtoFile.getName(), 0, -EXTENSION.length());
                    // If there is a duplicate, choose the most recent.
                    File oldFile = this.gtoMap.get(genomeId);
                    if (oldFile == null || gtoFile.lastModified() > oldFile.lastModified())
                        this.gtoMap.put(genomeId, gtoFile);
                }
                if (dirNum > newDirNum) {
                    newDir = subDir;
                    newDirNum = dirNum;
                    newDirSize = gtoFiles.length;
                }
                log.debug("{} genomes found.", this.gtoMap.size());
            } catch (NumberFormatException e) {
                log.warn("Skipping unexpected subdirectory {}.", subDir);
            }
        }
        log.info("{} genomes found in {}.", this.gtoMap.size(), master);
    }

    /**
     * @return the number of genomes in this master directory
     */
    public int size() {
        return this.gtoMap.size();
    }

    /**
     * Remove a genome from the directory.
     *
     * @param genomeId	ID of the genome to remove
     *
     * @throws IOException
     */
    public void remove(String genomeId) throws IOException {
        File gFile = this.gtoMap.get(genomeId);
        // Only proceed if the genome is found.
        if (gFile != null) {
            this.gtoMap.remove(genomeId);
            removeGenomeFile(gFile);
        }
    }

    /**
     * Remove a GTO from the directories.  This does not alter the hash, so it is used by both the public
     * removal call and the iterator.
     *
     * @param gFile		name of the GTO file
     *
     * @throws IOException
     */
    private void removeGenomeFile(File gFile) throws IOException {
        FileUtils.forceDelete(gFile);
        // Update the counter for the directory if it's the currently-building one.
        if (gFile.getParentFile().equals(this.newDir))
            this.newDirSize--;
    }

    /**
     * Add a genome to the directories.  Note we write it in compressed format.
     * If the genome already exists, it will be overwritten.
     *
     * @param gto	genome to add
     */
    public void add(Genome genome) throws IOException {
        // Insure there is room.
        if (this.newDirSize >= MAX_FILES_PER_DIRECTORY) {
            this.newDirNum++;
            this.newDir = new File(this.masterDir, Integer.toString(this.newDirNum));
            this.newDirSize = 0;
            FileUtils.forceMkdir(this.newDir);
        }
        // Check for an existing version.
        String genomeId = genome.getId();
        File gFile = this.gtoMap.get(genomeId);
        if (gFile == null)
            gFile = new File(this.newDir, genomeId + EXTENSION);
        // Note we write using GZIP compression.
        try (FileOutputStream outStream = new FileOutputStream(gFile);
                GZIPOutputStream zipStream = new GZIPOutputStream(outStream);
                PrintWriter writer = new PrintWriter(zipStream)) {
            writer.println(genome.toJsonString());
        }
        this.gtoMap.put(genomeId, gFile);
        this.newDirSize++;
        log.info("{} stored in {} with length {}.", genome, gFile, gFile.length());
    }

    @Override
    public Iterator<Genome> iterator() {
        return this.new Iter();
    }

    /**
     * @return an iterator through this object's genome IDs
     */
    public Iterator<String> idIterator() {
        return this.gtoMap.keySet().iterator();
    }

    /**
     * This is the class for iterating through the genomes.  It supports removal; however,
     * adding a genome during iteration will cause a ConcurrentMapException.
     */
    public class Iter implements Iterator<Genome> {

        /** main hash iterator */
        private Iterator<Map.Entry<String, File>> iter;
        /** last entry returned */
        private Map.Entry<String, File> curr;

        /**
         * Construct an iterator for the genomes.
         */
        private Iter() {
            this.iter = GenomeMultiDirectory.this.gtoMap.entrySet().iterator();
        }

        @Override
        public boolean hasNext() {
            return this.iter.hasNext();
        }

        @Override
        public Genome next() {
            Genome retVal;
            // Save the current genome entry and get the file.
            this.curr = this.iter.next();
            File gFile = this.curr.getValue();
            // Load the genome from the file and return it.  Note we have to convert the IO error
            // to unchecked to satisfy the interface requirements.
            try {
                retVal = GenomeMultiDirectory.this.readGenome(gFile);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return retVal;
        }

        @Override
        public void remove() {
            // Delete the current entry from the map.
            this.iter.remove();
            // Get the genome file and remove it from disk.  Once again, the IO error has to be
            // made unchecked to satisfy the iterator interface definition.
            File gFile = this.curr.getValue();
            try {
                GenomeMultiDirectory.this.removeGenomeFile(gFile);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

    }

    /**
     * @return the genome with the specified ID (or NULL if it is not found)
     *
     * @param genomeId	ID of genome to retrieve
     */
    public Genome get(String genomeId) {
        Genome retVal = null;
        File gFile = this.gtoMap.get(genomeId);
        if (gFile != null) try {
            retVal = this.readGenome(gFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return retVal;
    }

    /**
     * @return the genome in the specified file
     *
     * @param gFile		name of the file containin the genome
     */
    private Genome readGenome(File gFile) throws IOException {
        Genome retVal;
        // Note we need to read from a gzip-compressed file.
        try (FileInputStream inStream = new FileInputStream(gFile);
                GZIPInputStream zipStream = new GZIPInputStream(inStream);
                LineReader reader = new LineReader(zipStream)) {
            retVal = Genome.fromJson(reader.next());
        }
        return retVal;
    }

    /**
     * @return TRUE if the genome is found in this directory, else FALSE
     *
     * @param genomeId	ID of genome to check
     */
     public boolean contains(String genomeId) {
         return this.gtoMap.containsKey(genomeId);
     }

     /**
      * @return the name of the last directory being filled
      */
     public File getLastDir() {
         return this.newDir;
     }

     /**
      * @return the number of files in the last directory being filled
      */
     protected int getLastDirSize() {
         return this.newDirSize;
     }

    /**
     * @return the full set of available genome IDs
     */
    public Set<String> getIDs() {
        return this.gtoMap.keySet();
    }

    @Override
    public void finish() {
    }

    @Override
    public String toString() {
        return "GenomeM Master Directory " + this.masterDir.toString();
    }

}
