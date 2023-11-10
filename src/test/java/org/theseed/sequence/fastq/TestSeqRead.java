/**
 *
 */
package org.theseed.sequence.fastq;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.theseed.sequence.DnaKmers;
import org.theseed.sequence.SequenceKmers;

/**
 * @author Bruce Parrello
 *
 */
public class TestSeqRead {

    @Test
    public void testSeqRead() {
        String l1 =  "TGGGGAATATTGCACAATGGGGGAAACCCTGATGCAGCGACGCCGCGTGAGCGATGAAGTATTTCGGTATGTAAAGCTCTATCAGCTGGGAAGAAAATGACGGTACCTGACTACGAAGCCCCGGCTAACTACGTGCCAGCAGCCGCGGTAATACGTAGGGGGCAAGCGTTATCCGGATTTACTGGGTGTAAAGGGAGCGTAGACGGCATGGCAAGCCAGATGTGAAAGCCCGGGGCTCAACCCCGGGACTGCACTTGGAACTGTCAGGCTAGAGTGTCGGAGTGGAAAGCGGAATGACTAGTGGAGCAG";
        String l1q = "9FCCCFCDG<FGFGGGFAFGFGDFEGFGGGCFGCGGG?C:FGEGD@BFECFGGGGDGGGCGGFFGFCCFFFGCEEFEGG9FDA<FGGGCGGF7EFCGFGGFDFEFFFFGGGCG,>CGG:=DEGCECGEGCGGEF?7FFDGFFFGGCGC@EC;FFECCCC::14FFFCFF*CCEFGCGG72?FGGF>FGCFFFGGCFGG5C5C87ECEEEFCFEGF5F*8*<<?0<0<A6:EG55C=8*0+;*/;?5*8:;*6++3+2<<:CF?+0<CFF6CF4C*:9@EDDF)7)//701)4)77)*28?684(,11)0";
        String r1 =  "TGTTTGCTCCCCACGCTTTCGAGCCTCAACGTCAGTCATCGTCCAGAAAGCCGCCTTCGCCACTGGTGTTCCTCCTAATATCTACGCATTTCACCGCTACACTAGGAATTCCGCTTTCCTCTCCGACACTCTAGCCTGACAGTTCCAAATGCAGTCCCGGGGTTGAGCCCCGGGCTTGCACATCTGGCTTGCCATGCCGTCTACGCTCCCTTTACACCCAGTAACTCCGCATAACGCTTGCCCCCTACGTCTTACCGTGTCTGCT";
        String r1q = "GGGGGCGGGGGGGGGGGEGGGGGGGGFCFF:@FEGGAFG9FFFGGGFFECFGGCG@FGFGG@FFEF?EFEGGCDBFCFFFDGGGGGGGGGGGDGF>:C=FFG9EBDEFFCGAF>CEDFGGGFGGGG>EGFCEGGCGDCDFCFDFGGC9>FCBDAD8DDCC+3*3*,,7@@B5CCC*=**,=9+1:=6EFFGFF4F46C)1:=(:.(2(5=AA6.=:BAEA(9-9+:<F?()((*3-,),-,<78@11>?:*:(4=:2,*.,-).)";
        String lbl1 = "ERR2730212.1";
        SeqRead read = new SeqRead(lbl1, l1, l1q, r1, r1q);
        assertThat(read.getLabel(), equalTo("ERR2730212.1"));
        assertThat(read.getLseq(), equalTo(StringUtils.lowerCase(l1)));
        assertThat(read.getRseq(), equalTo(StringUtils.lowerCase(r1)));
        double q = read.getQual();
        assertThat(q, greaterThan(0.9));
        SequenceKmers readKmers = read.new Kmers();
        DnaKmers lKmers = new DnaKmers(l1);
        DnaKmers rKmers = new DnaKmers(r1);
        // We need to verify all the kmers in the read are in one of the sequence parts.
        for (String kmer : readKmers)
            assertThat(kmer, lKmers.contains(kmer) || rKmers.contains(kmer), equalTo(true));
        // Now verify the reverse.
        for (String kmer : lKmers)
            assertThat(kmer, readKmers.contains(kmer), equalTo(true));
        for (String kmer : rKmers)
            assertThat(kmer, readKmers.contains(kmer), equalTo(true));
    }

