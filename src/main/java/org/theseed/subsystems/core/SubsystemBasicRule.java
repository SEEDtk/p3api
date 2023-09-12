/**
 *
 */
package org.theseed.subsystems.core;

import java.util.Set;

import org.theseed.utils.ParseFailureException;

/**
 * A basic rule contains a single sub-rule, and is used as a placeholder during compilation.
 *
 * @author Bruce Parrello
 *
 */
public class SubsystemBasicRule extends SubsystemRule {

    // FIELDS
    /** parameter sub-rule */
    private SubsystemRule parm;

    /**
     * Construct an empty basic rule.
     */
    public SubsystemBasicRule() {
        this.parm = null;
    }

    @Override
    protected void addParm(SubsystemRule subRule, RuleCompiler compiler) throws ParseFailureException {
        // Insure we are not trying to put too many parameters in this rule.
        if (this.parm != null)
            throw new ParseFailureException("Operands found without an operator.");
        this.parm = subRule;
    }

    @Override
    public boolean check(Set<String> roleSet) {
        return this.parm.check(roleSet);
    }

}
