/**
 *
 */
package org.theseed.sequence.fastq;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Spliterator;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Bruce Parrello
 *
 */
class TestSplitter {

    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(TestSplitter.class);


    @Test
    void testSplitter() throws IOException {
        File dir = new File("data", "stream_test");
        FastqSampleGroup group = FastqSampleGroup.Type.FASTA.create(dir);
        Spliterator<SampleDescriptor> splitter = new FastqSampleGroup.Splitter(group);
        final var testList = new ArrayList<SampleDescriptor>(50);
        // Verify a try-advance.
        assertThat(splitter.tryAdvance(x -> testList.add(x)), equalTo(true));
        assertThat(testList.size(), equalTo(1));
        long total = ((FastqSampleGroup.Splitter) splitter).length();
        // Try some splits.
        var split1 = splitter.trySplit();
        long part1 = ((FastqSampleGroup.Splitter) split1).length();
        long part2 = ((FastqSampleGroup.Splitter) splitter).length();
        assertThat(part1 + part2, equalTo(total));
        while (split1 != null) {
            part1 = ((FastqSampleGroup.Splitter) split1).length();
            part2 = ((FastqSampleGroup.Splitter) splitter).length();
            if (splitter.estimateSize() > 1)
                assertThat(part1, greaterThanOrEqualTo(part2));
            split1 = splitter.trySplit();
        }
        // Do a logging test.
        group.stream(true).forEach(x -> log.info("Sample {} has length {}.", x.getId(), x.estimatedSize()));
    }

}
