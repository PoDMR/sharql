package com.gitlab.ctt.arq.analysis.aspect.db;

import jdk.nashorn.internal.ir.annotations.Ignore;
import org.apache.commons.codec.binary.Hex;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Pattern;

public class SqlUtil {
	public static void main(String[] args) {
		System.out.println(SqlUtil.createTable("Queries", QueryRecord.class));
		QueryRecord record = new QueryRecord();
		record.queryStr = "SELECT * WHERE { ?x a ?y }";
		System.out.println(SqlUtil.insertRecord("Queries", record, Collections.emptySet()));
	}

	private static final String ID_KEY = "id";
	private static Set<String> EXCLUDES = new HashSet<>(Arrays.asList(
		ID_KEY,
		"maybeQuery"
	));

	public static String checkExists(String tableName) {

		return String.format("SELECT EXISTS (\n" +
			"   SELECT 1\n" +
			"   FROM   information_schema.tables \n" +
			"   WHERE  table_schema = 'public'\n" +
			"   AND    table_name = '%s'\n" +
			"   );", tableName);
	}

	public static String typemap(String tableName) {

		return String.format("SELECT column_name, data_type\n" +
			"FROM information_schema.columns\n" +
			"WHERE table_name='%s';", tableName);
	}

	public static String createTable(String tableName, Class<?> type) {
		Field[] fields = type.getFields();
		List<String> fieldStrings = new ArrayList<>();
		for (Field field : fields) {

			String name = field.getName();
			if (!EXCLUDES.contains(name) && !field.isAnnotationPresent(Ignore.class)) {
				String typeName = field.getType().getSimpleName();
				typeName = typeName.replaceAll(Integer.class.getSimpleName(), "INT");
				typeName = typeName.replaceAll(String.class.getSimpleName(), "TEXT");
				typeName = typeName.replaceAll(Long.class.getSimpleName(), "BIGINT");
				typeName = typeName.replaceAll(Boolean.class.getSimpleName(), "BOOL");
				typeName = typeName.replaceAll(Double.class.getSimpleName(), "REAL");
				typeName = typeName.replaceAll(Pattern.quote(byte[].class.getSimpleName()), "BYTEA");
				fieldStrings.add(String.format("\"%s\" %s", name, typeName));
			}
		}
		return String.format("CREATE TABLE \"%s\"(\n" +
			"    id INT PRIMARY KEY NOT NULL,\n" +
			"    %s\n" +
			");", tableName, String.join(",\n    ", fieldStrings));
	}










	public static String insertRecord(String tableName, QueryRecord record, Set<String> excludes) {
		return insertObject(tableName, record, excludes);
	}

	@NotNull
	private static Map<String, String> toNameValueMap(Object object, Set<String> excludes) {
		Field[] fields = object.getClass().getFields();
		Map<String, String> name2value = new LinkedHashMap<>();




		for (Field field : fields) {
			String name = field.getName();
			if (!(EXCLUDES.contains(name) || excludes.contains(name)) || ID_KEY.equals(name)) {
				try {
					Object value = field.get(object);
					if (value != null) {
						String valueStr = value.toString();
						if (value.getClass().equals(String.class)) {
							valueStr = "'" + escapeString(valueStr) + "'";
						} else if (value.getClass().equals(byte[].class)) {
							String hashStr = Hex.encodeHexString((byte[]) value);
							valueStr = "decode('" + hashStr + "', 'hex')";
						}
						name2value.put("\"" + name + "\"", valueStr);
					}
				} catch (IllegalAccessException e) {
					throw new RuntimeException("Reflection exception", e);
				}
			}
		}
		return name2value;
	}

	public static String insertObject(String tableName, Object object, Set<String> excludes) {
		Map<String, String> name2value = toNameValueMap(object, excludes);

		return String.format("INSERT INTO \"%s\" (%s)\n" +
				"VALUES (%s); ", tableName,
			String.join(",", name2value.keySet()),
			String.join(",", name2value.values()));
	}

	public static String upsertRecord(QueryRecord record, Set<String> excludes,
			String tableName, String tableName2) {
		Map<String, String> name2value = toNameValueMap(record, excludes);
		String delimiter = ", ";  
		String names = String.join(delimiter, name2value.keySet());
		String values = String.join(delimiter, name2value.values());

		return String.format("WITH input(\n" +
				"  %1$s\n" +
				") AS (VALUES (\n" +
				"  %2$s\n" +
				")), ins AS (\n" +
				"INSERT INTO \"%3$s\" (\n" +
				"  %1$s\n" +
				") (SELECT * FROM input)\n" +
				"ON CONFLICT (\"hash\") DO NOTHING RETURNING id\n" +
				")\n" +
				"INSERT INTO \"%4$s\"\n" +
				"  (\"id\", \"origin\", \"originMajor\", \"originMinor\", \"originLinum\", \"copyOfId\")\n" +
				"SELECT input.id AS \"id\",\n" +
				"input.\"origin\" AS \"origin\",\n" +
				"input.\"originMajor\" AS \"originMajor\",\n" +
				"input.\"originMinor\" AS \"originMinor\",\n" +
				"input.\"originLinum\" AS \"originLinum\",\n" +
				"t1.id AS \"copyOfId\"\n" +
				"FROM input JOIN \"%3$s\" AS t1 USING (hash)\n" +
				"ON CONFLICT (id) DO NOTHING RETURNING id\n" +
				";",
			names, values, tableName, tableName2);
	}

	public static String insertDupe(String tableName, long id, String origin, long dupeOf) {

		return String.format("INSERT INTO \"%s\" (\"id\", \"origin\", \"copyOfId\")\n" +
				"VALUES (%s, '%s', %s); ", tableName,
			id, escapeString(origin), dupeOf);
	}

	private static String escapeString(String sqlStr) {
		return sqlStr.replaceAll("'", "''");
	}
}
