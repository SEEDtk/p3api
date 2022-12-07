/**
 *
 */
package org.theseed.cli;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

/**
 * This is a task object for listing a PATRIC workspace directory.
 *
 * @author Bruce Parrello
 *
 */
public class DirTask extends CliTask {

    /**
     * Construct a directory-listing task.
     *
     * @param workDir		working directory for temporary files
     * @param workspace		name of the relevant PATRIC workspace
     */
    public DirTask(File workDir, String workspace) {
        super("ListDirectory", workDir, workspace);
    }

    /**
     * List the specified directory.
     *
     * @param folder		directory to list
     *
     * @return a list of the files and folders in the directory
     */
    public List<DirEntry> list(String folder) {
        // Run the directory list command.
        List<String> dirStrings = this.run("p3-ls", "-alT", folder);
        // Parse the output.
        List<DirEntry> retVal = dirStrings.stream().map(x -> DirEntry.create(x)).collect(Collectors.toList());
        return retVal;
    }

    /**
     * Verify that a workspace folder exists.
     *
     * @param	folder to verify
     *
     * @return TRUE if it exists, else FALSE
     */
    public boolean check(String folder) {
        String parent = StringUtils.substringBeforeLast(folder, "/");
        String child = StringUtils.substring(folder, parent.length() + 1);
        boolean retVal = false;
        if (! child.isEmpty()) {
            var children = this.list(parent);
            retVal = children.stream().anyMatch(x -> x.getType() == DirEntry.Type.FOLDER && x.getName().contentEquals(child));
        }
        return retVal;
    }



}
