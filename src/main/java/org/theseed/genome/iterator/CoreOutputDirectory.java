/**
 *
 */
package org.theseed.genome.iterator;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.genome.Annotation;
import org.theseed.genome.Contig;
import org.theseed.genome.Feature;
import org.theseed.genome.Genome;
import org.theseed.genome.core.OrganismDirectories;
import org.theseed.io.MarkerFile;
import org.theseed.locations.Location;
import org.theseed.sequence.FastaOutputStream;
import org.theseed.sequence.Sequence;

/**
 * This object is a genome target for a CoreSEED organism directory.  It will output the main files used by the CoreGenome
 * object and the basic FIG scripts-- annotations, assigned_functions, contigs, GENOME, pattyfams.txt, TAXONOMY, TAXONOMY_ID,
 * and the Features directory complex (fasta and tbl for each feature type).
 *
 * @author Bruce Parrello
 *
 */
public class CoreOutputDirectory implements IGenomeTarget {

    // FIELDS
    /** logging facility */
    private static final Logger log = LoggerFactory.getLogger(CoreOutputDirectory.class);
    /** main organism directory */
    private OrganismDirectories orgDir;
    /** pattern for extracting feature type */
    private static final Pattern FEATURE_TYPE = Pattern.compile("fig\\|\\d+\\.\\d+\\.(\\w+)\\.\\d+");


    /**
     * Construct a CoreSEED genome target.
     *
     * @param coreDir		main CoreSEED directory
     * @param clearFlag		TRUE to erase the directory before starting
     */
    public CoreOutputDirectory(File coreDir, boolean clearFlag) throws IOException {
        // Get the organism directory.
        File baseDir = new File(coreDir, "Organisms");
        // Check for clearing and validate the directory.
        if (baseDir.exists()) {
            // Here the directory already exists.
            if (clearFlag) {
                // The client wants to erase it.
                log.info("Erasing organism directory {}.", baseDir);
                FileUtils.cleanDirectory(baseDir);
            }
        } else {
            // Here we must create the directory.
            log.info("Creating organism directory {}.", baseDir);
            FileUtils.forceMkdir(baseDir);
        }
        // Create the genome map.
        this.orgDir = new OrganismDirectories(baseDir);
    }

    @Override
    public boolean contains(String genomeId) {
        return this.orgDir.contains(genomeId);
    }

