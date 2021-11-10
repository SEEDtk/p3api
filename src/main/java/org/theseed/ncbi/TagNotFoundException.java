/**
 *
 */
package org.theseed.ncbi;

/**
 * This exception is thrown when an XML document is missing a required tag.
 *
 * @author Bruce Parrello
 *
 */
public class TagNotFoundException extends XmlException {

    // FIELDS
    /** serialization identifier */
    private static final long serialVersionUID = 7372077256137062131L;

    /**
     * Throw a standard tag-not-found exception.
     *
     * @param tagName				missing tag name
     * @param documentDescription	description of the XML document
     */
    public TagNotFoundException(String tagName, String documentDescription) {
        super("Required element \"" + tagName + "\" not found in " + documentDescription + ".");
    }


}
