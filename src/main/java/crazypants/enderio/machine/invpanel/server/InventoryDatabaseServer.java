package crazypants.enderio.machine.invpanel.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import com.enderio.core.common.network.CompressedDataInput;
import com.enderio.core.common.network.CompressedDataOutput;

import crazypants.enderio.conduit.item.ItemConduitNetwork;
import crazypants.enderio.conduit.item.NetworkedInventory;
import crazypants.enderio.config.Config;
import crazypants.enderio.machine.invpanel.InventoryDatabase;
import crazypants.enderio.machine.invpanel.PacketDatabaseReset;
import crazypants.enderio.machine.invpanel.TileInventoryPanel;
import crazypants.enderio.network.PacketHandler;

public class InventoryDatabaseServer extends InventoryDatabase<ItemEntry> {

    private static final AtomicInteger nextGeneration = new AtomicInteger();
    private static final int MAX_DIRTY_SLOTS_PER_TICK = 512;

    private final ItemConduitNetwork network;
    private int networkChangeCount;

    private AbstractInventory[] inventories;
    private final IdentityHashMap<NetworkedInventory, AbstractInventory> inventoryMap = new IdentityHashMap<>();
    private final LinkedHashSet<AbstractInventory> dirtyInventories = new LinkedHashSet<>();
    private int currentInventory;
    private ChangeLog changeLog;
    private boolean sentToClient;
    private int tickPause;
    private float power;

    public InventoryDatabaseServer(ItemConduitNetwork network) {
        this.network = network;
    }

    public ItemConduitNetwork getNetwork() {
        return network;
    }

    public boolean isCurrent() {
        return networkChangeCount == network.getChangeCount();
    }

    public void addChangeLog(ChangeLog cl) {
        if (changeLog == null) {
            changeLog = cl;
        } else if (changeLog instanceof ChangeLogList) {
            ((ChangeLogList) changeLog).add(cl);
        } else if (changeLog != cl) {
            changeLog = new ChangeLogList(changeLog, cl);
        }
    }

    public void removeChangeLog(ChangeLog cl) {
        if (changeLog == cl) {
            changeLog = null;
        } else if (changeLog instanceof ChangeLogList) {
            changeLog = ((ChangeLogList) changeLog).remove(cl);
        }
    }

    public List<ItemEntry> decompressMissingItems(byte[] compressed) throws IOException {
        CompressedDataInput cdi = new CompressedDataInput(compressed);
        try {
            int pktGeneration = cdi.readVariable();
            if (pktGeneration != generation) {
                return Collections.emptyList();
            }
            int numIDs = cdi.readVariable();
            ArrayList<ItemEntry> items = new ArrayList<>(numIDs);
            for (int i = 0; i < numIDs; i++) {
                int dbIndex = cdi.readVariable();
                if (dbIndex < complexItems.size()) {
                    ItemEntry entry = complexItems.get(dbIndex);
                    items.add(entry);
                }
            }
            return items;
        } finally {
            cdi.close();
        }
    }

    public byte[] compressItemInfo(List<ItemEntry> items) throws IOException {
        CompressedDataOutput cdo = new CompressedDataOutput();
        try {
            int count = items.size();
            cdo.writeVariable(count);
            for (ItemEntry entry : items) {
                assert entry.dbID >= COMPLEX_DBINDEX_START;
                int code = (entry.dbID - COMPLEX_DBINDEX_START) << 1;
                if (entry.nbt != null) {
                    code |= 1;
                }
                cdo.writeVariable(code);
                cdo.writeVariable(entry.itemID);
                cdo.writeVariable(entry.meta);
                if (entry.nbt != null) {
                    CompressedStreamTools.write(entry.nbt, cdo);
                }
                cdo.writeVariable(entry.countItems(this));
            }
            return cdo.getCompressed();
        } finally {
            cdo.close();
        }
    }

    public byte[] compressItemList() throws IOException {
        CompressedDataOutput cdo = new CompressedDataOutput();
        try {
            cdo.writeByte(0);
            for (Map.Entry<Integer, ItemEntry> entry : simpleRegsitry.entrySet()) {
                int count = entry.getValue().countItems(this);
                if (count > 0) {
                    cdo.writeVariable(count);
                    cdo.writeShort(entry.getKey());
                }
            }
            cdo.writeByte(0);
            int prevID = COMPLEX_DBINDEX_START;
            for (ItemEntry entry : complexItems) {
                if (entry != null) {
                    int count = entry.countItems(this);
                    if (count > 0) {
                        cdo.writeVariable(count);
                        cdo.writeVariable(entry.dbID - prevID);
                        prevID = entry.dbID;
                    }
                }
            }
            cdo.writeByte(0);
            sentToClient = true;
            return cdo.getCompressed();
        } finally {
            cdo.close();
        }
    }