    @Override
    public void add(Genome genome) throws IOException {
        // First, insure the directory exists.
        File genomeDir = this.orgDir.computeDir(genome.getId());
        if (! genomeDir.isDirectory()) {
            log.info("Creating genome directory {}.", genomeDir);
            FileUtils.forceMkdir(genomeDir);
        } else {
            log.info("Erasing genome directory {}.", genomeDir);
            FileUtils.cleanDirectory(genomeDir);
        }
        // Now create the marker files.
        MarkerFile.write(new File(genomeDir, "GENOME"), genome.getName());
        MarkerFile.write(new File(genomeDir, "TAXONOMY"), genome.getTaxString());
        MarkerFile.write(new File(genomeDir, "TAXONOMY_ID"), genome.getTaxonomyId());
        // Next write the contig fasta.
        try (FastaOutputStream contigStream = new FastaOutputStream(new File(genomeDir, "contigs"))) {
            for (Contig contig : genome.getContigs()) {
                Sequence contigSequence = new Sequence(contig.getId(), contig.getDescription(), contig.getSequence());
                contigStream.write(contigSequence);
            }
        }
        // We are going to process features next.  We will collect the feature types in here.
        Map<String, List<Feature>> typeMap = new HashMap<String, List<Feature>>(10);
        // Prepare a large list for pegs.
        typeMap.put("peg", new ArrayList<Feature>(4000));
        // Set up some counters.
        int fidCount = 0;
        int annoCount = 0;
        int funCount = 0;
        // Now we are going to do assigned functions, annotations, and pattyfams in a single pass.
        try (PrintWriter annoStream = new PrintWriter(new File(genomeDir, "annotation"));
                PrintWriter funStream = new PrintWriter(new File(genomeDir, "assigned_functions"));
                PrintWriter famStream = new PrintWriter(new File(genomeDir, "pattyfams.txt"))) {
            for (Feature feat : genome.getFeatures()) {
                // First, get the feature type from the feature ID.
                String fid = feat.getId();
                List<Feature> flist = typeMap.computeIfAbsent(getFidType(fid), x -> new ArrayList<Feature>());
                flist.add(feat);
                fidCount++;
                // Process the annotations.
                for (Annotation anno : feat.getAnnotations()) {
                    annoStream.println(fid);
                    annoStream.format("%d%n", (long) anno.getAnnotationTime());
                    annoStream.println(anno.getAnnotator());
                    annoStream.println("Set master function to");
                    annoStream.println(anno.getComment());
                    annoStream.println("//");
                    annoCount++;
                }
                // Process the function.
                String function = feat.getFunction();
                if (function != null & ! function.isEmpty()) {
                    funStream.println(fid + "\t" + function);
                    funCount++;
                }
                // Process the protein families.
                this.writeFamily(famStream, fid, feat.getFigfam());
                this.writeFamily(famStream, fid, feat.getPgfam());
                this.writeFamily(famStream, fid, feat.getPlfam());
            }
        }
        log.info("{} features found in {}:  {} annotations, {} assigned functions.", fidCount, annoCount, funCount);
        // Now write the feature directories.
        File featureDir = new File(genomeDir, "Features");
        FileUtils.forceMkdir(featureDir);
        for (Map.Entry<String, List<Feature>> typeEntry : typeMap.entrySet()) {
            String type = typeEntry.getKey();
            List<Feature> feats = typeEntry.getValue();
            log.info("Saving {} features of type {}.", feats.size(), type);
            File typeDir = new File(featureDir, type);
            FileUtils.forceMkdir(typeDir);
            try (FastaOutputStream protStream = new FastaOutputStream(new File(typeDir, "fasta"));
                    PrintWriter tblStream = new PrintWriter(new File(typeDir, "tbl"))) {
                for (Feature feat : feats) {
                    String fid = feat.getId();
                    // Process the FASTA output first.
                    if (feat.getType().contentEquals("CDS")) {
                        // Here we are doing a protein FASTA.
                        String prot = feat.getProteinTranslation();
                        if (prot != null && ! prot.isEmpty()) {
                            Sequence seq = new Sequence(fid, feat.getPegFunction(), prot);
                            protStream.write(seq);
                        }
                    } else {
                        // Here we want the DNA sequence.
                        Sequence seq = new Sequence(fid, feat.getFunction(), genome.getDna(feat.getLocation()));
                        protStream.write(seq);
                    }
                    // Now write the TBL record.
                    Map<String, NavigableSet<String>> aliasMap = feat.getAliasMap();
                    String aliases = "";
                    if (aliasMap != null && aliasMap.size() > 0) {
                        List<String> aliasList = new ArrayList<String>(aliasMap.size());
                        for (var aliasEntry : aliasMap.entrySet()) {
                            String aliasType = aliasEntry.getKey();
                            String prefix = (aliasType.equals("misc") ? "" : aliasType + "|");
                            for (String alias : aliasEntry.getValue())
                                aliasList.add(prefix + alias);
                        }
                    }
                    Location loc = feat.getLocation();
                    tblStream.println(fid + "\t" + loc.toSeedString() + "\t" + aliases);
                }
            }
        }
    }

    /**
     * Write a protein family record.
     *
     * @param famStream		protein family output writer
     * @param fid			feature ID
     * @param protfam		protein family ID
     */
    private void writeFamily(PrintWriter famStream, String fid, String protfam) throws IOException {
        if (protfam != null)
            famStream.println(fid + "\t" + protfam);
    }

    @Override
    public void finish() {
    }

    /**
     * @return the type of the specified feature ID
     *
     * @param fid	ID of the feature of interest
     */
    private static String getFidType(String fid) {
        String retVal = "invalid";
        Matcher m = FEATURE_TYPE.matcher(fid);
        if (m.matches())
            retVal = m.group(1);
        return retVal;
    }

    @Override
    public String toString() {
        return "CoreSEED Output Directory " + this.orgDir.getBaseDir().toString();
    }

    @Override
    public void remove(String genomeId) throws IOException {
        this.orgDir.remove(genomeId);
    }

    @Override
    public boolean canDelete() {
        return true;
    }

    @Override
    public Set<String> getGenomeIDs() {
        return new TreeSet<String>(this.orgDir.getIDs());
    }
}
