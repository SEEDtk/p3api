/**
 *
 */
package org.theseed.p3api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

/**
 * @author Bruce Parrello
 *
 */
class TaxTest {

    @Test
    void test() {
        P3Connection p3 = new P3Connection();
        P3TaxData taxData = new P3TaxData(p3);
        assertThat(taxData.checkSpecies("1423"), equalTo(11));
        assertThat(taxData.checkSpecies("13373"), equalTo(11));
        assertThat(taxData.checkSpecies("562"), equalTo(11));
        assertThat(taxData.checkSpecies("2095"), equalTo(4));
        assertThat(taxData.checkSpecies("1385"), equalTo(0));
        assertThat(taxData.checkSpecies("32008"), equalTo(0));
        assertThat(taxData.checkSpecies("543"), equalTo(0));
        assertThat(taxData.isFamily("186817"), equalTo(true));
        assertThat(taxData.isFamily("119060"), equalTo(true));
        assertThat(taxData.isFamily("543"), equalTo(true));
        assertThat(taxData.isFamily("272560"), equalTo(false));
        assertThat(taxData.isFamily("1386"), equalTo(false));
        assertThat(taxData.isFamily("2095"), equalTo(false));
        assertThat(taxData.isGenus("1386"), equalTo(true));
        assertThat(taxData.isGenus("32008"), equalTo(true));
        assertThat(taxData.isGenus("561"), equalTo(true));
        assertThat(taxData.isGenus("83333"), equalTo(false));
        assertThat(taxData.isGenus("186817"), equalTo(false));
        assertThat(taxData.isGenus("13373"), equalTo(false));
    }

}
