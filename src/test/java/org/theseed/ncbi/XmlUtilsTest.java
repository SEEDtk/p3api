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

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

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

}
