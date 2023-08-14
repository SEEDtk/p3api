/**
 *
 */
package org.theseed.ncbi;

/**
 * This enumeration represents a database in the NCBI Entrez system.  For each such
 * database, we need to know the table name and the element name, and we need the
 * return type and mode.
 *
 * @author Bruce Parrello
 *
 */
public enum NcbiTable {

    SRA {
        @Override
        public String db() {
            return "sra";
        }

        @Override
        public String tagName() {
            return "EXPERIMENT_PACKAGE";
        }

        @Override
        public String returnType() {
            return "rettype=full&retmode=xml";
        }

    }, BIOPROJECT {
        @Override
        public String db() {
            return "bioproject";
        }

        @Override
        public String tagName() {
            return "DocumentSummary";
        }

        @Override
        public String returnType() {
            return "rettype=xml&retmode=xml";
        }

    }, BIOSAMPLE {
        @Override
        public String db() {
            return "biosample";
        }

        @Override
        public String tagName() {
            return "BioSample";
        }

        @Override
        public String returnType() {
            return "rettype=full&retmode=xml";
        }

    }, PUBMED {
        @Override
        public String db() {
            return "pubmed";
        }

        @Override
        public String tagName() {
            return "PubmedArticle";
        }

        @Override
        public String returnType() {
            return "retmode=xml";
        }

    }, TAXONOMY {
        @Override
        public String db() {
            return "taxonomy";
        }

        @Override
        public String tagName() {
            return "Taxon";
        }

        @Override
        public String returnType() {
            return "retmode=xml";
        }

    }, GEO {
        @Override
        public String db() {
            return "gds";
        }

        @Override
        public String tagName() {
            return "GEO_DATASET";
        }

        @Override
        public String returnType() {
            return "retmode=xml";
        }

    }, ASSEMBLY {

        @Override
        public String db() {
            return "assembly";
        }

        @Override
        public String tagName() {
            return "DocumentSummary";
        }

        @Override
        public String returnType() {
            return "rettype=docsum&retmode=xml";
        }

    };

    /**
     * @return the name of the table
     */
    public abstract String db();

    /**
     * @return the tag name for the returned records
     */
    public abstract String tagName();

    /**
     * @return the return mode and type arguments
     */
    public abstract String returnType();

}
