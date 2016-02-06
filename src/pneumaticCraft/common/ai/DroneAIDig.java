package pneumaticCraft.common.ai;

import java.util.Arrays;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;
import pneumaticCraft.common.progwidgets.ProgWidgetAreaItemBase;
import pneumaticCraft.common.util.PneumaticCraftUtils;

public class DroneAIDig extends DroneAIBlockInteraction{

    /**
     * 
     * @param drone
     * @param speed
     * @param widget needs to implement IBlockOrdered.
     */
    public DroneAIDig(IDroneBase drone, ProgWidgetAreaItemBase widget){
        super(drone, widget);
    }

    @Override
    protected boolean isValidPosition(BlockPos pos){
        IBlockState blockState = worldCache.getBlockState(pos);
        Block block = blockState.getBlock();
        if(!worldCache.isAirBlock(pos) && !ignoreBlock(block)) {
            List<ItemStack> droppedStacks;
            if(block.canSilkHarvest(drone.world(), pos, blockState, drone.getFakePlayer())) {
                droppedStacks = Arrays.asList(new ItemStack[]{getSilkTouchBlock(block, blockState)});
            } else {
                droppedStacks = block.getDrops(drone.world(), pos, blockState, 0);
            }
            for(ItemStack droppedStack : droppedStacks) {
                if(widget.isItemValidForFilters(droppedStack, blockState)) {
                    swapBestItemToFirstSlot(block, pos);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected boolean respectClaims(){
        return true;
    }

    private void swapBestItemToFirstSlot(Block block, BlockPos pos){
        int bestSlot = 0;
        float bestSoftness = Float.MIN_VALUE;
        ItemStack oldCurrentStack = drone.getInv().getStackInSlot(0);
        for(int i = 0; i < drone.getInv().getSizeInventory(); i++) {
            drone.getInv().setInventorySlotContents(0, drone.getInv().getStackInSlot(i));
            float softness = block.getPlayerRelativeBlockHardness(drone.getFakePlayer(), drone.world(), pos);
            if(softness > bestSoftness) {
                bestSlot = i;
                bestSoftness = softness;
            }
        }
        drone.getInv().setInventorySlotContents(0, oldCurrentStack);
        if(bestSlot != 0) {
            ItemStack bestItem = drone.getInv().getStackInSlot(bestSlot);
            drone.getInv().setInventorySlotContents(bestSlot, drone.getInv().getStackInSlot(0));
            drone.getInv().setInventorySlotContents(0, bestItem);
        }
    }

    @Override
    protected boolean doBlockInteraction(BlockPos pos, double distToBlock){
        if(!((FakePlayerItemInWorldManager)drone.getFakePlayer().theItemInWorldManager).isDigging() || !((FakePlayerItemInWorldManager)drone.getFakePlayer().theItemInWorldManager).isAcknowledged()) {
            IBlockState blockState = worldCache.getBlockState(pos);
            Block block = blockState.getBlock();
            if(!ignoreBlock(block) && isBlockValidForFilter(worldCache, drone, pos, widget)) {
                if(block.getBlockHardness(drone.world(), pos) < 0) {
                    addToBlacklist(pos);
                    drone.addDebugEntry("gui.progWidget.dig.debug.cantDigBlock", pos);
                    drone.setDugBlock(null);
                    return false;
                }
                FakePlayerItemInWorldManager manager = (FakePlayerItemInWorldManager)drone.getFakePlayer().theItemInWorldManager;
                manager.onBlockClicked(pos, EnumFacing.DOWN);
                if(!manager.isAccepted) {
                    addToBlacklist(pos);
                    drone.addDebugEntry("gui.progWidget.dig.debug.cantDigBlock", pos);
                    drone.setDugBlock(null);
                    return false;
                }
                drone.setDugBlock(pos);
                return true;
            }
            drone.setDugBlock(null);
            return false;
        } else {
            return true;
        }
    }

    public static boolean isBlockValidForFilter(IBlockAccess worldCache, IDroneBase drone, BlockPos pos, ProgWidgetAreaItemBase widget){
        IBlockState blockState = worldCache.getBlockState(pos);
        Block block = blockState.getBlock();

        if(!block.isAir(worldCache, pos)) {
            List<ItemStack> droppedStacks;
            if(block.canSilkHarvest(drone.world(), pos, blockState, drone.getFakePlayer())) {
                droppedStacks = Arrays.asList(new ItemStack[]{getSilkTouchBlock(block, blockState)});
            } else {
                droppedStacks = block.getDrops(drone.world(), pos, blockState, 0);
            }
            for(ItemStack droppedStack : droppedStacks) {
                if(widget.isItemValidForFilters(droppedStack, blockState)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ItemStack getSilkTouchBlock(Block block, IBlockState state){
        return null;//TODO 1.8 return block.createStackedBlock(state);
        //  return SilkTouchBlockGetter.getSilkTouchStack(block, state);
    }

    private static boolean ignoreBlock(Block block){
        return PneumaticCraftUtils.isBlockLiquid(block);
    }

}
