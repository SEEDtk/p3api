/**
 *
 */
package org.theseed.genome;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.counters.CountMap;
import org.theseed.gff.GffReader;
import org.theseed.proteins.DnaTranslator;
import org.theseed.sequence.FastaInputStream;
import org.theseed.sequence.Sequence;

/**
 * This is a genome loaded from an NCBI GFF dump and a FASTA file.
 *
 * @author Bruce Parrello
 *
 */
public class GffGenome extends NewGenome {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(GffGenome.class);
    /** supported aliases */
    protected static final String[] ALIASES = new String[] { "Genbank", "GeneID", "locus_tag" };

    /**
     * Create a genome from an NCBI GFF genome dump and a FASTA file
     *
     * @param nameSfx		suffix to put on the genome name
     * @param domain		domain of the genome
     * @param gffFile		file containing a dump of all the GFF data for this genome
     * @param fastaFile		FASTA file containing the genome contigs
     *
     * @throws IOException
     */
    public GffGenome(String nameSfx, String domain, File gffFile, File fastaFile) throws IOException {
        super(nameSfx, domain);
        this.setHome("RefSeq");
        this.setSource("GenBank");
        try (GffReader gffStream = new GffReader(gffFile);
                FastaInputStream fastaStream = new FastaInputStream(fastaFile)) {
            // This will hold the genome ID.
            String genomeId = null;
            // This will track the number of features of each type.
            CountMap<String> fTypes = new CountMap<String>();
            // This will track the number of contigs.
            int contigCount = 0;
            // This will map Refseq IDs to contigs.
            Map<String, Contig> contigMap = new HashMap<String, Contig>(100);
            // Loop through the GFF records.
            for (GffReader.Line line : gffStream) {
                if (line.getType().contentEquals("region")) {
                    // REGION indicates this is a contig.
                    String oldId = line.getId();
                    log.info("Processing contig {}.", oldId);
                    if (genomeId == null)
                        genomeId = getGenomeId(line.getAttribute("taxon"));
                    // Here we have the genome ID, so we can create a contig for this region.
                    contigCount++;
                    String contigId = String.format("%s.con.%04d", genomeId, contigCount);
                    Contig newContig = new Contig(contigId, line.getRight(), this.getGeneticCode());
                    newContig.setAccession(oldId);
                    contigMap.put(oldId, newContig);
                    this.addContig(newContig);
                } else {
                    // Here we have a feature. Compute the ID.
                    String ftype = line.getType();
                    int num = fTypes.count(ftype);
                    String fid = String.format("fig|%s.%s.%d", genomeId, ftype, num);
                    log.debug("Creating feature {}.", fid);
                    // Compute the contig.
                    Contig contig = contigMap.get(line.getId());
                    if (contig == null)
                        throw new IOException("Feature is on contig " + line.getId() + " but no region record found yet.");
                    // Get the function.
                    String function = line.getAttributeOrEmpty("Note");
                    // Create the feature and store it.  We will need to compute the protein sequence later.
                    Feature feat = new Feature(fid, function, contig.getId(), Character.toString(line.getStrand()),
                            line.getLeft(), line.getRight());
                    // Check for aliases.
                    String fName = line.getAttributeOrEmpty("Name");
                    if (! fName.isEmpty()) feat.addAlias(fName);
                    for (String aliasType : ALIASES)
                        feat.formAlias(aliasType, line.getAttributeOrEmpty(aliasType));
                    // Add the feature to the genome.
                    this.addFeature(feat);
                }
            }
            // Now we read in the contig sequences.
            for (Sequence contigSeq : fastaStream) {
                // Find this contig.
                String oldId = contigSeq.getLabel();
                Contig contig = contigMap.get(oldId);
                if (contig == null) {
                    // Here it is a new contig.
                    contigCount++;
                    String contigId = String.format("%s.con.%04d", genomeId, contigCount);
                    contig = new Contig(contigId, contigSeq.getSequence().toLowerCase(), this.getGeneticCode());
                    this.addContig(contig);
                    log.info("New contig {} added as {}.", oldId, contigId);
                } else {
                    // Here the contig exists, but we need to store the sequence.
                    log.info("Storing DNA for contig {} from {}.", contig.getId(), oldId);
                    contig.setSequence(contigSeq.getSequence().toLowerCase());
                }
            }
            // Finally, we store the protein sequences.
            log.info("Computing protein sequences using genetic code {}.", this.getGeneticCode());
            DnaTranslator xlator = new DnaTranslator(this.getGeneticCode());
            for (Feature feat : this.getPegs()) {
                String dna = this.getDna(feat.getLocation());
                String prot = xlator.pegTranslate(dna);
                feat.setProteinTranslation(prot);
            }
            log.info("Genome {} loaded.", genomeId);
        }
    }

}
