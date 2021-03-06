package com.github.freeacs.dbi;

import com.github.freeacs.common.db.ConnectionProvider;
import com.github.freeacs.common.db.NoAvailableConnectionException;
import com.github.freeacs.dbi.DynamicStatement.NullInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

public class Groups {
	private static Logger logger = LoggerFactory.getLogger(Groups.class);
	private Map<String, Group> nameMap;
	private Map<Integer, Group> idMap;
	private Unittype unittype;

	public Groups(Map<Integer, Group> idMap, Map<String, Group> nameMap, Unittype unittype) {
		this.idMap = idMap;
		this.nameMap = nameMap;
		this.unittype = unittype;
	}

	public Group getById(Integer id) {
		return idMap.get(id);
	}

	public Group getByName(String name) {
		return nameMap.get(name);
	}

	/*
	 * Returns all groups
	 */
	public Group[] getGroups() {
		Group[] groups = new Group[nameMap.size()];
		nameMap.values().toArray(groups);
		return groups;
	}

	@Override
	public String toString() {
		return "Contains " + nameMap.size() + " groups";
	}

	protected static void checkPermission(Group group, XAPS xaps) {
		if (group.getTopParent().getProfile() == null) {
			if (!xaps.getUser().isUnittypeAdmin(group.getUnittype().getId()))
				throw new IllegalArgumentException("Not allowed action for this user");
		} else {
			if (!xaps.getUser().isProfileAdmin(group.getUnittype().getId(), group.getTopParent().getProfile().getId()))
				throw new IllegalArgumentException("Not allowed action for this user");
		}
	}

	public void addOrChangeGroup(Group group, XAPS xaps) throws SQLException, NoAvailableConnectionException {
		checkPermission(group, xaps);
		addOrChangeGroupImpl(group, unittype, xaps);
		group.setUnittype(unittype);
		nameMap.put(group.getName(), group);
		idMap.put(group.getId(), group);
		if (group.getOldName() != null) {
			nameMap.remove(group.getOldName());
			group.setOldName(null);
		}

	}

	public List<Group> getTopLevelGroups() {
		Group[] allGroups = getGroups();
		List<Group> topLevelGroups = new ArrayList<Group>();
		for (Group g : allGroups) {
			if (g.getParent() == null)
				topLevelGroups.add(g);
		}
		return topLevelGroups;
	}

	/* only used to refresh the cache, used from DBI */
	protected static void refreshGroupParameter(Group group, XAPS xaps, Connection c) throws SQLException, NoAvailableConnectionException {
		Statement s = null;
		ResultSet rs = null;
		String sql = null;
		try {
			sql = "SELECT * FROM group_param WHERE group_id = " + group.getId();
			s = c.createStatement();
			s.setQueryTimeout(60);
			rs = s.executeQuery(sql);
			Set<Integer> groupParamIdSet = new HashSet<Integer>();
			while (rs.next()) {
				Integer unittypeParamId = rs.getInt("unit_type_param_id");
				logger.info("refreshGroupParameter: Group: " + group + ", unittypeParamId: " + unittypeParamId);
				if (group != null)
					logger.info("refreshGroupParameter: Group.getUnittype(): " + group.getUnittype());
				if (group.getUnittype() != null)
					logger.info("refreshGroupParameter: Group.getUnittype().getUnittypeParameters(): " + group.getUnittype().getUnittypeParameters());
				if (group.getUnittype().getUnittypeParameters() != null)
					logger.info("refreshGroupParameter: Group.getUnittype().getUnittypeParameters().getById(utpId): " + group.getUnittype().getUnittypeParameters().getById(unittypeParamId));
				UnittypeParameter utp = group.getUnittype().getUnittypeParameters().getById(unittypeParamId);
				String value = rs.getString("value");
				Parameter.ParameterDataType pdt = Parameter.ParameterDataType.TEXT;
				Parameter.Operator op = Parameter.Operator.EQ;
				Integer groupParamId = null;
				//				if (XAPSVersionCheck.groupParamTypeSupported) {
				groupParamId = rs.getInt("id");
				op = Parameter.Operator.getOperator(rs.getString("operator"));
				pdt = Parameter.ParameterDataType.getDataType(rs.getString("data_type"));
				//				} else {
				//					String isEqualStr = rs.getString("is_equal");
				//					if (isEqualStr != null && isEqualStr.equals("0"))
				//						op = Operator.NE;
				//					groupParamId = utp.getId();
				//				}
				groupParamIdSet.add(groupParamId);
				Parameter parameter = new Parameter(utp, value, op, pdt);
				GroupParameter groupParameter = new GroupParameter(parameter, group);
				groupParameter.setId(groupParamId);
				GroupParameters groupParams = group.getGroupParameters();
				groupParams.addOrChangeGroupParameter(groupParameter);
			}
			// Find out if any group parameter has been deleted
			GroupParameters groupParams = group.getGroupParameters();
			for (GroupParameter gp : group.getGroupParameters().getGroupParameters()) {
				if (!groupParamIdSet.contains(gp.getId()))
					groupParams.deleteGroupParameter(gp);
			}
		} finally {
			if (rs != null)
				rs.close();
			if (s != null)
				s.close();
		}

	}

