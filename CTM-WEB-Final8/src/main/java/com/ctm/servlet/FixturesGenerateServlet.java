package com.ctm.servlet;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.ctm.daoimpl.ScheduleDaoRoundRobin;
import com.ctm.daoimpl.TeamDaoImpl;
import com.ctm.daoimpl.TournamentDaoImpl;
import com.ctm.model.Match;
import com.ctm.model.TeamStanding;
import com.ctm.model.Tournament;
import com.ctm.util.DaoUtil;

@WebServlet("/fixturesgen")
public class FixturesGenerateServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private final TournamentDaoImpl tDao = new TournamentDaoImpl();
    private final TeamDaoImpl teamDao = new TeamDaoImpl();
    private final ScheduleDaoRoundRobin roundRobin = new ScheduleDaoRoundRobin();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession s = req.getSession(false);
        if (s == null || !"admin".equalsIgnoreCase((String) s.getAttribute("role"))) {
            resp.sendRedirect("index.jsp");
            return;
        }

        String action = req.getParameter("action");
        long tid = parseLong(req.getParameter("tid"));

        // -------------------- LIST ALL TOURNAMENTS --------------------
        if (tid <= 0) {
            List<Tournament> tournaments = tDao.listAllTournaments();
            List<Map<String, Object>> stats = new ArrayList<>();

            for (Tournament t : tournaments) {
                List<TeamStanding> teams = tDao.listTeamsInTournament(t.getId());
                int enrolled = teams.size();
                boolean eligible = enrolled >= 3;
                boolean squadsOk = eligible;

                if (eligible) {
                    for (TeamStanding standing : teams) {
                        int count = teamDao.countPlayersOfTeam(standing.getTeamId());
                        if (count != 11) {
                            squadsOk = false;
                            break;
                        }
                    }
                }

                Map<String, Object> row = new HashMap<>();
                row.put("tournament", t);
                row.put("enrolled", enrolled);
                row.put("squadsOk", squadsOk);
                row.put("fixtures", tDao.fixturesExist(t.getId()));
                stats.add(row);
            }

            req.setAttribute("mode", "list");
            req.setAttribute("tournamentStats", stats);
            req.getRequestDispatcher("admin_fixtures.jsp").forward(req, resp);
            return;
        }

        // -------------------- TOURNAMENT FOUND --------------------
        Tournament tour = tDao.findTournament(tid).orElse(null);
        if (tour == null) {
            resp.sendRedirect("fixturesgen");
            return;
        }

        boolean fixturesExist = tDao.fixturesExist(tid);
        List<TeamStanding> teams = tDao.listTeamsInTournament(tid);
        int teamCount = teams.size();
        boolean allHave11 = teamCount >= 3;

        if (allHave11) {
            for (TeamStanding ts : teams) {
                if (teamDao.countPlayersOfTeam(ts.getTeamId()) != 11) {
                    allHave11 = false;
                    break;
                }
            }
        }

        // -------------------- GENERATE FIXTURES --------------------
        if ("generate".equalsIgnoreCase(action)) {
            if (teamCount < 3 || !allHave11 || fixturesExist) {
                req.setAttribute("err", "Fixture generation failed. Check team count or player requirements.");
                setConfirmAttributes(req, tour, teamCount, allHave11, fixturesExist);
                req.getRequestDispatcher("admin_fixtures.jsp").forward(req, resp);
                return;
            }

            String venue = req.getParameter("venue");
            boolean success = roundRobin.generateFixtures(tid, teams, venue);

            if (!success) {
                req.setAttribute("err", "Fixture generation failed due to SQL or logic issue.");
                setConfirmAttributes(req, tour, teamCount, allHave11, fixturesExist);
                req.getRequestDispatcher("admin_fixtures.jsp").forward(req, resp);
                return;
            }

            // -------------------- FETCH GENERATED FIXTURES --------------------
            List<Match> created = new ArrayList<>();
            String sql = "SELECT m.match_id, " +
                         "t1.name AS team_a_name, t2.name AS team_b_name, " +
                         "m.venue, m.datetime, m.status " +
                         "FROM matches m " +
                         "JOIN teams t1 ON m.team_a_id = t1.team_id " +
                         "JOIN teams t2 ON m.team_b_id = t2.team_id " +
                         "WHERE m.tournament_id=? ORDER BY m.datetime, m.match_id";

            try (PreparedStatement ps = DaoUtil.getMyPreparedStatement(sql)) {
                ps.setLong(1, tid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Match m = new Match();
                        m.setMatchId(rs.getLong("match_id"));
                        m.setTeam1Name(rs.getString("team_a_name"));
                        m.setTeam2Name(rs.getString("team_b_name"));
                        m.setVenue(rs.getString("venue"));
                        Timestamp ts = rs.getTimestamp("datetime");
                        if (ts != null) m.setDateTime(ts.toLocalDateTime());
                        m.setStatus(com.ctm.model.MatchStatus.valueOf(rs.getString("status")));
                        created.add(m);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            req.setAttribute("tournament", tour);
            req.setAttribute("mode", "result");
            req.setAttribute("msg", "Fixtures generated successfully!");
            req.setAttribute("matches", created);
            req.getRequestDispatcher("admin_fixtures.jsp").forward(req, resp);
            return;
        }

        // -------------------- CONFIRM MODE --------------------
        setConfirmAttributes(req, tour, teamCount, allHave11, fixturesExist);
        req.getRequestDispatcher("admin_fixtures.jsp").forward(req, resp);
    }

    private void setConfirmAttributes(HttpServletRequest req, Tournament t, int enrolled, boolean squadsOk, boolean already) {
        req.setAttribute("mode", "confirm");
        req.setAttribute("tournament", t);
        req.setAttribute("enrolledCount", enrolled);
        req.setAttribute("squadsOk", squadsOk);
        req.setAttribute("already", already ? 1 : 0);
    }

    private long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return -1L;
        }
    }
}
