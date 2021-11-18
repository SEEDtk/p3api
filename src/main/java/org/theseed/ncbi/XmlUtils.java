/**
 *
 */
package org.theseed.ncbi;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * This is a class that contains some simple XML utilities useful for NCBI documents.
 *
 * @author Bruce Parrello
 *
 */
public class XmlUtils {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(XmlUtils.class);
    /** xml document builder */
    private static final DocumentBuilderFactory DOC_FACTORY =
            DocumentBuilderFactory.newInstance();

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
     * Get the text content of the first named sub-element.  If none is found, we return an
     * empty string.
     *
     * @param element	source XML element
     * @param tagName	name of the desired sub-element
     *
     * @return the text content of the first named sub-element
     */
    public static String getXmlString(Element element, String tagName) {
        String retVal = "";
        Element tagNode = findFirstByTagName(element, tagName);
        if (tagNode != null)
            retVal = StringUtils.stripToEmpty(tagNode.getTextContent());
        return retVal;
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

    /**
     * @return all the child elements of a node
     *
     * @param parent	parent node of the desired children
     */
    public static List<Element> childrenOf(Element parent) {
        NodeList children = parent.getChildNodes();
        return setup(children);
    }

    /**
     * @return all the descendant elements of a node with a give type
     *
     * @param parent	parent node to start from
     * @param type		tag name of the desired descendants
     */
    public static List<Element> descendantsOf(Element parent, String type) {
        NodeList descendants = parent.getElementsByTagName(type);
        return setup(descendants);
    }

    /**
     * Create an element list from a node list.
     *
     * @param inList	node list containing one or more elements
     */
    private static List<Element> setup(NodeList inList) {
        List<Element> retVal = new ArrayList<Element>(inList.getLength());
        for (int i = 0; i < inList.getLength(); i++) {
            Node node = inList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE)
                retVal.add((Element) node);
        }
        return retVal;
    }

    /**
     * Read an xml document from a file.
     *
     * @param file	source file from which to read document
     *
     * @return the XML document read
     *
     * @throws IOException
     */
    public static Document readXmlFile(File file) throws IOException {
        Document retVal;
        try {
            DocumentBuilder builder = DOC_FACTORY.newDocumentBuilder();
            retVal = builder.parse(file);
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Error building XML document: " + e.toString());
        }
        return retVal;
    }

    /**
     * Convert an XML string into a DOM Element.
     *
     * @param xmlString		input XML string
     *
     * @return the document element for the XML document described by the string
     *
     * @throws XmlException
     */
    public static Element parseXmlString(String xmlString) throws XmlException {
        Element retVal;
        try {
            InputSource xmlSource = new InputSource(new StringReader(xmlString));
            DocumentBuilder builder = DOC_FACTORY.newDocumentBuilder();
            Document doc = builder.parse(xmlSource);
            retVal = doc.getDocumentElement();
            Element error = XmlUtils.findFirstByTagName(retVal, "ERROR");
            if (error != null)
                throw new XmlException("NCBI ERROR: " + error.getTextContent());
        } catch (UnsupportedOperationException | IOException | SAXException
                | ParserConfigurationException e) {
            throw new XmlException("Error accessing XML response: " + e.getMessage());
        }
        return retVal;
    }

    /**
     * Remove empty text modes from the specified element and all its descendants.
     */
    public static void cleanElement(Element element) {
        // This is essentially our processing stack.
        Deque<Element> deferred = new ArrayDeque<Element>();
        deferred.push(element);
        // Loop through the stack.
        while (! deferred.isEmpty()) {
            Element parent = deferred.removeLast();
            NodeList children = parent.getChildNodes();
            // Separate out the text and element children.
            List<Node> textElements = new ArrayList<Node>(children.getLength());
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                switch (child.getNodeType()) {
                case Node.TEXT_NODE :
                    textElements.add(child);
                    break;
                case Node.ELEMENT_NODE :
                    deferred.add((Element) child);
                    break;
                }
            }
            // Delete the text children.
            textElements.stream().forEach(x -> parent.removeChild(x));
        }
    }


}
