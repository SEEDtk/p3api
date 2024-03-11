/**
 *
 */
package org.theseed.ncbi;

/**
 * This is a special exception used when a link fails to download.  It enables us to catch certain kinds
 * of failures downloading from the PaperConnection.
 *
 * @author Bruce Parrello
 *
 */
public class DownloadException extends RuntimeException {

    // FIELDS
    /** URL that failed */
    private String badUrl;
    /** error type */
    private String errorType;
    /** serialization object type ID */
    private static final long serialVersionUID = -7975535579284966264L;

    /**
     * Construct a download exception.
     *
     * @param url			URL that failed
     * @param errorType		description of error
     */
    public DownloadException(String url, String errorType) {
        super("Could not download " + url + ": " + errorType);
        this.badUrl = url;
        this.errorType = errorType;
    }

    /**
     * @return the failing URL
     */
    public String getBadUrl() {
        return this.badUrl;
    }

    /**
     * @return the error type string
     */
    public String getErrorType() {
        return this.errorType;
    }

}
