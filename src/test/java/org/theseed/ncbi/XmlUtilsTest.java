/**
 *
 */
package org.theseed.ncbi;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
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
                assertThat(iter.hasNext(), equalTo(true));
                Element sampleA = iter.next();
                assertThat(XmlUtils.getXmlString(sampleA, "TAG"), equalTo("source_name"));
                assertThat(XmlUtils.getXmlString(sampleA, "VALUE"), equalTo("Escherichia coli MG1655 cells"));
                assertThat(iter.hasNext(), equalTo(true));
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
                assertThat(iter.hasNext(), equalTo(false));
            }
        }
    }

    @Test
    public void testCleaner() throws XmlException, FileNotFoundException {
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
        assertThat(parent2.getTextContent(), equalTo("This is real text"));
        xmlString = "<DocumentSummary uid=\"4686068\">\r\n"
                + "        <RsUid>1531508</RsUid>\r\n"
                + "        <GbUid>4686068</GbUid>\r\n"
                + "        <AssemblyAccession>GCF_000836885.1</AssemblyAccession>\r\n"
                + "        <LastMajorReleaseAccession>GCF_000836885.1</LastMajorReleaseAccession>\r\n"
                + "        <LatestAccession></LatestAccession>\r\n"
                + "        <ChainId>836885</ChainId>\r\n"
                + "        <AssemblyName>ViralProj14030</AssemblyName>\r\n"
                + "        <UCSCName></UCSCName>\r\n"
                + "        <EnsemblName></EnsemblName>\r\n"
                + "        <Taxid>57579</Taxid>\r\n"
                + "        <Organism>Adeno-associated virus - 4 (viruses)</Organism>\r\n"
                + "        <SpeciesTaxid>1511891</SpeciesTaxid>\r\n"
                + "        <SpeciesName>Dependoparvovirus primate1</SpeciesName>\r\n"
                + "        <AssemblyType>haploid</AssemblyType>\r\n"
                + "        <AssemblyStatus>Complete Genome</AssemblyStatus>\r\n"
                + "        <AssemblyStatusSort>1</AssemblyStatusSort>\r\n"
                + "        <WGS></WGS>\r\n"
                + "        <GB_BioProjects>\r\n"
                + "        </GB_BioProjects>\r\n"
                + "        <GB_Projects>\r\n"
                + "        </GB_Projects>\r\n"
                + "        <RS_BioProjects>\r\n"
                + "                <Bioproj>\r\n"
                + "                        <BioprojectAccn>PRJNA485481</BioprojectAccn>\r\n"
                + "                        <BioprojectId>485481</BioprojectId>\r\n"
                + "                </Bioproj>\r\n"
                + "        </RS_BioProjects>\r\n"
                + "        <RS_Projects>\r\n"
                + "        </RS_Projects>\r\n"
                + "        <BioSampleAccn></BioSampleAccn>\r\n"
                + "        <BioSampleId></BioSampleId>\r\n"
                + "        <Biosource>\r\n"
                + "                <InfraspeciesList>\r\n"
                + "                </InfraspeciesList>\r\n"
                + "                <Sex></Sex>\r\n"
                + "                <Isolate>ATCC VR-646</Isolate>\r\n"
                + "        </Biosource>\r\n"
                + "        <Coverage></Coverage>\r\n"
                + "        <PartialGenomeRepresentation>false</PartialGenomeRepresentation>\r\n"
                + "        <Primary>1531498</Primary>\r\n"
                + "        <AssemblyDescription></AssemblyDescription>\r\n"
                + "        <ReleaseLevel>Major</ReleaseLevel>\r\n"
                + "        <ReleaseType>Major</ReleaseType>\r\n"
                + "        <AsmReleaseDate_GenBank>2017/07/19 00:00</AsmReleaseDate_GenBank>\r\n"
                + "        <AsmReleaseDate_RefSeq>2015/02/12 00:00</AsmReleaseDate_RefSeq>\r\n"
                + "        <SeqReleaseDate>2000/08/01 00:00</SeqReleaseDate>\r\n"
                + "        <AsmUpdateDate>2021/11/05 00:00</AsmUpdateDate>\r\n"
                + "        <SubmissionDate>2000/08/01 00:00</SubmissionDate>\r\n"
                + "        <LastUpdateDate>2021/11/05 00:00</LastUpdateDate>\r\n"
                + "        <SubmitterOrganization>na</SubmitterOrganization>\r\n"
                + "        <RefSeq_category>na</RefSeq_category>\r\n"
                + "        <AnomalousList>\r\n"
                + "        </AnomalousList>\r\n"
                + "        <ExclFromRefSeq>\r\n"
                + "        </ExclFromRefSeq>\r\n"
                + "        <PropertyList>\r\n"
                + "                <string>full-genome-representation</string>\r\n"
                + "                <string>has-segment</string>\r\n"
                + "                <string>has_annotation</string>\r\n"
                + "                <string>latest</string>\r\n"
                + "                <string>latest_genbank</string>\r\n"
                + "                <string>latest_refseq</string>\r\n"
                + "                <string>refseq_has_annotation</string>\r\n"
                + "        </PropertyList>\r\n"
                + "        <FromType></FromType>\r\n"
                + "        <Synonym>\r\n"
                + "                <Genbank>GCA_000836885.1</Genbank>\r\n"
                + "                <RefSeq>GCF_000836885.1</RefSeq>\r\n"
                + "                <Similarity>identical</Similarity>\r\n"
                + "        </Synonym>\r\n"
                + "        <ContigN50>4767</ContigN50>\r\n"
                + "        <ScaffoldN50>4767</ScaffoldN50>\r\n"
                + "        <AnnotRptUrl></AnnotRptUrl>\r\n"
                + "        <FtpPath_GenBank>ftp://ftp.ncbi.nlm.nih.gov/genomes/all/GCA/000/836/885/GCA_000836885.1_ViralProj14030</FtpPath_GenBank>\r\n"
                + "        <FtpPath_RefSeq>ftp://ftp.ncbi.nlm.nih.gov/genomes/all/GCF/000/836/885/GCF_000836885.1_ViralProj14030</FtpPath_RefSeq>\r\n"
                + "        <FtpPath_Assembly_rpt>ftp://ftp.ncbi.nlm.nih.gov/genomes/all/GCF/000/836/885/GCF_000836885.1_ViralProj14030/GCF_000836885.1_ViralProj14030_assembly_report.txt</FtpPath_Assembly_rpt>\r\n"
                + "        <FtpPath_Stats_rpt>ftp://ftp.ncbi.nlm.nih.gov/genomes/all/GCF/000/836/885/GCF_000836885.1_ViralProj14030/GCF_000836885.1_ViralProj14030_assembly_stats.txt</FtpPath_Stats_rpt>\r\n"
                + "        <FtpPath_Regions_rpt></FtpPath_Regions_rpt>\r\n"
                + "        <Busco>\r\n"
                + "                <RefSeqAnnotationRelease></RefSeqAnnotationRelease>\r\n"
                + "                <BuscoLineage></BuscoLineage>\r\n"
                + "                <BuscoVer></BuscoVer>\r\n"
                + "                <Complete></Complete>\r\n"
                + "                <SingleCopy></SingleCopy>\r\n"
                + "                <Duplicated></Duplicated>\r\n"
                + "                <Fragmented></Fragmented>\r\n"
                + "                <Missing></Missing>\r\n"
                + "                <TotalCount>0</TotalCount>\r\n"
                + "        </Busco>\r\n"
                + "        <SortOrder>5C1XA2FF9999952320008368859898</SortOrder>\r\n"
                + "        <Meta><![CDATA[ <Stats> <Stat category=\"alt_loci_count\" sequence_tag=\"all\">0</Stat> <Stat category=\"chromosome_count\" sequence_tag=\"all\">0</Stat> <Stat category=\"contig_count\" sequence_tag=\"all\">1</Stat> <Stat category=\"contig_l50\" sequence_tag=\"all\">1</Stat> <Stat category=\"contig_n50\" sequence_tag=\"all\">4767</Stat> <Stat category=\"non_chromosome_replicon_count\" sequence_tag=\"all\">1</Stat> <Stat category=\"replicon_count\" sequence_tag=\"all\">1</Stat> <Stat category=\"scaffold_count\" sequence_tag=\"all\">1</Stat> <Stat category=\"scaffold_count\" sequence_tag=\"placed\">1</Stat> <Stat category=\"scaffold_count\" sequence_tag=\"unlocalized\">0</Stat> <Stat category=\"scaffold_count\" sequence_tag=\"unplaced\">0</Stat> <Stat category=\"scaffold_l50\" sequence_tag=\"all\">1</Stat> <Stat category=\"scaffold_n50\" sequence_tag=\"all\">4767</Stat> <Stat category=\"total_length\" sequence_tag=\"all\">4767</Stat> <Stat category=\"ungapped_length\" sequence_tag=\"all\">4767</Stat> </Stats> <FtpSites>   <FtpPath type=\"Assembly_rpt\">ftp://ftp.ncbi.nlm.nih.gov/genomes/all/GCF/000/836/885/GCF_000836885.1_ViralProj14030/GCF_000836885.1_ViralProj14030_assembly_report.txt</FtpPath>   <FtpPath type=\"GenBank\">ftp://ftp.ncbi.nlm.nih.gov/genomes/all/GCA/000/836/885/GCA_000836885.1_ViralProj14030</FtpPath>   <FtpPath type=\"RefSeq\">ftp://ftp.ncbi.nlm.nih.gov/genomes/all/GCF/000/836/885/GCF_000836885.1_ViralProj14030</FtpPath>   <FtpPath type=\"Stats_rpt\">ftp://ftp.ncbi.nlm.nih.gov/genomes/all/GCF/000/836/885/GCF_000836885.1_ViralProj14030/GCF_000836885.1_ViralProj14030_assembly_stats.txt</FtpPath> </FtpSites> <assembly-level>90</assembly-level> <assembly-status>Complete Genome</assembly-status> <representative-status>na</representative-status> <submitter-organization>na</submitter-organization>    ]]></Meta>\r\n"
                + "</DocumentSummary>";
        root = XmlUtils.parseXmlString(xmlString);
        XmlUtils.cleanElement(root);
        Node child1 = root.getChildNodes().item(0);
        String context = child1.getTextContent();
        assertThat(context, equalTo("1531508"));
    }

}
