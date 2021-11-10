/**
 *
 */
package org.theseed.ncbi;

/**
 * This is the base class for all XML exceptions.
 *
 * @author Bruce Parrello
 *
 */
public class XmlException extends Exception {

    /** serialization ID number */
    private static final long serialVersionUID = 424343488160926416L;

    /**
     * Create an XML exception
     *
     * @param message	error message to display
     */
    public XmlException(String message) {
        super(message);
    }

    /**
     * Create an XML exception with a root cause.
     *
     * @param message	error message to display
     * @param cause		underlying error that caused the exception
     */
    public XmlException(String message, Throwable cause) {
        super(message, cause);
    }

}
