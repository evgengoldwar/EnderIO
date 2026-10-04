package crazypants.enderio.conduit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityDiggingFX;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.entity.PlaySoundAtEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed;

import org.lwjgl.opengl.GL11;

import com.enderio.core.client.render.BoundingBox;
import com.enderio.core.common.util.BlockCoord;
import com.enderio.core.common.util.Util;

import cpw.mods.fml.common.Optional;
import cpw.mods.fml.common.Optional.Interface;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.IGuiHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import crazypants.enderio.BlockEio;
import crazypants.enderio.EnderIO;
import crazypants.enderio.GuiHandler;
import crazypants.enderio.ModObject;
import crazypants.enderio.api.tool.ITool;
import crazypants.enderio.conduit.facade.ItemConduitFacade.FacadeType;
import crazypants.enderio.conduit.geom.CollidableComponent;
import crazypants.enderio.conduit.geom.ConduitConnectorType;
import crazypants.enderio.conduit.gui.ExternalConnectionContainer;
import crazypants.enderio.conduit.gui.GuiExternalConnection;
import crazypants.enderio.conduit.gui.GuiExternalConnectionSelector;
import crazypants.enderio.conduit.gui.PacketFluidChannel;
import crazypants.enderio.conduit.gui.PacketFluidFilter;
import crazypants.enderio.conduit.gui.PacketOpenConduitUI;
import crazypants.enderio.conduit.gui.PacketSlotVisibility;
import crazypants.enderio.conduit.gui.item.PacketExistingItemFilterSnapshot;
import crazypants.enderio.conduit.gui.item.PacketModItemFilter;
import crazypants.enderio.conduit.liquid.PacketFluidLevel;
import crazypants.enderio.conduit.packet.PacketConnectionMode;
import crazypants.enderio.conduit.packet.PacketExtractMode;
import crazypants.enderio.conduit.packet.PacketItemConduitFilter;
import crazypants.enderio.conduit.packet.PacketOCConduitSignalColor;
import crazypants.enderio.conduit.packet.PacketRedstoneConduitOutputStrength;
import crazypants.enderio.conduit.packet.PacketRedstoneConduitSignalColor;
import crazypants.enderio.conduit.packet.PacketRoundRobinMode;
import crazypants.enderio.conduit.redstone.IInsulatedRedstoneConduit;
import crazypants.enderio.conduit.redstone.IRedstoneConduit;
import crazypants.enderio.conduit.redstone.InsulatedRedstoneConduit;
import crazypants.enderio.item.IRotatableFacade;
import crazypants.enderio.item.ItemConduitProbe;
import crazypants.enderio.machine.painter.PainterUtil;
import crazypants.enderio.network.PacketHandler;
import crazypants.enderio.tool.ToolUtil;
import crazypants.util.ForgeDirections;
import crazypants.util.IFacade;
import mods.immibis.core.api.multipart.IMultipartRenderingBlockMarker;
import mods.immibis.core.api.multipart.IMultipartSystem;
import powercrystals.minefactoryreloaded.api.rednet.IRedNetOmniNode;
import powercrystals.minefactoryreloaded.api.rednet.connectivity.RedNetConnectionType;

@Optional.InterfaceList({
        @Interface(
                iface = "powercrystals.minefactoryreloaded.api.rednet.IRedNetOmniNode",
                modid = "MineFactoryReloaded"),
        @Interface(
                iface = "mods.immibis.core.api.multipart.IMultipartRenderingBlockMarker",
                modid = "ImmibisMicroblocks") })
