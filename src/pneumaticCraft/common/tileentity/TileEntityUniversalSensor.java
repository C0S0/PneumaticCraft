package pneumaticCraft.common.tileentity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.apache.commons.lang3.tuple.Pair;

import pneumaticCraft.api.item.IItemRegistry.EnumUpgrade;
import pneumaticCraft.api.tileentity.IAirHandler;
import pneumaticCraft.api.universalSensor.IEventSensorSetting;
import pneumaticCraft.api.universalSensor.IPollSensorSetting;
import pneumaticCraft.api.universalSensor.ISensorSetting;
import pneumaticCraft.client.gui.GuiUniversalSensor;
import pneumaticCraft.client.render.RenderRangeLines;
import pneumaticCraft.common.block.Blockss;
import pneumaticCraft.common.item.ItemGPSTool;
import pneumaticCraft.common.item.Itemss;
import pneumaticCraft.common.network.DescSynced;
import pneumaticCraft.common.network.GuiSynced;
import pneumaticCraft.common.network.NetworkHandler;
import pneumaticCraft.common.network.PacketRenderRangeLines;
import pneumaticCraft.common.sensor.SensorHandler;
import pneumaticCraft.common.thirdparty.ThirdPartyManager;
import pneumaticCraft.common.thirdparty.computercraft.LuaConstant;
import pneumaticCraft.common.thirdparty.computercraft.LuaMethod;
import pneumaticCraft.lib.ModIds;
import pneumaticCraft.lib.PneumaticValues;
import pneumaticCraft.lib.TileEntityConstants;

