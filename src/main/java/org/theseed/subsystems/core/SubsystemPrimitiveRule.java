/**
 *
 */
package org.theseed.subsystems.core;

import java.util.Set;

import org.theseed.utils.ParseFailureException;

/**
 * This is a primitive rule.  It is satisfied if the identified role is in the role set.
 *
 * @author Bruce Parrello
 *
 */
public class SubsystemPrimitiveRule extends SubsystemRule {

    // FIELDS
    /** role of interest */
    private String roleId;

    /**
     * Construct a primitive rule for the specified role.
     *
     * @param role	ID of the role of interest
     */
    public SubsystemPrimitiveRule(String role) {
        this.roleId = role;
    }
    @Override
    protected void addParm(SubsystemRule subRule, RuleCompiler compiler) throws ParseFailureException {
        throw new ParseFailureException("Cannot add parameters to a primitive rule.");
    }

    @Override
    public boolean check(Set<String> roleSet) {
        return roleSet.contains(this.roleId);
    }

}