public class BlockConduitBundle extends BlockEio
        implements IGuiHandler, IFacade, IRotatableFacade, IRedNetOmniNode, IMultipartRenderingBlockMarker {

    private static final String KEY_CONNECTOR_ICON = "enderIO:conduitConnector";
    private static final String KEY_CONNECTOR_ICON_EXTERNAL = "enderIO:conduitConnectorExternal";

    public static BlockConduitBundle create() {

        PacketHandler.INSTANCE
                .registerMessage(PacketFluidLevel.class, PacketFluidLevel.class, PacketHandler.nextID(), Side.CLIENT);
        PacketHandler.INSTANCE
                .registerMessage(PacketExtractMode.class, PacketExtractMode.class, PacketHandler.nextID(), Side.SERVER);
        PacketHandler.INSTANCE.registerMessage(
                PacketConnectionMode.class,
                PacketConnectionMode.class,
                PacketHandler.nextID(),
                Side.SERVER);
        PacketHandler.INSTANCE.registerMessage(
                PacketItemConduitFilter.class,
                PacketItemConduitFilter.class,
                PacketHandler.nextID(),
                Side.SERVER);
        PacketHandler.INSTANCE.registerMessage(
                PacketExistingItemFilterSnapshot.class,
                PacketExistingItemFilterSnapshot.class,
                PacketHandler.nextID(),
                Side.SERVER);
        PacketHandler.INSTANCE.registerMessage(
                PacketModItemFilter.class,
                PacketModItemFilter.class,
                PacketHandler.nextID(),
                Side.SERVER);
        PacketHandler.INSTANCE
                .registerMessage(PacketFluidFilter.class, PacketFluidFilter.class, PacketHandler.nextID(), Side.SERVER);
        PacketHandler.INSTANCE.registerMessage(
                PacketFluidChannel.class,
                PacketFluidChannel.class,
                PacketHandler.nextID(),
                Side.SERVER);
        PacketHandler.INSTANCE.registerMessage(
                PacketRedstoneConduitSignalColor.class,
                PacketRedstoneConduitSignalColor.class,
                PacketHandler.nextID(),
                Side.SERVER);
        PacketHandler.INSTANCE.registerMessage(
                PacketRedstoneConduitOutputStrength.class,
                PacketRedstoneConduitOutputStrength.class,
                PacketHandler.nextID(),
                Side.SERVER);
        PacketHandler.INSTANCE.registerMessage(
                PacketOpenConduitUI.class,
                PacketOpenConduitUI.class,
                PacketHandler.nextID(),
                Side.SERVER);
        PacketHandler.INSTANCE.registerMessage(
                PacketSlotVisibility.class,
                PacketSlotVisibility.class,
                PacketHandler.nextID(),
                Side.SERVER);
        PacketHandler.INSTANCE.registerMessage(
                PacketOCConduitSignalColor.class,
                PacketOCConduitSignalColor.class,
                PacketHandler.nextID(),
                Side.SERVER);
        PacketHandler.INSTANCE.registerMessage(
                PacketRoundRobinMode.class,
                PacketRoundRobinMode.class,
                PacketHandler.nextID(),
                Side.SERVER);

        BlockConduitBundle result = new BlockConduitBundle();
        result.init();
        return result;
    }

    public static int rendererId = -1;

    private IIcon connectorIcon, connectorIconExternal;

    private final Random rand = new Random();

    protected BlockConduitBundle() {
        super(ModObject.blockConduitBundle.unlocalisedName, TileConduitBundle.class);
        setBlockBounds(0.334f, 0.334f, 0.334f, 0.667f, 0.667f, 0.667f);
        setHardness(1.5f);
        setResistance(10.0f);
        setCreativeTab(null);
        this.stepSound = new SoundType("silence", 0, 0) {

            @Override
            public String getBreakSound() {
                return "EnderIO:" + soundName + ".dig";
            }

            @Override
            public String getStepResourcePath() {
                return "EnderIO:" + soundName + ".step";
            }
        };
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean addHitEffects(World world, MovingObjectPosition target, EffectRenderer effectRenderer) {
        if (MicroblocksUtil.supportMicroblocks() && IM__addHitEffects(world, target, effectRenderer)) {
            return true;
        }

        TileEntity cbe = world.getTileEntity(target.blockX, target.blockY, target.blockZ);
        if (!(cbe instanceof TileConduitBundle)) {
            return false;
        }
        TileConduitBundle cb = (TileConduitBundle) cbe;

        IIcon tex = null;
        BoundingBox bound = BoundingBox.UNIT_CUBE;
        if (ConduitUtil.isSolidFacadeRendered(cb, Minecraft.getMinecraft().thePlayer)) {
            if (cb.getFacadeId() != null) {
                tex = cb.getFacadeId().getIcon(target.sideHit, cb.getFacadeMetadata());
            }
        } else if (target.hitInfo instanceof CollidableComponent) {
            CollidableComponent cc = (CollidableComponent) target.hitInfo;
            if (cc.bound != null) {
                bound = cc.bound;
            }
            IConduit con = cb.getConduit(cc.conduitType);
            if (con != null) {
                tex = con.getTextureForState(cc);
            }
        }
        addBlockHitEffects(
                world,
                effectRenderer,
                target.blockX,
                target.blockY,
                target.blockZ,
                target.sideHit,
                bound,
                tex == null ? blockIcon : tex);
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean addDestroyEffects(World world, int x, int y, int z, int meta, EffectRenderer effectRenderer) {
        if (MicroblocksUtil.supportMicroblocks() && IM__addDestroyEffects(world, x, y, z, meta, effectRenderer)) {
            return true;
        }

        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileConduitBundle)) {
            // nothing left to look at, fall back to a small puff in the center
            addDestroyEffects(world, effectRenderer, x, y, z, BoundingBox.UNIT_CUBE.scale(0.33, 0.33, 0.33), blockIcon);
            return true;
        }
        TileConduitBundle cb = (TileConduitBundle) tile;
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;

        // This is called right before the block/conduit is actually removed, so we can still see what will go away
        if (ConduitUtil.isSolidFacadeRendered(cb, player)) {
            Block facade = cb.getFacadeId();
            addDestroyEffects(
                    world,
                    effectRenderer,
                    x,
                    y,
                    z,
                    BoundingBox.UNIT_CUBE,
                    facade.getIcon(rand.nextInt(6), cb.getFacadeMetadata()));
            return true;
        }

        List<IConduit> broken = getConduitsToBreak(world, x, y, z, player);
        if (broken.isEmpty()) {
            // Most likely someone else broke it, we don't know which conduit so show all of them
            broken = new ArrayList<>(cb.getConduits());
        }
        boolean spawned = false;
        for (IConduit con : broken) {
            for (CollidableComponent cc : getComponentsOf(cb, con)) {
                IIcon tex = con.getTextureForState(cc);
                addDestroyEffects(world, effectRenderer, x, y, z, cc.bound, tex == null ? blockIcon : tex);
                spawned = true;
            }
        }
        if (!spawned) {
            addDestroyEffects(world, effectRenderer, x, y, z, BoundingBox.UNIT_CUBE.scale(0.33, 0.33, 0.33), blockIcon);
        }
        return true;
    }

    /**
     * Spawns breaking particles filling the given box (block relative coordinates) instead of the whole block.
     */
    @SideOnly(Side.CLIENT)
    private void addDestroyEffects(World world, EffectRenderer effectRenderer, int x, int y, int z, BoundingBox bb,
            IIcon tex) {
        final double density = 8;
        int nx = Math.max(1, (int) Math.round((bb.maxX - bb.minX) * density));
        int ny = Math.max(1, (int) Math.round((bb.maxY - bb.minY) * density));
        int nz = Math.max(1, (int) Math.round((bb.maxZ - bb.minZ) * density));
        double cx = (bb.minX + bb.maxX) / 2;
        double cy = (bb.minY + bb.maxY) / 2;
        double cz = (bb.minZ + bb.maxZ) / 2;
        for (int i = 0; i < nx; ++i) {
            for (int j = 0; j < ny; ++j) {
                for (int k = 0; k < nz; ++k) {
                    double px = bb.minX + (i + 0.5D) * (bb.maxX - bb.minX) / nx;
                    double py = bb.minY + (j + 0.5D) * (bb.maxY - bb.minY) / ny;
                    double pz = bb.minZ + (k + 0.5D) * (bb.maxZ - bb.minZ) / nz;
                    EntityDiggingFX fx = new EntityDiggingFX(
                            world,
                            x + px,
                            y + py,
                            z + pz,
                            (px - cx) * 2 + (rand.nextDouble() - 0.5D) * 0.2D,
                            (py - cy) * 2 + rand.nextDouble() * 0.2D,
                            (pz - cz) * 2 + (rand.nextDouble() - 0.5D) * 0.2D,
                            this,
                            rand.nextInt(6),
                            0).applyColourMultiplier(x, y, z);
                    fx.setParticleIcon(tex);
                    fx.multipleParticleScaleBy(0.7F);
                    effectRenderer.addEffect(fx);
                }
            }
        }
    }

    /**
     * Spawns a hit particle on the face of the box (block relative coordinates) that is being hit.
     */
    @SideOnly(Side.CLIENT)
    private void addBlockHitEffects(World world, EffectRenderer effectRenderer, int x, int y, int z, int side,
            BoundingBox bb, IIcon tex) {
        final double f = 0.05;
        double insetX = Math.min(f, (bb.maxX - bb.minX) / 4);
        double insetY = Math.min(f, (bb.maxY - bb.minY) / 4);
        double insetZ = Math.min(f, (bb.maxZ - bb.minZ) / 4);
        double d0 = x + bb.minX + insetX + rand.nextDouble() * (bb.maxX - bb.minX - insetX * 2);
        double d1 = y + bb.minY + insetY + rand.nextDouble() * (bb.maxY - bb.minY - insetY * 2);
        double d2 = z + bb.minZ + insetZ + rand.nextDouble() * (bb.maxZ - bb.minZ - insetZ * 2);
        if (side == 0) {
            d1 = y + bb.minY - f;
        } else if (side == 1) {
            d1 = y + bb.maxY + f;
        } else if (side == 2) {
            d2 = z + bb.minZ - f;
        } else if (side == 3) {
            d2 = z + bb.maxZ + f;
        } else if (side == 4) {
            d0 = x + bb.minX - f;
        } else if (side == 5) {
            d0 = x + bb.maxX + f;
        }
        EntityDiggingFX digFX = new EntityDiggingFX(world, d0, d1, d2, 0.0D, 0.0D, 0.0D, this, side, 0);
        digFX.applyColourMultiplier(x, y, z).multiplyVelocity(0.2F).multipleParticleScaleBy(0.5F);
        digFX.setParticleIcon(tex);
        effectRenderer.addEffect(digFX);
    }

    @Override
    protected void init() {
        super.init();
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            EnderIO.guiHandler.registerGuiHandler(GuiHandler.GUI_ID_EXTERNAL_CONNECTION_BASE + dir.ordinal(), this);
        }
        EnderIO.guiHandler.registerGuiHandler(GuiHandler.GUI_ID_EXTERNAL_CONNECTION_SELECTOR, this);
        MinecraftForge.EVENT_BUS.register(new EventHandler());
    }

    @Override
    public ItemStack getPickBlock(MovingObjectPosition target, World world, int x, int y, int z) {
        return getPickBlock(target, world, x, y, z, null);
    }

    @Override
    public ItemStack getPickBlock(MovingObjectPosition target, World world, int x, int y, int z, EntityPlayer player) {
        ItemStack ret = null;
        if (MicroblocksUtil.supportMicroblocks()) {
            ret = getMicroblockPickBlock(target, world, x, y, z, player);
        }
        if (ret == null && target != null && target.hitInfo instanceof CollidableComponent) {
            CollidableComponent cc = (CollidableComponent) target.hitInfo;
            TileEntity te = world.getTileEntity(x, y, z);
            if (!(te instanceof TileConduitBundle)) {
                return null;
            }
            TileConduitBundle bundle = (TileConduitBundle) te;
            IConduit conduit = bundle.getConduit(cc.conduitType);
            if (conduit != null) {
                ret = conduit.createItem();
            } else if (cc.conduitType == null && bundle.getFacadeId() != null) {
                // use the facde
                ret = new ItemStack(EnderIO.itemConduitFacade, 1, 0);
                PainterUtil.setSourceBlock(ret, bundle.getFacadeId(), bundle.getFacadeMetadata());
            }
        }
        return ret;
    }

    @Override
    public int getDamageValue(World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof IConduitBundle)) {
            return 0;
        }
        IConduitBundle bun = (IConduitBundle) te;
        return bun.getFacadeId() != null ? bun.getFacadeMetadata() : 0;
    }

    @Override
    public int quantityDropped(Random r) {
        return 0;
    }

    public IIcon getConnectorIcon(Object data) {
        return data == ConduitConnectorType.EXTERNAL ? connectorIconExternal : connectorIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister IIconRegister) {
        connectorIcon = IIconRegister.registerIcon(KEY_CONNECTOR_ICON);
        connectorIconExternal = IIconRegister.registerIcon(KEY_CONNECTOR_ICON_EXTERNAL);
        blockIcon = connectorIcon;
    }

    @Override
    public boolean isSideSolid(IBlockAccess world, int x, int y, int z, ForgeDirection side) {
        if (MicroblocksUtil.supportMicroblocks() && IM__isSideSolid(world, x, y, z, side)) {
            return true;
        }

        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof IConduitBundle)) {
            return false;
        }
        IConduitBundle con = (IConduitBundle) te;
        return con.hasFacade();
    }

    @Override
    public boolean canBeReplacedByLeaves(IBlockAccess world, int x, int y, int z) {
        return false;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public int getRenderType() {
        return rendererId;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public int getLightOpacity(IBlockAccess world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof IConduitBundle)) {
            return super.getLightOpacity(world, x, y, z);
        }
        IConduitBundle con = (IConduitBundle) te;
        return con.getLightOpacity();
    }

    @Override
    public int getLightValue(IBlockAccess world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof IConduitBundle)) {
            return super.getLightValue(world, x, y, z);
        }
        IConduitBundle con = (IConduitBundle) te;
        if (con.getFacadeId() != null && con.getFacadeId().isOpaqueCube()) {
            return 0;
        }
        Collection<IConduit> conduits = con.getConduits();
        int result = 0;
        for (IConduit conduit : conduits) {
            result += conduit.getLightValue();
        }
        return result;
    }

    @Override
    public float getBlockHardness(World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof IConduitBundle && ((IConduitBundle) te).getFacadeType() == FacadeType.HARDENED
                ? blockHardness * 10
                : blockHardness;
    }

    @Override
    public float getExplosionResistance(Entity par1Entity, World world, int x, int y, int z, double explosionX,
            double explosionY, double explosionZ) {
        float resist = getExplosionResistance(par1Entity);
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof IConduitBundle && ((IConduitBundle) te).getFacadeType() == FacadeType.HARDENED
                ? resist * 10
                : resist;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getRenderBlockPass() {
        return 1;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean canRenderInPass(int pass) {
        return pass == 0 || pass == 1;
    }

    @Override
    public int isProvidingStrongPower(IBlockAccess world, int x, int y, int z, int par5) {
        IRedstoneConduit con = getRedstoneConduit(world, x, y, z);
        if (con == null) {
            return 0;
        }
        return con.isProvidingStrongPower(ForgeDirection.getOrientation(par5));
    }

    @Override
    public int isProvidingWeakPower(IBlockAccess world, int x, int y, int z, int par5) {
        IRedstoneConduit con = getRedstoneConduit(world, x, y, z);
        if (con == null) {
            return 0;
        }

        return con.isProvidingWeakPower(ForgeDirection.getOrientation(par5));
    }

    @Override
    public boolean canProvidePower() {
        return true;
    }

    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        TileEntity rte = world.getTileEntity(x, y, z);
        if (!(rte instanceof IConduitBundle)) {
            return true;
        }
        IConduitBundle te = (IConduitBundle) rte;

        boolean breakBlock = true;
        List<ItemStack> drop = new ArrayList<>();
        if (ConduitUtil.isSolidFacadeRendered(te, player)) {
            breakBlock = false;
            ItemStack fac = new ItemStack(EnderIO.itemConduitFacade, 1, te.getFacadeType().ordinal());
            PainterUtil.setSourceBlock(fac, te.getFacadeId(), te.getFacadeMetadata());
            drop.add(fac);
            ConduitUtil.playBreakSound(te.getFacadeId().stepSound, world, x, y, z);
            te.setFacadeId(null);
            te.setFacadeMetadata(0);
            te.setFacadeType(FacadeType.BASIC);
        }

        if (breakBlock) {
            List<IConduit> toBreak = getConduitsToBreak(world, x, y, z, player);
            if (!toBreak.isEmpty()) {
                for (IConduit con : toBreak) {
                    if (world.isRemote) {
                        // predict the removal so the piece vanishes at once instead of after a server round trip
                        if (te instanceof TileConduitBundle) {
                            ((TileConduitBundle) te).removeConduitClientSide(con);
                        }
                    } else {
                        te.removeConduit(con);
                        drop.addAll(con.getDrops());
                    }
                }
                ConduitUtil.playBreakSound(Block.soundTypeMetal, world, x, y, z);
            }
        }

        breakBlock = te.getConduits().isEmpty() && !te.hasFacade();

        if (!breakBlock) {
            world.markBlockForUpdate(x, y, z);
        }

        // TODO no microblock sounds...not sure if fixable, need to contact immibis
        // Microblocks only drop when the whole block goes away, otherwise they would be duplicated
        if (breakBlock && MicroblocksUtil.supportMicroblocks()) {
            IM__getDrops(drop, world, x, y, z, te.getEntity().getBlockMetadata(), 0);
        }

        if (!world.isRemote && !player.capabilities.isCreativeMode) {
            for (ItemStack st : drop) {
                Util.dropItems(world, st, x, y, z, false);
            }
        }

        if (breakBlock) {
            world.setBlockToAir(x, y, z);
            return true;
        }
        return false;
    }

    /**
     * Determines which conduits the player would remove by breaking the bundle right now. The same logic is used for
     * the actual removal, the breaking particles, the crack overlay and the selection highlight, so what the player
     * sees is always what gets broken.
     * <p>
     * The closest conduit under the cursor wins. Connector boxes (e.g. the external connector plates) are ignored as
     * long as a conduit can be hit behind them, so a bundle with multiple conduits is
     * always taken apart one conduit at a time. Only if nothing but a connector is hit, the old behaviour applies:
     * conduits without any connection are removed (there is no other way to reach them), or all of them if there are
     * none.
     *
     * @return the conduits to remove, an empty list if the solid facade is targeted or nothing is hit
     */
    public List<IConduit> getConduitsToBreak(World world, int x, int y, int z, EntityPlayer player) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof IConduitBundle)) {
            return new ArrayList<>();
        }
        IConduitBundle te = (IConduitBundle) tile;
        List<IConduit> result = new ArrayList<>();
        if (ConduitUtil.isSolidFacadeRendered(te, player)) {
            return result;
        }

        List<RaytraceResult> results = doRayTraceAll(world, x, y, z, player);
        if (results == null || results.isEmpty()) {
            return result;
        }
        RaytraceResult.sort(Util.getEyePosition(player), results);

        boolean connectorHit = false;
        for (RaytraceResult rt : results) {
            if (rt.component == null) {
                continue;
            }
            Class<? extends IConduit> type = rt.component.conduitType;
            if (type == null) {
                connectorHit = true;
                continue;
            }
            IConduit con = te.getConduit(type);
            if (con != null && ConduitUtil.renderConduit(player, type)) {
                result.add(con);
                return result;
            }
        }

        if (connectorHit) {
            List<IConduit> cons = new ArrayList<>(te.getConduits());
            for (IConduit con : cons) {
                if (con.getConduitConnections().isEmpty() && con.getExternalConnections().isEmpty()
                        && ConduitUtil.renderConduit(player, con)) {
                    result.add(con);
                }
            }
            if (result.isEmpty()) {
                for (IConduit con : cons) {
                    if (ConduitUtil.renderConduit(player, con)) {
                        result.add(con);
                    }
                }
            }
        }
        return result;
    }

    /**
     * @return all collidable components (arms, core, connectors) that belong to the given conduit
     */
    public static List<CollidableComponent> getComponentsOf(IConduitBundle bundle, IConduit con) {
        List<CollidableComponent> result = new ArrayList<>();
        Class<? extends IConduit> type = con.getCollidableType();
        for (CollidableComponent cc : bundle.getCollidableComponents()) {
            if (cc.conduitType == type && cc.bound != null && !result.contains(cc)) {
                result.add(cc);
            }
        }
        return result;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block par5, int par6) {

        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof IConduitBundle)) {
            return;
        }
        IConduitBundle te = (IConduitBundle) tile;
        te.onBlockRemoved();
        world.removeTileEntity(x, y, z);
    }

    @Override
    public void onBlockClicked(World world, int x, int y, int z, EntityPlayer player) {
        ITool tool = ToolUtil.getEquippedTool(player);
        if (!player.isSneaking() || tool == null || !tool.canUse(player.getCurrentEquippedItem(), player, x, y, z)) {
            return;
        }
        ConduitUtil.openConduitGui(world, x, y, z, player);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float par7,
            float par8, float par9) {

        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof IConduitBundle)) {
            return false;
        }
        IConduitBundle bundle = (IConduitBundle) te;

        ItemStack stack = player.getCurrentEquippedItem();
        if (stack != null && stack.getItem() == EnderIO.itemConduitFacade) {
            // add or replace facade
            return handleFacadeClick(world, x, y, z, player, side, bundle, stack);

        } else if (ConduitUtil.isConduitEquipped(player)) {
            // Add conduit
            if (player.isSneaking()) {
                return false;
            }
            if (handleConduitClick(world, x, y, z, player, bundle, stack)) {
                return true;
            }

        } else if (ConduitUtil.isProbeEquipped(player)) {
            // Handle copy / paste of settings
            if (handleConduitProbeClick(world, x, y, z, player, bundle, stack)) {
                return true;
            }
        } else if (ToolUtil.isToolEquipped(player) && player.isSneaking()) {
            // Break conduit with tool
            if (handleWrenchClick(world, x, y, z, player)) {
                return true;
            }
        }

        // Check conduit defined actions
        RaytraceResult closest = doRayTrace(world, x, y, z, player);
        List<RaytraceResult> all = null;
        if (closest != null) {
            all = doRayTraceAll(world, x, y, z, player);
        }

        if (closest != null && closest.component != null && closest.component.data instanceof ConduitConnectorType) {

            ConduitConnectorType conType = (ConduitConnectorType) closest.component.data;
            if (conType == ConduitConnectorType.INTERNAL) {
                boolean result = false;
                // if its a connector pass the event on to all conduits
                for (IConduit con : bundle.getConduits()) {
                    if (ConduitUtil.renderConduit(player, con.getCollidableType())
                            && con.onBlockActivated(player, getHitForConduitType(all, con.getCollidableType()), all)) {
                        bundle.getEntity().markDirty();
                        result = true;
                    }
                }
                if (result) {
                    return true;
                }
            } else {
                if (!world.isRemote) {
                    player.openGui(
                            EnderIO.instance,
                            GuiHandler.GUI_ID_EXTERNAL_CONNECTION_BASE + closest.component.dir.ordinal(),
                            world,
                            x,
                            y,
                            z);
                }
                return true;
            }
        }

        if (closest == null || closest.component == null || closest.component.conduitType == null && all == null) {
            // Nothing of interest hit
            return false;
        }

        // Conduit specific actions
        if (all != null) {
            RaytraceResult.sort(Util.getEyePosition(player), all);
            for (RaytraceResult rr : all) {
                if (ConduitUtil.renderConduit(player, rr.component.conduitType)
                        && !(rr.component.data instanceof ConduitConnectorType)) {

                    IConduit con = bundle.getConduit(rr.component.conduitType);
                    if (con != null && con.onBlockActivated(player, rr, all)) {
                        bundle.getEntity().markDirty();
                        return true;
                    }
                }
            }
        } else {
            IConduit closestConduit = bundle.getConduit(closest.component.conduitType);
            if (closestConduit != null && ConduitUtil.renderConduit(player, closestConduit)
                    && closestConduit.onBlockActivated(player, closest, all)) {
                bundle.getEntity().markDirty();
                return true;
            }
        }
        return false;
    }

    private boolean handleWrenchClick(World world, int x, int y, int z, EntityPlayer player) {
        ITool tool = ToolUtil.getEquippedTool(player);
        if (tool != null) {
            if (tool.canUse(player.getCurrentEquippedItem(), player, x, y, z)) {
                if (!world.isRemote) {
                    removedByPlayer(world, player, x, y, z, true);
                    tool.used(player.getCurrentEquippedItem(), player, x, y, z);
                }
                return true;
            }
        }
        return false;
    }

    private boolean handleConduitProbeClick(World world, int x, int y, int z, EntityPlayer player,
            IConduitBundle bundle, ItemStack stack) {
        if (stack.getItemDamage() != 1) {
            return false; // not in copy paste mode
        }
        RaytraceResult rr = doRayTrace(world, x, y, z, player);
        if (rr == null || rr.component == null) {
            return false;
        }
        return ItemConduitProbe.copyPasteSettings(player, stack, bundle, rr.component.dir);
    }

    private boolean handleConduitClick(World world, int x, int y, int z, EntityPlayer player, IConduitBundle bundle,
            ItemStack stack) {
        IConduitItem equipped = (IConduitItem) stack.getItem();
        if (!bundle.hasType(equipped.getBaseConduitType())) {
            if (!world.isRemote) {
                bundle.addConduit(equipped.createConduit(stack, player));
                ConduitUtil.playBreakSound(soundTypeMetal, world, x, y, z);
                if (!player.capabilities.isCreativeMode) {
                    player.getCurrentEquippedItem().stackSize--;
                }
            }
            return true;
        }
        return false;
    }

    public boolean handleFacadeClick(World world, int x, int y, int z, EntityPlayer player, int side,
            IConduitBundle bundle, ItemStack stack) {
        if (MicroblocksUtil.supportMicroblocks() && hasMicroblocks(bundle)) {
            return false;
        }

        // Add facade
        if (player.isSneaking()) {
            return false;
        }

        Block facadeID = PainterUtil.getSourceBlock(player.getCurrentEquippedItem());
        if (facadeID == null) {
            return false;
        }

        int facadeMeta = PainterUtil.getSourceBlockMetadata(player.getCurrentEquippedItem());
        facadeMeta = PainterUtil.adjustFacadeMetadata(facadeID, facadeMeta, side);
        int facadeType = player.getCurrentEquippedItem().getItemDamage();

        if (bundle.hasFacade()) {
            if (!ConduitUtil.isSolidFacadeRendered(bundle, player)
                    || facadeEquals(bundle, facadeID, facadeMeta, facadeType)) {
                return false;
            }
            if (!world.isRemote && !player.capabilities.isCreativeMode) {
                ItemStack fac = new ItemStack(EnderIO.itemConduitFacade, 1, bundle.getFacadeType().ordinal());
                PainterUtil.setSourceBlock(fac, bundle.getFacadeId(), bundle.getFacadeMetadata());
                Util.dropItems(world, fac, x, y, z, false);
            }
        }

        bundle.setFacadeId(facadeID);
        bundle.setFacadeMetadata(facadeMeta);
        bundle.setFacadeType(FacadeType.VALUES[facadeType]);
        if (!world.isRemote) {
            ConduitUtil.playPlaceSound(facadeID.stepSound, world, x, y, z);
        }
        if (!player.capabilities.isCreativeMode) {
            stack.stackSize--;
        }
        world.markBlockForUpdate(x, y, z);
        bundle.getEntity().markDirty();
        return true;
    }

    private boolean facadeEquals(IConduitBundle bundle, Block facadeID, int facadeMeta, int facadeType) {
        return bundle.getFacadeId().equals(facadeID) && bundle.getFacadeMetadata() == facadeMeta
                && bundle.getFacadeType().ordinal() == facadeType;
    }

    @Override
    public boolean tryRotateFacade(World world, int x, int y, int z, ForgeDirection axis) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof IConduitBundle)) {
            return false;
        }
        IConduitBundle bundle = (IConduitBundle) te;

        int oldMeta = bundle.getFacadeMetadata();
        int newMeta = PainterUtil.rotateFacadeMetadata(bundle.getFacadeId(), oldMeta, axis);
        if (newMeta == oldMeta) {
            return false;
        }

        bundle.setFacadeMetadata(newMeta);
        world.markBlockForUpdate(x, y, z);
        bundle.getEntity().markDirty();
        return true;
    }

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == GuiHandler.GUI_ID_EXTERNAL_CONNECTION_SELECTOR) {
            return null;
        }
        // The server needs the container as it manages the adding and removing of
        // items, which are then sent to the client for display
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof IConduitBundle) {
            return new ExternalConnectionContainer(
                    player.inventory,
                    (IConduitBundle) te,
                    ForgeDirections.DIRECTIONS[id - GuiHandler.GUI_ID_EXTERNAL_CONNECTION_BASE]);
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof IConduitBundle) {
            if (id == GuiHandler.GUI_ID_EXTERNAL_CONNECTION_SELECTOR) {
                return new GuiExternalConnectionSelector((IConduitBundle) te);
            }
            return new GuiExternalConnection(
                    player.inventory,
                    (IConduitBundle) te,
                    ForgeDirections.DIRECTIONS[id - GuiHandler.GUI_ID_EXTERNAL_CONNECTION_BASE]);
        }
        return null;
    }

    private RaytraceResult getHitForConduitType(List<RaytraceResult> all, Class<? extends IConduit> collidableType) {
        for (RaytraceResult rr : all) {
            if (rr.component != null && rr.component.conduitType == collidableType) {
                return rr;
            }
        }
        return null;
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block blockId) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if ((tile instanceof IConduitBundle)) {
            ((IConduitBundle) tile).onNeighborBlockChange(blockId);
        }
    }

    @Override
    public void onNeighborChange(IBlockAccess world, int x, int y, int z, int tileX, int tileY, int tileZ) {
        TileEntity conduit = world.getTileEntity(x, y, z);
        if (conduit instanceof IConduitBundle) {
            ((IConduitBundle) conduit).onNeighborChange(world, x, y, z, tileX, tileY, tileZ);
        }
    }

    @Override
    public void addCollisionBoxesToList(World world, int x, int y, int z, AxisAlignedBB axisalignedbb,
            List<AxisAlignedBB> arraylist, Entity par7Entity) {

        if (MicroblocksUtil.supportMicroblocks()) {
            IM__addCollisionBoxesToList(world, x, y, z, axisalignedbb, arraylist, par7Entity);
        }

        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof IConduitBundle)) {
            return;
        }
        IConduitBundle con = (IConduitBundle) te;
        if (con.getFacadeId() != null) {
            setBlockBounds(0, 0, 0, 1, 1, 1);
            super.addCollisionBoxesToList(world, x, y, z, axisalignedbb, arraylist, par7Entity);
        } else {

            Collection<CollidableComponent> bounds = con.getCollidableComponents();
            for (CollidableComponent bnd : bounds) {
                setBlockBounds(
                        bnd.bound.minX,
                        bnd.bound.minY,
                        bnd.bound.minZ,
                        bnd.bound.maxX,
                        bnd.bound.maxY,
                        bnd.bound.maxZ);
                super.addCollisionBoxesToList(world, x, y, z, axisalignedbb, arraylist, par7Entity);
            }

            if (con.getConduits().isEmpty()) { // just in case
                setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
                super.addCollisionBoxesToList(world, x, y, z, axisalignedbb, arraylist, par7Entity);
            }
        }

        setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getSelectedBoundingBoxFromPool(World world, int x, int y, int z) {

        TileEntity te = world.getTileEntity(x, y, z);
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (!(te instanceof IConduitBundle)) {
            return null;
        }
        IConduitBundle con = (IConduitBundle) te;

        BoundingBox minBB = new BoundingBox(1, 1, 1, 0, 0, 0);

        if (!ConduitUtil.isSolidFacadeRendered(con, EnderIO.proxy.getClientPlayer())) {

            List<RaytraceResult> results = doRayTraceAll(world, x, y, z, player);
            Iterator<RaytraceResult> iter = results.iterator();
            while (iter.hasNext()) {
                CollidableComponent component = iter.next().component;
                if (component == null
                        || (component.conduitType == null && component.data != ConduitConnectorType.EXTERNAL)) {
                    iter.remove();
                }
            }

            // This is an ugly special case, TODO fix this
            for (RaytraceResult hit : results) {
                IInsulatedRedstoneConduit cond = con.getConduit(IInsulatedRedstoneConduit.class);
                if (cond != null && hit.component != null
                        && cond.getExternalConnections().contains(hit.component.dir)
                        && !cond.isSpecialConnection(hit.component.dir)
                        && hit.component.data == InsulatedRedstoneConduit.COLOR_CONTROLLER_ID) {
                    minBB = hit.component.bound;
                }
            }

            if (!minBB.isValid()) {
                RaytraceResult hit = RaytraceResult.getClosestHit(Util.getEyePosition(player), results);
                if (hit != null && hit.component != null && hit.component.bound != null) {
                    minBB = hit.component.bound;
                    if (hit.component.conduitType == null) {
                        ForgeDirection dir = hit.component.dir.getOpposite();
                        float trans = 0.0125f;
                        minBB = minBB.translate(dir.offsetX * trans, dir.offsetY * trans, dir.offsetZ * trans);
                        float scale = 0.7f;
                        minBB = minBB.scale(
                                1 + Math.abs(dir.offsetX) * scale,
                                1 + Math.abs(dir.offsetY) * scale,
                                1 + Math.abs(dir.offsetZ) * scale);
                    } else {
                        minBB = minBB.scale(1.09, 1.09, 1.09);
                    }
                }
            }
        } else {
            minBB = new BoundingBox(0, 0, 0, 1, 1, 1);
        }

        if (!minBB.isValid()) {
            minBB = new BoundingBox(0, 0, 0, 1, 1, 1);
        }

        return AxisAlignedBB.getBoundingBox(
                x + minBB.minX,
                y + minBB.minY,
                z + minBB.minZ,
                x + minBB.maxX,
                y + minBB.maxY,
                z + minBB.maxZ);
    }

    @Override
    public MovingObjectPosition collisionRayTrace(World world, int x, int y, int z, Vec3 origin, Vec3 direction) {

        RaytraceResult raytraceResult = doRayTrace(world, x, y, z, origin, direction, null);
        MovingObjectPosition ret = null;
        if (raytraceResult != null) {
            ret = raytraceResult.movingObjectPosition;
            if (ret != null) {
                ret.hitInfo = raytraceResult.component;
            }
        }

        if (MicroblocksUtil.supportMicroblocks()) {
            return IM__collisionRayTrace(ret, world, x, y, z, origin, direction);
        }

        return ret;
    }

    public RaytraceResult doRayTrace(World world, int x, int y, int z, EntityPlayer entityPlayer) {
        List<RaytraceResult> allHits = doRayTraceAll(world, x, y, z, entityPlayer);
        if (allHits == null) {
            return null;
        }
        Vec3 origin = Util.getEyePosition(entityPlayer);
        return RaytraceResult.getClosestHit(origin, allHits);
    }

    public List<RaytraceResult> doRayTraceAll(World world, int x, int y, int z, EntityPlayer entityPlayer) {
        double pitch = Math.toRadians(entityPlayer.rotationPitch);
        double yaw = Math.toRadians(entityPlayer.rotationYaw);

        double dirX = -Math.sin(yaw) * Math.cos(pitch);
        double dirY = -Math.sin(pitch);
        double dirZ = Math.cos(yaw) * Math.cos(pitch);

        double reachDistance = EnderIO.proxy.getReachDistanceForPlayer(entityPlayer);

        Vec3 origin = Util.getEyePosition(entityPlayer);
        Vec3 direction = origin.addVector(dirX * reachDistance, dirY * reachDistance, dirZ * reachDistance);
        return doRayTraceAll(world, x, y, z, origin, direction, entityPlayer);
    }

    private RaytraceResult doRayTrace(World world, int x, int y, int z, Vec3 origin, Vec3 direction,
            EntityPlayer entityPlayer) {
        List<RaytraceResult> allHits = doRayTraceAll(world, x, y, z, origin, direction, entityPlayer);
        if (allHits == null) {
            return null;
        }
        return RaytraceResult.getClosestHit(origin, allHits);
    }

    protected List<RaytraceResult> doRayTraceAll(World world, int x, int y, int z, Vec3 origin, Vec3 direction,
            EntityPlayer player) {

        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof IConduitBundle)) {
            return null;
        }
        IConduitBundle bundle = (IConduitBundle) te;
        List<RaytraceResult> hits = new ArrayList<>();

        if (player == null) {
            player = EnderIO.proxy.getClientPlayer();
        }

        if (ConduitUtil.isSolidFacadeRendered(bundle, player)) {
            setBlockBounds(0, 0, 0, 1, 1, 1);
            MovingObjectPosition hitPos = super.collisionRayTrace(world, x, y, z, origin, direction);
            if (hitPos != null) {
                hits.add(
                        new RaytraceResult(
                                new CollidableComponent(null, BoundingBox.UNIT_CUBE, ForgeDirection.UNKNOWN, null),
                                hitPos));
            }
        } else {
            ConduitDisplayMode mode = ConduitUtil.getDisplayMode(player);
            Collection<CollidableComponent> components = new ArrayList<>(bundle.getCollidableComponents());
            for (CollidableComponent component : components) {
                if ((component.conduitType != null || mode == ConduitDisplayMode.ALL)
                        && ConduitUtil.renderConduit(player, component.conduitType)) {
                    setBlockBounds(
                            component.bound.minX,
                            component.bound.minY,
                            component.bound.minZ,
                            component.bound.maxX,
                            component.bound.maxY,
                            component.bound.maxZ);
                    MovingObjectPosition hitPos = super.collisionRayTrace(world, x, y, z, origin, direction);
                    if (hitPos != null) {
                        hits.add(new RaytraceResult(component, hitPos));
                    }
                }
            }

            // safety to prevent unbreakable empty bundles in case of a bug
            if (bundle.getConduits().isEmpty() && !ConduitUtil.isFacadeHidden(bundle, player)) {
                setBlockBounds(0, 0, 0, 1, 1, 1);
                MovingObjectPosition hitPos = super.collisionRayTrace(world, x, y, z, origin, direction);
                if (hitPos != null) {
                    hits.add(new RaytraceResult(null, hitPos));
                }
            }
        }

        setBlockBounds(0, 0, 0, 1, 1, 1);

        return hits;
    }

    @Override
    public int getFacadeMetadata(IBlockAccess world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof IConduitBundle)) {
            return 0;
        }
        IConduitBundle cb = (IConduitBundle) te;
        return cb.getFacadeMetadata();
    }

    @Override
    public Block getFacade(IBlockAccess world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof IConduitBundle)) {
            return this;
        }
        IConduitBundle cb = (IConduitBundle) te;
        Block res = cb.getFacadeId();
        if (res == null) {
            return this;
        }
        return res;
    }

    @Override
    public Block getVisualBlock(IBlockAccess world, int x, int y, int z, ForgeDirection side) {
        return getFacade(world, x, y, z, side.ordinal());
    }

    @Override
    public int getVisualMeta(IBlockAccess world, int x, int y, int z, ForgeDirection side) {
        return getFacadeMetadata(world, x, y, z, side.ordinal());
    }

    @Override
    public boolean supportsVisualConnections() {
        return true;
    }

    @Override
    public void onInputsChanged(World world, int x, int y, int z, ForgeDirection side, int[] inputValues) {
        IRedstoneConduit conduit = getRedstoneConduit(world, x, y, z);
        if (conduit == null) {
            return;
        }

        conduit.onInputsChanged(world, x, y, z, side, inputValues);
    }

    @Override
    public void onInputChanged(World world, int x, int y, int z, ForgeDirection side, int inputValue) {
        // Unused because only called in "Single" mode.
    }

    @Override
    public int[] getOutputValues(World world, int x, int y, int z, ForgeDirection side) {
        IRedstoneConduit conduit = getRedstoneConduit(world, x, y, z);
        if (conduit == null) {
            return null;
        }

        return conduit.getOutputValues(world, x, y, z, side);
    }

    @Override
    public int getOutputValue(World world, int x, int y, int z, ForgeDirection side, int subnet) {
        IRedstoneConduit conduit = getRedstoneConduit(world, x, y, z);
        if (conduit == null) {
            return 0;
        }

        return conduit.getOutputValue(world, x, y, z, side, subnet);
    }

    @Override
    @Optional.Method(modid = "MineFactoryReloaded")
    public RedNetConnectionType getConnectionType(World world, int x, int y, int z, ForgeDirection side) {
        IRedstoneConduit conduit = getRedstoneConduit(world, x, y, z);
        if (conduit == null) {
            return RedNetConnectionType.None;
        }
        return conduit.canConnectToExternal(side, false) ? RedNetConnectionType.CableAll : RedNetConnectionType.None;
    }

    private static IRedstoneConduit getRedstoneConduit(IBlockAccess world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof IConduitBundle)) {
            return null;
        }
        IConduitBundle bundle = (IConduitBundle) te;
        return bundle.getConduit(IRedstoneConduit.class);
    }

    public ItemStack getMicroblockPickBlock(MovingObjectPosition target, World world, int x, int y, int z,
            EntityPlayer player) {
        return IMultipartSystem.instance.hook_getPickBlock(target, world, x, y, z, player);
    }

    // IM Hooks

    private boolean IM__isSideSolid(IBlockAccess world, int x, int y, int z, ForgeDirection side) {
        return IMultipartSystem.instance.hook_isSideSolid(world, x, y, z, side);
    }

    @SideOnly(Side.CLIENT)
    private boolean IM__addDestroyEffects(World world, int x, int y, int z, int meta, EffectRenderer effectRenderer) {
        return IMultipartSystem.instance.hook_addDestroyEffects(world, x, y, z, meta, effectRenderer);
    }

    @SideOnly(Side.CLIENT)
    private boolean IM__addHitEffects(World worldObj, MovingObjectPosition target, EffectRenderer effectRenderer) {
        return IMultipartSystem.instance.hook_addHitEffects(worldObj, target, effectRenderer);
    }

    private MovingObjectPosition IM__collisionRayTrace(MovingObjectPosition cur, World world, int x, int y, int z,
            Vec3 src, Vec3 dst) {
        return IMultipartSystem.instance.hook_collisionRayTrace(cur, world, x, y, z, src, dst);
    }

    private void IM__addCollisionBoxesToList(World world, int x, int y, int z, AxisAlignedBB mask,
            List<AxisAlignedBB> list, Entity entity) {
        IMultipartSystem.instance.hook_addCollisionBoxesToList(world, x, y, z, mask, list, entity);
    }

    private ArrayList<ItemStack> IM__getDrops(List<ItemStack> cur, World world, int x, int y, int z, int metadata,
            int fortune) {
        return IMultipartSystem.instance.hook_getDrops(cur, world, x, y, z, metadata, fortune);
    }

    private boolean hasMicroblocks(IConduitBundle bundle) {
        return !bundle.getCoverSystem().getAllParts().isEmpty();
    }

    public class EventHandler {

        @SideOnly(Side.CLIENT)
        @SubscribeEvent
        public void onPlaySound(PlaySoundSourceEvent event) {
            String path = event.sound.getPositionedSoundLocation().getResourcePath();
            if ("silence.step".equals(path)) {
                ISound snd = event.sound;
                World world = EnderIO.proxy.getClientWorld();
                if (world == null) return;
                BlockCoord bc = new BlockCoord(snd.getXPosF(), snd.getYPosF(), snd.getZPosF());
                TileEntity te = bc.getTileEntity(world);
                if (te instanceof TileConduitBundle && ((TileConduitBundle) te).hasFacade()) {
                    Block facade = getFacade(world, bc.x, bc.y, bc.z, -1);
                    ConduitUtil.playHitSound(facade.stepSound, world, bc.x, bc.y, bc.z);
                } else {
                    ConduitUtil.playHitSound(Block.soundTypeMetal, world, bc.x, bc.y, bc.z);
                }
            }
        }

        @SideOnly(Side.CLIENT)
        @SubscribeEvent
        public void onPlaySoundAtEntity(PlaySoundAtEntityEvent event) {
            String path = event.name;
            World world = event.entity.worldObj;
            if ("EnderIO:silence.step".equals(path) && world.isRemote) {
                BlockCoord bc = new BlockCoord(event.entity.posX, event.entity.posY - 2, event.entity.posZ);
                TileEntity te = bc.getTileEntity(world);
                if (te instanceof TileConduitBundle && ((TileConduitBundle) te).hasFacade()) {
                    Block facade = getFacade(world, bc.x, bc.y, bc.z, -1);
                    ConduitUtil.playStepSound(facade.stepSound, world, bc.x, bc.y, bc.z);
                } else {
                    ConduitUtil.playStepSound(Block.soundTypeMetal, world, bc.x, bc.y, bc.z);
                }
            }
        }

        /**
         * Outlines every part of the conduit that would be broken instead of only the single segment under the cursor.
         * This makes it obvious which conduit is targeted in a crowded bundle.
         */
        @SideOnly(Side.CLIENT)
        @SubscribeEvent
        public void onDrawBlockHighlight(DrawBlockHighlightEvent event) {
            MovingObjectPosition target = event.target;
            if (target == null || target.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                    || event.subID != 0
                    || !(target.hitInfo instanceof CollidableComponent)) {
                return;
            }
            EntityPlayer player = event.player;
            World world = player.worldObj;
            int x = target.blockX, y = target.blockY, z = target.blockZ;
            if (world.getBlock(x, y, z) != BlockConduitBundle.this) {
                return;
            }
            TileEntity te = world.getTileEntity(x, y, z);
            if (!(te instanceof IConduitBundle)) {
                return;
            }
            IConduitBundle bundle = (IConduitBundle) te;
            CollidableComponent hit = (CollidableComponent) target.hitInfo;
            if (hit.conduitType == null || InsulatedRedstoneConduit.COLOR_CONTROLLER_ID.equals(hit.data)
                    || ConduitUtil.isSolidFacadeRendered(bundle, player)) {
                // facades, external connectors and the color controller keep the normal single box
                return;
            }
            List<IConduit> targets = getConduitsToBreak(world, x, y, z, player);
            if (targets.size() != 1) {
                return;
            }
            List<CollidableComponent> components = getComponentsOf(bundle, targets.get(0));
            if (components.isEmpty()) {
                return;
            }

            double dx = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.partialTicks;
            double dy = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.partialTicks;
            double dz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.partialTicks;
            final double grow = 0.002;

            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(770, 771, 1, 0);
            GL11.glColor4f(0.0F, 0.0F, 0.0F, 0.4F);
            GL11.glLineWidth(2.0F);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDepthMask(false);
            for (CollidableComponent cc : components) {
                BoundingBox bb = cc.bound;
                RenderGlobal.drawOutlinedBoundingBox(
                        AxisAlignedBB.getBoundingBox(
                                x + bb.minX - grow - dx,
                                y + bb.minY - grow - dy,
                                z + bb.minZ - grow - dz,
                                x + bb.maxX + grow - dx,
                                y + bb.maxY + grow - dy,
                                z + bb.maxZ + grow - dz),
                        -1);
            }
            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_BLEND);
            event.setCanceled(true);
        }

        @SubscribeEvent
        public void onBreakSpeed(BreakSpeed event) {
            if (event.block == BlockConduitBundle.this) {
                ItemStack held = event.entityPlayer.getCurrentEquippedItem();
                if (held == null || held.getItem().getHarvestLevel(held, "pickaxe") == -1) {
                    event.newSpeed += 2;
                }
                IConduitBundle te = (IConduitBundle) event.entity.worldObj.getTileEntity(event.x, event.y, event.z);
                if (te != null && te.getFacadeType() == FacadeType.HARDENED) {
                    if (!ConduitUtil.isSolidFacadeRendered(te, event.entityPlayer)) {
                        event.newSpeed *= 6;
                    } else {
                        event.newSpeed *= 2;
                    }
                }
            }
        }
    }
}
