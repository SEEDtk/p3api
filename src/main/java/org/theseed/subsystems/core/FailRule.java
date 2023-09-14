/**
 *
 */
package org.theseed.subsystems.core;

import java.util.Set;

import org.theseed.utils.ParseFailureException;

/**
 * This is a null rule that always fails.  It is used for bad identifiers.
 *
 * @author Bruce Parrello
 *
 */
public class FailRule extends SubsystemRule {

    @Override
    protected void addParm(SubsystemRule subRule, RuleCompiler compiler) throws ParseFailureException {
        throw new ParseFailureException("No parameters allowed for bad-identifier rules.");
    }

    @Override
    public boolean check(Set<String> roleSet) {
        return false;
    }

    @Override
    public int hashCode() {
        return 101;
    }

    @Override
    public boolean equals(Object other) {
        SubsystemRule operand = this.normalize(other);
        boolean retVal = (operand != null && operand instanceof FailRule);
        return retVal;
    }

    @Override
    public String toString() {
        return "FAIL";
    }

    @Override
    protected boolean isCompound() {
        return false;
    }

}
