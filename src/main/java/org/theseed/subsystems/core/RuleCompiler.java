/**
 *
 */
package org.theseed.subsystems.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.subsystems.core.SubsystemListRule.Mode;
import org.theseed.utils.ParseFailureException;

/**
 * @author Bruce Parrello
 *
 */
public class RuleCompiler {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(RuleCompiler.class);
    /** compiler stack */
    private Deque<SubsystemRule> stack;
    /** list of unprocessed tokens */
    private List<String> tokens;
    /** map of identifiers to rules */
    private Map<String, SubsystemRule> nameMap;

    /**
     * Tokenize a rule string.
     *
     * @param line	rule string to tokenize
     *
     * @return an ordered list of tokens
     */
    protected static List<String> tokenize(String line) {
        List<String> retVal = new ArrayList<String>();
        // The current token is built in here.
        StringBuilder buffer = new StringBuilder(30);
        // This tracks internal parens in the token.
        int pLevel = 0;
        // Loop through the string.
        final int n = line.length();
        int i = 0;
        while (i < n) {
            char ch = line.charAt(i);
            switch (ch) {
            case ' ' :
            case ',' :
                endToken(buffer, retVal);
                break;
            case '{' :
            case '}' :
            case ')' :
                if (pLevel == 0) {
                    endToken(buffer, retVal);
                    retVal.add(Character.toString(ch));
                } else {
                    buffer.append(')');
                    pLevel--;
                }
                break;
            case '(' :
                if (buffer.length() > 0) {
                    buffer.append('(');
                    pLevel++;
                } else
                    retVal.add("(");
                break;
            default :
                buffer.append(ch);
            }
            i++;
        }
        endToken(buffer, retVal);
        return retVal;
    }

    /**
     * Store the current token in the buffer into the return list.
     *
     * @param buffer	buffer containing the current token
     * @param retVal	return list
     */
    private static void endToken(StringBuilder buffer, List<String> retVal) {
        // Only do this if the buffer is nonempty.
        if (buffer.length() > 0) {
            retVal.add(buffer.toString());
            buffer.setLength(0);
        }
    }

    /**
     * Compile a string into a subsystem rule.
     *
     * @param line			string to compile
     * @param nameSpace		map of names to subsystem rules
     *
     * @return the compiled subsystem rule
     *
     * @throws ParseFailureException
     */
    public static SubsystemRule parseRule(String line, Map<String, SubsystemRule> nameSpace) throws ParseFailureException {
        RuleCompiler compiler = new RuleCompiler(line, nameSpace);
        return compiler.compiledRule();
    }

    /**
     * Construct a new rule compiler.
     *
     * @param line	input line to compile
     * @param nameSpace
     */
    public RuleCompiler(String line, Map<String, SubsystemRule> nameSpace) {
        this.stack = new ArrayDeque<SubsystemRule>();
        this.tokens = tokenize(line);
        this.nameMap = nameSpace;
        this.stack.push(new SubsystemBasicRule());
    }

    /**
     * @return the compiled rule for this line
     *
     * @throws ParseFailureException
     */
    private SubsystemRule compiledRule() throws ParseFailureException {
        // Process tokens until we run out.
        while (! this.tokens.isEmpty()) {
            String token = this.tokens.remove(0);
            if (StringUtils.isNumeric(token)) {
                // This is a number, which heralds the start of a list rule.
                this.startListRule(token);
            } else if (token.contentEquals("{")) {
                // Valid open braces are automatically eaten by startListRule.
                throw new ParseFailureException("Unexpected open brace found.");
            } else if (token.contentEquals("}")) {
                // Here we are ending a list rule.
                if (this.stack.peek() instanceof SubsystemListRule) {
                    SubsystemListRule rule = (SubsystemListRule) this.stack.peek();
                    if (rule.type() != SubsystemListRule.Mode.NUM)
                        throw new ParseFailureException("Unexpected closing brace found.");
                    this.closeListRule();
                } else
                    throw new ParseFailureException("Closing brace found outside of list-rule context.");
            } else if (token.contentEquals("(")) {
                // An open parenthesis starts a sub-rule.
                this.startSubRule();
            } else if (token.contentEquals(")")) {
                // A close parenthesis terminates the current rule.
                this.closeSubRule();
            } else if (token.contentEquals("and")) {
                // Here we are building an and-rule.
                this.processOperator(SubsystemListRule.Mode.AND);
            } else if (token.contentEquals("or")) {
                this.processOperator(SubsystemListRule.Mode.OR);
            } else if (token.contentEquals("not")) {
                this.startNegativeRule();
            } else {
                // Here we have an identifier token.
                this.processIdentifier(token);
            }
        }
        SubsystemRule retVal = this.stack.pop();
        return retVal;
    }

