package com.github.freeacs.base.http;

import com.github.freeacs.base.Log;
import com.github.freeacs.base.db.DBAccess;
import com.github.freeacs.common.db.ConnectionProvider;
import com.github.freeacs.dbi.DBI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


public class OKServlet extends HttpServlet {

	private static final long serialVersionUID = -3217484543967391741L;
	private static Map<String, Long> currentConnectionTmsMap = new HashMap<String, Long>();

	public void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		doGet(req, res);
	}

	@SuppressWarnings("rawtypes")
	public void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {

		PrintWriter out = res.getWriter();
		Map<Connection, Long> usedConn = ConnectionProvider.getUsedConnCopy(DBAccess.getXAPSProperties());
		String status = "XAPSOK";
		try {
			Class tr069ProvClass = Class.forName("com.owera.xaps.tr069.Provisioning");
			Field field = tr069ProvClass.getField("VERSION");
			status += " " + (String) field.get(null);
		} catch (Throwable t) {
			try {
				Class sppProvClass = Class.forName("com.owera.xaps.spp.HTTPProvisioning");
				Field field = sppProvClass.getField("VERSION");
				status += " " + (String) field.get(null);
			} catch (Throwable t2) {
			}
		}
		
		try {
			DBI dbi = DBAccess.getDBI();
			if (dbi != null && dbi.getDbiThrowable() != null) {
				status = "ERROR: DBI reported error:\n" + dbi.getDbiThrowable() + "\n";
				for (StackTraceElement ste : dbi.getDbiThrowable().getStackTrace())
					status += ste.toString();
			}
		} catch (Throwable t) {
		}
		
		if (usedConn != null) {
			Collection<Long> usedConnValues = usedConn.values();
			for (Long tms : usedConnValues) {
				long spentTime = (System.currentTimeMillis() - tms) / 1000;
				if (spentTime > 60) {
					status = "ERROR: Connection hangup for more than 60 seconds. Consider restart MySQL and/or Tomcat on Fusion server";
					Log.fatal(OKServlet.class, status);
					break;
				}
			}
		}
		if (!status.contains("ERROR") && ThreadCounter.currentSessionsCount() > 0) {
			Map<String, Long> currentSessions = ThreadCounter.cloneCurrentSessions();
			Iterator<String> cctmIterator = currentConnectionTmsMap.keySet().iterator();
			while (cctmIterator.hasNext()) {
				String uId = cctmIterator.next();
				if (currentSessions.get(uId) == null) { // the process has been completed
					cctmIterator.remove();
				} else {
					Long sessionTime = (System.currentTimeMillis() - currentConnectionTmsMap.get(uId)) / 1000;
					if (sessionTime > 600) { // if a process has not been completed in 600 sec -> problem 
						status = "ERROR: A session may not have been completed for more than 600 seconds. May indicate a hang-situation. Consider restart Tomcat on Fusion server";
						Log.fatal(OKServlet.class, status);
						break;
					}
				}
			}
			for (String uId : currentSessions.keySet()) {
				if (currentConnectionTmsMap.get(uId) == null) // new process has been added
					currentConnectionTmsMap.put(uId, System.currentTimeMillis());
			}
		} else {
			currentConnectionTmsMap = new HashMap<String, Long>();
		}
		out.println(status);
		out.close();
	}
}
