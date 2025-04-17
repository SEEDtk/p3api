/**
 *
 */
package org.theseed.genome.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.TextStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.genome.Contig;
import org.theseed.genome.Feature;
import org.theseed.genome.Genome;
import org.theseed.genome.TaxItem;
import org.theseed.io.LineReader;
import org.theseed.io.MarkerFile;
import org.theseed.io.TabbedLineReader;
import org.theseed.locations.Location;
import org.theseed.p3api.P3Connection;
import org.theseed.sequence.FastaInputStream;
import org.theseed.sequence.Sequence;

/**
 * This class constructs a genome from a CoreSEED genome directory.
 *
 * @author Bruce Parrello
 *
 */
public class CoreGenome extends Genome {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(CoreGenome.class);
    /** list of valid domains */
    public static final Set<String> DOMAINS = (Set<String>) Stream.of("Bacteria", "Archaea", "Eukaryota", "Viruses")
              .collect(Collectors.toCollection(HashSet::new));
    /** list of protein feature types */
    protected static final Set<String> PROTEINS = (Set<String>) Stream.of("peg", "mp")
            .collect(Collectors.toCollection(HashSet::new));
    /** empty protein map (for non-proteins) */
    private static final Map<String, String> EMPTY_MAP = new HashMap<String, String>(5);
    /** empty alias set */
    private static final Set<String> NO_ALIASES = Collections.emptySet();
    /** organism directory */
    private File orgDir;
    /** connection to PATRIC */
    private P3Connection p3;
    /** completeness flag */
    private boolean complete;

    /**
     * Construct a coreSEED genome from a genome directory.
     *
     * @param p3		connection to PATRIC
     * @param inDir		organism directory for the genome; the base name must be the genome ID
     *
     * @throws IOException
     */
    public CoreGenome(P3Connection p3, File inDir) throws IOException {
        super(inDir.getName());
        log.info("Loading SEED genome from {}.", inDir);
        this.setHome("CORE");
        this.setSource("RAST");
        // Save the directory and the PATRIC connection.
        this.orgDir = inDir;
        this.p3 = p3;
        // Compute the taxonomy, scientific name, domain, and genetic code.
        this.computeTaxonomy();
        // Read the contigs.
        this.readContigs();
        // Read all the features.
        this.readFeatures();
        // Read the annotations.
        this.readAnnotations();
        // Set the completeness flag.
        File cFile = new File(inDir, "COMPLETE");
        this.complete = cFile.exists();
    }

    /**
     * Read the annotations file and store the annotations in the features.
     *
     * @throws IOException
     */
    private void readAnnotations() throws IOException {
        File annoFile = new File(this.orgDir, "annotations");
        if (! annoFile.exists())
            log.warn("No annotations file found in genome directory {}.", this.orgDir);
        else {
            try (LineReader annoStream = new LineReader(annoFile)) {
                // We will accumulate the annotation in here.
                List<String> annotation = new ArrayList<String>(10);
                // Loop through the input.  We process an annotation at each "//" marker.
                int lineCount = 0;
                for (String line : annoStream) {
                    lineCount++;
                    if (line.contentEquals("//")) {
                        // Here we have a full annotation.
                        if (annotation.size() < 4)
                            log.warn("Skipping invalid annotation in line {} of {}.", lineCount, annoFile);
                        else {
                            String fid = annotation.get(0);
                            Feature feat = this.getFeature(fid);
                            // If the annotation is for a deleted feature, the above call will return NULL.
                            if (feat != null) {
                                TextStringBuilder comment = new TextStringBuilder(300);
                                for (int i = 3; i < annotation.size(); i++)
                                    comment.appendSeparator('\n').append(annotation.get(i));
                                double timeStamp = Double.valueOf(annotation.get(1));
                                feat.addAnnotation(comment.toString(), timeStamp, annotation.get(2));
                            }
                        }
                        annotation.clear();
                    } else {
                        annotation.add(line);
                    }
                }
            }
        }
    }

