/**
 *
 */
package org.theseed.genome;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.theseed.counters.CountMap;
import org.theseed.genome.Genome;
import org.theseed.gff.ViprKeywords;
import org.theseed.io.TabbedLineReader;
import org.theseed.io.TabbedLineReader.Line;
import org.theseed.p3api.Connection;
import org.theseed.p3api.IdClearinghouse;
import org.theseed.proteins.DnaTranslator;
import org.theseed.sequence.FastaInputStream;
import org.theseed.sequence.Sequence;

/**
 * This class represents a virus genome from the ViPR database.
 *
 * @author Bruce Parrello
 *
 */
public class ViprGenome extends Genome {

    /**
     * Initialize the genome.
     *
     * @param genomeId	ID of the virus genome
     * @param name		name of the genome
     * @param code		genetic code for protein translation
     *
     * @throws NumberFormatException
     */
    protected ViprGenome(String name, int code) throws NumberFormatException {
        super(name, "Virus", code);
        this.setHome("ViPR");
    }

    /**
     * This class reads files from the ViPR database and builds a collection of virus genomes.
     *
     * @author Bruce Parrello
     *
     */
    public static class Builder {

        // FIELDS
        /** map of GenBank IDs to genome objects */
        private Map<String, ViprGenome> virusMap;
        /** DNA translator */
        private DnaTranslator xlate;
        /** queue of features in the current file */
        private Queue<Feature> features;
        /** counters for computing PEG IDs, keyed by genBank ID */
        private CountMap<String> pegCounts;
        /** counters for computing peptide IDs, keyed by genBank ID */
        private CountMap<String> mpCounts;
        /** taxonomic lineage for each virus taxonomic ID */
        private Map<String, TaxItem[]> lineageMap;
        /** genome ID clearinghouse */
        private IdClearinghouse idServer;
        /** PATRIC connection for taxonomies */
        private Connection p3;

        /**
         * Load the virus genomes from a FASTA and GFF file.  The files can contain multiple viruses identified
         * by GenBank accession numbers.  It is presumed they were downloaded from ViPR using the default
         * field selections.
         *
         * @param code			genetic code for protein translation
         * @param proteinGff	GFF file containing the virus protein definitions
         * @param contigFasta	FASTA file containing the virus DNA sequences
         *
         * @return a collection of the genomes loaded
         *
         * @throws IOException
         *
         */
        public Collection<ViprGenome> Load(int code, File proteinGff, File contigFasta) throws IOException {
            // Create a map of GenBank IDs to genome objects.
            this.virusMap = new HashMap<String, ViprGenome>();
            // Here we build the genomes and create the contigs.
            try (FastaInputStream contigStream = new FastaInputStream(contigFasta)) {
                // Loop through the contigs in the input file.
                for (Sequence contigSeq : contigStream) {
                    // Get the keywords for this sequence.
                    Map<String, String> keywords = ViprKeywords.fastaParse(contigSeq);
                    String genBankId = keywords.get("gb");
                    String name = keywords.get("Organism");
                    // Verify that this genome has only one contig.
                    ViprGenome genome = virusMap.get(genBankId);
                    if (genome != null)
                        throw new RuntimeException("Duplicate contig for genome " + genBankId + ": " + name);
                    genome = new ViprGenome(name, code);
                    virusMap.put(genBankId, genome);
                    // Add this sequence as a contig.  We need to compute the contig ID.
                    String contigId = genBankId + ".con.0001";
                    Contig contig = new Contig(contigId, contigSeq.getSequence(), code);
                    genome.addContig(contig);
                }
            }
            // Now we add the metadata and proteins.  The genome ID is also computed here.  Each protein is represented
            // by two standard GFF records and a FASTA record.  The first standard record is keyed by genbank genome
            // ID with a type of "contig" and contains the taxon ID in the keyword column.  This is used to compute the
            // real genome ID. The second record has a type of "CDS" and contains the location of the feature in the contig.
            // It may also contain a swissprot alias, and one or more GO terms (as values of "Ontology_term").  The final
            // record consists of four lines:  "##FASTA", ">" followed by a genbank genome ID, ">" followed by a protein
            // function, and the protein DNA sequence.  This last needs to be translated, so we create a DNA translator.
            this.xlate = new DnaTranslator(code);
            // The initial proteins are read from the beginning of the file.  The sequence and function data is read from
            // the end.  The only way to associate them is via the order in which they appear, so we store the prototype
            // features in this queue.
            this.features = new LinkedList<Feature>();
            // These counters map tracks the number of features produced per genome/type and is used to compute feature IDs.  The
            // key is the genbank genome ID.
            this.pegCounts = new CountMap<String>();
            this.mpCounts = new CountMap<String>();
            // Initialize the taxonomy map.
            this.lineageMap = new HashMap<String, TaxItem[]>();
            // Get the server connections.
            this.p3 = new Connection();
            this.idServer = new IdClearinghouse();
            // Now loop through the GFF3 file.
            try (TabbedLineReader proteinStream = new TabbedLineReader(proteinGff, 9)) {
                // Verify the version.
                if (! proteinStream.hasNext())
                    throw new RuntimeException("Protein GFF file is empty.");
                TabbedLineReader.Line line = proteinStream.next();
                if (! Pattern.matches("##gff-version\\s+3$", line.get(0)))
                    throw new RuntimeException("Protein GFF file is missing GFF3 format line.");
                // Loop through the file's data lines.
                while (proteinStream.hasNext()) {
                    // Figure out where we are in the file.
                    line = proteinStream.next();
                    String id = line.get(0);
                    if (id.contentEquals("##FASTA")) {
                        // Here we have the protein function and DNA sequence.
                        Feature feat = features.poll();
                       this.processSequence(proteinStream, feat);
                    } else if (! id.isEmpty()) {
                        // Here we have the genome's genbank ID and taxonomic ID.  If this genome does not have an
                        // ID yet, we need to compute the ID and lineage.
                        ViprGenome vGenome = virusMap.get(id);
                        if (vGenome.getId() == null) {
                            String keywords = line.get(8);
                            this.initGenome(vGenome, keywords);
                        }
                        // Create the feature from the following line and add it to the queue.  We will pull it off
                        // when the feature's FASTA line shows up.
                        line = proteinStream.next();
                        Feature feat = this.createFeature(vGenome, line);
                        features.add(feat);
                    }
                }
            }
            // Return the genomes created.
            Collection<ViprGenome> retVal = virusMap.values();
            return retVal;
        }

