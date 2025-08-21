/**
 *
 */
package org.theseed.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a special exception used when an error occurs in a CLI invocation.
 *
 * @author Bruce Parrello
 *
 */
public class CliTaskException extends RuntimeException {

    // FIELDS
    /** logging facility */
    private static final Logger log = LoggerFactory.getLogger(CliTaskException.class);
    /** object type ID for serialization */
    private static final long serialVersionUID = -6647487357277570415L;

    /**
     * Create a runtime exception for a CLI task error.
     * @param message
     */
    public CliTaskException(CliTask source, String message) {
        super(message);
        log.error("CLI internal error encountered for job {} of type {}.", source.getJobName(), source.getClass().getTypeName());
        log.error("Processor error message is {}.", message);
    }

}
