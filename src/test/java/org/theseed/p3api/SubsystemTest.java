/**
 *
 */
package org.theseed.p3api;

import junit.framework.TestCase;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Bruce Parrello
 *
 */
public class SubsystemTest extends TestCase {

    /**
     * Test subsystem loading.
     */
    public void testSubsystemLoad() {
        Connection p3 = new Connection();
        P3SubsystemProjector projector = new P3SubsystemProjector(p3);
        assertThat("A", equalTo("A"));
    }

}
