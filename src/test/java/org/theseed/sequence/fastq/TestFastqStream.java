/**
 *
 */
package org.theseed.sequence.fastq;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.theseed.test.Matchers.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Bruce Parrello
 *
 */
public class TestFastqStream {

    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(TestFastqStream.class);


    @Test
    public void testDirStream() throws IOException {
        DirFastqStream nullStream = new DirFastqStream(new File("data", "core.test"));
        assertThat(nullStream.hasNext(), isFalse());
        FastqStream fqStream = new DirFastqStream(new File("data", "ERRtest"));
        compareFqStream(fqStream);
    }

    @Test
    public void testQzaStream() throws IOException {
        FastqStream qzaStream = new QzaFastqStream(new File("data", "ERRtest_L001.trimmed.qza"));
        compareFqStream(qzaStream);
    }

    private void compareFqStream(FastqStream fqStream) throws IOException, FileNotFoundException {
        assertThat(fqStream.getSampleId(), equalTo("ERRtest"));
        // Get readers for the paired files and test them.
        try (BufferedReader reader1 = new BufferedReader(new FileReader(new File("data/ERRtest", "set1_1.fastq")));
             BufferedReader reader2 = new BufferedReader(new FileReader(new File("data/ERRtest", "set1_2.fastq")))) {
            for (SeqRead.Part part1 = SeqRead.read(reader1); part1 != null; part1 = SeqRead.read(reader1)) {
                SeqRead.Part part2 = SeqRead.read(reader2);
                SeqRead expected = new SeqRead(part1, part2);
                assertThat(fqStream.hasNext(), isTrue());
                SeqRead actual = fqStream.next();
                assertThat(expected.getLabel(), actual, equalTo(expected));
            }
        }
        // Get a reader for the singleton file and test it.
        try (BufferedReader reader0 = new BufferedReader(new FileReader(new File("data", "set2_s.fq")))) {
            for (SeqRead.Part part1 = SeqRead.read(reader0); part1 != null; part1 = SeqRead.read(reader0)) {
                SeqRead expected = new SeqRead(part1);
                SeqRead actual = fqStream.next();
                assertThat(expected.getLabel(), actual, equalTo(expected));
            }
        }
    }

}
