package pneumaticCraft.common.progwidgets;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pneumaticCraft.client.gui.GuiProgrammer;
import pneumaticCraft.client.gui.programmer.GuiProgWidgetCondition;
import pneumaticCraft.client.gui.widget.GuiCheckBox;
import pneumaticCraft.client.gui.widget.IGuiWidget;
import pneumaticCraft.common.ai.DroneAIBlockCondition;
import pneumaticCraft.common.ai.DroneAIDig;
import pneumaticCraft.common.ai.IDroneBase;
import pneumaticCraft.common.util.PneumaticCraftUtils;
import pneumaticCraft.lib.Textures;

public class ProgWidgetBlockCondition extends ProgWidgetCondition{
    private boolean checkingForAir;
    private boolean checkingForLiquids;

    @Override
    public String getWidgetString(){
        return "conditionBlock";
    }

    @Override
    public Class<? extends IProgWidget>[] getParameters(){
        return new Class[]{ProgWidgetArea.class, ProgWidgetItemFilter.class, ProgWidgetString.class};
    }

    @Override
    protected DroneAIBlockCondition getEvaluator(IDroneBase drone, IProgWidget widget){
        return new DroneAIBlockCondition(drone, (ProgWidgetAreaItemBase)widget){

            @Override
            protected boolean evaluate(BlockPos pos){
                if(checkingForAir && drone.world().isAirBlock(pos)) return true;
                if(checkingForLiquids && PneumaticCraftUtils.isBlockLiquid(drone.world().getBlockState(pos).getBlock())) return true;
                if(!checkingForAir && !checkingForLiquids || getConnectedParameters()[1] != null) {
                    return DroneAIDig.isBlockValidForFilter(drone.world(), drone, pos, widget);
                } else {
                    return false;
                }
            }
        };
    }

    @Override
    @SideOnly(Side.CLIENT)
    public GuiScreen getOptionWindow(GuiProgrammer guiProgrammer){
        return new GuiProgWidgetCondition(this, guiProgrammer){
            @Override
            public void initGui(){
                super.initGui();
                addWidget(new GuiCheckBox(500, guiLeft + 5, guiTop + 60, 0xFF000000, I18n.format("gui.progWidget.conditionBlock.checkForAir")).setChecked(checkingForAir).setTooltip(I18n.format("gui.progWidget.conditionBlock.checkForAir.tooltip")));
                addWidget(new GuiCheckBox(501, guiLeft + 5, guiTop + 72, 0xFF000000, I18n.format("gui.progWidget.conditionBlock.checkForLiquids")).setChecked(checkingForLiquids).setTooltip(I18n.format("gui.progWidget.conditionBlock.checkForLiquids.tooltip")));
            }

            @Override
            protected boolean requiresNumber(){
                return false;
            }

            @Override
            protected boolean isSidedWidget(){
                return false;
            }

            @Override
            public void actionPerformed(IGuiWidget widget){
                if(widget.getID() == 500) checkingForAir = !checkingForAir;
                if(widget.getID() == 501) checkingForLiquids = !checkingForLiquids;
                else super.actionPerformed(widget);
            }

        };
    }

    @Override
    public ResourceLocation getTexture(){
        return Textures.PROG_WIDGET_CONDITION_BLOCK;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag){
        super.writeToNBT(tag);
        tag.setBoolean("checkingForAir", checkingForAir);
        tag.setBoolean("checkingForLiquids", checkingForLiquids);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag){
        super.readFromNBT(tag);
        checkingForAir = tag.getBoolean("checkingForAir");
        checkingForLiquids = tag.getBoolean("checkingForLiquids");
    }

}
