/**
 *
 */
package org.theseed.ncbi;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * This is a class that contains some simple XML utilities useful for NCBI documents.
 *
 * @author Bruce Parrello
 *
 */
public class XmlUtils {

    /**
     * @return the integer value of the text content of the first named sub-element
     *
     * @param element	source XML element
     * @param tagName	name of the desired sub-element
     *
     * @throws XmlException
     */
    public static int getXmlInt(Element element, String tagName) throws XmlException {
        String text = XmlUtils.getXmlString(element, tagName);
        int retVal;
        try {
            retVal = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new XmlException("Expected integer value for element \"" +
                    tagName + "\", but found \"" + text + "\".");
        }
        return retVal;
    }

    /**
     * @return the text content of the first named sub-element
     *
     * @param element	source XML element
     * @param tagName	name of the desired sub-element
     *
     * @throws TagNotFoundException
     */
    public static String getXmlString(Element element, String tagName) throws TagNotFoundException {
        Element tagNode = getFirstByTagName(element, tagName);
        return tagNode.getTextContent();
    }

    /**
     * @return the first named sub-element
     *
     * @param element	source XML element
     * @param tagName	name of the desired sub-element
     *
     * @throws TagNotFoundException
     */
    public static Element getFirstByTagName(Element element, String tagName) throws TagNotFoundException {
        Element retVal = findFirstByTagName(element, tagName);
        if (retVal == null)
            throw new TagNotFoundException(tagName, "ENTREZ response document");
        return retVal;
    }

    /**
     * @return the first named sub-element, or NULL if there is none
     *
     * @param element	source XML element
     * @param tagName	name of the desired sub-element
     */
    public static Element findFirstByTagName(Element element, String tagName) {
        NodeList tagNode = element.getElementsByTagName(tagName);
        Element retVal = null;
        if (tagNode.getLength() > 0)
            retVal = (Element) tagNode.item(0);
        return retVal;
    }

}
