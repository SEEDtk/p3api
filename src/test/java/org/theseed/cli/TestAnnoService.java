/**
 *
 */
package org.theseed.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.genome.Feature;
import org.theseed.genome.Genome;

/**
 * @author Bruce Parrello
 *
 */
class TestAnnoService {

    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(TestAnnoService.class);

    @Test
    void testAnnotation() throws InterruptedException, IOException {
        File workDir = new File("data", "p3Test");
        LoginTask checkLogin = new LoginTask("loginTest", workDir, "");
        String name = checkLogin.checkLogin();
        if (name == null) {
            log.info("Not logged in.");
        } else {
            log.info("logged in as {}", name);
            File faDir = new File("data", "p3Test");
            File faFile = new File(faDir, "test.contigs.fasta");
            String expDir = "/" + name + "/home/Experiments";
            DirTask checkDir = new DirTask(faFile, expDir);
            var entries = checkDir.list(expDir);
            boolean found = entries.stream().anyMatch(x -> x.getName().contentEquals("UnitTest") && x.getType() == DirEntry.Type.FOLDER);
            if (! found)
                log.error("Missing UnitTest directory in Experiments folder for {}.", name);
            else {
                AnnoService task = new AnnoService(faFile, 1122614, "Oceanicola nanhaiensis test annotation",
                        "Bacteria", 11, faDir, expDir + "/UnitTest");
                String taskId = task.start();
                Set<String> tasks = Set.of(taskId);
                StatusTask status = new StatusTask(1000, workDir, expDir);
                boolean done = false;
                boolean ok = false;
                while (! done && ! ok) {
                    var jobMap = status.getStatus(tasks);
                    String taskStatus = jobMap.get(taskId);
                    if (taskStatus == null || taskStatus.contentEquals(StatusTask.FAILED))
                        done = true;
                    else if (taskStatus.contentEquals(StatusTask.COMPLETED))
                        ok = true;
                    else
                        Thread.sleep(1000);
                }
                assertThat("Task ID = " + taskId, ok);
                File gtoFile = task.getResultFile();
                Genome genome = new Genome(gtoFile);
                assertThat(genome.getContigCount(), equalTo(157));
                assertThat(genome.getTaxonomyId(), equalTo(1122614));
                Feature seed = genome.getByFunction("Phenylalanyl-tRNA synthetase alpha chain (EC 6.1.1.20)");
                assertThat(seed.getProteinTranslation(),
                        equalTo("MDDLDALKSDWLGRIGAASDEAMLEELRVAALGKKGDISLRMRELGRMTPEERQVAGPALNALKDEVNSAIAAKKAALADAALDERLRAEWLDVTLPARHRRVGTIHPVSQVTEEVTAIFADMGFTVAEGPQIETDWYNFDALNIPGHHPARAEMDTFYMHRAEGDDRPPHVLRTHTSPVQIRHMEAHGAPCRVIAPGRVYRADYDQTHTPMFHQVEGLAIDRDISMANLKWTLEEFFSAYFGTKVKTRFRASHFPFTEPSAEVDIQCSWEGGTVKVGEGDDWLEVLGSGMVHPKVLEAAGVDPSQWQGFAFGMGIDRIGMLKYGIPDLRAFFDSDLRWLRHYGFSALEVPTVHAGM"));
                assertThat(seed.getLocation().toSeedString(), equalTo("NODE_87_length_119331_cov_19.1226_63863-1074"));
            }
        }
    }

}