        /**
         * Store the protein sequence for a feature.
         *
         * @param proteinStream		GFF input stream, positioned at the beginning of the FASTA lines for a feature
         * @param feat				target feature to contain the protein sequence
         */
        private void processSequence(TabbedLineReader proteinStream, Feature feat) {
            // Read the first label line and verify the genbank ID.
            TabbedLineReader.Line line = proteinStream.next();
            if (line == null)
                throw new RuntimeException("Incomplete FASTA in GFF3 file.");
            String contig = feat.getLocation().getContigId();
            String found = line.get(0).substring(1) + ".";
            if (! StringUtils.startsWith(contig, found))
                throw new RuntimeException("FASTA label for virus is " + found + " but contig is " + contig + ".");
            // Read the next label line and get the functional assignment.
            line = proteinStream.next();
            if (line == null)
                throw new RuntimeException("No function line for FASTA in GFF3 file.");
            String function = StringUtils.substringAfter(line.get(0), " ");
            feat.setFunction(function);
            // Get the DNA and translate it.  Note we remove any trailing stop codon.
            line = proteinStream.next();
            if (line == null)
                throw new RuntimeException("No data line for FASTA in GFF3 file.");
            String protein = StringUtils.removeEnd(this.xlate.pegTranslate(line.get(0)), "*");
            feat.setProteinTranslation(protein);
        }

        /**
         * Compute the ID and lineage of a genome.
         *
         * @param vGenome	uninitialized genome
         * @param keywords	keywords from the genome's first definition line, containing the taxon ID
         */
        private void initGenome(ViprGenome vGenome, String keywords) {
            Map<String,String> keywordMap = ViprKeywords.gffParse(keywords);
            String taxonId = keywordMap.get("taxon");
            // Get the taxonomic lineage.
            TaxItem[] lineage = this.lineageMap.computeIfAbsent(taxonId, k -> p3.getLineage(k));
            // Compute the genome ID.
            String genomeId = this.idServer.computeGenomeId(taxonId);
            // Store the ID and lineage.
            vGenome.setId(genomeId);
            vGenome.setLineage(lineage);
        }

        /**
         * Create a new feature and add it to the specified genome.
         *
         * @param vGenome	genome to contain the new feature
         * @param pegCounts	peg count map used to compute genome ID
         * @param line		data line containing the location of the protein, the GO terms, and the type
         *
         * @return the new feature created
         */
        private Feature createFeature(ViprGenome vGenome, Line line) {
            // Compute the peg ID.
            String pegId = this.computePegId(vGenome, line.get(2));
            // Compute the contig ID.
            String contigId = vGenome.getSourceId() + ".con.0001";
            // Get the strand.  A dot counts as a plus.
            String strand = line.get(6);
            if (strand.contains(".")) strand = "+";
            // Create the feature and put it in the genome.
            Feature retVal = new Feature(pegId, "hypothetical protein", contigId, strand, line.getInt(3), line.getInt(4));
            vGenome.addFeature(retVal);
            // Parse the keywords.
            Map<String, String> keywords = ViprKeywords.gffParse(line.get(9));
            String viprId = keywords.get("ID");
            retVal.formAlias("vipr:", viprId);
            String swissprot = keywords.get("swissprot");
            retVal.formAlias("swissprot:", swissprot);
            // Extract the GO terms.
            String[] goTermList = StringUtils.split(keywords.get("Ontology_term").substring(3), ',');
            for (String goTermItem : goTermList)
                retVal.addGoTerm(goTermItem);
            // Return the feature.
            return retVal;
        }

        /**
         * Compute the ID to give the next feature of the specified type.
         *
         * @param vGenome	target genome to contain the feature
         * @param type		type of the feature (mat_peptide or CDS)
         *
         * @return the new feature's ID string
         */
        private String computePegId(ViprGenome vGenome, String type) {
            int pegSuffix;
            String typeCode;
            String genBankId = vGenome.getSourceId();
            String genomeId = vGenome.getId();
            if (type.contentEquals("CDS")) {
                pegSuffix = this.pegCounts.count(genBankId);
                typeCode = "peg";
            } else {
                pegSuffix = this.mpCounts.count(genBankId);
                typeCode = "mp";
            }
            String retVal = String.format("fig|%s.%s.%d", genomeId, typeCode, pegSuffix);
            return retVal;
        }

    }

}