	/* only used to refresh the cache, used from DBI */
	protected static void refreshGroup(Integer groupId, XAPS xaps) throws SQLException, NoAvailableConnectionException {
		ResultSet rs = null;
		PreparedStatement ps = null;
		//		if (!XAPSVersionCheck.groupSupported)
		//			return;
		Connection c = ConnectionProvider.getConnection(xaps.connectionProperties, true);
		SQLException sqlex = null;
		try {
			DynamicStatement ds = new DynamicStatement();
			ds.addSqlAndArguments("SELECT unit_type_id, group_name, description, parent_group_id, profile_id, count FROM group_ WHERE group_id = ?", groupId);
			ps = ds.makePreparedStatement(c);
			rs = ps.executeQuery();
			if (rs.next()) {
				//				boolean makeNewGroup = false;
				Unittype unittype = xaps.getUnittype(rs.getInt(1));
				if (unittype == null)
					return; // The unittype is not accessible for this user
				Group group = unittype.getGroups().getById(groupId);
				if (group == null) {
					return; // The group is not accessible for this user
					//					group = new Group(groupId);
					//					makeNewGroup = true;
				}
				group.setName(rs.getString("group_name"));
				group.setDescription(rs.getString("description"));
				Integer parentGroupId = rs.getInt("parent_group_id");
				if (parentGroupId != null) {
					group.setParent(unittype.getGroups().getById(parentGroupId));
				} else {
					group.setParent(null);
				}
				group.setProfile(unittype.getProfiles().getById(rs.getInt("profile_id")));
				group.setCount(rs.getInt("count"));
				group.setUnittype(unittype);
				//				if (makeNewGroup) {
				//					unittype.getGroups().getIdMap().put(groupId, group);
				//					unittype.getGroups().getNameMap().put(group.getName(), group);
				//				}
				refreshGroupParameter(group, xaps, c);
				logger.debug("Refreshed group " + group);
			}
		} catch (SQLException sqle) {
			sqlex = sqle;
			throw sqle;
		} finally {
			if (rs != null)
				rs.close();
			if (ps != null)
				ps.close();
			if (c != null)
				ConnectionProvider.returnConnection(c, sqlex);
		}
	}

	private void deleteGroupImpl(Unittype unittype, Group group, XAPS xaps) throws SQLException, NoAvailableConnectionException {
		PreparedStatement s = null;
		String sql = null;
		//		if (!XAPSVersionCheck.groupSupported)
		//			return;
		Connection c = ConnectionProvider.getConnection(xaps.connectionProperties, true);
		SQLException sqlex = null;
		try {
			sql = "UPDATE group_ SET parent_group_id = ?, profile_id = ? WHERE parent_group_id = ?";
			s = c.prepareStatement(sql);
			if (group.getParent() == null)
				s.setNull(1, Types.INTEGER);
			else
				s.setInt(1, group.getParent().getId());
			if (group.getProfile() == null)
				s.setNull(2, Types.INTEGER);
			else
				s.setInt(2, group.getProfile().getId());
			s.setInt(3, group.getId());

			s.setQueryTimeout(60);
			int rowsAffected = s.executeUpdate();
			logger.info("Updated " + rowsAffected + " childgroups of group " + group + " with either a new parent or no parent");
			sql = "DELETE FROM group_ WHERE group_id = " + group.getId();
			s.setQueryTimeout(60);
			s.executeUpdate(sql);
			logger.info("Deleted group " + group);
			if (xaps.getDbi() != null)
				xaps.getDbi().publishDelete(group, group.getUnittype());
		} catch (SQLException sqle) {
			sqlex = sqle;
			throw sqle;
		} finally {
			if (s != null)
				s.close();
			if (c != null)
				ConnectionProvider.returnConnection(c, sqlex);
		}
	}

	/**
	 * The first time this method is run, the flag is set. The second time this
	 * method is run, the parameter is removed from the name- and id-Map.
	 *
	 * @throws NoAvailableConnectionException 
	 * @throws SQLException 
	 */
	public void deleteGroup(Group group, XAPS xaps) throws SQLException, NoAvailableConnectionException {
		checkPermission(group, xaps);
		for (GroupParameter gp : group.getGroupParameters().getGroupParameters()) {
			group.getGroupParameters().deleteGroupParameter(gp, xaps);
		}
		deleteGroupImpl(unittype, group, xaps);
		removeGroupFromDataModel(group);
	}