    /**
     * This method is called when a NOT operator is found.  It pushes a negative rule onto the stack.
     */
    private void startNegativeRule() {
        SubsystemRule rule = new SubsystemNegativeRule();
        this.stack.push(rule);
    }

    /**
     * Process a binary operator.  The binary operators are AND and OR.  If a list rule of the specified mode is
     * active, then we continue on.  If another rule is on the stack, we use it to start a list rule in the
     * mode indicated.
     *
     * @param mode	mode of this operator (AND or OR).
     */
    private void processOperator(Mode mode) {
        // Get the top rule on the stack.
        SubsystemRule top = this.stack.peek();
        if (top instanceof SubsystemListRule && ((SubsystemListRule) top).type() == mode) {
            // Here we are continuing a list rule of the same type.  The next identifier is added to it.
        } else {
            // Here we are changing modes.
            top = this.stack.pop();
            SubsystemListRule newRule = new SubsystemListRule(mode, top);
            this.stack.push(newRule);
        }
    }

    /**
     * Here we have an identifier token.  Find it in the name map and add it to the current rule.
     *
     * @param token		identifier token string
     *
     * @throws ParseFailureException
     */
    private void processIdentifier(String token) throws ParseFailureException {
        SubsystemRule subRule = this.nameMap.get(token);
        if (subRule == null)
            throw new ParseFailureException("No rule found for identifier\"" + token + "\".");
        // Here the token represents a real rule.
        this.stack.peek().addParm(subRule, this);
    }

    /**
     * Here we have found a close parenthesis.  The current rule is closed and added to the rule above it.
     *
     * @throws ParseFailureException
     */
    private void closeSubRule() throws ParseFailureException {
        this.unroll();
    }

    /**
     * Here we have a new sub-rule starting.  Push an empty basic rule onto the stack.
     */
    private void startSubRule() {
        this.stack.push(new SubsystemBasicRule());
    }

    /**
     * Here the top rule on the stack has completed, so we pop it and add it to its parent.
     *
     * @throws ParseFailureException
     */
    protected void unroll() throws ParseFailureException {
        if (this.stack.size() < 2)
            throw new ParseFailureException("Excess right parenthesis found in rule.");
        SubsystemRule subRule = this.stack.pop();
        this.stack.peek().addParm(subRule, this);
    }

    /**
     * Here we have a new list rule that starts with a number.
     *
     * @param token		number token that starts the list rule
     *
     * @throws ParseFailureException
     */
    private void startListRule(String token) throws ParseFailureException {
        // This can't fail, since it will be all digits.
        int num = Integer.parseInt(token);
        SubsystemListRule rule = new SubsystemListRule(num);
        this.stack.push(rule);
        // Skip the next two tokens, which must be "of" and "{".
        String skipToken = this.tokens.remove(0);
        if (! skipToken.contentEquals("of"))
            throw new ParseFailureException("OF token missing after number.");
        skipToken = this.tokens.remove(0);
        if (! skipToken.contentEquals("{"))
            throw new ParseFailureException("Open brace missing after OF token.");
    }

    /**
     * Here we have come to the end of a list rule.
     *
     * @throws ParseFailureException
     */
    private void closeListRule() throws ParseFailureException {
        this.unroll();
    }

}
