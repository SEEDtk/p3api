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
class TestReadStreams {

    @Test
    void testSampleGroupTypes() throws IOException {
        File sampleDir = new File("data");
        FastqSampleDescriptor sampleDesc = new FastqSampleDescriptor(sampleDir, "small", "small_test.fastq.gz", null, null);
        assertThat(sampleDesc.getId(), equalTo("small"));
        ReadStream inStream = sampleDesc.reader();
        var iter = inStream.iterator();
        assertThat(iter.hasNext(), equalTo(true));
        SeqRead read = iter.next();
        assertThat(read.getLabel(), equalTo("ERR1912949.1.1"));
        assertThat(read.getLseq(), equalTo("ctcaagtcctgacttacctcaacccttaccacggtgagcattttatcttttactcgcggatccttgagctcgcggataagcgcaactatttcg"));
        assertThat(iter.hasNext(), equalTo(true));
        read = iter.next();
        assertThat(read.getLabel(), equalTo("ERR1912949.1.2"));
        assertThat(read.getLseq(), equalTo("aagcgctttaaggacgacgtaaaagaggttcagtccggctacgaatgcggaatcggtcttgagaaattcaacgacattaaagagggagacatc"));
        assertThat(iter.hasNext(), equalTo(true));
        inStream.close();
        // Now do paired-end.
        sampleDir = new File("data/fqTest", "SRR11321054");
        sampleDesc = new FastqSampleDescriptor(sampleDir, "SRR11321054", "SRR11321054_1.fastq", "SRR11321054_2.fastq", "SRR11321054_s.fastq");
        assertThat(sampleDesc.getId(), equalTo("SRR11321054"));
        inStream = sampleDesc.reader();
        iter = inStream.iterator();
        assertThat(iter.hasNext(), equalTo(true));
        read = iter.next();
        assertThat(read.getLabel(), equalTo("SRR11321054.2"));
        assertThat(read.getLseq(), equalTo("cctacgggaggctgcagtggggaatattgcacaatgggggaaaccctgatgcagcaacgccgcgtgagtgaagaagtatttcggtatgtaaagctctatcagcagggaagaaagttacggtacctgtctaataatccccttctaactacgtgccagctgccgcggtaatacgtagggggcaagcgttatccggatttactgggtgtaaagcgcacgcagtcggtttgttaagtcagatgtgaaatccccgggctcatcctgggaactgcatctgttactggcaagcttgagtctcgtagag"));
        assertThat(read.getRseq(), equalTo("gactacaggggtatctaatcctgtttgctccccacgctttcgcacctgagcgtcagtcttcgtccagtgggccgccttcgccaccggtattcctcctgatctctacgcatttcaccgctacacctggaattctacccccctctactacactcaagcttcccagtatcagatgcagttcccaggttgagcccgtggttttcacatctgacttaacaacccgcctgcgttcgctttacacccagtatatccggataacgctttccccctacgtattccctcggctgctggcacgtacttacc"));
        assertThat(iter.hasNext(), equalTo(true));
        read = iter.next();
        assertThat(read.getLabel(), equalTo("SRR11321054.3"));
        assertThat(read.getRseq(), equalTo("gactacccgggtatctaatcctgtttgctacccacgctttcgtgcctcagcgtcagttattgcccagcaggccgccttcgccactggtgttcctcccgatatctacgcattccaccgctacaccgggaattccgcctacctctgctctactcaagccaaacagttttgaaagcagttcctgggttgagcccatggctttcacttccaacttgtcctcccgcctgcgctccctttacacccagtaattccggacaacgcttgcgccctacgttttaccgcggctgctggcacgtagttagc"));
        assertThat(read.getLseq(), equalTo("cctacggggggctgcagtggggaatattgcacaatgggggaaaccctgatgcagcgacgccgcgtggaggaagaaggtcttcggtttgtaaactcctgttgttgaggaagataattacggtactcaacaagttatttacgtctatctacgttccagcagccgcggtaaaacgtaggtcacaagcgttgtccggaattactgggtgtaaagggagcgcaggcgggtgcacaagttggaagtgaaatccatgggctcaacccatgaactgctttcaaacctgtttttcttgagtagtgcagag"));
        read = iter.next();
        assertThat(read.getLabel(), equalTo("SRR11321054.4"));
        assertThat(read.getLseq(), equalTo("cctacgggtggctgcagtggggaatattgcacaatgggcgcaagcctgatgcagccatgccgcgtgtatgaagaaggccttctggttgtaaagtactttcagcggggaggaaggttgtaaagttaatacctttgctcatttacgttaccctcagaagaagcaccggctaactccgtcccagcagcctcggtaatactgagggtgcaagcgttaatcggaattactgggtttaaagggagctcaggccgtcctttaagcgtgctgtgaaatgccgcggctcaaccgttgcactgcagcgcga"));
        assertThat(read.getRseq(), equalTo("gactactcgggtatctaatcctgttcgatacccgcgctttcgtgcctcagcgtcagtttctctccggtaagctgccttcgcaatcggagttcttcgtgatatctaagcatttcaccgctacaccacgaattccgcctacctcgtgcgtactcaagtcctccagttctcgctgcagttccacggttgagccgcggcatttcacagcacgcttaactgacggcctgcgctccctttaaaccccgtaattccgattaacgcttgcaccctccgtattaccgcggctgctggcacggatttagc"));
        read = iter.next();
        assertThat(read.getLabel(), equalTo("SRR11321054.5"));
        assertThat(read.getLseq(), equalTo("cctacgggtggctgcagtggggaatattgcacaatgggcgcaagcctgatgcagcgacgccgcgtgagggatggaggccttcgggttgtaaacctcttttatcggggagcaagcgtgagttagtttacccgttgaataagcaccggctaactacgtgccagcagccgcggtaatacgtagggtgcaagcgttatccggaattatttggcgtaaagggctcgtaggcggttcgtcgcgtccggtgtgcaagtccatcgcttaacggtggatccgcgccgggtacgggcgggcttgagtgcgg"));
        assertThat(read.getRseq(), equalTo("gactacaggggtatctaatcctgttcgctccccacgctttcgcccctcagcgtcagttactgcccagtgacctgccttcgccattggtgttcttcccgatatctacacattccaccgttacaccgggaattccagtctcccctaccgcactcaagcccgcccgtacccggcgcggatccaccgttaagcgatggactttcacaccggacgcgaccaaccgcctacgagccctttacgcccaatcattccggataacgcttgcaccctacgtattaccgcggctgctggcacgtagttagc"));
        read = iter.next();
        assertThat(read.getLabel(), equalTo("SRR11321056.1.1"));
        assertThat(read.getLseq(), equalTo("cctacgggcggcagcagtggggaatattgctcaatggaggcaactctgatgcagcgacgccgcgtgaatgatgaagtttttctgttttttaacttctttcttcctggtagtaaatttctttacctttctctttttccccttctttcttccttccagctgccgcggttaaacttatgttgcaagcgttctccggaattactgggttttatgcgagcttattcggtttcgccagtctgttgtgaattccctgggctcccccccggtccttcattgtttactgttcatcttgtgtgctggcgtg"));
        assertThat(read.getRseq(), equalTo(""));
        read = iter.next();
        assertThat(read.getLabel(), equalTo("SRR11321056.2.1"));
        assertThat(read.getLseq(), equalTo("cctacgggaggcagcagtgggggatattgcacaatgggggaaaccctgatgcagcgacgccgcgtgtatgatgaaggttttctgtttgtaaactcctttctttagggacgttaatttcgctacctatctatttatccccttctatctccgttccagctgccgcggtaaaacgtaggttgcaagcgttctccggaattactggttgtaaagcgagcgcactcggtttgacaagtttgtagtgaaatctatgggctcatcccataatcttctttctaaactgtttttcttcagtagtgcagag"));
        assertThat(read.getRseq(), equalTo(""));
        assertThat(iter.hasNext(), equalTo(false));
        inStream.close();
    }

}