    /**
     * Fill in the taxonomic and name information for the genome.
     *
     * @throws FileNotFoundException
     */
    private void computeTaxonomy() throws FileNotFoundException {
        // First, get the genome name.
        String name = this.readFlag("GENOME");
        if (name == null) {
            log.error("No GENOME file found in " + this.orgDir);
            this.setName("Unknown genome in directory " + this.orgDir);
        } else {
            this.setName(name);
        }
        // Now, get the taxonomy ID.
        String taxId = this.readFlag("TAXONOMY_ID");
        if (taxId == null) {
            // If there is no taxonomy ID, we use the first part of the genome ID.
            taxId = StringUtils.substringBefore(this.getId(), ".");
        }
        // Convert the tax ID to a number.
        int taxIdNum = Integer.valueOf(taxId);
        // Read the domain from the taxonomy file.
        String domain;
        String taxonomy = this.readFlag("TAXONOMY");
        if (taxonomy == null) {
            // No taxonomy.  Default to Bacteria.
            domain = "Bacteria";
        } else {
            // The domain is the first taxonomic grouping.
            domain = StringUtils.substringBefore(taxonomy, ";");
            if (! DOMAINS.contains(domain))
                log.warn("Invalid domain \"{}\" in taxonomy of {}.", domain, this.orgDir);
        }
        // Store the domain and the taxonomy ID.
        this.setDomain(domain);
        this.setTaxonomyId(taxIdNum);
        // Compute and store the genome lineage.
        boolean ok = this.p3.computeLineage(this, taxIdNum);
        if (! ok)
            log.warn("Could not compute taxonomic lineage for genome in {}.", this.orgDir);
        else {
            // Verify the lineage.
            ok = false;
            Iterator<TaxItem> taxIter = this.taxonomy();
            while (! ok && taxIter.hasNext()) {
                TaxItem taxItem = taxIter.next();
                ok = taxItem.getName().contentEquals(domain);
            }
            if (! ok)
                log.error("Taxonomic ID {} for genome in {} has wrong domain.", taxId, this.orgDir);
        }
    }

