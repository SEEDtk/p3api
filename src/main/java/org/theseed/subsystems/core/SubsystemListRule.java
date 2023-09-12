/**
 *
 */
package org.theseed.subsystems.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.theseed.utils.ParseFailureException;

/**
 * A subsystem list rule contains a number and a list of sub-rules.  The rule is satisfied if the specified number
 * of sub-rules is satisfied.
 *
 * @author Bruce Parrello
 *
 */
public class SubsystemListRule extends SubsystemRule {

    // FIELDS
    /** mode of this subsystem list rule during compilation */
    private Mode mode;
    /** number of rules that must be satisfied for this rule to be satisfied */
    private int num;
    /** list of rules to satisfy */
    private List<SubsystemRule> rules;

    protected static enum Mode {
        /** this list rule is simulating an AND */
        AND {
            @Override
            protected void updateCount(SubsystemListRule rule) {
                rule.num = rule.rules.size();
            }
        },
        /** this list rule is simulating an OR */
        OR {
            @Override
            protected void updateCount(SubsystemListRule rule) {
            }
        },
        /** this is a standard list rule */
        NUM {
            @Override
            protected void updateCount(SubsystemListRule rule) {
            }
        };

        /**
         * Update the count for the specified rule, if necessary.
         *
         * @param rule	rule possessing this mode
         */
        protected abstract void updateCount(SubsystemListRule rule);
    }

    /**
     * Construct a subsystem list rule for the AND or OR mode.
     *
     * @param operator	operator mode
     * @param top		subsystem rule to use as first operand
     */
    public SubsystemListRule(Mode operator, SubsystemRule top) {
        this.mode = operator;
        this.num = 1;
        this.setup();
        this.rules.add(top);
    }

    /**
     * Initialize a subsystem list rule for a specified number.
     *
     * @param num	number of sub-rules that must be satisfied
     */
    public SubsystemListRule(int num) {
        this.mode = Mode.NUM;
        this.num = num;
        this.setup();
    }

    /**
     * Initialize the rule list.
     */
    private void setup() {
        this.rules = new ArrayList<SubsystemRule>();
    }

    @Override
    protected void addParm(SubsystemRule subRule, RuleCompiler compiler) throws ParseFailureException {
        // Add the new sub-rule.
        this.rules.add(subRule);
        // Update the count.
        this.mode.updateCount(this);
    }

    @Override
    public boolean check(Set<String> roleSet) {
        // Loop through the list of sub-rules, setting the return to TRUE if the desired number are true.
        int found = 0;
        final int n = this.rules.size();
        for (int i = 0; i < n && found < this.num; i++) {
            if (this.rules.get(i).check(roleSet))
                found++;
        }
        return (found >= this.num);
    }

    /**
     * @return the mode of this rule
     */
    public Mode type() {
        return this.mode;
    }

}
