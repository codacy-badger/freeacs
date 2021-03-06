package com.github.freeacs.ws.impl;

import com.github.freeacs.dbi.*;
import com.github.freeacs.dbi.Parameter.Operator;
import com.github.freeacs.dbi.Parameter.ParameterDataType;
import com.github.freeacs.dbi.Unit;
import com.github.freeacs.ws.*;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.util.*;
import java.util.Map.Entry;

public class GetUnits {
	private static final Logger logger = LoggerFactory.getLogger(GetUnits.class);

	private XAPS xaps;
	private XAPSWS xapsWS;

	public GetUnitsResponse getUnits(GetUnitsRequest gur) throws RemoteException {
		try {
			
			xapsWS = XAPSWSFactory.getXAPSWS(gur.getLogin());
			xaps = xapsWS.getXAPS();
			XAPSUnit xapsUnit = xapsWS.getXAPSUnit(xaps);

			com.github.freeacs.ws.Unit unitWS = gur.getUnit();

			/* Validate input - only allow permitted unittypes/profiles for this login */
			com.github.freeacs.dbi.Unittype unittypeXAPS = null;
			List<com.github.freeacs.dbi.Profile> profilesXAPS = new ArrayList<com.github.freeacs.dbi.Profile>();
			if (unitWS.getUnittype() != null && unitWS.getUnittype().getName() != null) {
				unittypeXAPS = xapsWS.getUnittypeFromXAPS(unitWS.getUnittype().getName());
				if (unitWS.getProfile() != null && unitWS.getProfile().getName() != null) {
					profilesXAPS.add(xapsWS.getProfileFromXAPS(unittypeXAPS.getName(), unitWS.getProfile().getName()));
				} else
					profilesXAPS = Arrays.asList(unittypeXAPS.getProfiles().getProfiles());
			}
			boolean useCase3 = unitWS.getParameters() != null && unitWS.getParameters().getParameterArray().getItem().length > 0;
			if (useCase3) {
				if (profilesXAPS.size() == 0) {
					throw XAPSWS.error(logger, "Unittype and profiles are not specified, not possible to execute parameter-search");
				}
			}

			/* Input is validated - now execute searches */
			Map<String, Unit> unitMap = new TreeMap<String, Unit>();
			if (unitWS.getUnitId() != null) { // Use-case 1
				Unit unitXAPS = xapsUnit.getUnitById(unitWS.getUnitId());
				if (unitXAPS != null)
					unitMap.put(unitWS.getUnitId(), unitXAPS);
			} else if (useCase3) {// Use-case 3, expect parameters and unittype
				List<com.github.freeacs.dbi.Parameter> upList = validateParameters(unitWS, profilesXAPS);
				Map<String, Unit> tmpMap = xapsUnit.getUnits(unittypeXAPS, profilesXAPS, upList, 51);
				for (Unit unitXAPS : tmpMap.values())
					unitMap.put(unitXAPS.getId(), xapsUnit.getUnitById(unitXAPS.getId()));
			} else { // Use-case 2
				Map<String, Unit> tmpMap = xapsUnit.getUnits(unitWS.getSerialNumber(), profilesXAPS, 51);
				for (Unit unitXAPS : tmpMap.values())
					unitMap.put(unitXAPS.getId(), xapsUnit.getUnitById(unitXAPS.getId()));
			}
			
			/* Search is executed - now build response */
			boolean moreUnits = unitMap.size() > 50;
			UnitList ul = new UnitList();
			com.github.freeacs.ws.Unit[] unitArray = new com.github.freeacs.ws.Unit[unitMap.size()];
			ul.setUnitArray(new ArrayOfUnit(unitArray));
			int ucount = 0;
			for (Unit unit : unitMap.values()) {
				com.github.freeacs.dbi.Unittype utXAPS = unit.getUnittype();
				com.github.freeacs.ws.Unittype utWS = new com.github.freeacs.ws.Unittype(utXAPS.getName(), null, utXAPS.getVendor(), utXAPS.getDescription(), utXAPS.getProtocol().toString(), null);
				com.github.freeacs.dbi.Profile pXAPS = unit.getProfile();
				com.github.freeacs.ws.Profile pWS = new com.github.freeacs.ws.Profile(pXAPS.getName(), null);
				UnittypeParameter snUtp = getSerialNumberUtp(utXAPS);
				String serialNumber = null;
				Map<String, String> unitParams = unit.getParameters();
				if (snUtp != null)
					serialNumber = unitParams.get(snUtp.getName());
				com.github.freeacs.ws.Parameter[] parameterArray = new com.github.freeacs.ws.Parameter[unitParams.size()];
				int pcount = 0;
				for (Entry<String, String> entry : unitParams.entrySet()) {
					String flags = "U";
					if (unit.getUnitParameters().get(entry.getKey()) == null)
						flags = "P";
					com.github.freeacs.ws.Parameter paramWS = new com.github.freeacs.ws.Parameter(entry.getKey(), entry.getValue(), flags);
					parameterArray[pcount++] = paramWS;
				}
				ParameterList parameterList = new ParameterList(new ArrayOfParameter(parameterArray));
				com.github.freeacs.ws.Unit uWS = new com.github.freeacs.ws.Unit(unit.getId(), serialNumber, pWS, utWS, parameterList);
				unitArray[ucount++] = uWS;
			}
			return new GetUnitsResponse(ul, moreUnits);
		} catch (Throwable t) {
			if (t instanceof RemoteException)
				throw (RemoteException) t;
			else {
				throw XAPSWS.error(logger, t);
			}
		}
	}

