/**
 *
 */
package org.theseed.cli;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * @author Bruce Parrello
 *
 */
class TestDirTask {

    /** logging facility */
    private static final Logger log = LoggerFactory.getLogger(TestAnnoService.class);

    @Test
    void testFolderCheck() {
        File workDir = new File("data", "p3Test");
        LoginTask checkLogin = new LoginTask("loginTest", workDir, "");
        String name = checkLogin.checkLogin();
        if (name == null) {
            log.info("Not logged in.");
        } else {
            log.info("logged in as {}", name);
            File faDir = new File("data", "p3Test");
            String expDir = "/" + name + "/home/Experiments";
            DirTask checkDir = new DirTask(faDir, expDir);
            assertThat(checkDir.check(expDir), equalTo(true));
            assertThat(checkDir.check(expDir + "/Frog"), equalTo(false));
        }
    }

}
