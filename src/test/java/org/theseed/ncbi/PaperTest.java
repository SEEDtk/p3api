/**
 *
 */
package org.theseed.ncbi;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;

import org.apache.http.ParseException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Bruce Parrello
 *
 */
class PaperTest {

    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(PaperTest.class);


    private static String[] LINKS = {
        "https://dx.doi.org/10.1128/JB.181.11.3472-3477.1999",
        "https://dx.doi.org/10.1128/JB.181.11.3392-3401.1999",
        "https://dx.doi.org/10.1128/AAC.43.6.1449",
        "https://dx.doi.org/10.1021/bi990159s",
        "https://dx.doi.org/10.1023/a:1006181908753",
        "https://dx.doi.org/10.1023/a:1006181720100",
        "https://dx.doi.org/10.1073/pnas.96.11.6205",
        "https://dx.doi.org/10.1073/pnas.96.11.6119",
        "https://dx.doi.org/10.1016/s0014-5793(99)00429-9",
        "https://dx.doi.org/10.1046/j.1432-1327.1999.00399.x",
        "https://dx.doi.org/10.1074/jbc.274.22.15953",
        "https://dx.doi.org/10.1074/jbc.274.22.15598",
        "https://dx.doi.org/10.1016/s0378-1119(99)00117-1",
        "https://dx.doi.org/10.1038/8253"
    };

    @Test
    void test() throws ParseException, IOException {
        PaperConnection conn = new PaperConnection();
        for (String url : LINKS) {
            try {
                String html = conn.getPaper(url);
                assertThat(html, not(nullValue()));
                log.info("{} downloaded.", url);
            } catch (Exception e) {
                log.error("Could not download {}: {}", url, e.toString());
            }
        }
    }

}
