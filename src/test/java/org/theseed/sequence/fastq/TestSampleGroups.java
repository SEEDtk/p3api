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
        rStream = group.sampleIter("ERS006602");
        assertThat(rStream.hasNext(), equalTo(true));
        read = rStream.next();
        assertThat(read.getLabel(), equalTo("NODE_1_length_177931_cov_13.2654"));
        assertThat(read.getCoverage(), closeTo(13.2654, 0.0001));
        assertThat(rStream.hasNext(), equalTo(true));
        read = rStream.next();
        assertThat(read.getLabel(), equalTo("NODE_2_length_109977_cov_11.0852"));
        assertThat(read.getCoverage(), closeTo(11.0852, 0.0001));
        assertThat(read.getSequence(), equalTo("aacttcattggtcggtgcattcctgcttcaaactcgaagagagcacaggtacgtcaatcatcaccgacccgtaccacccgtacgtcggtttctcgatgcccgaagtttcttgcgacgccgtgacgctcagtc"));
        assertThat(rStream.hasNext(), equalTo(true));
        read = rStream.next();
        assertThat(read.getLabel(), equalTo("NODE_3_length_70673_cov_11.7944"));
        assertThat(read.getCoverage(), closeTo(11.7944, 0.0001));
        assertThat(rStream.hasNext(), equalTo(true));
        read = rStream.next();
        assertThat(read.getLabel(), equalTo("NODE_4_length_68806_cov_10.5822"));
        assertThat(read.getCoverage(), closeTo(10.5822, 0.0001));
        assertThat(rStream.hasNext(), equalTo(false));
    }

}
