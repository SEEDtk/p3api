/**
 *
 */
package org.theseed.ncbi;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.theseed.test.Matchers.*;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * @author Bruce Parrello
 *
 */
public class NcbiTest {

    @Test
    public void testQuery() {
        NcbiQuery query = new NcbiQuery(NcbiTable.SRA).EQ("Organism", "Escherichia coli").EQ("Strategy", "rna-seq");
        String result = query.toString();
        assertThat(result, equalTo("db=sra&term=\"Escherichia+coli\"[Organism]+AND+\"rna-seq\"[Strategy]"));
        query = new NcbiQuery(NcbiTable.BIOPROJECT);
        result = query.toString();
        assertThat(result, equalTo("db=bioproject"));
        query = new NcbiQuery(NcbiTable.SRA).EQ("Strategy", "rna-seq");
        result = query.toString();
        assertThat(result, equalTo("db=sra&term=\"rna-seq\"[Strategy]"));
        Calendar now = GregorianCalendar.getInstance();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd");
        String nowFormat = formatter.format(now.getTime());
        query.since(NcbiQuery.PUB_DATE, LocalDate.of(2014, 10, 20));
        result = query.toString();
        assertThat(query.getTable().db(), equalTo("sra"));
        assertThat(result, equalTo("db=sra&term=\"rna-seq\"[Strategy]&datetype=pdat&mindate=2014/10/20&maxdate=" + nowFormat));
    }

    @Test
    public void testConnection() throws XmlException {
        NcbiConnection ncbi = new NcbiConnection();
        // Insure the chunk size is small enough to require chunking.
        ncbi.setChunkSize(10);
        // Get a known project.
        List<Element> experiments = new NcbiQuery(NcbiTable.SRA).EQ("BioProject", "PRJNA238884").run(ncbi);
        assertThat(experiments.size(), equalTo(22));
        // Verify that everything points to the correct project and journal.
        for (Element experiment : experiments) {
            // Get the experiment ID.
            Element expElement = XmlUtils.getFirstByTagName(experiment, "EXPERIMENT");
            String expId = expElement.getAttribute("accession");
            if (expId.contentEquals("SRX474186")) {
                Element sample = XmlUtils.getFirstByTagName(expElement, "SAMPLE_DESCRIPTOR");
                assertThat(sample.getAttribute("accession"), equalTo("SRS560175"));
                Element run = XmlUtils.getFirstByTagName(experiment, "RUN");
                assertThat(run.getAttribute("accession"), equalTo("SRR1173986"));
            }
            boolean projFound = false;
            boolean pmFound = false;
            // Get all the xrefs.
            NodeList xrefs = experiment.getElementsByTagName("XREF_LINK");
            for (int i = 0; i < xrefs.getLength(); i++) {
                Element xref = (Element) xrefs.item(i);
                String xdb = XmlUtils.getXmlString(xref, "DB");
                switch (xdb) {
                case "bioproject" :
                    String proj = XmlUtils.getXmlString(xref, "LABEL");
                    assertThat(expId, proj, equalTo("PRJNA238884"));
                    projFound = true;
                    break;
                case "pubmed" :
                    String pubId = XmlUtils.getXmlString(xref, "ID");
                    if (pubId.contentEquals("25266388"))
                        pmFound = true;
                    break;
                }
            }
            assertThat(expId, projFound, isTrue());
            assertThat(expId, pmFound, isTrue());
        }
    }

    @Test
    public void testFieldList() throws XmlException {
        NcbiConnection ncbi = new NcbiConnection();
        List<NcbiConnection.Field> fields = ncbi.getFieldList(NcbiTable.SRA);
        assertThat(fields.size(), equalTo(22));
        Set<String> fieldSet = new HashSet<String>(Arrays.asList("ALL", "UID", "FILT", "ACCN", "TITL", "PROP", "WORD",
                "ORGN", "AUTH", "PDAT", "MDAT", "GPRJ", "BSPL", "PLAT", "STRA", "SRC", "SEL", "LAY", "RLEN", "ACS",
                "ALN", "MBS"));
        for (NcbiConnection.Field field : fields) {
            String fieldName = field.getName();
            assertThat(fieldSet, hasItem(fieldName));
            if (fieldName.contentEquals("ORGN")) {
                assertThat(field.getFullName(), equalTo("Organism"));
                assertThat(field.getDescription(), equalTo("Scientific and common names of organism, and all higher levels of taxonomy"));
                assertThat(field.toLine(), equalTo("ORGN\tOrganism\tScientific and common names of organism, and all higher levels of taxonomy"));
            }
        }
    }

    @Test
    public void testDates() {
        // Create a local date from today and verify it can be recovered from a string.
        LocalDate today = LocalDate.now();
        String dateString = today.toString();
        LocalDate fromString = LocalDate.parse(dateString);
        assertThat(fromString, equalTo(today));
    }

}