    @Test
    public void testSingleRead() {
        String l1 =  "TGGGGAATATTGCACAATGGGGGAAACCCTGATGCAGCGACGCCGCGTGAGCGATGAAGTATTTCGGTATGTAAAGCTCTATCAGCTGGGAAGAAAATGACGGTACCTGACTACGAAGCCCCGGCTAACTACGTGCCAGCAGCCGCGGTAATACGTAGGGGGCAAGCGTTATCCGGATTTACTGGGTGTAAAGGGAGCGTAGACGGCATGGCAAGCCAGATGTGAAAGCCCGGGGCTCAACCCCGGGACTGCACTTGGAACTGTCAGGCTAGAGTGTCGGAGTGGAAAGCGGAATGACTAGTGGAGCAG";
        String l1q = "9FCCCFCDG<FGFGGGFAFGFGDFEGFGGGCFGCGGG?C:FGEGD@BFECFGGGGDGGGCGGFFGFCCFFFGCEEFEGG9FDA<FGGGCGGF7EFCGFGGFDFEFFFFGGGCG,>CGG:=DEGCECGEGCGGEF?7FFDGFFFGGCGC@EC;FFECCCC::14FFFCFF*CCEFGCGG72?FGGF>FGCFFFGGCFGG5C5C87ECEEEFCFEGF5F*8*<<?0<0<A6:EG55C=8*0+;*/;?5*8:;*6++3+2<<:CF?+0<CFF6CF4C*:9@EDDF)7)//701)4)77)*28?684(,11)0";
        String lbl1 = "ERR2730212.1";
        SeqRead read = new SeqRead(lbl1, l1, l1q);
        assertThat(read.getLseq(), equalTo(StringUtils.lowerCase(l1)));
        assertThat(read.getRseq(), equalTo(""));
        assertThat(read.getLabel(), equalTo("ERR2730212.1"));
        double q = read.getQual();
        assertThat(q, greaterThan(0.9));
        SequenceKmers readKmers = read.new Kmers();
        DnaKmers lKmers = new DnaKmers(l1);
        assertThat(readKmers.distance(lKmers), equalTo(0.0));
        SeqRead.Part part = read.getSequence();
        assertThat(part.getSequence(), equalTo(StringUtils.lowerCase(l1)));
        assertThat(part.getQual(), equalTo(l1q));
        l1			= "ACGTAACCGGTTAAACCCGGGTTTAAAACCCCGGGGTTTT";
        l1q			= "ABCDEF0123456789ABCDEF0123456789ABCDEF01";
        String r1 	= "TGCATGCATGCATGCAAAATCCCC";
        String r1q	= "!@#$%^&*()0123456789ABCD";
        read = new SeqRead(lbl1, l1, l1q, r1, r1q);
        part = read.getSequence();
        String seq = part.getSequence();
        String qua = part.getQual();
        assertThat(seq, equalTo("acgtaaccggttaaacccgggtttaaaaccccggggtttttgcatgcatgcatgca"));
        assertThat(qua, equalTo("ABCDEF0123456789ABCDEF0123456789ABCDEF01543210)(*&^%$#@!"));
        r1 	= "ACGTACGTACGTACG";
        r1q = "012345679ABCDEF";
        read = new SeqRead(lbl1, l1, l1q, r1, r1q);
        part = read.getSequence();
        seq = part.getSequence();
        qua = part.getQual();
        assertThat(seq, equalTo("acgtaaccggttaaacccgggtttaaaaccccggggttttxcgtacgtacgtacgt"));
        assertThat(qua, equalTo("ABCDEF0123456789ABCDEF0123456789ABCDEF01!FEDCBA976543210"));

    }

    @Test
    public void testPartRead() throws IOException {
        File testFile = new File("data", "test.fastq");
        try (FileReader fr = new FileReader(testFile); BufferedReader reader = new BufferedReader(fr)) {
            SeqRead.Part part = SeqRead.read(reader);
            assertThat(part.getLabel(), equalTo("ERR2730212.1"));
            assertThat(part.isReverse(), equalTo(true));
            assertThat(part.getSequence(), equalTo("tgtttgctccccacgctttcgagcctcaacgtcagtcatcgtccagaaagccgccttcgccactggtgttcctccta"));
            assertThat(part.getQual(), equalTo("GGGGGCGGGGGGGGGGGEGGGGGGGGFCFF:@FEGGAFG9FFFGGGFFECFGGCG@FGFGG@FFEF?EFEGGCDBFC"));
            try {
                part = SeqRead.read(reader);
                fail("Missing exception.");
            } catch (IOException e) { }
            part = SeqRead.read(reader);
            assertThat(part.getLabel(), equalTo("ERR2730212.3"));
            assertThat(part.isReverse(), equalTo(false));
            assertThat(part.getSequence(), equalTo("tgtttgctccccacgctttcgagcctcaacgtcagttgccgtccagtaagccgcc"));
            assertThat(part.getQual(), equalTo("GGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGFGGGGGGGGGGGGGGGG"));
            part = SeqRead.read(reader);
            assertThat(part.getLabel(), equalTo("ERR2730212.4"));
            assertThat(part.isReverse(), equalTo(true));
            assertThat(part.getSequence(), equalTo("tgtttgctacccacactttcgagcctcagcgtcagttggtgcccagtaggccgcc"));
            assertThat(part.getQual(), equalTo("F9F96,CCEFEFGG,CF,,CFFC@FE@CFF7@68,<C,CF8F,CEFFF,,CF+:4"));
            part = SeqRead.read(reader);
            assertThat(part.getLabel(), equalTo("ERR2730212.5"));
            assertThat(part.isReverse(), equalTo(false));
            assertThat(part.getSequence(), equalTo("tgtttgctacccacactttcgagcctcagcgtcagttggtgcccagtaggccgccttcgccact"));
            assertThat(part.getQual(), equalTo("GGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGEGGG"));
            part = SeqRead.read(reader);
            assertThat(part.getLabel(), equalTo("ERR2730212.6"));
            assertThat(part.isReverse(), equalTo(false));
            assertThat(part.getSequence(), equalTo("tgtttgctccccacgctttcgagcctcaacgtcagtcatcgtccagaaagccgccttcgccactgg"));
            assertThat(part.getQual(), equalTo("GGGGGGGGGGGGGGEGGGGGGGGGGGGGGGGGGGGGGGFGGGGGGGCFGGGGGGGGGGGGGGGGGG"));
            part = SeqRead.read(reader);
            assertThat(part, nullValue());
        };
    }

}