public class TileEntityUniversalSensor extends TileEntityPneumaticBase implements IInventory, IRangeLineShower,
        IGUITextFieldSensitive, IMinWorkingPressure, IRedstoneControl{

    @DescSynced
    public boolean[] sidesConnected = new boolean[6];
    private ItemStack[] inventory = new ItemStack[5];
    public static final int UPGRADE_SLOT_1 = 0;
    public static final int UPGRADE_SLOT_4 = 3;
    public static final int INVENTORY_SIZE = 5;

    @GuiSynced
    private String sensorSetting = "";
    private int ticksExisted;
    public int redstoneStrength;
    private int eventTimer;
    public float dishRotation;
    public float oldDishRotation;
    public float dishSpeed;
    @GuiSynced
    public boolean invertedRedstone;
    @DescSynced
    public boolean isSensorActive;
    @GuiSynced
    private String sensorGuiText = ""; //optional parameter text for sensors.
    private boolean requestPollPullEvent;
    private Set<BlockPos> positions;

    private int oldSensorRange; //range used by the range line renderer, to figure out if the range has been changed.
    private final RenderRangeLines rangeLineRenderer = new RenderRangeLines(0x330000FF);

    //  private final List<IComputerAccess> attachedComputers = new ArrayList<IComputerAccess>(); //keep track of the computers so we can raise a os.pullevent.

    public TileEntityUniversalSensor(){
        super(PneumaticValues.DANGER_PRESSURE_UNIVERSAL_SENSOR, PneumaticValues.MAX_PRESSURE_UNIVERSAL_SENSOR, PneumaticValues.VOLUME_UNIVERSAL_SENSOR, UPGRADE_SLOT_1, 1, 2, UPGRADE_SLOT_4);
        for(Item upgrade : SensorHandler.getInstance().getUniversalSensorUpgrades()) {
            addApplicableUpgrade(upgrade);
        }
        addApplicableUpgrade(EnumUpgrade.RANGE);
    }

    @Override
    public void update(){
        oldDishRotation = dishRotation;
        if(isSensorActive) {
            dishSpeed = Math.min(dishSpeed + 0.2F, 10);
        } else {
            dishSpeed = Math.max(dishSpeed - 0.2F, 0);
        }
        dishRotation += dishSpeed;

        if(worldObj.isRemote) {
            int sensorRange = getRange();
            if(oldSensorRange != sensorRange || oldSensorRange == 0) {
                oldSensorRange = sensorRange;
                if(!firstRun) rangeLineRenderer.resetRendering(sensorRange);
            }
            rangeLineRenderer.update();
        }
        super.update();

        if(!worldObj.isRemote) {
            ticksExisted++;
            ISensorSetting sensor = SensorHandler.getInstance().getSensorFromPath(sensorSetting);
            if(sensor != null && getPressure() > PneumaticValues.MIN_PRESSURE_UNIVERSAL_SENSOR) {
                isSensorActive = true;
                addAir(-PneumaticValues.USAGE_UNIVERSAL_SENSOR);
                if(sensor instanceof IPollSensorSetting) {

                    if(ticksExisted % ((IPollSensorSetting)sensor).getPollFrequency(this) == 0) {
                        int newRedstoneStrength = ((IPollSensorSetting)sensor).getRedstoneValue(worldObj, getPos(), getRange(), sensorGuiText);
                        if(invertedRedstone) newRedstoneStrength = 15 - newRedstoneStrength;
                        if(newRedstoneStrength != redstoneStrength) {
                            redstoneStrength = newRedstoneStrength;
                            if(requestPollPullEvent) {
                                notifyComputers(redstoneStrength);
                            }
                            updateNeighbours();
                        }
                    }
                    eventTimer = 0;
                } else {
                    if(eventTimer > 0) {
                        eventTimer--;
                        if(eventTimer == 0 && redstoneStrength != (invertedRedstone ? 15 : 0)) {
                            redstoneStrength = invertedRedstone ? 15 : 0;
                            updateNeighbours();
                        }
                    }
                }
            } else {
                isSensorActive = false;
                if(redstoneStrength != (invertedRedstone ? 15 : 0)) {
                    redstoneStrength = invertedRedstone ? 15 : 0;
                    updateNeighbours();
                }
            }
        }
    }

    /**
     * Will initiate the wireframe rendering. When invoked on the server, it sends a packet to every client to render the box.
     */
    @Override
    public void showRangeLines(){
        if(worldObj.isRemote) {
            rangeLineRenderer.resetRendering(getRange());
        } else {
            NetworkHandler.sendToAllAround(new PacketRenderRangeLines(this), worldObj, TileEntityConstants.PACKET_UPDATE_DISTANCE + getRange());
        }
    }

    @SideOnly(Side.CLIENT)
    public void renderRangeLines(){
        rangeLineRenderer.render();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox(){
        if(!rangeLineRenderer.isCurrentlyRendering()) return super.getRenderBoundingBox();
        int range = getRange();
        return new AxisAlignedBB(getPos().getX() - range, getPos().getY() - range, getPos().getZ() - range, getPos().getX() + 1 + range, getPos().getY() + 1 + range, getPos().getZ() + 1 + range);
    }

    public void onEvent(Event event){
        ISensorSetting sensor = SensorHandler.getInstance().getSensorFromPath(sensorSetting);
        if(sensor != null && sensor instanceof IEventSensorSetting && getPressure() > PneumaticValues.MIN_PRESSURE_UNIVERSAL_SENSOR) {
            int newRedstoneStrength = ((IEventSensorSetting)sensor).emitRedstoneOnEvent(event, this, getRange(), sensorGuiText);
            if(newRedstoneStrength != 0) eventTimer = ((IEventSensorSetting)sensor).getRedstonePulseLength();
            if(invertedRedstone) newRedstoneStrength = 15 - newRedstoneStrength;
            if(eventTimer > 0 && ThirdPartyManager.computerCraftLoaded) {
                if(event instanceof PlayerInteractEvent) {
                    PlayerInteractEvent e = (PlayerInteractEvent)event;
                    notifyComputers(newRedstoneStrength, e.pos.getX(), e.pos.getY(), e.pos.getZ());
                } else {
                    notifyComputers(newRedstoneStrength);
                }
            }
            if(newRedstoneStrength != redstoneStrength) {
                redstoneStrength = newRedstoneStrength;
                updateNeighbours();
            }
        }
    }

    public int getRange(){
        return getUpgrades(EnumUpgrade.RANGE) + 2;
    }

    public void setSensorSetting(String sensorPath){
        sensorSetting = sensorPath;
        if(worldObj != null && worldObj.isRemote) {
            GuiScreen guiScreen = Minecraft.getMinecraft().currentScreen;
            if(guiScreen instanceof GuiUniversalSensor) {
                ((GuiUniversalSensor)guiScreen).updateButtons();
            }
        }
    }

    private boolean setSensorSetting(ISensorSetting sensor){
        if(areGivenUpgradesInserted(sensor.getRequiredUpgrades())) {
            setSensorSetting(sensor.getSensorPath());
            return true;
        } else {
            return false;
        }
    }

    public String getSensorSetting(){
        return sensorSetting;
    }

    @Override
    public void onGuiUpdate(){
        setSensorSetting(sensorSetting);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag){
        super.writeToNBT(tag);
        tag.setString("sensorSetting", sensorSetting);
        tag.setBoolean("invertedRedstone", invertedRedstone);
        tag.setFloat("dishSpeed", dishSpeed);
        tag.setString("sensorText", sensorGuiText);
        // Write the ItemStacks in the inventory to NBT
        NBTTagList tagList = new NBTTagList();
        for(int currentIndex = 0; currentIndex < inventory.length; ++currentIndex) {
            if(inventory[currentIndex] != null) {
                NBTTagCompound tagCompound = new NBTTagCompound();
                tagCompound.setByte("Slot", (byte)currentIndex);
                inventory[currentIndex].writeToNBT(tagCompound);
                tagList.appendTag(tagCompound);
            }
        }
        tag.setTag("Items", tagList);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag){
        super.readFromNBT(tag);
        setSensorSetting(tag.getString("sensorSetting"));
        invertedRedstone = tag.getBoolean("invertedRedstone");
        dishSpeed = tag.getFloat("dishSpeed");
        sensorGuiText = tag.getString("sensorText");
        // Read in the ItemStacks in the inventory from NBT
        NBTTagList tagList = tag.getTagList("Items", 10);
        inventory = new ItemStack[getSizeInventory()];
        for(int i = 0; i < tagList.tagCount(); ++i) {
            NBTTagCompound tagCompound = tagList.getCompoundTagAt(i);
            byte slot = tagCompound.getByte("Slot");
            if(slot >= 0 && slot < inventory.length) {
                inventory[slot] = ItemStack.loadItemStackFromNBT(tagCompound);
            }
        }
        positions = getGPSPositionsStatic(this, getRange());
    }

    @Override
    public void handleGUIButtonPress(int buttonID, EntityPlayer player){
        if(buttonID >= 10 && buttonID % 10 == 0) {
            String[] directories = SensorHandler.getInstance().getDirectoriesAtLocation(getSensorSetting());
            if(buttonID / 10 <= directories.length) {// <= because of the redstone button being 0.
                if(getSensorSetting().equals("")) {
                    setSensorSetting(directories[buttonID / 10 - 1]);
                } else {
                    setSensorSetting(getSensorSetting() + "/" + directories[buttonID / 10 - 1]);
                }
            }
        } else if(buttonID == 1) {//the 'back' button

            String[] folders = getSensorSetting().split("/");
            String newPath = getSensorSetting().replace(folders[folders.length - 1], "");
            if(newPath.endsWith("/")) {
                newPath = newPath.substring(0, newPath.length() - 1);
            }
            setSensorSetting(newPath);
        } else if(buttonID == 0) {
            invertedRedstone = !invertedRedstone;
            redstoneStrength = 15 - redstoneStrength;
            updateNeighbours();
        }
    }

    public boolean areGivenUpgradesInserted(Set<Item> requiredItems){
        for(Item requiredItem : requiredItems) {
            if(getUpgrades(requiredItem) == 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onNeighborTileUpdate(){
        super.onNeighborTileUpdate();
        List<Pair<EnumFacing, IAirHandler>> connections = getAirHandler(null).getConnectedPneumatics();
        Arrays.fill(sidesConnected, false);
        for(Pair<EnumFacing, IAirHandler> entry : connections) {
            sidesConnected[entry.getKey().ordinal()] = true;
        }
    }

    // INVENTORY METHODS- && NBT
    // ------------------------------------------------------------

    /**
     * Returns the number of slots in the inventory.
     */
    @Override
    public int getSizeInventory(){

        return inventory.length;
    }

    /**
     * Returns the stack in slot i
     */
    @Override
    public ItemStack getStackInSlot(int slot){

        return inventory[slot];
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount){

        ItemStack itemStack = getStackInSlot(slot);
        if(itemStack != null) {
            if(itemStack.stackSize <= amount) {
                setInventorySlotContents(slot, null);
            } else {
                itemStack = itemStack.splitStack(amount);
                if(itemStack.stackSize == 0) {
                    setInventorySlotContents(slot, null);
                }
            }
        }

        return itemStack;
    }

    @Override
    public ItemStack removeStackFromSlot(int slot){

        ItemStack itemStack = getStackInSlot(slot);
        if(itemStack != null) {
            setInventorySlotContents(slot, null);
        }
        return itemStack;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack itemStack){
        // super.setInventorySlotContents(slot, itemStack);
        inventory[slot] = itemStack;
        if(itemStack != null && itemStack.stackSize > getInventoryStackLimit()) {
            itemStack.stackSize = getInventoryStackLimit();
        }
        if(!worldObj.isRemote && !getSensorSetting().equals("") && !areGivenUpgradesInserted(SensorHandler.getInstance().getRequiredStacksFromText(getSensorSetting()))) {
            setSensorSetting("");
        }
        positions = getGPSPositionsStatic(this, getRange());
    }

    public Set<BlockPos> getGPSPositions(){
        return positions;
    }

    private static Set<BlockPos> getGPSPositionsStatic(TileEntityUniversalSensor teUs, int sensorRange){
        List<BlockPos> gpsPositions = new ArrayList<BlockPos>();
        for(int i = TileEntityUniversalSensor.UPGRADE_SLOT_1; i <= TileEntityUniversalSensor.UPGRADE_SLOT_4; i++) {
            ItemStack gps = teUs.getStackInSlot(i);
            if(gps != null && gps.getItem() == Itemss.GPSTool) {
                BlockPos pos = ItemGPSTool.getGPSLocation(gps);
                if(pos != null && Math.abs(pos.getX() - teUs.getPos().getX()) <= sensorRange && Math.abs(pos.getY() - teUs.getPos().getY()) <= sensorRange && Math.abs(pos.getZ() - teUs.getPos().getZ()) <= sensorRange) {
                    gpsPositions.add(pos);
                }
            }
        }
        if(gpsPositions.size() == 1) {
            return new HashSet(gpsPositions);
        } else if(gpsPositions.size() > 1) {
            int minX = Math.min(gpsPositions.get(0).getX(), gpsPositions.get(1).getX());
            int minY = Math.min(gpsPositions.get(0).getY(), gpsPositions.get(1).getY());
            int minZ = Math.min(gpsPositions.get(0).getZ(), gpsPositions.get(1).getZ());
            int maxX = Math.max(gpsPositions.get(0).getX(), gpsPositions.get(1).getX());
            int maxY = Math.max(gpsPositions.get(0).getY(), gpsPositions.get(1).getY());
            int maxZ = Math.max(gpsPositions.get(0).getZ(), gpsPositions.get(1).getZ());
            Set<BlockPos> positions = new HashSet<BlockPos>();
            for(int x = minX; x <= maxX; x++) {
                for(int y = Math.min(255, maxY); y >= minY && y >= 0; y--) {
                    for(int z = minZ; z <= maxZ; z++) {
                        positions.add(new BlockPos(x, y, z));
                    }
                }
            }
            return positions;
        }
        return null;
    }

    @Override
    public int getInventoryStackLimit(){
        return 64;
    }

    @Override
    public String getName(){
        return Blockss.universalSensor.getUnlocalizedName();
    }

    @Override
    public boolean isItemValidForSlot(int i, ItemStack itemstack){
        return canInsertUpgrade(i, itemstack);
    }

    @Override
    public void setText(int textFieldID, String text){
        sensorGuiText = text;
        if(!worldObj.isRemote) scheduleDescriptionPacket();
    }

    @Override
    public String getText(int textFieldID){
        return sensorGuiText;
    }

    @Override
    public boolean hasCustomName(){
        return false;
    }

    /*
     * COMPUTERCRAFT API
     */
    @Override
    public String getType(){
        return "universalSensor";
    }

    @Override
    public void addLuaMethods(){
        super.addLuaMethods();

        luaMethods.add(new LuaMethod("getSensorNames"){
            @Override
            public Object[] call(Object[] args) throws Exception{
                if(args.length == 0) {
                    return SensorHandler.getInstance().getSensorNames();
                } else {
                    throw new IllegalArgumentException("getSensorNames doesn't accept any arguments!");
                }
            }
        });

        luaMethods.add(new LuaMethod("setSensor"){
            @Override
            public Object[] call(Object[] args) throws Exception{
                if(args.length == 1) {
                    ISensorSetting sensor = null;
                    if(args[0] instanceof String) {
                        sensor = SensorHandler.getInstance().getSensorForName((String)args[0]);
                    } else {
                        sensor = SensorHandler.getInstance().getSensorByIndex(((Double)args[0]).intValue() - 1);
                    }
                    if(sensor != null) return new Object[]{setSensorSetting(sensor)};
                    throw new IllegalArgumentException("Invalid sensor name/index: " + args[0]);
                } else if(args.length == 0) {
                    setSensorSetting("");
                    return new Object[]{true};
                } else {
                    throw new IllegalArgumentException("setSensor needs one argument(a number as index, or a sensor name).");
                }
            }
        });

        luaMethods.add(new LuaMethod("getSensor"){
            @Override
            public Object[] call(Object[] args) throws Exception{
                if(args.length == 0) {
                    ISensorSetting curSensor = SensorHandler.getInstance().getSensorFromPath(getSensorSetting());
                    return curSensor == null ? null : new Object[]{getSensorSetting().substring(getSensorSetting().lastIndexOf('/') + 1)};
                } else {
                    throw new IllegalArgumentException("getSensor doesn't take any arguments!");
                }
            }
        });

        luaMethods.add(new LuaMethod("setTextfield"){
            @Override
            public Object[] call(Object[] args) throws Exception{
                if(args.length == 1) {
                    setText(0, (String)args[0]);
                    return null;
                } else {
                    throw new IllegalArgumentException("setTextfield takes one argument (string)");
                }
            }
        });

        luaMethods.add(new LuaMethod("getTextfield"){
            @Override
            public Object[] call(Object[] args) throws Exception{
                if(args.length == 0) {
                    return new Object[]{getText(0)};
                } else {
                    throw new IllegalArgumentException("getTextfield takes no arguments");
                }
            }
        });

        luaMethods.add(new LuaMethod("isSensorEventBased"){
            @Override
            public Object[] call(Object[] args) throws Exception{
                if(args.length == 0) {
                    return new Object[]{SensorHandler.getInstance().getSensorFromPath(getSensorSetting()) instanceof IEventSensorSetting};
                } else {
                    throw new IllegalArgumentException("isSensorEventBased takes no arguments");
                }
            }
        });

        luaMethods.add(new LuaMethod("getSensorValue"){
            @Override
            public Object[] call(Object[] args) throws Exception{
                if(args.length == 0) {
                    ISensorSetting s = SensorHandler.getInstance().getSensorFromPath(getSensorSetting());
                    if(s instanceof IPollSensorSetting) {
                        requestPollPullEvent = true;
                        return new Object[]{redstoneStrength};
                    } else if(s != null) {
                        throw new IllegalArgumentException("The selected sensor is pull event based. You can't poll the value.");
                    } else {
                        throw new IllegalArgumentException("There's no sensor selected!");
                    }
                } else {
                    throw new IllegalArgumentException("getSensorValue takes no arguments");
                }
            }
        });
        luaMethods.add(new LuaConstant("getMinWorkingPressure", PneumaticValues.MIN_PRESSURE_UNIVERSAL_SENSOR));

        luaMethods.add(new LuaMethod("setGPSToolCoordinate"){
            @Override
            public Object[] call(Object[] args) throws Exception{
                if(args.length == 4) {
                    ItemStack stack = getStackInSlot(((Double)args[0]).intValue() - 1); //minus one, as lua is 1-oriented.
                    if(stack != null && stack.getItem() == Itemss.GPSTool) {
                        ItemGPSTool.setGPSLocation(stack, new BlockPos((Double)args[1], (Double)args[2], (Double)args[3]));
                        return new Object[]{true};
                    } else {
                        return new Object[]{false};
                    }
                } else {
                    throw new IllegalArgumentException("setGPSToolCoordinate needs 4 arguments: slot, x, y, z");
                }
            }

        });

        luaMethods.add(new LuaMethod("getGPSToolCoordinate"){
            @Override
            public Object[] call(Object[] args) throws Exception{
                if(args.length == 1) {
                    ItemStack stack = getStackInSlot(((Double)args[0]).intValue() - 1); //minus one, as lua is 1-oriented.
                    if(stack != null && stack.getItem() == Itemss.GPSTool) {
                        BlockPos pos = ItemGPSTool.getGPSLocation(stack);
                        if(pos != null) {
                            return new Object[]{pos.getX(), pos.getY(), pos.getZ()};
                        } else {
                            return new Object[]{0, 0, 0};
                        }
                    } else {
                        return null;
                    }
                } else {
                    throw new IllegalArgumentException("setGPSToolCoordinate needs 1 argument: slot");
                }
            }
        });
    }

    /*@Override
    @Optional.Method(modid = ModIds.COMPUTERCRAFT)
    public void attach(IComputerAccess computer){
        attachedComputers.add(computer);
    }

    @Override
    @Optional.Method(modid = ModIds.COMPUTERCRAFT)
    public void detach(IComputerAccess computer){
        attachedComputers.remove(computer);
    }*/

    /**
     * Called on a event sensor
     * @param newRedstoneStrength
     */
    @Optional.Method(modid = ModIds.COMPUTERCRAFT)
    private void notifyComputers(Object... arguments){
        /*     for(IComputerAccess computer : attachedComputers) {
                 computer.queueEvent(getType(), arguments);
             }*/
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer var1){
        return isGuiUseableByPlayer(var1);
    }

    @Override
    public void openInventory(EntityPlayer player){}

    @Override
    public void closeInventory(EntityPlayer player){}

    @Override
    public void clear(){
        Arrays.fill(inventory, null);
    }

    @Override
    public int getRedstoneMode(){
        return invertedRedstone ? 1 : 0;
    }

    @Override
    public float getMinWorkingPressure(){
        return PneumaticValues.MIN_PRESSURE_UNIVERSAL_SENSOR;
    }

}
