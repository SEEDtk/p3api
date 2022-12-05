/**
 *
 */
package org.theseed.cli;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This task is used to determine if the user is logged in.
 *
 * @author Bruce Parrello
 *
 */
public class LoginTask extends CliTask {

    // FIELDS
    /** pattern match for extracting login name */
    private static final Pattern LOGIN_PATTERN = Pattern.compile("You are logged in as (.+)\\.");

    /**
     * @param jobName		job name to use
     * @param workDir		local working directory name
     * @param workspace		remote workspace name
     */
    public LoginTask(String jobName, File workDir, String workspace) {
        super(jobName, workDir, workspace);
    }

    /**
     * @return the login name if the user is logged in, else NULL
     */
    public String checkLogin() {
        var results = this.run("p3-login", "--status");
        String retVal = null;
        if (results.size() == 1) {
            String message = results.get(0);
            Matcher m = LOGIN_PATTERN.matcher(message);
            if (m.matches())
                retVal = m.group(1);
        }
        return retVal;
    }

}
