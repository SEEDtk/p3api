/**
 *
 */
package org.theseed.p3api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.github.cliftonlabs.json_simple.JsonObject;

/**
 * @author Bruce Parrello
 *
 */
class SolrTest {

	@Test
	void testGenomeDump() {
		RawP3Connection p3 = new RawP3Connection();
		List<JsonObject> listJson = p3.getRecords("genome", Criterion.EQ("genome_id", "1280.25101"));
		assertThat(listJson.size(), equalTo(1));
		JsonObject genomeJson = listJson.get(0);
		assertThat(KeyBuffer.getString(genomeJson, "genome_id"), equalTo("1280.25101"));
		assertThat(KeyBuffer.getString(genomeJson, "genome_name"),
				equalTo("Staphylococcus aureus strain 12593_2_37"));
		assertThat(KeyBuffer.getInt(genomeJson, "trna"), equalTo(57));
		assertThat(KeyBuffer.getDouble(genomeJson, "hypothetical_cds_ratio"), closeTo(0.19923, 0.00001));
		assertThat(KeyBuffer.getString(genomeJson, "host_name"), equalTo("Human, Homo sapiens"));
		listJson = p3.getRecords("genome_feature", Criterion.EQ("genome_id", "1280.25101"),
				Criterion.EQ("feature_type", "tRNA"));
		assertThat(listJson.size(), equalTo(57));

	}

}
