/**
 *
 */
package org.theseed.cli;

/**
 * This is an unchecked exception for PATRIC CLI errors that may need to be caught and given special handling.
 *
 * @author Bruce Parrello
 *
 */
public class PatricException extends RuntimeException {

    /** serialization version number */
    private static final long serialVersionUID = -7538696651967995684L;

    /**
     * Construct a PATRIC CLI exception with a message.
     * @param message
     */
    public PatricException(String message) {
        super(message);
    }

    /**
     * Construct a PATRIC CLI exception with an underlying cause.
     *
     * @param message	message to display
     * @param cause		exception being percolated
     */
    public PatricException(String message, Throwable cause) {
        super(message, cause);
    }

}