    public byte[] compressChangedItems(Collection<ItemEntry> items) throws IOException {
        CompressedDataOutput cdo = new CompressedDataOutput();
        try {
            cdo.writeVariable(items.size());
            for (ItemEntry entry : items) {
                cdo.writeVariable(entry.dbID);
                cdo.writeVariable(entry.countItems(this));
            }
            return cdo.getCompressed();
        } finally {
            cdo.close();
        }
    }

    public void resetDatabase() {
        simpleRegsitry.clear();
        complexRegistry.clear();
        complexItems.clear();
        currentInventory = 0;
        dirtyInventories.clear();
        if (sentToClient) {
            PacketHandler.INSTANCE.sendToAll(new PacketDatabaseReset(generation));
            sentToClient = false;
        }
    }

    public void updateNetworkSources() {
        resetDatabase();
        generation = nextGeneration.incrementAndGet();
        networkChangeCount = network.getChangeCount();

        inventoryMap.clear();
        List<NetworkedInventory> sources = network.getInventoryPanelSources();
        if (sources == null || sources.isEmpty()) {
            this.inventories = null;
        } else {
            this.inventories = new AbstractInventory[sources.size()];
            for (int i = 0; i < sources.size(); i++) {
                NetworkedInventory ni = sources.get(i);
                inventories[i] = InventoryFactory.createInventory(ni);
                inventoryMap.put(ni, inventories[i]);
            }
        }

        if (changeLog != null) {
            changeLog.databaseReset();
        }
    }

    public int getNumInventories() {
        return (inventories == null) ? 0 : inventories.length;
    }

    public float getPower() {
        return power;
    }

    public void addPower(float power) {
        this.power += power;
    }

    public boolean isOperational() {
        return power > 0 && inventories != null;
    }

    public int extractItems(ItemEntry entry, int count, TileInventoryPanel te) {
        float availablePower = power + te.getAvailablePower();
        availablePower -= Config.inventoryPanelExtractCostPerOperation;
        if (availablePower <= 0) {
            return 0;
        }
        if (Config.inventoryPanelExtractCostPerOperation > 0) {
            long maxCount = Math.round(Math.floor(availablePower / Config.inventoryPanelExtractCostPerOperation));
            count = (int) Math.min(maxCount, count);
        }
        if (count > 0) {
            int extracted = entry.extractItems(this, count);
            power -= Config.inventoryPanelExtractCostPerOperation
                    + extracted * Config.inventoryPanelExtractCostPerOperation;
            te.refuelPower(this);
            return extracted;
        }
        return 0;
    }

    private void scanNextInventory() {
        if (!isOperational()) {
            tickPause = 20;
            return;
        }

        AbstractInventory inv = inventories[currentInventory];
        int slots = inv.scanInventory(this);

        currentInventory = (currentInventory + 1) % inventories.length;
        tickPause += 1 + (slots + 8) / 9;
        power -= slots * Config.inventoryPanelScanCostPerSlot;
    }

    /**
     * Called when items were inserted into an inventory through the item conduit network (for example from the return
     * area of an inventory panel). The inventory is scanned on the next tick, so the new items can be used right away
     * instead of waiting for the regular scan cycle to reach this inventory.
     */
    public void inventoryChanged(NetworkedInventory ni) {
        if (isCurrent()) {
            AbstractInventory inv = inventoryMap.get(ni);
            if (inv != null) {
                dirtyInventories.add(inv);
            }
        }
    }

    private void scanDirtyInventories() {
        if (dirtyInventories.isEmpty() || !isOperational()) {
            return;
        }
        int scannedSlots = 0;
        Iterator<AbstractInventory> iter = dirtyInventories.iterator();
        while (iter.hasNext() && scannedSlots < MAX_DIRTY_SLOTS_PER_TICK) {
            AbstractInventory inv = iter.next();
            iter.remove();
            int slots = inv.scanInventory(this);
            scannedSlots += slots;
            power -= slots * Config.inventoryPanelScanCostPerSlot;
        }
    }

    public void tick() {
        scanDirtyInventories();
        if (--tickPause <= 0) {
            scanNextInventory();
        }
    }

    void entryChanged(ItemEntry entry) {
        if (changeLog != null) {
            changeLog.entryChanged(entry);
        }
    }

    public void sendChangeLogs() {
        if (changeLog != null) {
            changeLog.sendChangeLog();
        }
    }

    @Override
    protected ItemEntry createItemEntry(int dbId, int hash, int itemID, int meta, NBTTagCompound nbt) {
        return new ItemEntry(dbId, hash, itemID, meta, nbt);
    }

    AbstractInventory getInventory(int aiIndex) {
        return inventories[aiIndex];
    }
}
