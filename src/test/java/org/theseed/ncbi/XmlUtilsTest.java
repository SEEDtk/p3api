/**
 *
 */
package org.theseed.ncbi;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.theseed.test.Matchers.*;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * @author Bruce Parrello
 *
 */
public class XmlUtilsTest {

    @Test
    public void testXmlFile() throws IOException, XmlException {
        Document doc = XmlUtils.readXmlFile(new File("data", "experiments.xml"));
        Element root = doc.getDocumentElement();
        List<Element> children = XmlUtils.childrenOf(root);
        assertThat(children.size(), equalTo(22));
        for (Element child : children) {
            assertThat(child.getNodeName(), equalTo("EXPERIMENT_PACKAGE"));
            Element experiment = XmlUtils.getFirstByTagName(child, "EXPERIMENT");
            String expName = experiment.getAttribute("accession");
            Element frog = XmlUtils.findFirstByTagName(child, "Frog");
            assertThat(expName, frog, nullValue());
            Element sample = XmlUtils.getFirstByTagName(child, "SAMPLE");
            List<Element> xrefs = XmlUtils.descendantsOf(sample, "XREF_LINK");
            for (Element xref : xrefs) {
                if (XmlUtils.getXmlString(xref, "DB").contentEquals("bioproject"))
                    assertThat(expName, XmlUtils.getXmlString(xref, "LABEL"), equalTo("PRJNA238884"));
            }
            if (expName.contentEquals("SRX474186")) {
                Element sampleAParent = XmlUtils.getFirstByTagName(sample, "SAMPLE_ATTRIBUTES");
                List<Element> sampleAChildren = XmlUtils.childrenOf(sampleAParent);
                assertThat(sampleAChildren.size(), equalTo(5));
                Iterator<Element> iter = sampleAChildren.iterator();
                assertThat(iter.hasNext(), isTrue());
                Element sampleA = iter.next();
                assertThat(XmlUtils.getXmlString(sampleA, "TAG"), equalTo("source_name"));
                assertThat(XmlUtils.getXmlString(sampleA, "VALUE"), equalTo("Escherichia coli MG1655 cells"));
                assertThat(iter.hasNext(), isTrue());
                sampleA = iter.next();
                assertThat(XmlUtils.getXmlString(sampleA, "TAG"), equalTo("strain"));
                assertThat(XmlUtils.getXmlString(sampleA, "VALUE"), equalTo("K-12"));
                sampleA = iter.next();
                assertThat(XmlUtils.getXmlString(sampleA, "TAG"), equalTo("medium"));
                assertThat(XmlUtils.getXmlString(sampleA, "VALUE"), equalTo("M63"));
                sampleA = iter.next();
                assertThat(XmlUtils.getXmlString(sampleA, "TAG"), equalTo("growth phase"));
                assertThat(XmlUtils.getXmlString(sampleA, "VALUE"), equalTo("exponential"));
                sampleA = iter.next();
                assertThat(XmlUtils.getXmlString(sampleA, "TAG"), equalTo("substrain"));
                assertThat(XmlUtils.getXmlString(sampleA, "VALUE"), equalTo("MG1655"));
                assertThat(iter.hasNext(), isFalse());
            }
        }
    }

    @Test
    public void testCleaner() throws XmlException {
        String xmlString = "<root>  <parent1>  <child1>   </child1>\n"
                + "<child2>   </child2>  </parent1>  <parent2>This is real text</parent2> </root>";
        Element root = XmlUtils.parseXmlString(xmlString);
        NodeList rootChildren = root.getChildNodes();
        assertThat(rootChildren.getLength(), equalTo(5));
        Element parent1 = (Element) rootChildren.item(1);
        assertThat(parent1.getTagName(), equalTo("parent1"));
        List<Element> parentChildren = XmlUtils.childrenOf(parent1);
        assertThat(parentChildren.size(), equalTo(2));
        assertThat(parentChildren.get(0).getTagName(), equalTo("child1"));
        assertThat(parentChildren.get(1).getTagName(), equalTo("child2"));
        Element parent2 = (Element) rootChildren.item(3);
        assertThat(parent2.getTagName(), equalTo("parent2"));
        assertThat(parent2.getTextContent(), equalTo("This is real text"));
        XmlUtils.cleanElement(root);
        rootChildren = root.getChildNodes();
        assertThat(rootChildren.getLength(), equalTo(2));
        Element parent1Test = (Element) rootChildren.item(0);
        assertThat(parent1Test, sameInstance(parent1));
        Element parent2Test = (Element) rootChildren.item(1);
        assertThat(parent2Test, sameInstance(parent2));
    }

}
