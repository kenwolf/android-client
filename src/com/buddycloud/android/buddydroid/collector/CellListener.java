package com.buddycloud.android.buddydroid.collector;

import java.util.List;

import android.content.Context;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

import com.buddycloud.android.buddydroid.BuddycloudService;
import com.buddycloud.jbuddycloud.packet.BeaconLog;

public class CellListener extends PhoneStateListener {

    private BuddycloudService service;
    private TelephonyManager telephonyManager;
    private String cell;
    private String newCell;
    private int power = -1;

    public CellListener(BuddycloudService service) {
        this.service = service;
        telephonyManager = (TelephonyManager) service.getSystemService(
            Context.TELEPHONY_SERVICE
        );
        CellLocation.requestLocationUpdate();
    }

    @Override
    public void onCellLocationChanged(CellLocation location) {
        if (!(location instanceof GsmCellLocation)) {
            return;
        }
        GsmCellLocation gsmCell = (GsmCellLocation) location;
        String operator = telephonyManager.getNetworkOperator();
        if (operator == null || operator.length() < 4) {
            return;
        }
        newCell = operator.substring(0, 3) + ':'
                    + operator.substring(3) + ':'
                    + gsmCell.getLac() + ':' + gsmCell.getCid();
        if (cell == null) {
            cell = newCell;
        }
        try {
            service.sendBeaconLog(3);
        } catch (InterruptedException e) {
        }
    }

    @Override
    public void onSignalStrengthChanged(int asu) {
        boolean force = cell == null;
        cell = newCell;
        power = 113 - 2*asu;
        try {
            service.sendBeaconLog(force ? 2 : 10);
        } catch (InterruptedException e) {
        }
    }

    public void appendTo(BeaconLog log) {
        log.add("cell", cell, power);
        List<NeighboringCellInfo> neighboringCellInfo =
            telephonyManager.getNeighboringCellInfo();
        if (neighboringCellInfo == null) {
            return;
        }
        Log.d("CellListener", "Neighbour update");
        for (NeighboringCellInfo info : neighboringCellInfo) {
            log.add(
                "cell",
                cell.substring(0, cell.lastIndexOf(':') + 1) + info.getCid(),
                113 - 2*info.getRssi()
            );
        }
    }

}