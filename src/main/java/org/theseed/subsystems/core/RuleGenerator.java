/**
 *
 */
package org.theseed.subsystems.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This object is used to build rules from a CoreSubsystem. We do this when no existing rules are found.
 *
 * The basic procedure is to create a bitmap for each instance of a variant, indicating the roles present.
 * Unique bitmaps that are not a superset of others are kept. The intersection of all the bitmaps for a
 * variant is defined as a group, and then this group is ANDed with the conjunction of the remaining bits
 * in each map. The rules are ordered from the most bits to the fewest. A default rule is added for variant
 * -1 that matches if any role is present. To facilitate this rule, a definition is added for "any" that
 * matches if any one role is present. Auxiliary roles are not included. Bits for those will never be
 * set.
 *
 * @author Bruce Parrello
 *
 */
public class RuleGenerator {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(RuleGenerator.class);

    // TODO data members for RuleGenerator

    // TODO constructors and methods for RuleGenerator

}
