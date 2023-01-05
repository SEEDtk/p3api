/**
 *
 */
package org.theseed.sequence.fastq;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * @author Bruce Parrello
 *
 */
class TestSampleGroups {

    @Test
    void testDirTypes() throws IOException {
        File testDir = new File("data", "fqTest");
        // Check as a FASTQ directory.  Should be two samples.
        FastqSampleGroup group = FastqSampleGroup.Type.FASTQ.create(testDir);
        var sampleSet = group.getSamples();
        assertThat(sampleSet.size(), equalTo(2));
        assertThat(sampleSet, containsInAnyOrder("SRR11321054", "SRR11321056"));
        ReadStream rStream = group.sampleIter("SRR11321054");
        assertThat(rStream.hasNext(), equalTo(true));
        SeqRead read = rStream.next();
        assertThat(read.getLabel(), equalTo("SRR11321054.2"));
        assertThat(read.getLseq(), equalTo("cctacgggaggctgcagtggggaatattgcacaatgggggaaaccctgatgcagcaacgccgcgtgagtgaagaagtatttcggtatgtaaagctctatcagcagggaagaaagttacggtacctgtctaataatccccttctaactacgtgccagctgccgcggtaatacgtagggggcaagcgttatccggatttactgggtgtaaagcgcacgcagtcggtttgttaagtcagatgtgaaatccccgggctcatcctgggaactgcatctgttactggcaagcttgagtctcgtagag"));
        assertThat(read.getRseq(), equalTo("gactacaggggtatctaatcctgtttgctccccacgctttcgcacctgagcgtcagtcttcgtccagtgggccgccttcgccaccggtattcctcctgatctctacgcatttcaccgctacacctggaattctacccccctctactacactcaagcttcccagtatcagatgcagttcccaggttgagcccgtggttttcacatctgacttaacaacccgcctgcgttcgctttacacccagtatatccggataacgctttccccctacgtattccctcggctgctggcacgtacttacc"));
        assertThat(read.getCoverage(), equalTo(1.0));
        String allSeq = read.getSequence();
        assertThat(allSeq.substring(0, 100), equalTo(read.getLseq().substring(0, 100)));
        assertThat(rStream.hasNext(), equalTo(true));
        read = rStream.next();
        assertThat(read.getLabel(), equalTo("SRR11321054.3"));
        assertThat(read.getLseq(), equalTo("cctacggggggctgcagtggggaatattgcacaatgggggaaaccctgatgcagcgacgccgcgtggaggaagaaggtcttcggtttgtaaactcctgttgttgaggaagataattacggtactcaacaagttatttacgtctatctacgttccagcagccgcggtaaaacgtaggtcacaagcgttgtccggaattactgggtgtaaagggagcgcaggcgggtgcacaagttggaagtgaaatccatgggctcaacccatgaactgctttcaaacctgtttttcttgagtagtgcagag"));
        assertThat(read.getRseq(), equalTo("gactacccgggtatctaatcctgtttgctacccacgctttcgtgcctcagcgtcagttattgcccagcaggccgccttcgccactggtgttcctcccgatatctacgcattccaccgctacaccgggaattccgcctacctctgctctactcaagccaaacagttttgaaagcagttcctgggttgagcccatggctttcacttccaacttgtcctcccgcctgcgctccctttacacccagtaattccggacaacgcttgcgccctacgttttaccgcggctgctggcacgtagttagc"));
        assertThat(read.getCoverage(), equalTo(1.0));
        assertThat(rStream.hasNext(), equalTo(true));
        read = rStream.next();
        assertThat(read.getLabel(), equalTo("SRR11321054.4"));
        assertThat(read.getCoverage(), equalTo(1.0));
        allSeq = read.getSequence();
        assertThat(allSeq.substring(0, 100), equalTo(read.getLseq().substring(0, 100)));
        assertThat(rStream.hasNext(), equalTo(true));
        read = rStream.next();
        assertThat(read.getLabel(), equalTo("SRR11321054.5"));
        assertThat(read.getCoverage(), equalTo(1.0));
        allSeq = read.getSequence();
        assertThat(allSeq.substring(0, 100), equalTo(read.getLseq().substring(0, 100)));
        assertThat(rStream.hasNext(), equalTo(false));
        group = FastqSampleGroup.Type.FASTA.create(testDir);
        sampleSet = group.getSamples();
        assertThat(sampleSet.size(), equalTo(2));
        assertThat(sampleSet, containsInAnyOrder("ERR1136887", "ERS006602"));
    }

}
