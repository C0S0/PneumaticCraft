package pneumaticCraft.common.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pneumaticCraft.PneumaticCraft;
import pneumaticCraft.common.util.PneumaticCraftUtils;
import pneumaticCraft.lib.ModIds;
import pneumaticCraft.lib.Names;

public class ItemPneumatic extends Item{
    public ItemPneumatic(){
        setCreativeTab(PneumaticCraft.tabPneumaticCraft);
    }

    public ItemPneumatic(String unlocalizedName){
        this();
        if(unlocalizedName == null) throw new IllegalStateException("Item " + this + " has no unlocalized name!");
        setUnlocalizedName(unlocalizedName);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> curInfo, boolean extraInfo){
        super.addInformation(stack, player, curInfo, extraInfo);
        addTooltip(stack, player, curInfo);
    }

    public void registerItemVariants(){
        List<ItemStack> stacks = new ArrayList<ItemStack>();
        getSubItems(this, null, stacks);
        for(ItemStack stack : stacks) {
            ResourceLocation resLoc = new ResourceLocation(Names.MOD_ID, getModelLocation(stack));
            ModelBakery.registerItemVariants(this, resLoc);
            Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(this, stack.getItemDamage(), new ModelResourceLocation(resLoc, "inventory"));
        }
    }

    protected String getModelLocation(ItemStack stack){
        return stack.getUnlocalizedName().substring(5);
    }

    public static void addTooltip(ItemStack stack, EntityPlayer player, List curInfo){
        String info = "gui.tooltip." + stack.getItem().getUnlocalizedName();
        String translatedInfo = I18n.format(info);
        if(!translatedInfo.equals(info)) {
            if(PneumaticCraft.proxy.isSneakingInGui()) {
                translatedInfo = EnumChatFormatting.AQUA + translatedInfo;
                if(!Loader.isModLoaded(ModIds.IGWMOD)) translatedInfo += " \\n \\n" + I18n.format("gui.tab.info.assistIGW");
                curInfo.addAll(PneumaticCraftUtils.convertStringIntoList(translatedInfo, 40));
            } else {
                curInfo.add(EnumChatFormatting.AQUA + I18n.format("gui.tooltip.sneakForInfo"));
            }
        }
    }
}
