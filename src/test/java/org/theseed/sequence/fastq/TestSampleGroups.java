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
        SeqRead.setMinOverlap(5);
        // Check as a FASTQ directory.  Should be four samples.
        FastqSampleGroup group = FastqSampleGroup.Type.FASTQ.create(testDir);
        var sampleSet = group.getSampleIDs();
        assertThat(sampleSet.size(), equalTo(4));
        assertThat(sampleSet, containsInAnyOrder("SRR11321054", "SRR11321056", "sample.x", "SRR1212"));
        // Verify the sizes.
        SampleDescriptor desc = group.getDescriptor("SRR11321054");
        assertThat(desc.estimatedSize(), equalTo(6672L));
        desc = group.getDescriptor("SRR11321056");
        assertThat(desc.estimatedSize(), equalTo(2672L));
        desc = group.getDescriptor("sample.x");
        assertThat(desc.estimatedSize(), equalTo(3240L));
        // Check reading.
        ReadStream rStream = group.sampleIter("SRR11321054");
        assertThat(rStream.hasNext(), equalTo(true));
        SeqRead read = rStream.next();
        assertThat(read.getLabel(), equalTo("SRR11321054.2"));
        assertThat(read.getLseq(), equalTo("cctacgggaggctgcagtggggaatattgcacaatgggggaaaccctgatgcagcaacgccgcgtgagtgaagaagtatttcggtatgtaaagctctatcagcagggaagaaagttacggtacctgtctaataatccccttctaactacgtgccagctgccgcggtaatacgtagggggcaagcgttatccggatttactgggtgtaaagcgcacgcagtcggtttgttaagtcagatgtgaaatccccgggctcatcctgggaactgcatctgttactggcaagcttgagtctcgtagag"));
        assertThat(read.getRseq(), equalTo("gactacaggggtatctaatcctgtttgctccccacgctttcgcacctgagcgtcagtcttcgtccagtgggccgccttcgccaccggtattcctcctgatctctacgcatttcaccgctacacctggaattctacccccctctactacactcaagcttcccagtatcagatgcagttcccaggttgagcccgtggttttcacatctgacttaacaacccgcctgcgttcgctttacacccagtatatccggataacgctttccccctacgtattccctcggctgctggcacgtacttacc"));
        assertThat(read.getCoverage(), equalTo(1.0));
        SeqRead.Part allPart = read.getSequence();
        String allSeq = allPart.getSequence();
        String allQ = allPart.getQual();
        assertThat(allSeq.substring(0, 100), equalTo(read.getLseq().substring(0, 100)));
        assertThat(allQ.substring(0, 100), equalTo(read.getLQual().substring(0, 100)));
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
        allPart = read.getSequence();
        allSeq = allPart.getSequence();
        allQ = allPart.getQual();
        assertThat(allSeq.substring(0, 100), equalTo(read.getLseq().substring(0, 100)));
        assertThat(allQ.substring(0, 100), equalTo(read.getLQual().substring(0, 100)));
        assertThat(rStream.hasNext(), equalTo(true));
        read = rStream.next();
        assertThat(read.getLabel(), equalTo("SRR11321054.5"));
        assertThat(read.getCoverage(), equalTo(1.0));
        allPart = read.getSequence();
        allSeq = allPart.getSequence();
        allQ = allPart.getQual();
        assertThat(allSeq.substring(0, 100), equalTo(read.getLseq().substring(0, 100)));
        assertThat(allQ.substring(0, 100), equalTo(read.getLQual().substring(0, 100)));
        assertThat(rStream.hasNext(), equalTo(true));
        rStream = group.sampleIter("sample.x");
        assertThat(rStream.hasNext(), equalTo(true));
        read = rStream.next();
        assertThat(read.getLabel(), equalTo("ERR1912949.1.1"));
        assertThat(read.getLseq(), equalTo("ctcaagtcctgacttacctcaacccttaccacggtgagcattttatcttttactcgcggatccttgagctcgcggataagcgcaactatttcg"));
        assertThat(read.getRseq(), equalTo(""));
        assertThat(rStream.hasNext(), equalTo(true));
        read = rStream.next();
        assertThat(read.getLabel(), equalTo("ERR1912949.1.2"));
        assertThat(read.getLseq(), equalTo("aagcgctttaaggacgacgtaaaagaggttcagtccggctacgaatgcggaatcggtcttgagaaattcaacgacattaaagagggagacatc"));
        assertThat(read.getRseq(), equalTo(""));
        assertThat(rStream.hasNext(), equalTo(true));
        read = rStream.next();
        assertThat(read.getLabel(), equalTo("ERR1912949.2.1"));
        assertThat(read.getLseq(), equalTo("catttggttttacctccataatttcgtataatcggttttcgtatcggtgatgccagtgtgcttgtcggtgaaaaatccgctgtgctgcaaatc"));
        assertThat(read.getRseq(), equalTo(""));
        assertThat(rStream.hasNext(), equalTo(true));
        read = rStream.next();
        assertThat(read.getLabel(), equalTo("ERR1912949.2.2"));
        assertThat(read.getLseq(), equalTo("aaccggacttcaaaattgccgtacacgccgtacactctgtacggcatttttcaacttcatccgcaaaatacggttttgcttgtgcctttggtg"));
        assertThat(read.getRseq(), equalTo(""));
        assertThat(rStream.hasNext(), equalTo(true));
        rStream = group.sampleIter("SRR1212");
        read = rStream.next();
        assertThat(read.getLabel(), equalTo("SRR11321056.1.1"));
        assertThat(read.getLseq(), equalTo("cctacgggcggcagcagtggggaatattgctcaatggaggcaactctgatgcagcgacgccgcgtgaatgatgaagtttttctgttttttaacttctttcttcctggtagtaaatttctttacctttctctttttccccttctttcttccttccagctgccgcggttaaacttatgttgcaagcgttctccggaattactgggttttatgcgagcttattcggtttcgccagtctgttgtgaattccctgggctcccccccggtccttcattgtttactgttcatcttgtgtgctggcgtg"));
        assertThat(read.getRseq(), equalTo(""));
        assertThat(read.getLQual(), equalTo("8@C<A?,@=>9:)88-;<--@:7-;,<C,@-;--<C,,,6,-,:6:@,,9,9,,6++7++8+7+@+,+,,,9,,,,<,5<A4,,C,<,:+,,99A5@,<,,,,,,,4,,,,,,,8,,5,,<,55?,,85,,,,,,3,:,,,B3,,,7,3,8,88,,3,5,:8+**5**,,,,7,,,,3,3,,,1*6<*67<****4?,42,,*5*4*,+2+***1*5+++1++*3*+*/***32:++2+2*++**000++**..1**/1)2)*)))2*0*3<)*+*+20*220+2/9*)))0)06*(()))"));
        // Try as a FASTA group.
        group = FastqSampleGroup.Type.FASTA.create(testDir);
        sampleSet = group.getSampleIDs();
        assertThat(sampleSet.size(), equalTo(3));
        assertThat(sampleSet, containsInAnyOrder("ERR1136887", "ERS006602", "verify"));
        // Check the sizes.
        desc = group.getDescriptor("ERR1136887");
        assertThat(desc.estimatedSize(), equalTo(947L));
        desc = group.getDescriptor("ERS006602");
        assertThat(desc.estimatedSize(), equalTo(317692L));
        desc = group.getDescriptor("verify");
        assertThat(desc.estimatedSize(), equalTo(416L));
        // Read the contigs.
        rStream = group.sampleIter("ERS006602");
        assertThat(rStream.hasNext(), equalTo(true));
        read = rStream.next();
        assertThat(read.getLabel(), equalTo("NODE_1_length_177931_cov_13.2654"));
        assertThat(read.getCoverage(), closeTo(13.2654, 0.0001));
        assertThat(rStream.hasNext(), equalTo(true));
        read = rStream.next();
        assertThat(read.getLabel(), equalTo("NODE_2_length_109977_cov_11.0852"));
        assertThat(read.getCoverage(), closeTo(11.0852, 0.0001));
        assertThat(read.getSequence().getSequence(), equalTo("aacttcattggtcggtgcattcctgcttcaaactcgaagagagcacaggtacgtcaatcatcaccgacccgtaccacccgtacgtcggtttctcgatgcccgaagtttcttgcgacgccgtgacgctcagtc"));
        assertThat(rStream.hasNext(), equalTo(true));
        read = rStream.next();
        assertThat(read.getLabel(), equalTo("NODE_3_length_70673_cov_11.7944"));
        assertThat(read.getCoverage(), closeTo(11.7944, 0.0001));
        assertThat(rStream.hasNext(), equalTo(true));
        read = rStream.next();
        assertThat(read.getLabel(), equalTo("NODE_4_length_68806_cov_10.5822"));
        assertThat(read.getCoverage(), closeTo(10.5822, 0.0001));
        assertThat(rStream.hasNext(), equalTo(false));
        rStream = group.sampleIter("verify");
        assertThat(rStream.hasNext(), equalTo(true));
        read = rStream.next();
        assertThat(read.getLabel(), equalTo("fig|1650663.6.peg.717|EDD77_10475|"));
        assertThat(read.getSequence().getSequence(), equalTo("atggaagcaaagatcaatgctctgaaagagcagatggagatttcgcttggcgctgtgcagtaa"));
        assertThat(rStream.hasNext(), equalTo(true));
        read = rStream.next();
        assertThat(read.getLabel(), equalTo("fig|1879010.6635.peg.1860|"));
        assertThat(read.getCoverage(), closeTo(50.0, 0.0001));
        assertThat(read.getSequence().getSequence(), equalTo("atggaagagaagatcaaggcactgaaagagcagatggaagccgctctcggcagtgtagag"));
        assertThat(rStream.hasNext(), equalTo(false));
    }

}