	//	private List<Profile> validatedProfiles(com.owera.xapsws.Unit unitWS) throws RemoteException {
	//		Unittype unittype = null;
	//		if (unitWS.getUnittype() != null) {
	//			unittype = xapsWS.getUnittypeFromXAPS(unitWS.getUnittype().getName());
	//			if (unitWS.getProfile() != null && unitWS.getProfile().getName() != null) {
	//				List<Profile> allowedProfiles = new ArrayList<Profile>();
	//				allowedProfiles.add(xapsWS.getProfileFromXAPS(unittype.getName(), unitWS.getProfile().getName()));
	//				return allowedProfiles;
	//			} else
	//				return Arrays.asList(unittype.getProfiles().getProfiles());
	//		}
	//		return null;
	//		//			return xaps.getAllowedProfiles(unittype);
	//	}

	private com.github.freeacs.dbi.Unittype getUnittypeForParameters(List<com.github.freeacs.dbi.Profile> allowedProfiles) throws RemoteException {
		com.github.freeacs.dbi.Unittype unittype = allowedProfiles.get(0).getUnittype();
		for (com.github.freeacs.dbi.Profile p : allowedProfiles) {
			if (!p.getUnittype().getName().equals(unittype.getName()))
				throw XAPSWS.error(logger, "Cannot specify parameters or SerialNumber without specifying Unittype"); // there are more than 1 unittype - indicating no unittype has been specified
		}
		return unittype;
	}

	private List<com.github.freeacs.dbi.Parameter> validateParameters(com.github.freeacs.ws.Unit unitWS, List<com.github.freeacs.dbi.Profile> allowedProfiles) throws RemoteException {
		if (allowedProfiles == null || allowedProfiles.size() == 0)
			throw XAPSWS.error(logger, "Unittype and profiles are not specified, not possible to make parameter-search");
		List<com.github.freeacs.dbi.Parameter> parameters = new ArrayList<com.github.freeacs.dbi.Parameter>();
		if (unitWS.getParameters() != null && unitWS.getParameters().getParameterArray() != null) {
			com.github.freeacs.dbi.Unittype unittype = getUnittypeForParameters(allowedProfiles);
			for (com.github.freeacs.ws.Parameter pWS : unitWS.getParameters().getParameterArray().getItem()) {
				UnittypeParameter utp = unittype.getUnittypeParameters().getByName(pWS.getName());
				if (utp == null)
					throw XAPSWS.error(logger, "Unittype parameter " + pWS.getName() + " is not found in unittype " + unittype.getName());
				//				boolean equal = true;
				ParameterDataType pdt = ParameterDataType.TEXT;
				Operator op = Operator.EQ;
				if (pWS.getFlags() != null) {
					String[] opTypeArr = pWS.getFlags().split(",");
					try {
						op = Operator.getOperatorFromLiteral(opTypeArr[0]);
						if (opTypeArr.length == 2)
							pdt = ParameterDataType.getDataType(opTypeArr[1]);
					} catch (IllegalArgumentException iae) {
						throw XAPSWS.error(logger, "An error occurred in flag (" + pWS.getFlags() + "): " + iae.getMessage());
					}
				}
				com.github.freeacs.dbi.Parameter pXAPS = new com.github.freeacs.dbi.Parameter(utp, pWS.getValue(), op, pdt);
				parameters.add(pXAPS);
			}
		}
		//		if (unitWS.getSerialNumber() != null) {
		//			Unittype unittype = getUnittypeForParameters(allowedProfiles);
		//			UnittypeParameter serialNumberUtp = getSerialNumberUtp(unittype);
		//			if (serialNumberUtp == null)
		//				throw XAPSWS.error(logger, "SerialNumber unittype parameter does not exist!");
		//			parameters.add(new Parameter(serialNumberUtp, unitWS.getSerialNumber(), true));
		//		}
		return parameters;
	}

	private UnittypeParameter getSerialNumberUtp(com.github.freeacs.dbi.Unittype unittype) {
		String snName = "InternetGatewayDevice.DeviceInfo.SerialNumber";
		UnittypeParameter serialNumberUtp = unittype.getUnittypeParameters().getByName(snName);
		if (serialNumberUtp == null) {
			snName = "Device.DeviceInfo.SerialNumber";
			serialNumberUtp = unittype.getUnittypeParameters().getByName(snName);
		}
		return serialNumberUtp;
	}
}
