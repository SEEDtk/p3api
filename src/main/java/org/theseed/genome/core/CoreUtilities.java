/**
 *
 */
package org.theseed.genome.core;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.io.TabbedLineReader;

/**
 * This class manages a coreSEED organism directory and provides useful utilities for accessing genome data.
 *
 * @author Bruce Parrello
 *
 */
public class CoreUtilities {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(CoreUtilities.class);
    /** base organism directory */
    private File orgDir;
    /** cache of genome sequences */
    private Map<String, PegList> genomeMap;
    /** path-and-name suffix to convert a genome ID to the complete path to the assigned-functions file */
    private static final String FUNCTION_FILE_SUFFIX = File.separator + "assigned_functions";
    /** path-and-name suffix to convert a genome ID to the complete path to the peg FASTA file */
    private static final String PEG_FILE_SUFFIX = File.separator + "Features" + File.separator + "peg" + File.separator + "fasta";
    /** genome ID extraction pattern */
    private static final Pattern GENOME_ID_PATTERN = Pattern.compile("fig\\|(\\d+\\.\\d+)\\.\\w+\\.\\d+");
    /** feature type extraction pattern */
    private static final Pattern FID_TYPE_PATTERN = Pattern.compile("fig\\|\\d+\\.\\d+\\.(\\w+)\\.\\d+");



    /**
     * Connect this object to an organism directory and initialize the genome cache.
     *
     * @param organismDir	target organism directory
     */
    public CoreUtilities(File organismDir) {
        this.orgDir = organismDir;
        this.genomeMap = new HashMap<String, PegList>(1500);
    }

    /**
     * Return the list of pegs in a genome.  A cache is maintained of genomes already found.
     *
     * @param orgDir	CoreSEED organism directory
     * @param genomeId	ID of genomes whose pegs are desired
     * @param gMap		hash of genome IDs to cached peg lists
     *
     * @return a PegList object for the identified genome, or NULL if the genome does not exist
     *
     * @throws IOException
     */
    public PegList getGenomePegs(String genomeId) throws IOException {
        PegList retVal = this.genomeMap.get(genomeId);
        if (retVal == null) {
            // Here we have to read the genome in.
            retVal = readGenomeSequences(genomeId);
            // Cache the genome in case it comes up again.
            this.genomeMap.put(genomeId, retVal);
        }
        return retVal;
    }

    /**
     * Get a table of all the protein sequences for the specified genome.
     *
     * @param genomeId	ID of the relevant genome
     *
     * @return a PegList of all the protein sequences
     *
     * @throws IOException
     */
    public PegList readGenomeSequences(String genomeId) throws IOException {
        PegList retVal = null;
        File pegFile = new File(this.orgDir, genomeId + PEG_FILE_SUFFIX);
        if (! pegFile.isFile()) {
            log.info("Could not find sequences for genome " + genomeId + ".");
        } else {
            // Here the genome exists.
            log.info("Reading sequences for genome " + genomeId + ".");
            retVal = new PegList(pegFile);
        }
        return retVal;
    }

    /**
     * @return a map of peg IDs to functions for a genome
     *
     * @param the genome ID
     *
     * @throws IOException
     */
    public Map<String, String> getGenomeFunctions(String genomeId) throws IOException {
        return getGenomeFunctions(genomeId, "peg");
    }

    /**
     * Extract the feature assignments for a given list of feature types in a single genome.
     * The assigned-functions file may contain multiple assignments for a given feature.  In
     * this case, the last one is kept.  In addition, we have to check the deleted-features
     * files.
     *
     * @param genomeId	ID of the source genome
     * @param type		array of feature types to include
     *
     * @return a map from feature IDs to assigned functions
     *
     * @throws IOException
     */
    public Map<String, String> getGenomeFunctions(String genomeId, String... type) throws IOException {
        Map<String, String> retVal = new HashMap<String, String>(6000);
        // This set will hold the deleted features.
        Set<String> deletedFids = new HashSet<String>(100);
        for (String type0 : type) {
            File deleteFile = new File(this.orgDir, genomeId + deletedFidSuffix(type0));
            if (deleteFile.exists()) {
                log.info("Reading deleted fids of type " + type0 + " for " + genomeId + ".");
                try (TabbedLineReader deleteReader = new TabbedLineReader(deleteFile, 1)) {
                    for (TabbedLineReader.Line line : deleteReader) {
                        String fid = line.get(0);
                        deletedFids.add(fid);
                    }
                }
            }
        }
        // Now, pull in all the un-deleted pegs, and map each peg to its function.  Because we are
        // storing the pegs in a map, only the last function will be kept, which is desired behavior.
        File functionFile = new File(this.orgDir, genomeId + FUNCTION_FILE_SUFFIX);
        try (TabbedLineReader functionReader = new TabbedLineReader(functionFile, 2)) {
            log.info("Reading assigned functions for " + genomeId + ".");
            for (TabbedLineReader.Line line : functionReader) {
                String fid = line.get(0);
                if (! deletedFids.contains(fid)) {
                    // Verify we have an acceptable type.  Most of the time we only have one,
                    // so there is a
                    Matcher m = FID_TYPE_PATTERN.matcher(fid);
                    if (m.matches()) {
                        String fType = m.group(1);
                        boolean ok = fType.contentEquals(type[0]);
                        for (int i = 1; i < type.length && ! ok; i++)
                            ok = fType.contentEquals(type[i]);
                        if (ok)
                            retVal.put(fid, line.get(1));
                    }
                }
            }
        }
        return retVal;
    }

    /**
     * @return an object for iterating through all the genomes
     */
    public Iterable<String> getGenomes() {
        log.info("Reading genomes from " + this.orgDir + ".");
        OrganismDirectories retVal = new OrganismDirectories(this.orgDir);
        log.info(retVal.size() + " genomes found.");
        return retVal;
    }

    /**
     * @return the genome ID for a feature ID
     *
     * @param fid	the feature ID from which the genome ID is to be computed
     */
    public static String genomeOf(String fid) {
        Matcher matcher = GENOME_ID_PATTERN.matcher(fid);
        String retVal = null;
        if (matcher.matches()) {
            retVal = matcher.group(1);
        }
        return retVal;
    }

    /**
     * @return the type for a feature ID, or NULL if the feature ID is invalid
     *
     * @param fid	the ID of the feature whose type code is desired
     */
    public static String typeOf(String fid) {
        Matcher m = FID_TYPE_PATTERN.matcher(fid);
        String retVal = null;
        if (m.matches())
            retVal = m.group(1);
        return retVal;
    }

    /**
     * @return the filename suffix for a deleted-features file of the specified type
     *
     * @param type	feature type
     */
    public static String deletedFidSuffix(String type) {
        String retVal =  File.separator + "Features" + File.separator + type + File.separator + "deleted.features";
        return retVal;
    }

}
