/**
 *
 */
package org.theseed.cli;

import java.util.regex.Pattern;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;

/**
 * This object represents a source for RNA Sequencing data.  The subclass constructor specifies the
 * input file names or accession number, and a common method is used to store the specification
 * in the JSON object that is passed to the CLI interface.
 *
 * @author Bruce Parrello
 *
 */
public abstract class RnaSource {

    // FIELDS
    /** this pattern is used to extract the sample ID from the fastq file name */
    protected static final Pattern NAME_PATTERN = Pattern.compile("(?:.+/)?(.+?)(?:_(?:\\d_)?[ps]trim)?\\.(?:fastq|fq)(?:\\.gz)?");

    /**
     * Store this source in a JSON parameter object.
     *
     * @param parms		JSON parameter object to modify
     */
    public abstract void store(JsonObject parms);

    /**
     * @return TRUE if this source is valid
     */
    public abstract boolean isValid();

    /**
     * @return the left-file name for a job from this source
     *
     * @param jobName	name of the job
     */
    public abstract String getLeftName(String jobName);

    /**
     * @return the right-file name for a job from this source
     *
     * @param jobName	name of the job
     */
    public abstract String getRightName(String jobName);


    /**
     * Subclass for an RNA Seq library stored in the NCBI SRA database.
     */
    public static class SRA extends RnaSource {

        private String srrId;

        /**
         * Construct an SRA RNA Seq library source.
         *
         * @param srrAccession		accession ID for the library
         */
        public SRA(String srrAccession) {
            this.srrId = srrAccession;
        }

        @Override
        public void store(JsonObject parms) {
            // Note the basic format:  a hash with a single value wrapped inside a list.
            JsonObject srrSpec = new JsonObject().putChain("srr_accession", this.srrId);
            JsonArray source = new JsonArray().addChain(srrSpec);
            parms.put("srr_libs", source);
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public String getLeftName(String jobName) {
            return jobName + "_1_ptrim.fq";
        }

        @Override
        public String getRightName(String jobName) {
            return jobName + "_2_ptrim.fq";
        }

    }

    /**
     * Subclass for a paired-end RNA Seq library stored in two files.
     */
    public static class Paired extends RnaSource {

        private String leftFile;
        private String rightFile;

        /**
         * Construct an empty paired-end library source.
         */
        public Paired() {
            this.leftFile = null;
            this.rightFile = null;
        }

        /**
         * Construct an RNA Seq paired-end library source.
         *
         * @param leftName		name of the left FASTQ file
         * @param rightName		name of the right FASTQ file
         */
        public Paired(String leftName, String rightName) {
            this.leftFile = leftName;
            this.rightFile = rightName;
        }

        @Override
        public void store(JsonObject parms) {
            // Compute the sample ID.
            String sampleId = "sample";
            var m = NAME_PATTERN.matcher(this.leftFile);
            if (m.matches())
                sampleId = m.group(1);
            // Do we really have two files? If not, only the leftfile will be present.
            if (this.rightFile != null) {
                // Yes, we have two files.
                JsonArray source = new JsonArray()
                        .addChain(new JsonObject()
                                .putChain("read1", this.leftFile)
                                .putChain("read2", this.rightFile)
                                .putChain("sample_id", sampleId)
                                );
                parms.put("paired_end_libs", source);
            } else {
                // Only one file, use single-end.
                JsonArray source = new JsonArray()
                        .addChain(new JsonObject()
                                .putChain("read", this.leftFile)
                                .putChain("sample_id", sampleId)
                                );
                parms.put("single_end_libs", source);
            }
        }

        @Override
        public boolean isValid() {
            return (this.leftFile != null && this.rightFile != null);
        }

        /**
         * Specify the left-pair FASTQ file for this source.
         *
         * @param fileName		FASTQ file to specify
         */
        public void storeLeft(String fileName) {
            this.leftFile = fileName;
        }

        /**
         * Specify the right-pair FASTQ file for this source.
         *
         * @param fileName		FASTQ file to specify
         */
        public void storeRight(String fileName) {
            this.rightFile = fileName;
        }

        @Override
        public String getLeftName(String jobName) {
            return jobName + "_R1_001_ptrim.fq";
        }

        @Override
        public String getRightName(String jobName) {
            return jobName + "_R2_001_ptrim.fq";
        }

    }

}
