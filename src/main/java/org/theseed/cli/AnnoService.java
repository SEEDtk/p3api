/**
 *
 */
package org.theseed.cli;

import java.io.File;
import java.io.IOException;
import org.apache.commons.lang3.StringUtils;

import com.github.cliftonlabs.json_simple.JsonObject;

/**
 * This submits a genome to the annotation service.  We need the input FASTA file, plus an overriding task name and
 * various bits of taxonomic information.
 *
 * @author Bruce Parrello
 *
 */
public class AnnoService extends CliService {

    // FIELDS
    /** parameter JSON structure */
    private JsonObject parms;
    /** output name */
    private String outName;
    /** copy task */
    private CopyTask copier;


    /**
     * Construct an annotation service request.
     *
     * @param fastaFile		input FASTA file
     * @param taxId			taxonomic ID to assign to the genome
     * @param genomeName	full name to give to the genome
     * @param domain		domain name
     * @param gc			genetic code to use for protein translation
     * @param workDir		working directory for temporary files
     * @param workspace		name of the output workspace
     */
    public AnnoService(File fastaFile, int taxId, String genomeName, String domain, int gc, File workDir, String workspace) {
        super(fastaFile.getName(), workDir, workspace);
        // The output name is computed from the FASTA file name.  We will need it later.
        this.outName = StringUtils.substringBeforeLast(fastaFile.getName(), ".");
        // Set up the run parameters.
        this.parms = new JsonObject();
        this.parms.put("skip_indexing", "1");
        // The tax ID is expected to be a string.
        this.parms.put("taxonomy_id", Integer.toString(taxId));
        this.parms.put("queue_nowait", "0");
        this.parms.put("scientific_name", genomeName);
        this.parms.put("domain", domain);
        this.parms.put("output_file", this.outName);
        this.parms.put("skip_workspace_output", "0");
        this.parms.put("public", "0");
        this.parms.put("output_path", workspace);
        this.parms.put("reference_genome_id", "");
        this.parms.put("code", gc);
        this.parms.put("recipe", "default");
        // Compute the PATRIC file name from ours.
        String remoteFile = workspace + "/" + fastaFile.getName();
        // Create the copy task.
        this.copier = new CopyTask(this.getWorkDir(), this.getWorkspace());
        // Copy the contigs to the workspace.
        this.copier.copyLocalFile(fastaFile, remoteFile, DirEntry.Type.CONTIGS);
        // Store the contig file name.
        this.parms.put("contigs", remoteFile);
    }

    /**
     * Request that this genome be indexed. (Use with caution, as a lot of indexing strains PATRIC.)
     *
     * @param flag		TRUE to index the genome, else FALSE
     */
    public void requestIndexing(boolean flag) {
        this.parms.put("skip_indexing", (flag ? "1" : "0"));
    }

    /**
     * Specify a reference genome.
     *
     * @param genome_id		ID of the desired reference genome
     */
    public void requestRefGenome(String genome_id) {
        this.parms.put("reference_genome_id", genome_id);
    }

    @Override
    protected void startService() {
        // Submit the job.
        this.submit("GenomeAnnotation", this.parms);
    }

    /**
     * @return a file containing the output genome for this annotation
     *
     * @throws IOException
     */
    public File getResultFile() throws IOException {
        // Determine the desired output file.
        File retVal = new File(this.getWorkDir(), this.outName + ".gto");
        // Copy it from the workspace.
        this.copier.copyRemoteFile(this.getWorkspace() + "/." + this.outName + "/" + this.outName + ".genome", retVal);
        return retVal;
    }

}
