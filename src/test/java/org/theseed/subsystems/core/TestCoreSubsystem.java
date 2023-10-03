/**
 *
 */
package org.theseed.subsystems.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.theseed.genome.Genome;
import org.theseed.proteins.RoleMap;
import org.theseed.utils.ParseFailureException;

/**
 * @author Bruce Parrello
 *
 */
class TestCoreSubsystem {

    protected static final String[] SUB_NAMES = new String[] { "Citrate_Metabolism", "Cluster_with_dapF",
            "Histidine_Biosynthesis", "ZZ_gjo_need_homes_3", "2-oxoisovalerate_to_2-isopropyl-3-oxosuccinate_module" };
    protected static final String[] REAL_NAMES = new String[] { "Citrate Metabolism", "Cluster with dapF",
            "Histidine Biosynthesis", "ZZ gjo need homes 3", "2-oxoisovalerate to 2-isopropyl-3-oxosuccinate module" };

    @Test
    void testCoreSubsystemLoad() throws IOException, ParseFailureException {
        RoleMap roleMap = RoleMap.load(new File("data/ss_test/Subsystems", "core.roles.in.subsystems"));
        CoreSubsystem[] subs = new CoreSubsystem[5];
        for (int subIdx = 0; subIdx < 4; subIdx++) {
            File inDir = new File("data/ss_test/Subsystems", SUB_NAMES[subIdx]);
            subs[subIdx] = new CoreSubsystem(inDir, roleMap);
            assertThat(subs[subIdx].getName(), equalTo(REAL_NAMES[subIdx]));
        }
        // First, we handle the good subsystem, histidine biosynthesis.
        CoreSubsystem sub = subs[2];
        // Test the meta-data-- aux roles, version, classes, goodness.
        assertThat(sub.isAuxRole("HistTrnaSynt"), equalTo(true));
        assertThat(sub.isAuxRole("HistTrnaSyntLike"), equalTo(true));
        assertThat(sub.isAuxRole("PutaHistDehy"), equalTo(true));
        assertThat(sub.isAuxRole("SimiImidGlycPhos"), equalTo(true));
        assertThat(sub.isAuxRole("SimiImidGlycPhos2"), equalTo(true));
        assertThat(sub.isAuxRole("SimiImidGlycPhos3"), equalTo(true));
        assertThat(sub.isAuxRole("DomaSimiAminTerm"), equalTo(false));
        assertThat(sub.getVersion(), equalTo(201));
        assertThat(sub.getSuperClass(), equalTo("Amino Acids and Derivatives"));
        assertThat(sub.getMiddleClass(), equalTo("Histidine Metabolism"));
        assertThat(sub.getSubClass(), equalTo(""));
        assertThat(sub.isGood(), equalTo(true));
        // Get the variant codes from the spreadsheet.
        assertThat(sub.variantOf("1215343.11"), equalTo("likely"));
        assertThat(sub.variantOf("306264.1"), equalTo("active.1.0"));
        assertThat(sub.variantOf("100226.99"), nullValue());
        // Return the feature IDs from the spreadsheet.
        assertThat(sub.fidSetOf("306264.1"), containsInAnyOrder("fig|306264.1.peg.1424", "fig|306264.1.peg.1766", "fig|306264.1.peg.626",
                "fig|306264.1.peg.616", "fig|306264.1.peg.620", "fig|306264.1.peg.618", "fig|306264.1.peg.621",
                "fig|306264.1.peg.624", "fig|306264.1.peg.1724", "fig|306264.1.peg.625"));
        assertThat(sub.fidSetOf("100226.99").size(), equalTo(0));
        assertThat(sub.fidSetOf("218496.1"), containsInAnyOrder("fig|218496.1.rna.767", "fig|218496.1.peg.760"));
        // Test the role helpers.
        assertThat(sub.getRoleId("Histidinol-phosphatase [alternative form] (EC 3.1.3.15)"), equalTo("HistPhosAlteForm"));
        assertThat(sub.isExactRole("HistPhosAlteForm", "Histidinol-phosphatase [alternative form] (EC 3.1.3.15)"), equalTo(true));
        assertThat(sub.getRoleId("Histidinol-phosphatase [alternative form]"), equalTo("HistPhosAlteForm"));
        assertThat(sub.isExactRole("HistPhosAlteForm", "Histidinol-phosphatase [alternative form]"), equalTo(false));
        assertThat(sub.getRoleId("Histidine-phosphatase [alternative form] (EC 3.1.3.15)"), equalTo(null));
        assertThat(sub.isExactRole("HistPhosAlteForm", "Histidine-phosphatase [alternative form] (EC 3.1.3.15)"), equalTo(false));
        assertThat(sub.getRoleId("Phenylalanyl-tRNA synthetase alpha chain"), equalTo(null));
        assertThat(sub.isExactRole("PhenTrnaSyntAlph", "Phenylalanyl-tRNA synthetase alpha chain"), equalTo(false));
        assertThat(sub.getExpectedRole("HistPhosAlteForm"), equalTo("Histidinol-phosphatase [alternative form] (EC 3.1.3.15)"));
        Genome genome = new Genome(new File("data/ss_test_gto", "1215343.11.gto"));
        Set<String> gRoleSet = CoreSubsystem.getRoleSet(genome, roleMap);
        assertThat(gRoleSet, hasItem("PhosSynt4"));
        assertThat(gRoleSet, hasItem("UdpGluc4Epim"));
        assertThat(gRoleSet, not(hasItem("MethCoaHydr")));
        assertThat(sub.applyRules(gRoleSet), equalTo("likely"));
        genome = new Genome(new File("data/ss_test_gto", "306264.1.gto"));
        gRoleSet = CoreSubsystem.getRoleSet(genome, roleMap);
        assertThat(sub.applyRules(gRoleSet), equalTo("active.1.0"));
        // Test the string representation of some rules.
        SubsystemRule rule = sub.getRule("active.1.0");
        String ruleString = rule.toString();
        SubsystemRule recursive = RuleCompiler.parseRule(ruleString, sub.getNameSpace());
        assertThat(recursive, equalTo(rule));
        // Now test the bad ones.
        for (int i = 0; i < 4; i++) {
            if (i != 2) {
                sub = subs[i];
                assertThat(sub.getName(), sub.isGood(), equalTo(false));
            }
        }
    }

    @Test
    void testSubsystemList() throws IOException {
        // Verify that we find four subsystems directories.
        List<File> subs = CoreSubsystem.getSubsystemDirectories(new File("data", "ss_test"));
        assertThat(subs.size(), equalTo(5));
        List<String> subNames = subs.stream().map(x -> x.getName()).collect(Collectors.toList());
        assertThat(subNames, containsInAnyOrder(SUB_NAMES));
        // Verify that all found directories have spreadsheets.
        for (File sub : subs) {
            File ssFile = new File(sub, "spreadsheet");
            assertThat(sub.getName(), ssFile.exists(), equalTo(true));
        }
    }

}
