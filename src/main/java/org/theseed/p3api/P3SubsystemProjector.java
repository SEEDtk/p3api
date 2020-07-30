/**
 *
 */
package org.theseed.p3api;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.theseed.genome.Feature;
import org.theseed.genome.Genome;
import org.theseed.p3api.Connection.Table;
import org.theseed.subsystems.SubsystemProjector;
import org.theseed.subsystems.SubsystemSpec;
import org.theseed.subsystems.VariantSpec;

import com.github.cliftonlabs.json_simple.JsonObject;

/**
 * This is a version of the subsystem projector that pulls subsystem data from PATRIC.  We download all
 * the subsystem definitions in a single query, and then for each genome we build the subsystems from the
 * genome's subsystem data in the database.
 *
 * @author Bruce Parrello
 *
 */
public class P3SubsystemProjector extends SubsystemProjector {

    // FIELDS
    /** connection to PATRIC */
    private Connection p3;

    /**
     * Create a new PATRIC subsystem projector.
     *
     * @param p3	connection to PATRIC
     */
    public P3SubsystemProjector(Connection p3) {
        this.p3 = p3;
        // Now we must query the database to get the full subsystem list.  Note we have to have a criterion, so we
        // picked one that is always true.
        List<JsonObject> subsystems = p3.query(Table.SUBSYSTEM, "subsystem_name,superclass,class,subclass,role_name",
                Criterion.NE("subsystem_id", "_"));
        // Loop through the list, filling in the subsystems.
        for (JsonObject record : subsystems) {
            String subName = Connection.getString(record, "subsystem_name");
            log.debug("Creating {}.", subName);
            SubsystemSpec subsystem = new SubsystemSpec(subName);
            subsystem.setClassifications(Connection.getString(record, "superclass"), Connection.getString(record, "class"),
                    Connection.getString(record, "subclass"));
            // Get the roles and insert them in order.
            String[] roles = Connection.getStringList(record, "role_name");
            for (String role : roles)
                subsystem.addRole(role);
            // Store the subsystem itself.
            this.addSubsystem(subsystem);
        }
        log.info("{} subsystems created.", subsystems.size());
    }

    /**
     * Project subsystems onto a genome.
     *
     * @param genome	genome to contain the subsystems
     */
    @Override
    public void project(Genome genome) {
        // Get all the subsystem item records for this genome.
        List<JsonObject> items = p3.getRecords(Table.SUBSYSTEM_ITEM, "genome_id", Collections.singleton(genome.getId()),
                "patric_id,role_name,subsystem_name,active");
        // We need to create a variant specification for each subsystem in this genome and a role map for the genome
        // as a whole.  These are then used to instantiate the subsystems in the genome.
        Map<String, VariantSpec> variantMap = new HashMap<String, VariantSpec>(items.size());
        Map<String, Set<String>> roleMap = this.computeRoleMap(genome);
        for (JsonObject item : items) {
            String fid = Connection.getString(item, "patric_id");
            Feature feat = genome.getFeature(fid);
            if (feat == null) {
                log.warn("Invalid feature ID {} in subsystem data for {}.", fid, genome);
            } else {
                String roleDesc = Connection.getString(item, "role_name");
                String subName = Connection.getString(item, "subsystem_name");
                String varCode = Connection.getString(item, "active");
                // Get the subsystem.
                SubsystemSpec subsystem = this.getSubsystem(subName);
                if (subsystem == null) {
                    log.warn("Invalid subsystem name \"{}\" in data for {}.", subName, genome);
                } else {
                    int idx = subsystem.getRoleIndex(roleDesc);
                    if (idx < 0) {
                        log.warn("Invalid role \"{}\" in subsystem data for {}.", roleDesc, genome);
                    } else {
                        // Now we know all the data in this subsystem item is correct.  Fill in the variant spec.
                        VariantSpec variant = variantMap.computeIfAbsent(subName, k -> new VariantSpec(subsystem, varCode));
                        variant.setCell(idx, this);
                    }
                }
            }
        }
        // Here we have all the variant specifications built.  Instantiate the subsystems.
        log.info("{} subsystems found in {}.", variantMap.size(), genome);
        for (VariantSpec variant : variantMap.values()) {
            variant.instantiate(genome, roleMap);
        }
    }

}