    /**
     * Read all the features from the feature directories.
     *
     * @throws IOException
     */
    private void readFeatures() throws IOException {
        // The first task is to get the assigned functions.  We will pull in the functions for deleted as
        // well as real pegs.  If there are duplicates, the second assignment wins.
        log.debug("Processing functions.");
        Map<String, String> functionMap = this.readMap("assigned_functions", 2);
        // Now we do the same thing with protein families, but this is tricky since both types are in the
        // same file.
        log.debug("Processing protein families.");
        Map<String, String> gFamilyMap = new HashMap<String, String>(functionMap.size());
        Map<String, String> lFamilyMap = new HashMap<String, String>(functionMap.size());
        File famFile = new File(this.orgDir, "pattyfams.txt");
        if (! famFile.exists())
            log.warn("No protein family file found in {}.", this.orgDir);
        else {
            try (TabbedLineReader famStream = new TabbedLineReader(famFile, 4)) {
                for (TabbedLineReader.Line line : famStream) {
                    String fid = line.get(0);
                    String famId = line.get(1);
                    if (famId.startsWith("PGF"))
                        gFamilyMap.put(fid, famId);
                    else
                        lFamilyMap.put(fid, famId);
                }
            }
        }
        // The actual processing of the features requires looping through subdirectories.
        File featureDir = new File(this.orgDir, "Features");
        File[] typeDirs = featureDir.listFiles(File::isDirectory);
        if (typeDirs == null)
            log.warn("No feature directories for {}.", this.orgDir);
        else {
            for (File typeDir : typeDirs) {
                log.debug("Processing feature directory {}.", typeDir);
                // Get the set of deleted features.
                Set<String> deleted = this.readSet(new File(typeDir, "deleted.features"));
                // Determine if this is an aSDomain.
                boolean asFlag = typeDir.getName().contentEquals("aSDomain");
                // If this is a protein type, get the protein translations.  If it's an aSDomain, we need a blank map.
                Map<String, String> proteinMap = EMPTY_MAP;
                if (PROTEINS.contains(typeDir.getName()))
                    proteinMap = this.readProteins(typeDir);
                else if (asFlag)
                    proteinMap = new HashMap<String, String>();
                // Get the locations and the aliases.
                Map<String, Location> locationMap = new HashMap<String, Location>();
                Map<String, Collection<String>> aliasMap = new HashMap<String, Collection<String>>();
                File tblFile = new File(typeDir, "tbl");
                if (! tblFile.exists()) {
                    // Here we have no location data.  We have to create dummy locations for
                    // every known protein.
                    log.warn("No features in type directory {}.", typeDir);
                    for (String fid : proteinMap.keySet()) {
                        locationMap.put(fid, null);
                        aliasMap.put(fid, NO_ALIASES);
                    }
                } else {
                    // Here we have location data.
                    try (LineReader tblStream = new LineReader(tblFile)) {
                        for (String line : tblStream) {
                            String[] fields = StringUtils.split(line, '\t');
                            String fid = fields[0];
                            // Only proceed if the feature is not deleted.
                            if (! deleted.contains(fid)) {
                                if (fields.length < 2 || fields[1].isEmpty())
                                    log.warn("Location missing from feature line {} in {}.", fields[0], typeDir);
                                else {
                                    // We need to compute the location from the second column.  We start by
                                    // parsing the first region to get the contig and strand.
                                    String[] regions = StringUtils.split(fields[1]);
                                    Location loc = Location.parseSeedLocation(regions[0]);
                                    for (int i = 1; i < regions.length; i++) {
                                        Location locI = Location.parseSeedLocation(regions[i]);
                                        loc.add(locI);
                                    }
                                    locationMap.put(fid, loc);
                                    // If this is aSDomain, the alias is a protein translation. Otherwise,
                                    // we need to collect the aliases.
                                    Collection<String> aliases = Collections.emptyList();
                                    if (asFlag) {
                                        proteinMap.put(fid, fields[2]);
                                    } else if (fields.length >= 3) {
                                        // Otherwise we do the aliases.
                                        aliases = new ArrayList<String>(fields.length - 2);
                                        for (int i = 2; i < fields.length; i++) {
                                            if (! fields[i].isEmpty())
                                                aliases.add(fields[i]);
                                        }
                                    }
                                    aliasMap.put(fid, aliases);
                                }
                            }
                        }
                    }
                }
                // Loop through the features we are keeping.
                for (Map.Entry<String, Location> featureLocation : locationMap.entrySet()) {
                    String fid = featureLocation.getKey();
                    Location loc = featureLocation.getValue();
                    String function = functionMap.getOrDefault(fid, "");
                    Feature feat = new Feature(fid, function, loc);
                    this.addFeature(feat);
                    // Check for aliases. Each alias is of the form type|value.
                    Collection<String> aliases = aliasMap.get(fid);
                    for (String alias : aliases) {
                        String[] parts = StringUtils.split(alias, "|", 2);
                        if (parts.length == 1)
                            feat.addAlias("misc", alias);
                        else
                            feat.addAlias(parts[0], parts[1]);
                    }
                    // Check for a protein translation.
                    if (proteinMap.containsKey(fid))
                        feat.setProteinTranslation(proteinMap.get(fid));
                    // Check for protein families.
                    if (lFamilyMap.containsKey(fid))
                        feat.setPlfam(lFamilyMap.get(fid));
                    if (gFamilyMap.containsKey(fid))
                        feat.setPgfam(gFamilyMap.get(fid));
                    // Add the feature to the genome.
                    this.addFeature(feat);
                }
            }
        }
    }