	protected void removeGroupFromDataModel(Group group) {
		Group parent = group.getParent();
		for (Group child : group.getChildren()) {
			child.setParentFromDelete(parent);
			if (parent != null)
				parent.addChild(child);
			child.setProfileFromDelete(group.getProfile());
		}
		if (parent != null)
			parent.removeChild(group);
		nameMap.remove(group.getName());
		idMap.remove(group.getId());
	}

	public Unittype getUnittype() {
		return unittype;
	}

	private void addOrChangeGroupImpl(Group group, Unittype unittype, XAPS xaps) throws SQLException, NoAvailableConnectionException {
		PreparedStatement s = null;
		//		String sql = null;
		//		if (!XAPSVersionCheck.groupSupported)
		//			return;
		if (group.getParent() != null && group.getId() == null)
			addOrChangeGroup(group.getParent(), xaps);
		Connection c = ConnectionProvider.getConnection(xaps.connectionProperties, true);
		SQLException sqlex = null;
		try {
			if (group.getId() == null) {
				DynamicStatement ds = new DynamicStatement();
				ds.addSqlAndArguments("INSERT INTO group_ (group_name, unit_type_id", group.getName(), group.getUnittype().getId());
				if (group.getDescription() != null)
					ds.addSqlAndArguments(", description", group.getDescription());
				if (group.getParent() != null)
					ds.addSqlAndArguments(", parent_group_id", group.getParent().getId());
				if (group.getProfile() != null)
					ds.addSqlAndArguments(", profile_id", group.getProfile().getId());
				if (/*XAPSVersionCheck.groupCount &&*/group.getCount() != null)
					ds.addSqlAndArguments(", count", group.getCount());
				//				if (XAPSVersionCheck.groupRollingSupported) {
				//				if (group.getTimeRollingRule() != null)
				//					ds.addSqlAndArguments(", time_rolling_rule", group.getTimeRollingRule());
				//				if (group.getTimeParameter() != null)
				//					ds.addSqlAndArguments(", time_param_id", group.getTimeParameter().getId());
				//				}
				ds.setSql(ds.getSql() + ") VALUES (" + ds.getQuestionMarks() + ")");
				s = ds.makePreparedStatement(c, "group_id");
				s.setQueryTimeout(60);
				s.executeUpdate();
				ResultSet gk = s.getGeneratedKeys();
				if (gk.next())
					group.setId(gk.getInt(1));
				s.close();
				logger.info("Inserted group " + group.getName());
				if (xaps.getDbi() != null)
					xaps.getDbi().publishAdd(group, group.getUnittype());
			} else {
				DynamicStatement ds = new DynamicStatement();
				ds.addSqlAndArguments("UPDATE group_ SET group_name = ?, ", group.getName());
				ds.addSqlAndArguments("description = ?, ", group.getDescription());
				if (group.getParent() == null)
					ds.addSqlAndArguments("parent_group_id  = ?, ", new NullInteger());
				else
					ds.addSqlAndArguments("parent_group_id  = ?, ", group.getParent().getId());
				//				if (XAPSVersionCheck.groupRollingSupported) {
				//				ds.addSqlAndArguments("time_rolling_rule = ?, ", group.getTimeRollingRule());
				//				if (group.getTimeParameter() != null)
				//					ds.addSqlAndArguments("time_param_id = ?, ", group.getTimeParameter().getId());
				//				else
				//					ds.addSqlAndArguments("time_param_id = ?, ", new NullInteger());
				//				}
				if (/*XAPSVersionCheck.groupCount &&*/group.getCount() != null)
					ds.addSqlAndArguments("count = ?, ", group.getCount());
				if (group.getProfile() == null)
					ds.addSqlAndArguments("profile_id = ? ", new NullInteger());
				else
					ds.addSqlAndArguments("profile_id = ? ", group.getProfile().getId());
				ds.addSqlAndArguments("WHERE group_id = ?", group.getId());
				PreparedStatement ps = ds.makePreparedStatement(c);
				ps.setQueryTimeout(60);
				ps.executeUpdate();

				logger.info("Updated group " + group.getName());
				if (xaps.getDbi() != null)
					xaps.getDbi().publishChange(group, group.getUnittype());
			}
		} catch (SQLException sqle) {
			sqlex = sqle;
			throw sqle;
		} finally {
			if (s != null)
				s.close();
			if (c != null)
				ConnectionProvider.returnConnection(c, sqlex);
		}
	}

	protected Map<String, Group> getNameMap() {
		return nameMap;
	}

	protected Map<Integer, Group> getIdMap() {
		return idMap;
	}
}
