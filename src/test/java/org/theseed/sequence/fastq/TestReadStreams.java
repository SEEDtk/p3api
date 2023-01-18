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
    void test() throws IOException {
        File sampleDir = new File("data");
        FastqSampleDescriptor sampleDesc = new FastqSampleDescriptor(sampleDir, "small_test.fastq.gz", null);
        ReadStream inStream = sampleDesc.reader();
        var iter = inStream.iterator();
        assertThat(iter.hasNext(), equalTo(true));
        SeqRead read = iter.next();
        assertThat(read.getLabel(), equalTo("ERR1912949.1"));
        assertThat(read.getLseq(), equalTo("ctcaagtcctgacttacctcaacccttaccacggtgagcattttatcttttactcgcggatccttgagctcgcggataagcgcaactatttcg"));
        assertThat(read.getRseq(), equalTo("aagcgctttaaggacgacgtaaaagaggttcagtccggctacgaatgcggaatcggtcttgagaaattcaacgacattaaagagggagacatc"));
        assertThat(iter.hasNext(), equalTo(true));
        read = iter.next();
        assertThat(read.getLabel(), equalTo("ERR1912949.2"));
        assertThat(read.getLseq(), equalTo("catttggttttacctccataatttcgtataatcggttttcgtatcggtgatgccagtgtgcttgtcggtgaaaaatccgctgtgctgcaaatc"));
        assertThat(read.getRseq(), equalTo("aaccggacttcaaaattgccgtacacgccgtacactctgtacggcatttttcaacttcatccgcaaaatacggttttgcttgtgcctttggtg"));
        read = iter.next();
        assertThat(read.getLabel(), equalTo("ERR1912949.3"));
        assertThat(read.getLseq(), equalTo("cagcacagcaacttcccggtcattgatgcgctggaagagaacaagatgcttcaccagcaggtcaaggagctgaacggagaactgaaacgggcc"));
        assertThat(read.getRseq(), equalTo(""));
        read = iter.next();
        assertThat(read.getLabel(), equalTo("ERR1912949.4"));
        assertThat(read.getLseq(), equalTo("tgtaatattaagcatcctgccccaactgtcataaaaatagtttactacagccgtgccattggcgtcaaccagtcctgtgatgcacatcagtcc"));
        assertThat(read.getRseq(), equalTo(""));
        read = iter.next();
        assertThat(read.getLabel(), equalTo("ERR1912949.5"));
        assertThat(read.getLseq(), equalTo("cgcaccactctttattaactgcctgcaacattgcatatcacttatacgatagaaatcaatttgttttttatacgtataagcatacactattaa"));
        assertThat(read.getRseq(), equalTo(""));
        read = iter.next();
        assertThat(read.getLabel(), equalTo("ERR1912949.6"));
        assertThat(read.getLseq(), equalTo("gaatattttattagcagggggagctggctacattggttctcatacagcagtggaattattaacagcaggacatgacgtagttatcgtagataa"));
        assertThat(read.getRseq(), equalTo(""));
        assertThat(iter.hasNext(), equalTo(false));
        inStream.close();
        // Now do paired-end.
        sampleDir = new File("data/fqTest", "SRR11321054");
        sampleDesc = new FastqSampleDescriptor(sampleDir, "SRR11321054_1.fastq", "SRR11321054_2.fastq");
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
        inStream.close();
    }

}
