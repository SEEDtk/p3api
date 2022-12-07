/**
 *
 */
package org.theseed.cli;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.utils.ProcessUtils;

import com.github.cliftonlabs.json_simple.JsonObject;

/**
 * This object represents a task run through the command-line interface.  It exposes methods to run the task
 * and to process the results.  A task is identified by its job name, so two tasks for the same job cannot
 * coexist in a set.
 *
 * @author Bruce Parrello
 */
public abstract class CliTask implements Comparable<CliTask> {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(CliTask.class);
    /** work folder for temporary files */
    private File workDir;
    /** workspace name */
    private String workspace;
    /** ID of this task */
    private String taskId;
    /** name of this job */
    private String jobName;
    /** suffix for CLI commands */
    private static String suffix = null;
    /** priority list of CLI command suffixes */
    private static final String[] SUFFIXES = new String[] { "", ".cmd" };
    /** directory containing CLI commands */
    private static File cliDir = null;
    /** pattern matcher for getting task ID */
    private static final Pattern TASK_ID_PATTERN = Pattern.compile("Started task\\s+(.+)");

    /**
     * Construct an object for running CLI tasks
     *
     * @param jobName		name of the parent job
     * @param workDir		proposed work directory
     * @param workpsace		workspace name
     */
    public CliTask(String jobName, File workDir, String workspace) {
        this.workDir = workDir;
        this.setWorkspace(workspace);
        this.jobName = jobName;
        // First time through, we set up the command path.
        if (cliDir == null) {
            String cliPath = System.getenv("CLI_PATH");
            if (cliPath == null)
                throw new IllegalStateException("CLI_PATH is not properly configured on this machine.  Insure it points to your CLI bin directory.");
            cliDir = new File(cliPath);
            log.info("CLI path is {}.", cliDir);
            // Do a directory scan to determine the suffix to use.
            for (int i = 0; suffix == null && i < SUFFIXES.length; i++) {
                File test = new File(cliDir, "p3-ls" + SUFFIXES[i]);
                if (test.exists())
                    suffix = SUFFIXES[i];
            }
            if (suffix == null)
                throw new IllegalStateException("CLI_PATH does not point to a usable P3 CLI directory.");
        }
    }

    /**
     * Run the specified command (non-pipeline) with the specified parameters.
     *
     * @param command	name of the command to run
     * @param parm		parameters to use, in order
     *
     * @return the standard output as a string list
     *
     * @throws InterruptedException
     * @throws IOException
     */
    public List<String> run(String command, String... parm) {
        // Build the command and the parameters.
        File program = new File(cliDir, command + suffix);
        List<String> parms = Arrays.asList(parm);
        // Run the command
        List<String> retVal = null;
        try {
            retVal = ProcessUtils.runProgram(program, parms);
        } catch (Exception e) {
            throw new RuntimeException("Error executing " + command + ": " + e.toString());
        }
        // Throw an error if it failed.
        if (retVal == null)
            throw new RuntimeException(command + " failed with nonzero exit code.");
        return retVal;
    }

    /**
     * Submit a service request with the specified configuration.
     *
     * @param service	ID of the target service
     * @param json		json object with parameters
     */
    public void submit(String service, JsonObject json) {
        // Insure the parameters contain the job name.
        json.put("jobName", this.jobName);
        // Create the JSON file for the service parameters.
        File jsonFile;
        try {
            jsonFile = File.createTempFile(service, ".parms.json", this.workDir);
            jsonFile.deleteOnExit();
            try (FileWriter writer = new FileWriter(jsonFile)) {
                json.toJson(writer);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // Set the the parameters for the run call.
        String[] parms = new String[] { service, jsonFile.toString(), this.getWorkspace() };
        // Run the command.
        List<String> output = this.run("appserv-start-app", parms);
        if (output.size() < 1)
            throw new RuntimeException("No output from submission of " + service + ".");
        // Extract the task ID.
        Matcher m = TASK_ID_PATTERN.matcher(output.get(0));
        if (! m.matches())
            throw new RuntimeException("Invalid output from submission of " + service + ":  " + output.get(0));
        this.taskId = m.group(1);
    }

    @Override
    public int compareTo(CliTask other) {
        return this.jobName.compareTo(other.jobName);
    }

    /**
     * @return the task ID
     */
    public String getTaskId() {
        return this.taskId;
    }

    /**
     * @return the job name
     */
    public String getJobName() {
        return this.jobName;
    }

    /**
     * @return the working directory for temporary files
     */
    public File getWorkDir() {
        return this.workDir;
    }

    /**
     * @return the workspace
     */
    public String getWorkspace() {
        return workspace;
    }

    /**
     * @param workspace the workspace to set
     */
    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

}