    /**
     * Read in a map of proteins from the FASTA file in the specified type directory.
     *
     * @param typeDir	type directory of interest
     *
     * @return a map from feature IDs to protein sequences
     *
     * @throws FileNotFoundException
     */
    private Map<String, String> readProteins(File typeDir) throws FileNotFoundException {
        Map<String, String> retVal = null;
        File proteinFile = new File(typeDir, "fasta");
        if (! proteinFile.exists()) {
            log.warn("No FASTA file found in protein directory {}.", typeDir);
            retVal = Collections.emptyMap();
        } else {
            retVal = new HashMap<String, String>(2000);
            try (FastaInputStream inStream = new FastaInputStream(proteinFile)) {
                for (Sequence peg : inStream)
                    retVal.put(peg.getLabel(), peg.getSequence());
            }
        }
        return retVal;
    }

    /**
     * Read in the contigs.
     *
     * @throws FileNotFoundException
     */
    private void readContigs() throws FileNotFoundException {
        // Get the contig FASTA file.
        File fastaFile = new File(this.orgDir, "contigs");
        if (! fastaFile.exists())
            log.error("No contig file found for genome directory {}.", this.orgDir);
        else {
            log.debug("Reading contigs.");
            try (FastaInputStream fastaStream = new FastaInputStream(fastaFile)) {
                for (Sequence seq : fastaStream) {
                    Contig contig = new Contig(seq.getLabel(), seq.getSequence(), this.getGeneticCode());
                    this.addContig(contig);
                }
            }
        }
    }

    /**
     * Read a coreSEED flag file.  Such files are always a single line of text.
     *
     * @param flagName	name of the flag file to read
     *
     * @return the content of the file, or NULL if it is not found
     */
    private String readFlag(String flagName) {
        File inFile = new File(this.orgDir, flagName);
        String retVal = null;
        if (! inFile.canRead()) {
            log.warn("No {} file found in organism directory {}.", flagName, this.orgDir);
        } else {
            retVal = MarkerFile.read(inFile);
        }
        return retVal;
    }

    /**
     * Read a map file.  Map files are headerless, and always have a key in the first column and
     * a value in the second.  If there are duplicate keys, the second one overrides.
     *
     * @param mapName	name of map file to read
     * @param width		total number of columns in the file (this affects performance only, but must be >= 2)
     *
     * @return a string map read from the file
     */
    protected Map<String, String> readMap(String mapName, int width) {
        File mapFile = new File(this.orgDir, mapName);
        Map<String, String> retVal = readMap(mapFile, width);
        return retVal;
    }

    /**
     * Read a map file.  Map files are headerless, and always have a key in the first column and
     * a value in the second.  If there are duplicate keys, the second one overrides.
     *
     * @param mapFile	map file to read
     * @param width		total number of columns in the file (this affects performance only, but must be >= 2)
     *
     * @return a string map read from the file
     */
    protected Map<String, String> readMap(File mapFile, int width) {
        Map<String, String> retVal = new HashMap<String, String>();
        if (! mapFile.exists())
            log.warn("No {} file found in genome directory {}.", mapFile, this.orgDir);
        else {
            try (TabbedLineReader mapStream = new TabbedLineReader(mapFile, width)) {
                for (TabbedLineReader.Line line : mapStream)
                    retVal.put(line.get(0), line.get(1));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return retVal;
    }

    /**
     * Read a set file.  Set files are headerless, and have a single column containing a key.
     * A missing set file does not even generate a warning.
     *
     * @param setName	name of set file to read
     *
     * @return a string set read from the file
     */
    protected Set<String> readSet(String setName) {
        File setFile = new File(this.orgDir, setName);
        Set<String> retVal = readSet(setFile);
        return retVal;
    }

    /**
     * Read a set file.  Set files are headerless, and have a single column containing a key.
     * A missing set file does not even generate a warning.
     *
     * @param setrFile	set file to read
     *
     * @return a string set read from the file
     */
    protected Set<String> readSet(File setFile) {
        Set<String> retVal = new HashSet<String>();
        if (setFile.exists()) {
            try (TabbedLineReader setStream = new TabbedLineReader(setFile, 1)) {
                for (TabbedLineReader.Line line : setStream)
                    retVal.add(line.get(0));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return retVal;
    }

    /**
     * @return TRUE if this genome is complete
     */
    @Override
    public boolean isComplete() {
        return this.complete;
    }

}
