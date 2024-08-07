/**
 *
 */
package org.theseed.genome;

import org.junit.jupiter.api.Test;
import static org.hamcrest.Matchers.*;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.theseed.p3api.P3Connection;
import org.theseed.p3api.P3Genome;
import org.theseed.p3api.P3SubsystemProjector;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Bruce Parrello
 *
 */
public class SubsystemTest {

    /**
     * Test subsystem loading.
     */
    @Test
    public void testSubsystemLoad() {
        P3Connection p3 = new P3Connection();
        P3SubsystemProjector projector = new P3SubsystemProjector(p3);
        assertThat("A", equalTo("A"));
        P3Genome testGenome = P3Genome.load(p3, "1313.7001", P3Genome.Details.PROTEINS);
        projector.project(testGenome);
        Collection<SubsystemRow> subsystems = testGenome.getSubsystems();
        assertThat(subsystems.size(), equalTo(200));
        SubsystemRow subsystem = testGenome.getSubsystem("D-alanylation of teichoic acid");
        assertThat(subsystem, not(nullValue()));
        List<SubsystemRow.Role> roles = subsystem.getRoles();
        assertThat(roles.size(), equalTo(6));
        assertThat(roles.get(5).getName(), equalTo("Acyl carrier protein"));
        Set<Feature> feats = roles.get(5).getFeatures();
        assertThat(feats.stream().map(f -> f.getId()).collect(Collectors.toList()),
                containsInAnyOrder("fig|1313.7001.peg.1202", "fig|1313.7001.peg.1447"));
        assertThat(roles.get(0).getName(), equalTo("Component involved in D-alanylation of teichoic acids"));
        assertThat(roles.get(3).getName(), equalTo("D-alanine--poly(phosphoribitol) ligase ACP subunit (EC 6.1.1.13)"));
        feats = roles.get(3).getFeatures();
        assertThat(feats.stream().map(f -> f.getId()).collect(Collectors.toList()), contains("fig|1313.7001.peg.1120"));
    }

}
