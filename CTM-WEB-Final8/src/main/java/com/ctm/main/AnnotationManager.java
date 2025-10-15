package com.ctm.main;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.ctm.annotations.Column;
import com.ctm.annotations.FK;
import com.ctm.annotations.Id;
import com.ctm.annotations.Table;
import com.ctm.model.Match;
import com.ctm.model.MatchTeamScore;
import com.ctm.model.Player;
import com.ctm.model.Team;
import com.ctm.model.Tournament;
import com.ctm.model.TournamentTeam;
import com.ctm.util.DaoUtil;

public class AnnotationManager {

    public static void ensureSchema() {
        try (Connection con = DaoUtil.getMyConnection(); Statement st = con.createStatement()) {

            createOrUpdateTable(con, st, Team.class);
            createOrUpdateTable(con, st, Player.class);
            createOrUpdateTable(con, st, Tournament.class);
            createOrUpdateTable(con, st, Match.class); // ✅ ensures FIRST_INNINGS_TEAM_ID, DATETIME TIMESTAMP
            createOrUpdateTable(con, st, TournamentTeam.class);
            createOrUpdateTable(con, st, MatchTeamScore.class);

            createJoinTables(st);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** ✅ Create new table or add missing columns */
    private static void createOrUpdateTable(Connection con, Statement st, Class<?> c) throws SQLException {
        Table t = c.getAnnotation(Table.class);
        if (t == null) return;

        String tableName = t.name().toUpperCase();
        boolean exists;

        try (ResultSet rs = con.getMetaData().getTables(null, null, tableName, new String[]{"TABLE"})) {
            exists = rs.next();
        }

        if (!exists) {
            createTable(c, st);
            return;
        }

        System.out.println("🔍 Checking table: " + tableName);

        for (Field f : c.getDeclaredFields()) {
            Column col = f.getAnnotation(Column.class);
            if (col == null) continue;

            String colName = col.name().toUpperCase();
            String desiredType = resolveSqlType(f, col);

            boolean colExists = false;

            try (ResultSet rs = con.getMetaData().getColumns(null, null, tableName, colName)) {
                if (rs.next()) {
                    colExists = true;
                    String typeName = rs.getString("TYPE_NAME");
                    int currentSize = rs.getInt("COLUMN_SIZE");

                    // ⚙️ Enlarge VARCHAR2 columns if smaller than model definition
                    if (typeName.equalsIgnoreCase("VARCHAR2") && f.getType() == String.class) {
                        int desiredLen = (col.length() == 0 ? 100 : col.length());
                        if (currentSize < desiredLen) {
                            String alterSQL = "ALTER TABLE " + tableName +
                                    " MODIFY " + colName + " VARCHAR2(" + desiredLen + ")";
                            try (Statement alt = con.createStatement()) {
                                alt.executeUpdate(alterSQL);
                                System.out.println("⚙️ Increased column " + colName +
                                        " size from " + currentSize + " to " + desiredLen);
                            }
                        }
                    }
                }
            }

            // 🆕 Add missing columns (like FIRST_INNINGS_TEAM_ID, DATETIME TIMESTAMP)
            if (!colExists) {
                String sql = "ALTER TABLE " + tableName + " ADD " + colName + " " + desiredType;
                try (Statement alt = con.createStatement()) {
                    alt.executeUpdate(sql);
                    System.out.println("🆕 Added missing column " + colName + " (" + desiredType + ") in " + tableName);
                }
            }
        }
    }

    /** ✅ Creates table if not exists */
    private static void createTable(Class<?> c, Statement st) throws SQLException {
        Table t = c.getAnnotation(Table.class);
        if (t == null) return;

        StringBuilder ddl = new StringBuilder("CREATE TABLE " + t.name() + " (");
        StringBuilder cols = new StringBuilder();
        StringBuilder constraints = new StringBuilder();
        boolean first = true;

        for (Field f : c.getDeclaredFields()) {
            Column col = f.getAnnotation(Column.class);
            if (col == null) continue;

            if (!first) cols.append(", ");
            first = false;

            String colType = resolveSqlType(f, col);
            StringBuilder colDef = new StringBuilder(col.name() + " " + colType);

            if (!col.nullable()) colDef.append(" NOT NULL");

            if (!col.defaultValue().isEmpty()) {
                String def = col.defaultValue();
                if ((f.getType() == String.class || f.getType().isEnum()) && !def.startsWith("'")) {
                    def = "'" + def + "'";
                }
                colDef.append(" DEFAULT ").append(def);
            }

            if (f.getAnnotation(Id.class) != null) colDef.append(" PRIMARY KEY");

            cols.append(colDef);

            FK fk = f.getAnnotation(FK.class);
            if (fk != null) {
                String constraintName = ("FK_" + t.name() + "_" + col.name())
                        .toUpperCase().replaceAll("[^A-Z0-9_]", "_");
                constraints.append(", CONSTRAINT ").append(constraintName)
                           .append(" FOREIGN KEY (").append(col.name()).append(") REFERENCES ")
                           .append(fk.references());
                if (!fk.onDelete().isEmpty()) constraints.append(" ON DELETE ").append(fk.onDelete());
                if (!fk.onUpdate().isEmpty()) constraints.append(" ON UPDATE ").append(fk.onUpdate());
            }
        }

        ddl.append(cols).append(constraints).append(")");

        try {
            st.executeUpdate(ddl.toString());
            System.out.println("✅ Created new table: " + t.name());
        } catch (SQLException e) {
            if (e.getErrorCode() != 955) throw e;
        }
    }

    /** ✅ Detects correct SQL type for each field */
    private static String resolveSqlType(Field f, Column col) {
        if (!col.type().isEmpty()) return col.type(); // allow @Column(type="TIMESTAMP")

        if (f.getType() == String.class || f.getType().isEnum()) {
            int len = (col.length() == 0 ? 100 : col.length());
            return "VARCHAR2(" + len + ")";
        } else if (f.getType() == int.class || f.getType() == Integer.class ||
                   f.getType() == long.class || f.getType() == Long.class) {
            return "NUMBER";
        } else if (f.getType() == double.class || f.getType() == Double.class) {
            return "NUMBER";
        } else if (f.getType().getSimpleName().equals("LocalDateTime")) {
            return "TIMESTAMP";
        } else {
            return "VARCHAR2(100)";
        }
    }

    /** ✅ Creates required join tables */
    private static void createJoinTables(Statement st) {
        try {
            st.executeUpdate(
                    "CREATE TABLE team_players (" +
                            " team_id NUMBER NOT NULL, " +
                            " jersey_no NUMBER NOT NULL, " +
                            " PRIMARY KEY (team_id, jersey_no))"
            );
            System.out.println("✅ Created table: team_players");
        } catch (SQLException e) {
            if (e.getErrorCode() != 955)
                System.out.println("ℹ️ Table already exists: team_players");
        }

        try {
            st.executeUpdate(
                    "CREATE TABLE tournament_teams (" +
                            " tournament_id NUMBER NOT NULL, " +
                            " team_id NUMBER NOT NULL, " +
                            " name VARCHAR2(80), " +
                            " city VARCHAR2(80), " +
                            " points NUMBER DEFAULT 0, " +
                            " nrr NUMBER DEFAULT 0, " +
                            " played NUMBER DEFAULT 0, " +
                            " PRIMARY KEY (tournament_id, team_id))"
            );
            System.out.println("✅ Created table: tournament_teams");
        } catch (SQLException e) {
            if (e.getErrorCode() != 955)
                System.out.println("ℹ️ Table already exists: tournament_teams");
        }
    }
}
